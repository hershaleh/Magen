package app.magen.lock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.UserManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    /** Until this uptime, losing the device-admin status is expected (release, transfer) and raises no alert. */
    @Volatile
    var adminLossExpectedUntil = 0L

    fun expectAdminLoss() {
        adminLossExpectedUntil = android.os.SystemClock.uptimeMillis() + 30_000L
    }

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
                    // Stop apply() first, then clear while Magen is still the owner (only the owner can clear
                    // the system's uninstall block). If that cannot be verified, do NOT move the file: ownership
                    // would end with the block still stored by Android.
                    if (!isOwner(ctx)) {
                        "Magen is not the active Device Owner. Refusing to move the system owner file because its policy state cannot be safely verified."
                    } else {
                        ownerRemovalPending = true
                        val cleared = clearOwnerRestrictions(dpm(ctx), admin(ctx), ctx)
                        if (!cleared) {
                            ownerRemovalPending = false
                            lastUninstallError ?: "Could not clear Magen's uninstall block first. Nothing was changed."
                        } else {
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
            }
        }
    } catch (e: Throwable) {
        ownerRemovalPending = false
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
                expectAdminLoss()
                if (!clearOwnerRestrictions(d, admin(ctx), ctx)) {
                    ownerRemovalPending = false
                    return lastUninstallError ?: "Could not clear Magen's uninstall block first. Nothing was changed."
                }
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

    /** DevicePolicyManager.getUserRestrictions(ComponentName) was added in API 24.
     * On API 23, read the current user's effective restrictions through UserManager instead. */
    private fun restrictions(d: DevicePolicyManager, a: ComponentName, ctx: Context): android.os.Bundle =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) d.getUserRestrictions(a)
        else ctx.getSystemService(UserManager::class.java).userRestrictions

    private fun restrict(d: DevicePolicyManager, a: ComponentName, key: String, on: Boolean) {
        if (on) d.addUserRestriction(a, key) else d.clearUserRestriction(a, key)
    }

    /** Observable policy error; Compose updates the Home status as soon as enforcement fails. */
    var lastApplyError by mutableStateOf<String?>(null)
        private set

    /** Re-applies every restriction from the current Store values without hiding failures. */
    fun apply() {
        val ctx = Store.app
        if (ownerRemovalPending || !isOwner(ctx)) {
            lastApplyError = null
            return
        }
        try {
            val d = dpm(ctx)
            val a = admin(ctx)
            val protectNow = Store.protect && !Store.uninstallAllowed()
            clearLegacyUninstallBlock(d, a, ctx)
            if (d.isUninstallBlocked(a, ctx.packageName)) {
                throw IllegalStateException("Android still reports Magen as uninstall-blocked")
            }
            if (Build.VERSION.SDK_INT >= 30) {
                val expected = if (protectNow) listOf(ctx.packageName) else emptyList()
                d.setUserControlDisabledPackages(a, expected)
                if (d.getUserControlDisabledPackages(a).toSet() != expected.toSet()) {
                    throw IllegalStateException("Android did not apply the expected user-control package list")
                }
            }
            fun applyAndVerify(key: String, enabled: Boolean) {
                restrict(d, a, key, enabled)
                val effective = restrictions(d, a, ctx).getBoolean(key)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // API 24+ can report the restrictions owned by this specific admin.
                    if (effective != enabled) {
                        throw IllegalStateException("Android did not apply restriction: " + key)
                    }
                } else if (enabled && !effective) {
                    // API 23 exposes only the aggregate restrictions for the whole user.
                    // When disabling, another admin may legitimately keep the restriction enabled;
                    // do not report that as Magen failing to clear its own policy.
                    throw IllegalStateException("Android did not apply restriction: " + key)
                }
            }
            applyAndVerify(UserManager.DISALLOW_SAFE_BOOT, protectNow)
            applyAndVerify(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, Store.blockingEnabled && Store.blockApk)
            if (Build.VERSION.SDK_INT >= 29) {
                applyAndVerify(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, Store.blockingEnabled && Store.blockApk)
            }
            Catalog.settings.filter { it.restriction != null }.groupBy { it.restriction!! }.forEach { (key, cats) ->
                applyAndVerify(key, Store.blockingEnabled && cats.any { it.id in Store.blockedSettings })
            }
            lastApplyError = null
        } catch (e: Throwable) {
            lastApplyError = e.javaClass.simpleName + ": " + (e.message ?: "Android rejected a policy change")
        }
    }
    /**
     * Older Magen versions called setUninstallBlocked(true). Android stores that flag persistently (in
     * package-restrictions.xml) and it does NOT disappear when Device Owner ends, which left Magen impossible
     * to uninstall (DELETE_FAILED_OWNER_BLOCKED) even with `dpm list-owners` saying "no owners". The Device Owner
     * package is already protected from uninstall by Android itself, so Magen no longer sets the flag at all and
     * removes any leftover while it is still the owner.
     */
    private fun clearLegacyUninstallBlock(d: DevicePolicyManager, a: ComponentName, ctx: Context) {
        if (d.isUninstallBlocked(a, ctx.packageName)) d.setUninstallBlocked(a, ctx.packageName, false)
    }

    /**
     * True when Android still blocks uninstalling Magen although Magen is not the Device Owner. Only a Device
     * Owner can clear that block, so recovery means becoming Device Owner once (Magen then clears it itself).
     */
    fun staleUninstallBlock(ctx: Context): Boolean {
        if (isOwner(ctx)) return false
        // A failed system query is not proof that the stale block is absent. Let the caller fail closed.
        return dpm(ctx).isUninstallBlocked(null, ctx.packageName)
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
        return try {
            keys.forEach { key ->
                d.clearUserRestriction(a, key)
                if (restrictions(d, a, ctx).getBoolean(key)) {
                    throw IllegalStateException("Android still reports restriction after clearing: " + key)
                }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                d.setUserControlDisabledPackages(a, emptyList())
                if (d.getUserControlDisabledPackages(a).isNotEmpty()) {
                    throw IllegalStateException("Android still reports user-control-disabled packages")
                }
            }
            d.setUninstallBlocked(a, ctx.packageName, false)
            if (d.isUninstallBlocked(a, ctx.packageName)) {
                throw IllegalStateException("Android still reports Magen as uninstall-blocked")
            }
            lastUninstallError = null
            true
        } catch (e: Throwable) {
            lastUninstallError = e.javaClass.simpleName + ": " + (e.message ?: "Android refused to clear a policy restriction")
            false
        }
    }

    /**
     * Drops Device Owner status only: clears every restriction this app set, then gives up
     * Device Owner, but keeps regular device-admin rights so "regular" mode can still lock and
     * block via the accessibility service. The app itself stays installed. Safe to call even if
     * Magen is not currently the Device Owner.
     */
    fun releaseOwner(ctx: Context): Boolean {
        ownerRemovalPending = true
        return try {
            releaseOwnerNow(ctx)
        } finally {
            // Never leave policy enforcement paused, even if an unexpected failure escapes releaseOwnerNow().
            ownerRemovalPending = false
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseOwnerNow(ctx: Context): Boolean {
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

    /** One line each for what Magen can see, plus the command that shows what Android itself has recorded. */
    fun stateSummary(ctx: Context): String =
        "Device Owner: ${if (isOwner(ctx)) "yes" else "no"} / Device admin: ${if (isAdmin(ctx)) "yes" else "no"}\n" +
            lastAdminLoss(ctx) +
            "Android's own record: adb shell dumpsys device_policy"

    /** When Android last removed Magen as device admin, and whether Magen itself had asked for it. */
    private fun lastAdminLoss(ctx: Context): String {
        val sp = ctx.applicationContext.getSharedPreferences("magen", Context.MODE_PRIVATE)
        val at = sp.getLong("adminLostAt", 0L)
        if (at == 0L) return ""
        val whenText = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(at))
        val why = if (sp.getBoolean("adminLostExpected", false)) "requested by Magen" else "NOT requested by Magen"
        return "Last device-admin loss: $whenText ($why)\n"
    }

    private fun deviceInfo() =
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"

    /**
     * Prepares Magen for user-approved uninstall. Do not launch Android's uninstall UI unless
     * the uninstall block was verified clear and Device Owner was actually released.
     */
    fun release(ctx: Context): Boolean {
        ownerRemovalPending = true
        return try {
            expectAdminLoss()
            releaseNow(ctx)
        } finally {
            // Restore normal enforcement on every exit path; the release operation itself verifies success.
            ownerRemovalPending = false
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseNow(ctx: Context): Boolean {
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
            if (d.isAdminActive(a)) {
                d.removeActiveAdmin(a)
                // Do not block the UI thread waiting for Android's asynchronous admin-state update.
                // Fail closed if the record is still present; the user can retry after Android finishes.
                if (d.isAdminActive(a)) {
                    lastUninstallError = "Android has started removing Magen's device-admin permission, but still lists it as active. Wait a few seconds and try again; the uninstall screen has not been opened."
                    return false
                }
            }
            if (staleUninstallBlock(ctx)) {
                lastUninstallError = "Android still blocks uninstalling Magen (a leftover from an older Strong mode). See More, \"Leftover uninstall block\"."
                return false
            }
            true
        } catch (e: Throwable) {
            lastUninstallError = "${e.javaClass.name}: ${e.message ?: "Could not prepare Magen for uninstall"}"
            false
        }
    }
}
