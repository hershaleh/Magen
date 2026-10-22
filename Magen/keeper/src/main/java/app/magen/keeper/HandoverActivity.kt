package app.magen.keeper

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle

/**
 * Started with startActivityForResult by an app that wants Device Owner back (Magen after a reinstall).
 * The caller's package name comes from Android itself (callingPackage), so it cannot be faked.
 * If this exact app (same signature) once handed ownership to Keeper, ownership goes back at once;
 * otherwise Keeper's code is required.
 */
class HandoverActivity : Activity() {
    private var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val caller = callingPackage
        if (caller == null) {
            finishWith(getString(R.string.err_no_caller))
        } else if (!Transfer.isOwner(this)) {
            finishWith(getString(R.string.err_not_owner))
        } else {
            val target = Transfer.targets(this, caller).firstOrNull()
            if (target == null) finishWith(getString(R.string.err_no_support, caller)) else decide(caller, target)
        }
    }

    private fun decide(caller: String, target: Transfer.Target) {
        val cert = KeeperStore.certOf(this, caller)
        if (cert != null && KeeperStore.isPinned(this, caller, cert)) {
            handOver(target)
        } else {
            dialog = Ui.askPin(
                this,
                getString(R.string.handover_confirm, target.label, caller),
                { handOver(target) },
                { finishWith(getString(R.string.err_cancelled)) },
            )
        }
    }

    private fun handOver(target: Transfer.Target) {
        val error = Transfer.transfer(this, target.component)
        if (error == null) {
            setResult(RESULT_OK)
            finish()
        } else {
            finishWith(error)
        }
    }

    private fun finishWith(message: String) {
        setResult(RESULT_CANCELED, Intent().putExtra("error", message))
        finish()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }
}
