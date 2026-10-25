package app.magen.keeper

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
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

        // Android only hands ownership to an app that is already an enabled device admin.
        if (!Transfer.isActive(this, Transfer.ownReceiver(this))) {
            root.addView(text(R.string.admin_needed))
            root.addView(
                Button(this).apply {
                    text = getString(R.string.admin_activate)
                    isAllCaps = false
                    setOnClickListener {
                        startActivity(
                            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Transfer.ownReceiver(this@MainActivity))
                                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explain)),
                        )
                    }
                },
            )
        }

        // The list is always shown, so it is clear whether an app (Magen) is found at all.
        root.addView(text(R.string.transfer_title, 18f, true))
        root.addView(text(R.string.transfer_desc))
        val targets = Transfer.targets(this)
        if (targets.isEmpty()) {
            root.addView(text(R.string.no_targets))
        } else {
            targets.forEach { t ->
                root.addView(
                    Button(this).apply {
                        val active = Transfer.isActive(this@MainActivity, t.component)
                        val note = if (active) "" else getString(R.string.target_inactive)
                        text = "${t.label}  (${t.component.packageName})$note"
                        isAllCaps = false
                        isEnabled = owner
                        setOnClickListener {
                            if (active) {
                                confirmTransfer(t)
                            } else {
                                AlertDialog.Builder(this@MainActivity)
                                    .setMessage(getString(R.string.err_target_inactive, t.label))
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        }
                    },
                )
            }
        }
        if (!owner) {
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
