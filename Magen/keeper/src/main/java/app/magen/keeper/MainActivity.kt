package app.magen.keeper

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Keeper's only screen: shows whether Keeper holds Device Owner and lets the person hand it to another app. */
class MainActivity : Activity() {
    companion object {
        private var unlocked = false
    }

    private var dialog: Dialog? = null

    override fun onResume() {
        super.onResume()
        if (unlocked) {
            render()
        } else {
            setContentView(View(this))
            dialog?.dismiss()
            dialog = Ui.askPin(this, getString(R.string.gate_msg), { unlocked = true; render() }, { finish() })
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) unlocked = false
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    private fun text(res: Int, size: Float = 15f, bold: Boolean = false, vararg args: Any): TextView = TextView(this).apply {
        text = if (args.isEmpty()) getString(res) else getString(res, *args)
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6))
    }

    private fun render() {
        val owner = Transfer.isOwner(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 28), Ui.dp(context, 20), Ui.dp(context, 28))
        }
        root.addView(text(R.string.app_name, 24f, true))
        root.addView(text(if (owner) R.string.status_owner else R.string.status_not_owner, 16f, true))

        if (owner) {
            root.addView(text(R.string.transfer_title, 18f, true))
            root.addView(text(R.string.transfer_desc))
            val targets = Transfer.targets(this)
            if (targets.isEmpty()) {
                root.addView(text(R.string.no_targets))
            } else {
                targets.forEach { t ->
                    root.addView(
                        Button(this).apply {
                            text = "${t.label}  (${t.component.packageName})"
                            isAllCaps = false
                            setOnClickListener { confirmTransfer(t) }
                        },
                    )
                }
            }
        } else {
            root.addView(text(R.string.how_to_get_owner))
            root.addView(
                TextView(this).apply {
                    text = "adb shell dpm set-device-owner app.magen.keeper/.KeeperAdminReceiver"
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    setTextIsSelectable(true)
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    textDirection = View.TEXT_DIRECTION_LTR
                    setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8))
                },
            )
        }

        root.addView(text(R.string.remembered_title, 18f, true))
        val pinned = KeeperStore.pinnedPackages(this)
        root.addView(text(R.string.remembered_desc))
        root.addView(
            TextView(this).apply {
                text = if (pinned.isEmpty()) getString(R.string.remembered_none) else pinned.joinToString("\n")
                textSize = 14f
                setPadding(0, 0, 0, Ui.dp(context, 8))
            },
        )
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun confirmTransfer(t: Transfer.Target) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_title)
            .setMessage(getString(R.string.confirm_transfer, t.label, t.component.packageName))
            .setPositiveButton(R.string.confirm) { _, _ ->
                val error = Transfer.transfer(this, t.component)
                if (error == null) {
                    Toast.makeText(this, getString(R.string.transfer_done, t.label), Toast.LENGTH_LONG).show()
                    render()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.transfer_failed)
                        .setMessage(error)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
