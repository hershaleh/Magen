package app.magen.keeper

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.os.PersistableBundle

class KeeperAdminReceiver : DeviceAdminReceiver() {
    /**
     * Android calls this after another app handed Device Owner to Keeper. The sender (Magen) puts its own
     * package name in the bundle; Keeper remembers it together with its signing certificate, so that the same
     * app can later take ownership back without a code.
     */
    override fun onTransferOwnershipComplete(context: Context, bundle: PersistableBundle?) {
        val from = bundle?.getString("from_package") ?: return
        val cert = KeeperStore.certOf(context, from) ?: return
        KeeperStore.addPin(context, from, cert)
    }
}
