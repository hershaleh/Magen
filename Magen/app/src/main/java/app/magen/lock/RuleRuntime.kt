package app.magen.lock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.magen.lock.core.EventStatus
import app.magen.lock.core.RuleRunner
import app.magen.lock.core.RuleWarning
import app.magen.lock.core.SecurityLog
import java.util.TimeZone

/**
 * Glue between Android and [RuleRunner]: reads the clocks, runs one tick, keeps the counters saved, writes the
 * security log and shows the notices. The decisions themselves are made by the tested core classes.
 */
object RuleRuntime {
    private const val CHANNEL = "magen_rules"
    private const val SAVE_EVERY_MS = 15_000L

    private var runner: RuleRunner? = null
    private var lastSave = 0L

    /** For screens: Shabbat/holiday blocking is active right now. */
    var shabbatActive by mutableStateOf(false)
        private set

    /** For screens: Shabbat blocking is on but cannot be relied on (no place chosen, no sunset computed). */
    var shabbatUnverified by mutableStateOf(false)
        private set

    @Synchronized
    private fun runner(): RuleRunner = runner ?: run {
        val (warned, warnedDay) = RuleStore.loadWarned()
        RuleRunner(RuleStore.config, RuleStore.loadLedger(), RuleStore.loadClock(), warned = warned, warnedDay = warnedDay)
            .also { it.shabbat.latch = RuleStore.loadLatch(); runner = it }
    }

    private fun bootCount(ctx: Context): Int =
        runCatching { Settings.Global.getInt(ctx.contentResolver, "boot_count") }.getOrDefault(-1)

    /** One tick. [pkg] is the app in front (null when none or the screen is off). */
    @Synchronized
    fun step(ctx: Context, pkg: String?, essential: Boolean): RuleRunner.Step {
        val r = runner()
        r.config = RuleStore.config
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val s = r.step(wall, elapsed, bootCount(ctx), TimeZone.getDefault().getOffset(wall).toLong(), pkg, essential)

        shabbatActive = s.shabbat.active
        shabbatUnverified = r.config.shabbat.enabled && !s.shabbat.verified
        if (RuleStore.readOnly != s.shabbat.active) RuleStore.readOnly = s.shabbat.active

        if (s.clockChangedByMs != 0L) {
            RuleStore.record(
                "clock_changed", "device", wall,
                "The device clock was changed. Magen keeps counting from its own trusted time.",
                "changedByMs=${s.clockChangedByMs} rebooted=${s.rebooted}",
            )
        } else if (s.rebooted) {
            resolveOpen("clock_changed", wall, "The clock is consistent after a restart.")
        }
        if (shabbatUnverified) {
            RuleStore.record(
                "shabbat_check_failed", "place", wall,
                "Shabbat blocking is on but cannot be guaranteed: choose a place.",
                "hasPlace=${r.config.shabbat.hasPlace} windows=${s.shabbat.nextStartMs != null}",
            )
        } else {
            resolveOpen("shabbat_check_failed", wall, "A place is set and times are computed.")
        }

        val nowElapsed = elapsed
        if (nowElapsed - lastSave > SAVE_EVERY_MS || s.warnings.isNotEmpty()) {
            lastSave = nowElapsed
            RuleStore.saveRuntime(r.ledger, r.clock, r.warned, r.warnedDay, r.shabbat.latch)
        }
        return s
    }

    private fun resolveOpen(type: String, now: Long, note: String) {
        val open = RuleStore.events.filter { it.type == type && it.isOpen }
        if (open.isEmpty()) return
        RuleStore.mutateEvents { list -> open.fold(list) { acc, e -> SecurityLog.resolve(acc, e.id, now, verifiedGone = true, note = note) } }
    }

    /** Forgets the cached state so the next tick reloads it from [RuleStore], for example after a restore. */
    @Synchronized
    fun reset() {
        runner = null
        lastSave = 0L
    }

    /** Saves the counters now, for example when the service stops. */
    @Synchronized
    fun flush() {
        val r = runner ?: return
        RuleStore.saveRuntime(r.ledger, r.clock, r.warned, r.warnedDay, r.shabbat.latch)
    }

    // ---- notices ---------------------------------------------------------------------------------

    fun notify(ctx: Context, warnings: List<RuleWarning>) {
        if (warnings.isEmpty()) return
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.notif_rules_channel), NotificationManager.IMPORTANCE_DEFAULT))
            }
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
            for (w in warnings) {
                val minutes = maxOf(1, (w.remainingMs / 60_000L).toInt())
                val (title, text) = when (w.kind) {
                    RuleWarning.Kind.QUOTA_PERSONAL, RuleWarning.Kind.QUOTA_GROUP ->
                        ctx.getString(R.string.notif_quota_title) to ctx.getString(R.string.notif_quota_text, minutes)
                    RuleWarning.Kind.SHABBAT_SOON ->
                        ctx.getString(R.string.notif_shabbat_title) to ctx.getString(R.string.notif_shabbat_text, minutes)
                }
                val n = NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_fg)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(ctx).notify(w.key.hashCode(), n)
            }
        }
    }
}
