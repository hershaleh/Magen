package app.magen.lock.core

/**
 * Daily foreground-time quotas (spec section 6): the rules, kept free of storage and Android so every rule is a
 * plain function that can be tested.
 *
 * A quota's limit is derived from its plan every time it is evaluated, never stored as a "remaining" number, so a
 * changed quota takes effect at once: lowering it below the time already used blocks immediately, raising it
 * restores the difference. Zero minutes is a full block; no plan limit at all (null) means there is no quota.
 */
class QuotaPlan(
    /** Minutes for every day of the week, or null. */
    val uniformMin: Int? = null,
    /** Minutes for single days (0 = Sunday ... 6 = Saturday). A day listed here overrides [uniformMin]. */
    val perDayMin: Map<Int, Int> = emptyMap(),
) {
    init {
        require(uniformMin == null || uniformMin >= 0) { "negative quota" }
        require(perDayMin.all { it.key in 0..6 && it.value >= 0 }) { "bad per-day quota" }
    }

    /** The limit for [dayOfWeek] in minutes, or null when that day has no quota. */
    fun limitMin(dayOfWeek: Int): Int? = perDayMin[dayOfWeek] ?: uniformMin
}

/** A quota that is switched on, with its pause state. Scheduled [breaks] pause it too (see [Quotas.isPaused]). */
class QuotaDef(
    val plan: QuotaPlan,
    /** Paused by hand: nothing is counted and the quota blocks nothing until it is resumed. */
    val paused: Boolean = false,
    /** Scheduled breaks: while one is running the quota is paused. Uses the same windows as a block schedule. */
    val breaks: BlockSchedule = BlockSchedule(),
)

object Quotas {
    const val DEFAULT_WARNING_MS = 5 * 60_000L

    fun isPaused(def: QuotaDef, localMinute: Long): Boolean = def.paused || def.breaks.isBlocked(localMinute)

    /** The quota's limit today in milliseconds, or null when there is none. */
    fun limitMs(def: QuotaDef, dayOfWeek: Int): Long? = def.plan.limitMin(dayOfWeek)?.let { it * 60_000L }

    /** Milliseconds left today (never negative), or null when there is no quota. Ignores pauses. */
    fun remainingMs(def: QuotaDef, usedMs: Long, dayOfWeek: Int): Long? =
        limitMs(def, dayOfWeek)?.let { maxOf(it - maxOf(usedMs, 0L), 0L) }

    /** True when the quota is used up and not paused. A zero-minute quota is exhausted from the first moment. */
    fun isExhausted(def: QuotaDef, usedMs: Long, dayOfWeek: Int, localMinute: Long): Boolean {
        if (isPaused(def, localMinute)) return false
        val limit = limitMs(def, dayOfWeek) ?: return false
        return usedMs >= limit
    }

    /**
     * Whether the "few minutes left" warning is due: time is left, no more than [thresholdMs] of it, and the
     * warning has not been shown yet today. An app opened with less than the threshold left warns at once.
     */
    fun warningDue(remainingMs: Long?, alreadyWarned: Boolean, thresholdMs: Long = DEFAULT_WARNING_MS): Boolean =
        remainingMs != null && remainingMs > 0 && remainingMs <= thresholdMs && !alreadyWarned
}

/** Group membership: an app belongs to at most one group. */
object QuotaGroups {
    /** Apps that appear in more than one group, mapped to the groups they appear in. Empty when valid. */
    fun conflicts(groups: Map<String, Set<String>>): Map<String, List<String>> {
        val seen = HashMap<String, MutableList<String>>()
        for ((group, apps) in groups) for (app in apps) seen.getOrPut(app) { ArrayList() }.add(group)
        return seen.filterValues { it.size > 1 }
    }
}

/**
 * Today's counted foreground time, per app and per group. Only moves forward: a new day clears it, an earlier or
 * the same day never does, so setting the clock back cannot give time back (see [TrustedClock]).
 */
class UsageLedger(
    val day: Long = Long.MIN_VALUE,
    val personalMs: Map<String, Long> = emptyMap(),
    val groupMs: Map<String, Long> = emptyMap(),
) {
    /** The ledger for [today]: this one, or an empty one when [today] is a later day. */
    fun rolledTo(today: Long): UsageLedger = if (today > day) UsageLedger(today) else this

    fun credit(pkg: String?, groupId: String?, personal: Boolean, group: Boolean, ms: Long): UsageLedger {
        if (ms <= 0) return this
        val p = if (personal && pkg != null) personalMs + (pkg to (personalMs[pkg] ?: 0L) + ms) else personalMs
        val g = if (group && groupId != null) groupMs + (groupId to (groupMs[groupId] ?: 0L) + ms) else groupMs
        return UsageLedger(day, p, g)
    }

    fun personalUsed(pkg: String) = personalMs[pkg] ?: 0L
    fun groupUsed(groupId: String) = groupMs[groupId] ?: 0L
}

/**
 * Turns "this app was in front at this moment" samples into counted time. Each sample credits the interval since
 * the PREVIOUS sample to the app that was in front then, so the time between two samples is counted exactly once,
 * leaving an app or turning the screen off (a null app) stops the count, and nothing is counted twice when the
 * same app is sampled again. The interval is capped at [maxGapMs], because a long silence is far more likely a
 * missed event than continuous use, and a backwards step of the monotonic clock (a reboot) credits nothing.
 */
class UsageTracker(
    var ledger: UsageLedger = UsageLedger(),
    private val maxGapMs: Long = 60_000L,
) {
    private var lastElapsed = -1L
    private var lastPkg: String? = null
    private var lastGroup: String? = null
    private var lastPersonalCounts = false
    private var lastGroupCounts = false

    /**
     * @param nowElapsed monotonic time of the sample (SystemClock.elapsedRealtime)
     * @param today trusted local epoch day
     * @param pkg app in front now, or null when none (screen off, home screen is not an app, ...)
     * @param groupId the group [pkg] belongs to, or null
     * @param personalCounts whether the app's own quota is running (set and not paused) now
     * @param groupCounts whether the group's quota is running now
     */
    fun sample(nowElapsed: Long, today: Long, pkg: String?, groupId: String?, personalCounts: Boolean, groupCounts: Boolean) {
        val prev = lastElapsed
        if (prev >= 0 && nowElapsed > prev && lastPkg != null) {
            val gap = minOf(nowElapsed - prev, maxGapMs)
            ledger = ledger.credit(lastPkg, lastGroup, lastPersonalCounts, lastGroupCounts, gap)
        }
        ledger = ledger.rolledTo(today)
        lastElapsed = nowElapsed
        lastPkg = pkg
        lastGroup = groupId
        lastPersonalCounts = personalCounts
        lastGroupCounts = groupCounts
    }
}
