package app.magen.keeper

import android.app.admin.DeviceAdminInfo
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PersistableBundle

/** Finding apps that accept Device Owner, and handing it over. */
object Transfer {
    class Target(val component: ComponentName, val label: String)

    private fun dpm(c: Context) = c.getSystemService(DevicePolicyManager::class.java)

    fun ownReceiver(c: Context) = ComponentName(c, KeeperAdminReceiver::class.java)

    /** transferOwnership() only accepts a target that is already an active device admin. */
    fun isActive(c: Context, component: ComponentName): Boolean = dpm(c).isAdminActive(component)

    fun isOwner(c: Context): Boolean = dpm(c).isDeviceOwnerApp(c.packageName)

    /** Installed apps whose device-admin receiver declares support for receiving ownership (optionally one package). */
    fun targets(c: Context, onlyPackage: String? = null): List<Target> {
        val pm = c.packageManager
        val intent = Intent(DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED)
        if (onlyPackage != null) intent.setPackage(onlyPackage)
        val out = ArrayList<Target>()
        for (ri in pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)) {
            try {
                val info = DeviceAdminInfo(c, ri)
                if (info.packageName == c.packageName || !info.supportsTransferOwnership()) continue
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.packageName, 0)).toString()
                }.getOrDefault(info.packageName)
                out.add(Target(info.component, label))
            } catch (e: Exception) {
                // Receiver with unreadable metadata: not a usable target.
            }
        }
        return out
    }

    /** Hands Device Owner to [target]. Returns null on success, otherwise the Android error text. */
    fun transfer(c: Context, target: ComponentName): String? {
        return try {
            if (!isOwner(c)) {
                c.getString(R.string.err_not_owner)
            } else {
                val extras = PersistableBundle().apply { putString("from_package", c.packageName) }
                dpm(c).transferOwnership(ownReceiver(c), target, extras)
                null
            }
        } catch (e: Throwable) {
            "${e.javaClass.name}: ${e.message ?: "no details"}"
        }
    }
}
