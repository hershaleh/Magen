package app.magen.lock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings

/**
 * System-level enforcement. Everything here is a no-op unless Magen is the Device Owner
 * ("Strong mode"). Without it, the accessibility service does the blocking.
 */
object Policy {
    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)

    fun isAdmin(ctx: Context): Boolean = dpm(ctx).isAdminActive(admin(ctx))

    fun isOwner(ctx: Context): Boolean = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    fun accessibilityOn(ctx: Context): Boolean {
        if (GuardService.instance != null) return true
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val c = ComponentName(ctx, GuardService::class.java)
        return enabled.contains(c.flattenToString()) || enabled.contains(c.flattenToShortString())
    }

    private fun restrict(d: DevicePolicyManager, a: ComponentName, key: String, on: Boolean) {
        if (on) d.addUserRestriction(a, key) else d.clearUserRestriction(a, key)
    }

    /** Re-applies every restriction from the current [Store] values. Safe to call often. */
    fun apply() {
        val ctx = Store.app
        if (!isOwner(ctx)) return
        val d = dpm(ctx)
        val a = admin(ctx)

        // During the explicit uninstall grace period, do not let the accessibility service's
        // periodic Policy.apply() put strong-mode restrictions back while the user is leaving.
        if (Store.uninstallAllowed()) {
            clearOwnerRestrictions(d, a, ctx)
            return
        }

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

    /** Clears every restriction this app ever set as Device Owner. Safe to call even if not owner. */
    private fun clearOwnerRestrictions(d: DevicePolicyManager, a: ComponentName, ctx: Context) {
        val keys = Catalog.settings.mapNotNull { it.restriction }.distinct() + listOf(
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY,
        )
        keys.forEach { runCatching { d.clearUserRestriction(a, it) } }
        runCatching { d.setUninstallBlocked(a, ctx.packageName, false) }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { d.setUserControlDisabledPackages(a, emptyList()) }
        }
    }

    /**
     * Drops Device Owner status only: clears every restriction this app set, then gives up
     * Device Owner, but keeps regular device-admin rights so "regular" mode can still lock and
     * block via the accessibility service. The app itself stays installed. Safe to call even if
     * Magen is not currently the Device Owner.
     */
    @Suppress("DEPRECATION")
    fun releaseOwner(ctx: Context): Boolean = runCatching {
        if (!isOwner(ctx)) return@runCatching true
        val d = dpm(ctx)
        val a = admin(ctx)
        clearOwnerRestrictions(d, a, ctx)
        d.clearDeviceOwnerApp(ctx.packageName)
        !isOwner(ctx)
    }.getOrDefault(false)

    enum class UninstallPreparation {
        READY,
        OWNER_STILL_ACTIVE,
        ADMIN_STILL_ACTIVE,
    }

    /**
     * Releases Device Owner and admin status before opening Android's uninstall screen.
     * Never reports success just because an API call was attempted: the system state is checked
     * again. Some Android builds may refuse to remove the admin programmatically; in that case
     * the UI must send the user to Device Admin settings instead of pretending uninstall is ready.
     */
    @Suppress("DEPRECATION")
    fun prepareForUninstall(ctx: Context): UninstallPreparation {
        val d = dpm(ctx)
        val a = admin(ctx)

        if (isOwner(ctx)) {
            clearOwnerRestrictions(d, a, ctx)
            runCatching { d.clearDeviceOwnerApp(ctx.packageName) }
            if (isOwner(ctx)) return UninstallPreparation.OWNER_STILL_ACTIVE
        }

        if (d.isAdminActive(a)) {
            runCatching { d.removeActiveAdmin(a) }
        }

        return when {
            isOwner(ctx) -> UninstallPreparation.OWNER_STILL_ACTIVE
            d.isAdminActive(a) -> UninstallPreparation.ADMIN_STILL_ACTIVE
            else -> UninstallPreparation.READY
        }
    }
}
