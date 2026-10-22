package app.magen.lock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.UserManager
import android.provider.Settings

/**
 * System-level enforcement. Everything here is a no-op unless Magen is the Device Owner
 * ("Strong mode"). Without it, the accessibility service does the blocking.
 */
object Policy {
    /** Diagnostic from the most recent attempt to release Device Owner, for UI troubleshooting. */
    @Volatile
    var lastReleaseOwnerError: String? = null
        private set

    /** Diagnostic when uninstall preparation cannot safely remove policy restrictions. */
    @Volatile
    var lastUninstallError: String? = null
        private set

    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)

    fun isAdmin(ctx: Context): Boolean = dpm(ctx).isAdminActive(admin(ctx))

    /** True when [component] (for example Magen Keeper's receiver) is an enabled device admin. */
    fun isActiveAdmin(ctx: Context, component: ComponentName): Boolean = dpm(ctx).isAdminActive(component)

    fun isOwner(ctx: Context): Boolean = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    fun accessibilityOn(ctx: Context): Boolean {
        if (GuardService.instance != null) return true
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val c = ComponentName(ctx, GuardService::class.java)
        return enabled.contains(c.flattenToString()) || enabled.contains(c.flattenToShortString())
    }

    /** Set after the root removal moved the owner file: stops [apply] from re-adding restrictions before the reboot. */
    @Volatile
    var ownerRemovalPending = false

    private const val OWNER_FILE_NEW = "/data/system/device_owner_2.xml"
    private const val OWNER_FILE_OLD = "/data/system/device_owner.xml"

    private fun su(cmd: String): Pair<Int, String> {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        return p.waitFor() to out
    }

    /** True when a root shell can be obtained (may show the root manager's prompt). Call off the main thread. */
    fun rootAvailable(): Boolean = runCatching {
        val (code, out) = su("id")
        code == 0 && out.contains("uid=0")
    }.getOrDefault(false)

    /**
     * Advanced, unofficial removal for rooted phones: clears Magen's restrictions, then moves the file where
     * Android stores the Device Owner registration to a backup. A reboot finishes the job. Returns null on
     * success, or a short English reason. Call off the main thread. Does not work on every device or version.
     */
    fun releaseOwnerViaRoot(ctx: Context): String? = try {
        if (!rootAvailable()) {
            "Root access is not available or was denied."
        } else {
            val file = listOf(OWNER_FILE_NEW, OWNER_FILE_OLD).firstOrNull { su("test -f $it").first == 0 }
            when {
                file == null -> "No Device Owner file found in /data/system. Nothing was changed."
                else -> {
                    // Still the owner here, so the system can clean up what Magen set.
                    if (isOwner(ctx)) clearOwnerRestrictions(dpm(ctx), admin(ctx), ctx)
                    ownerRemovalPending = true
                    // A unique name each time, so an older backup is never overwritten.
                    val backup = "$file.magen-backup-${System.currentTimeMillis() / 1000}"
                    val (code, out) = su("test -e $backup || mv $file $backup")
                    if (code == 0) {
                        null
                    } else {
                        ownerRemovalPending = false
                        "Moving the file failed (exit $code): $out"
                    }
                }
            }
        }
    } catch (e: Throwable) {
        "${e.javaClass.name}: ${e.message ?: "no details"}"
    }

    /**
     * Hands Device Owner to another app that supports it (Magen Keeper), using the official
     * transferOwnership() API. Magen's own restrictions are cleared first because a transfer carries
     * the owner's policies over to the new owner. Returns null on success, else a short reason.
     */
    fun transferOwner(ctx: Context, target: ComponentName): String? {
        if (Build.VERSION.SDK_INT < 28) return "Transferring Device Owner needs Android 9 or newer."
        return try {
            if (!isOwner(ctx)) {
                "Magen is not the Device Owner."
            } else {
                val d = dpm(ctx)
                ownerRemovalPending = true
                clearOwnerRestrictions(d, admin(ctx), ctx)
                val extras = PersistableBundle().apply { putString("from_package", ctx.packageName) }
                d.transferOwnership(admin(ctx), target, extras)
                ownerRemovalPending = false
                null
            }
        } catch (e: Throwable) {
            ownerRemovalPending = false
            "${e.javaClass.name}: ${e.message ?: "no details"}\n${deviceInfo()}"
        }
    }

    /** Reboots through root so Android reloads the device-management state. */
    fun rebootViaRoot() {
        runCatching { su("reboot") }
    }

    private fun restrict(d: DevicePolicyManager, a: ComponentName, key: String, on: Boolean) {
        if (on) d.addUserRestriction(a, key) else d.clearUserRestriction(a, key)
    }

    /** Re-applies every restriction from the current [Store] values. Safe to call often. */
    fun apply() {
        val ctx = Store.app
        if (!isOwner(ctx) || ownerRemovalPending) return
        val d = dpm(ctx)
        val a = admin(ctx)
        val protectNow = Store.protect && !Store.uninstallAllowed()
        runCatching {
            d.setUninstallBlocked(a, ctx.packageName, protectNow)
            if (Build.VERSION.SDK_INT >= 30) {
                d.setUserControlDisabledPackages(a, if (protectNow) listOf(ctx.packageName) else emptyList())
            }
            restrict(d, a, UserManager.DISALLOW_SAFE_BOOT, protectNow)
            restrict(d, a, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, Store.blockApk)
            if (Build.VERSION.SDK_INT >= 29) {
                restrict(d, a, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, Store.blockApk)
            }
            // Several pages can share one system restriction, so it is on if ANY of them is selected.
            Catalog.settings.filter { it.restriction != null }.groupBy { it.restriction!! }.forEach { (key, cats) ->
                restrict(d, a, key, cats.any { it.id in Store.blockedSettings })
            }
        }
    }

    /**
     * Clears restrictions before surrendering Device Owner. Crucially, verify Android actually
     * removed the uninstall block; never drop Device Owner silently while that block may remain.
     */
    private fun clearOwnerRestrictions(d: DevicePolicyManager, a: ComponentName, ctx: Context): Boolean {
        val keys = Catalog.settings.mapNotNull { it.restriction }.distinct() + listOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY,
        )
        keys.forEach { runCatching { d.clearUserRestriction(a, it) } }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { d.setUserControlDisabledPackages(a, emptyList()) }
        }
        return try {
            d.setUninstallBlocked(a, ctx.packageName, false)
            if (d.isUninstallBlocked(a, ctx.packageName)) {
                lastUninstallError = "Android still reports Magen as uninstall-blocked. Device Owner was kept active; try again or copy this diagnostic."
                false
            } else {
                true
            }
        } catch (e: Throwable) {
            lastUninstallError = "${e.javaClass.name}: ${e.message ?: "Android refused to clear the uninstall block"}"
            false
        }
    }

    /**
     * Drops Device Owner status only: clears every restriction this app set, then gives up
     * Device Owner, but keeps regular device-admin rights so "regular" mode can still lock and
     * block via the accessibility service. The app itself stays installed. Safe to call even if
     * Magen is not currently the Device Owner.
     */
    @Suppress("DEPRECATION")
    fun releaseOwner(ctx: Context): Boolean {
        lastReleaseOwnerError = null
        return try {
            if (!isOwner(ctx)) return true
            val d = dpm(ctx)
            val a = admin(ctx)
            if (!clearOwnerRestrictions(d, a, ctx)) {
                lastReleaseOwnerError = lastUninstallError ?: "Could not verify that the uninstall block was cleared."
                return false
            }
            d.clearDeviceOwnerApp(ctx.packageName)
            val released = !isOwner(ctx)
            if (!released) {
                lastReleaseOwnerError = "Android returned from clearDeviceOwnerApp(), but still reports this app as Device Owner.\n${deviceInfo()}"
            }
            released
        } catch (e: Throwable) {
            lastReleaseOwnerError = "${e.javaClass.name}: ${e.message ?: "no details"}\n${deviceInfo()}"
            false
        }
    }

    private fun deviceInfo() =
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"

    /**
     * Prepares Magen for user-approved uninstall. Do not launch Android's uninstall UI unless
     * the uninstall block was verified clear and Device Owner was actually released.
     */
    @Suppress("DEPRECATION")
    fun release(ctx: Context): Boolean {
        lastUninstallError = null
        return try {
            val d = dpm(ctx)
            val a = admin(ctx)
            if (isOwner(ctx)) {
                if (!clearOwnerRestrictions(d, a, ctx)) return false
                d.clearDeviceOwnerApp(ctx.packageName)
                if (isOwner(ctx)) {
                    lastUninstallError = "Android did not release Device Owner. Magen has not opened the uninstall screen."
                    return false
                }
            }
            if (d.isAdminActive(a)) d.removeActiveAdmin(a)
            true
        } catch (e: Throwable) {
            lastUninstallError = "${e.javaClass.name}: ${e.message ?: "Could not prepare Magen for uninstall"}"
            false
        }
    }
}
