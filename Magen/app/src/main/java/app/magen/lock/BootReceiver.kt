package app.magen.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Checks the protection after a restart (spec section 10): the accessibility service must be back on. A missing
 * service is logged as an open, serious event; a healthy restart is logged as a low one. Nothing here changes
 * settings, and the open event is resolved by the guard only after it verifies the service is running.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val now = System.currentTimeMillis()
        runCatching { RuleRuntime.reset() }
        if (!Policy.accessibilityOn(context)) {
            RuleStore.record(
                "accessibility_disabled", "boot", now,
                "After the restart, the Magen accessibility service is off, so blocking is not active.",
                "boot_completed accessibility=off",
            )
        } else {
            RuleStore.record("service_restarted", "boot", now, "The phone restarted and Magen protection is running.", "boot_completed accessibility=on")
        }
    }
}
