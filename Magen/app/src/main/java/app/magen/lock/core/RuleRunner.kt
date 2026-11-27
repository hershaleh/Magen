package app.magen.lock.core

/** A notice the runtime should show: [key] makes it appear once. */
class RuleWarning(val key: String, val kind: Kind, val subject: String, val remainingMs: Long) {
    enum class Kind { QUOTA_PERSONAL, QUOTA_GROUP, SHABBAT_SOON }
}

/** Where Shabbat/holiday blocking stands right now. */
class ShabbatStatus(
    /** True from before entry until after exit, and stays true until the window ends even if settings change. */
    val active: Boolean,
    /** End of the active window, or null. */
    val endMs: Long?,
    /** Start of the next window, or null. */
    val nextStartMs: Long?,
    /** False when the feature is on but cannot be relied on (no place chosen, or no sunset computed). */
    val verified: Boolean,
)

/**
 * Keeps Shabbat state: the windows computed for the current days, and a latch so that a window that has begun is
 * never cut short by a change of place or settings. Pure logic.
 */
class ShabbatTracker {
    private var latchedUntil = Long.MIN_VALUE
    private var cachedKey: String? = null
    private var cached: List<JewishTimes.Window> = emptyList()

    private fun windowsFor(day: Long, c: ShabbatConfig): List<JewishTimes.Window> {
        val s = c.settings
        val key = "$day|${c.latitude}|${c.longitude}|${s.inIsrael}|${s.includeSecondDay}|${s.candleLightingMin}|${s.bufferBeforeMin}|${s.twilightDepressionDeg}|${s.bufferAfterMin}"
        if (key != cachedKey) {
            cachedKey = key
            cached = JewishTimes.windows(day - 1, day + 14, c.latitude!!, c.longitude!!, s)
        }
        return cached
    }

    fun status(nowMs: Long, offsetMs: Long, c: ShabbatConfig): ShabbatStatus {
        val latched = nowMs < latchedUntil
        if (!c.enabled && !latched) return ShabbatStatus(false, null, null, true)
        var active = latched
        var end: Long? = if (latched) latchedUntil else null
        var next: Long? = null
        var verified = true
        if (c.enabled) {
            if (!c.hasPlace) {
                verified = false
            } else {
                val day = CivilDate.localEpochDay(nowMs, offsetMs)
                val ws = windowsFor(day, c)
                val now = ws.firstOrNull { it.contains(nowMs) }
                if (now != null) {
                    active = true
                    end = maxOf(end ?: Long.MIN_VALUE, now.endMs)
                    latchedUntil = maxOf(latchedUntil, now.endMs)
                }
                next = ws.filter { it.startMs > nowMs }.minOfOrNull { it.startMs }
                // The week should always contain a window; none means the sunset could not be computed here.
                if (ws.isEmpty()) verified = false
            }
        }
        return ShabbatStatus(active, if (active) end else null, next, verified)
    }

    /** For persistence: the end of a window already begun (so a restart in the middle of Shabbat keeps blocking). */
    var latch: Long
        get() = latchedUntil
        set(v) { latchedUntil = v }
}

/**
 * One tick of the whole policy flow (spec sections 6 to 8): trusted time, usage counting, Shabbat, the central
 * decision, and the notices that are due. Android supplies the raw readings; everything else is decided here, so
 * the flow set -> save -> count -> decide can be tested without a device.
 */
class RuleRunner(
    var config: RuleConfig = RuleConfig(),
    ledger: UsageLedger = UsageLedger(),
    val clock: TrustedClock = TrustedClock(),
    val shabbat: ShabbatTracker = ShabbatTracker(),
    warned: Set<String> = emptySet(),
    warnedDay: Long = Long.MIN_VALUE,
) {
    private val tracker = UsageTracker(ledger)
    private val warnedKeys = HashSet(warned)
    private var warnedForDay = warnedDay

    val ledger: UsageLedger get() = tracker.ledger
    val warned: Set<String> get() = warnedKeys
    val warnedDay: Long get() = warnedForDay

    class Step(
        val decision: Decision,
        val clockChangedByMs: Long,
        val rebooted: Boolean,
        val warnings: List<RuleWarning>,
        val shabbat: ShabbatStatus,
        val trustedMs: Long,
    )

    /**
     * @param pkg app in front, or null when none or the screen is off
     * @param essential the app may never be blocked (Magen, launcher, dialer, system UI, installer, Settings)
     */
    fun step(wallMs: Long, elapsed: Long, boot: Int, offsetMs: Long, pkg: String?, essential: Boolean): Step {
        val reading = clock.read(wallMs, elapsed, boot)
        val trusted = reading.trustedWall
        val day = CivilDate.localEpochDay(trusted, offsetMs)
        val minute = CivilDate.localMinuteIndex(trusted, offsetMs)
        val dow = CivilDate.dayOfWeek(day)
        if (day > warnedForDay) { warnedKeys.removeAll { !it.startsWith("shabbat:") }; warnedForDay = day }

        val sh = shabbat.status(trusted, offsetMs, config.shabbat)
        val rule = pkg?.let { config.apps[it] }
        val groupDef = rule?.groupId?.let { config.groups[it]?.quota }

        // Count only an app that is really in front and may be counted; a paused quota counts nothing.
        val counted = if (pkg != null && !essential) pkg else null
        tracker.sample(
            elapsed, day, counted, rule?.groupId,
            personalCounts = rule?.quota?.let { !Quotas.isPaused(it, minute) } ?: false,
            groupCounts = groupDef?.let { !Quotas.isPaused(it, minute) } ?: false,
        )

        val personalUsed = pkg?.let { tracker.ledger.personalUsed(it) } ?: 0L
        val groupUsed = rule?.groupId?.let { tracker.ledger.groupUsed(it) } ?: 0L
        val decision = if (pkg == null) Decision(emptyList()) else {
            // Shabbat blocks every app that is not essential; the other sources need the app's own rule.
            BlockEngine.decide(
                config.policyFor(pkg, essential, permanent = false, customRuleActive = false),
                EvalContext(minute, dow, personalUsed, groupUsed, config.groupDefs, sh.active),
            )
        }

        val warnings = ArrayList<RuleWarning>()
        if (pkg != null && !essential && !decision.blocked) {
            rule?.quota?.takeIf { !Quotas.isPaused(it, minute) }?.let { q ->
                val rem = Quotas.remainingMs(q, personalUsed, dow)
                val key = "q:$pkg"
                if (Quotas.warningDue(rem, key in warnedKeys)) { warnedKeys += key; warnings += RuleWarning(key, RuleWarning.Kind.QUOTA_PERSONAL, pkg, rem!!) }
            }
            val gid = rule?.groupId
            if (gid != null && groupDef != null && !Quotas.isPaused(groupDef, minute)) {
                val rem = Quotas.remainingMs(groupDef, groupUsed, dow)
                val key = "g:$gid"
                if (Quotas.warningDue(rem, key in warnedKeys)) { warnedKeys += key; warnings += RuleWarning(key, RuleWarning.Kind.QUOTA_GROUP, gid, rem!!) }
            }
        }
        val next = sh.nextStartMs
        if (!sh.active && next != null && config.shabbat.enabled) {
            val left = next - trusted
            val key = "shabbat:$next"
            if (left in 1..config.shabbat.warnMin * 60_000L && key !in warnedKeys) {
                warnedKeys += key
                warnings += RuleWarning(key, RuleWarning.Kind.SHABBAT_SOON, "", left)
            }
        }
        return Step(decision, reading.clockChangedByMs, reading.rebooted, warnings, sh, trusted)
    }
}
