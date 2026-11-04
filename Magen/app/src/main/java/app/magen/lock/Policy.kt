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
        if (!isOwner(ctx)) {
            "Magen is not the Device Owner. Root removal was refused so another app's Device Owner file cannot be touched."
        } else if (!rootAvailable()) {
            "Root access is not available or was denied."
        } else {
            val file = listOf(OWNER_FILE_NEW, OWNER_FILE_OLD).firstOrNull { su("test -f $it").first == 0 }
            when {
                file == null -> "No Device Owner file found in /data/system. Nothing was changed."
                else -> {
                    // Stop apply() first, then clear while Magen is still the owner (only the owner can clear
                    // the system's uninstall block). If that cannot be verified, do NOT move the file: ownership
                    // would end with the block still stored by Android.
                    ownerRemovalPending = true
                    val cleared = !isOwner(ctx) || clearOwnerRestrictions(dpm(ctx), admin(ctx), ctx)
                    if (!cleared) {
                        ownerRemovalPending = false
                        if (isOwner(ctx)) apply()
                        lastUninstallError ?: "Could not clear Magen's uninstall block first. Nothing was changed."
                    } else {
                        // A unique name each time, so an older backup is never overwritten.
                        val backup = "$file.magen-backup-${System.currentTimeMillis()}"
                        // A pre-existing backup must be treated as a failure, not as success: the old
                        // shell expression (test -e backup || mv source backup) returned 0 when the
                        // backup already existed, even though the owner file had not been moved.
                        val command = "if [ -e '$backup' ]; then echo 'Backup already exists'; exit 17; fi; " +
                            "mv '$file' '$backup' && test ! -e '$file' && test -e '$backup'"
                        val (code, out) = su(command)
                        if (code == 0) {
                            null
                        } else {
                            ownerRemovalPending = false
                            if (isOwner(ctx)) apply()
                            "Moving the file failed (exit $code): $out"
                        }
                    }
                }
            }
        }
    } catch (e: Throwable) {
        ownerRemovalPending = false
        if (isOwner(ctx)) runCatching { apply() }
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
                    apply()
                    return lastUninstallError ?: "Could not clear Magen's uninstall block first. Nothing was changed."
                }
                val extras = PersistableBundle().apply { putString("from_package", ctx.packageName) }
                d.transferOwnership(admin(ctx), target, extras)
                ownerRemovalPending = false
                null
            }
        } catch (e: Throwable) {
            ownerRemovalPending = false
            if (isOwner(ctx)) runCatching { apply() }
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
    /** Last Device Owner policy application result. Null means all attempted operations passed read-back. */
    @Volatile
    var lastApplyError: String? = null
        private set

    @Volatile
    var lastApplyAt: Long = 0L
        private set

    /** Re-applies policy and records each failure instead of silently swallowing it. */
    fun apply() {
        val ctx = Store.app
        if (!isOwner(ctx) || ownerRemovalPending) return
        val d = dpm(ctx)
        val a = admin(ctx)
        val protectNow = Store.protect && !Store.uninstallAllowed()
        val failures = mutableListOf<String>()

        fun attempt(label: String, action: () -> Unit) {
            try {
                action()
            } catch (e: Throwable) {
                failures += "$label: ${e.javaClass.simpleName}: ${e.message ?: "failed"}"
            }
        }

        attempt("Clear legacy uninstall block") { clearLegacyUninstallBlock(d, a, ctx) }
        if (Build.VERSION.SDK_INT >= 30) {
            attempt("Protect Magen from user control") {
                d.setUserControlDisabledPackages(a, if (protectNow) listOf(ctx.packageName) else emptyList())
            }
        }
        attempt("Safe boot restriction") { restrict(d, a, UserManager.DISALLOW_SAFE_BOOT, protectNow) }
        attempt("Unknown-source install restriction") {
            restrict(d, a, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, Store.blockApk)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            attempt("Global unknown-source install restriction") {
                restrict(d, a, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, Store.blockApk)
            }
        }
        // Several pages can share one system restriction, so it is on if ANY of them is selected.
        Catalog.settings.filter { it.restriction != null }.groupBy { it.restriction!! }.forEach { (key, cats) ->
            val enabled = cats.any { it.id in Store.blockedSettings }
            attempt("Settings restriction $key") { restrict(d, a, key, enabled) }
        }

        // Read back Android's recorded policy. A successful API call alone is not proof of enforcement.
        if (Build.VERSION.SDK_INT >= 26) {
            attempt("Verify Android restrictions") {
                val actual = d.getUserRestrictions(a)
                val expected = mutableMapOf(
                    UserManager.DISALLOW_SAFE_BOOT to protectNow,
                    UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES to Store.blockApk,
                )
                if (Build.VERSION.SDK_INT >= 29) {
                    expected[UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY] = Store.blockApk
                }
                Catalog.settings.filter { it.restriction != null }.groupBy { it.restriction!! }.forEach { (key, cats) ->
                    expected[key] = cats.any { it.id in Store.blockedSettings }
                }
                expected.forEach { (key, wanted) ->
                    val found = actual.getBoolean(key, false)
                    if (found != wanted) failures += "Verification $key: expected $wanted, Android reports $found"
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            attempt("Verify Magen user-control protection") {
                val found = d.getUserControlDisabledPackages(a).orEmpty().contains(ctx.packageName)
                if (found != protectNow) {
                    failures += "Verification user-control protection: expected $protectNow, Android reports $found"
                }
            }
        }
        lastApplyAt = System.currentTimeMillis()
        lastApplyError = failures.takeIf { it.isNotEmpty() }?.joinToString("\n")
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
    fun staleUninstallBlock(ctx: Context): Boolean =
        !isOwner(ctx) && runCatching { dpm(ctx).isUninstallBlocked(null, ctx.packageName) }.getOrDefault(false)

    /**
     * Clears restrictions before surrendering Device Owner. Crucially, verify Android actually
     * removed the uninstall block; never drop Device Owner silently while that block may remain.
     */
    private fun clearOwnerRestrictions(d: DevicePolicyManager, a: ComponentName, ctx: Context): Boolean {
        val keys = (Catalog.settings.mapNotNull { it.restriction } + listOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        ) + if (Build.VERSION.SDK_INT >= 29) {
            listOf(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
        } else {
            emptyList()
        }).distinct()
        val failures = mutableListOf<String>()
        keys.forEach { key ->
            try {
                d.clearUserRestriction(a, key)
            } catch (e: Exception) {
                failures += "$key: ${e.javaClass.simpleName}: ${e.message ?: "clear failed"}"
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                d.setUserControlDisabledPackages(a, emptyList())
            } catch (e: Exception) {
                failures += "user-control packages: ${e.javaClass.simpleName}: ${e.message ?: "clear failed"}"
            }
        }
        try {
            d.setUninstallBlocked(a, ctx.packageName, false)
            if (d.isUninstallBlocked(a, ctx.packageName)) {
                failures += "Android still reports Magen as uninstall-blocked"
            }
        } catch (e: Exception) {
            failures += "uninstall block: ${e.javaClass.simpleName}: ${e.message ?: "clear failed"}"
        }

        // Never surrender Device Owner while Android still reports any of our restrictions.
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val remaining = d.getUserRestrictions(a)
                keys.filter { remaining.getBoolean(it, false) }.forEach {
                    failures += "Restriction remains enabled: $it"
                }
            } catch (e: Exception) {
                failures += "Restriction read-back: ${e.javaClass.simpleName}: ${e.message ?: "verification failed"}"
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                if (ctx.packageName in d.getUserControlDisabledPackages(a).orEmpty()) {
                    failures += "Magen remains in user-control disabled packages"
                }
            } catch (e: Exception) {
                failures += "User-control read-back: ${e.javaClass.simpleName}: ${e.message ?: "verification failed"}"
            }
        }
        lastUninstallError = failures.takeIf { it.isNotEmpty() }?.joinToString("\n")
        return failures.isEmpty()
    }

    /**
     * Drops Device Owner status only: clears every restriction this app set, then gives up
     * Device Owner, but keeps regular device-admin rights so "regular" mode can still lock and
     * block via the accessibility service. The app itself stays installed. Safe to call even if
     * Magen is not currently the Device Owner.
     */
    fun releaseOwner(ctx: Context): Boolean {
        ownerRemovalPending = true
        val released = releaseOwnerNow(ctx)
        // Pause apply() only while a release is in flight. Afterwards it must run again: either Magen is still
        // the owner (protection must continue) or it is not, and a later re-provisioning must not be ignored.
        ownerRemovalPending = false
        // If Android refuses release after restrictions were cleared, restore protection immediately.
        if (!released && isOwner(ctx)) apply()
        return released
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
        expectAdminLoss()
        val ready = releaseNow(ctx)
        ownerRemovalPending = false
        // Do not leave an owned device unprotected after a failed uninstall preparation.
        if (!ready && isOwner(ctx)) apply()
        return ready
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
            // Check for a legacy uninstall block before dropping regular device-admin status.
            // Without Device Owner, Magen cannot clear this flag; preserve the remaining admin state
            // and direct the user to the dedicated recovery flow instead.
            if (staleUninstallBlock(ctx)) {
                lastUninstallError =
                    "Android still blocks uninstalling Magen (a leftover from an older Strong mode). " +
                    "Magen's Device Owner status may already have been released, but its regular admin was kept. " +
                    "See More, \"Leftover uninstall block\"."
                return false
            }
            if (d.isAdminActive(a)) {
                d.removeActiveAdmin(a)
                // Do not poll with Thread.sleep here: this method is called from the UI flow.
                // Android may finish removing the admin asynchronously; the user can retry after it completes.
                if (d.isAdminActive(a)) {
                    lastUninstallError =
                        "Android has received the request to remove Magen as device administrator, but still reports it as active. " +
                        "Device Owner has already been released if Magen was in Strong mode. " +
                        "The uninstall screen was not opened. Wait a moment, then retry; no uninstall approval window was opened."
                    return false
                }
            }
            true
        } catch (e: Throwable) {
            lastUninstallError = "${e.javaClass.name}: ${e.message ?: "Could not prepare Magen for uninstall"}"
            false
        }
    }
}
