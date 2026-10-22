package app.magen.keeper

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

object Ui {
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    private fun pinField(c: Context, hint: String) = EditText(c).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(8))
        this.hint = hint
    }

    /**
     * Asks for Keeper's code. When no code exists yet, asks the person to create one instead.
     * [onOk] runs once the code is right (or has just been created).
     */
    fun askPin(a: Activity, message: String, onOk: () -> Unit, onCancel: () -> Unit): AlertDialog {
        val creating = !KeeperStore.hasPin(a)
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 24), dp(a, 8), dp(a, 24), 0)
        }
        val first = pinField(a, a.getString(if (creating) R.string.pin_new else R.string.pin_enter))
        box.addView(first)
        val second = if (creating) pinField(a, a.getString(R.string.pin_confirm)).also { box.addView(it) } else null

        val dialog = AlertDialog.Builder(a)
            .setTitle(R.string.app_name)
            .setMessage(message)
            .setView(box)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setCancelable(false)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = first.text.toString()
                if (creating) {
                    if (pin.length < 4) {
                        Toast.makeText(a, R.string.pin_short, Toast.LENGTH_SHORT).show()
                    } else if (pin != second?.text.toString()) {
                        Toast.makeText(a, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
                    } else {
                        KeeperStore.setPin(a, pin)
                        dialog.dismiss()
                        onOk()
                    }
                } else {
                    val wait = KeeperStore.lockRemaining(a)
                    if (wait > 0) {
                        Toast.makeText(a, a.getString(R.string.try_again_in, wait), Toast.LENGTH_SHORT).show()
                    } else if (KeeperStore.checkPin(a, pin)) {
                        dialog.dismiss()
                        onOk()
                    } else {
                        Toast.makeText(a, R.string.wrong_pin, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
        return dialog
    }
}
