package app.magen.lock

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.magen.lock.core.RuleCodec
import app.magen.lock.core.RuleConfig
import app.magen.lock.core.SecurityEvent
import app.magen.lock.core.SecurityLog
import app.magen.lock.core.StateCodec
import app.magen.lock.core.TrustedClock
import app.magen.lock.core.UsageLedger

/**
 * Persistent store of the time-based rules (quotas, groups, schedules, Shabbat), the usage counters, the trusted
 * clock and the security log. It lives in its own preferences file and never touches the code, recovery, uninstall
 * or protection keys kept by [Store].
 */
object RuleStore {
    private const val FILE = "magen_rules"
    private lateinit var sp: SharedPreferences

    /** The current rules. Compose state, so screens update when it changes. */
    var config by mutableStateOf(RuleConfig())
        private set

    /** The security log, oldest first. */
    var events by mutableStateOf<List<SecurityEvent>>(emptyList())
        private set

    /** True while Shabbat or a holiday is blocking: Magen's own rule settings are read-only (spec section 8). */
    var readOnly by mutableStateOf(false)

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val raw = sp.getString("config", "") ?: ""
        val decoded = RuleCodec.decode(raw)
        config = decoded ?: RuleConfig()
        events = StateCodec.decodeEvents(sp.getString("events", "") ?: "") ?: emptyList()
        if (decoded == null) {
            // Keep the unreadable text for diagnosis and say so in the log; never guess a configuration.
            sp.edit().putString("config.unreadable", raw).apply()
            record("settings_corrupt", "rules", System.currentTimeMillis(), "Saved rules could not be read and were reset.", "length=${raw.length}")
        }
    }

    /**
     * Changes the rules and saves them. Refused (false) while [readOnly]. The change is saved before it takes
     * effect, so the enforcement and the screens always see what is stored.
     */
    @Synchronized
    fun updateConfig(change: (RuleConfig) -> RuleConfig): Boolean {
        if (readOnly) return false
        val next = change(config)
        sp.edit().putString("config", RuleCodec.encode(next)).commit()
        config = next
        return true
    }

    /** Replaces the rules from a restored backup. Same refusal while read-only. */
    fun restoreConfig(c: RuleConfig): Boolean = updateConfig { c }

    @Synchronized
    fun record(type: String, key: String, nowMs: Long, message: String = "", technical: String = "") {
        val next = SecurityLog.record(events, type, key, nowMs, message, technical)
        save(next)
    }

    @Synchronized
    fun mutateEvents(change: (List<SecurityEvent>) -> List<SecurityEvent>) {
        save(change(events))
    }

    private fun save(next: List<SecurityEvent>) {
        sp.edit().putString("events", StateCodec.encodeEvents(next)).apply()
        events = next
    }

    /** How many days the log is kept; null keeps it for ever. */
    var retentionDays: Int?
        get() = sp.getInt("logRetentionDays", -1).takeIf { it > 0 }
        set(v) { sp.edit().putInt("logRetentionDays", v ?: -1).apply() }

    // ---- counters and clock: saved by the runtime, restored at start -----------------------------

    fun loadLedger(): UsageLedger = StateCodec.decodeLedger(sp.getString("ledger", "") ?: "") ?: UsageLedger()

    fun loadClock(): TrustedClock {
        val s = sp.getString("clock", "") ?: ""
        val p = s.split(",").mapNotNull { it.toLongOrNull() }
        return if (p.size == 4) TrustedClock(p[0], p[1], p[2].toInt(), p[3]) else TrustedClock()
    }

    fun loadWarned(): Pair<Set<String>, Long> =
        (sp.getStringSet("warned", emptySet()) ?: emptySet<String>()).toSet() to sp.getLong("warnedDay", Long.MIN_VALUE)

    fun loadLatch(): Long = sp.getLong("shabbatLatch", Long.MIN_VALUE)

    fun saveRuntime(ledger: UsageLedger, clock: TrustedClock, warned: Set<String>, warnedDay: Long, latch: Long) {
        sp.edit()
            .putString("ledger", StateCodec.encodeLedger(ledger))
            .putString("clock", clock.state.joinToString(","))
            .putStringSet("warned", HashSet(warned))
            .putLong("warnedDay", warnedDay)
            .putLong("shabbatLatch", latch)
            .apply()
    }
}
