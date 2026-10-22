package app.magen.lock

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * "Magen Keeper" is a tiny companion app (module :keeper) that can hold Device Owner while Magen is
 * removed or reinstalled. Magen hands ownership to it with transferOwnership(), and later takes it back
 * through Keeper's HandoverActivity. Needs Android 9 (API 28) or newer.
 */
object Keeper {
    const val PKG = "app.magen.keeper"

    fun installed(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(PKG, 0) }.isSuccess

    fun receiver() = ComponentName(PKG, "$PKG.KeeperAdminReceiver")

    /** Start it with startActivityForResult: Keeper reads the caller from Android, so it must be a result launch. */
    fun handoverIntent() = Intent().setComponent(ComponentName(PKG, "$PKG.HandoverActivity"))
}
