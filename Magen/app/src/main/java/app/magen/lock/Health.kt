package app.magen.lock

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Checks that run after a restart or the first rule tick (spec section 10): a changed Android or vendor build, and
 * battery restrictions that may stop background work. They only record events; nothing is changed on the device.
 */
object Health {
    fun check(ctx: Context, nowMs: Long) {
        runCatching { versionChange(ctx, nowMs) }
        runCatching { batteryRestriction(ctx, nowMs) }
    }

    private fun versionChange(ctx: Context, nowMs: Long) {
        val sp = ctx.getSharedPreferences("magen_rules", Context.MODE_PRIVATE)
        val now = "${Build.VERSION.RELEASE}/${Build.VERSION.SDK_INT}/${Build.VERSION.INCREMENTAL}"
        val before = sp.getString("buildFingerprint", null)
        if (before != now) {
            sp.edit().putString("buildFingerprint", now).apply()
            if (before != null) {
                RuleStore.record(
                    "system_updated", "build", nowMs,
                    "Android or the manufacturer's system version changed. Magen checks that its permissions and services still work.",
                    "from=$before to=$now",
                )
            }
        }
    }

    private fun batteryRestriction(ctx: Context, nowMs: Long) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            RuleStore.record(
                "background_limited", "battery", nowMs,
                "Battery optimisation is on for Magen, so Android may limit background work. If blocking stops, exclude Magen from battery optimisation.",
                "ignoringBatteryOptimizations=false",
            )
        }
    }
}
