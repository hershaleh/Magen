package app.magen.lock.core

/**
 * When an app is blocked by its own weekly schedule (spec section 7). One schedule per app: there are no profiles.
 *
 * Times are local civil times, expressed as a local minute index (see [CivilDate.localMinuteIndex]) so a change of
 * time zone or daylight saving is handled by the caller converting the instant with the zone's current offset.
 *
 * - A weekly [Window] starts on [Window.dayOfWeek] (0 = Sunday) at [Window.startMin]. When [Window.endMin] is not
 *   after the start it ends the NEXT day (a window that crosses midnight, such as 22:00-06:00). Equal start and end
 *   means an empty window; a whole day is 0..1440. [Window.endMin] may be 1440 (end of day).
 * - A [DateException] is an absolute local date-time range that overrides the weekly pattern. It can cross midnight
 *   and span several days. [DateException.block] = true adds blocking, false lifts blocking for that range.
 *   When a blocking and a lifting exception overlap, blocking wins (the safe direction).
 */
class BlockSchedule(
    val windows: List<Window> = emptyList(),
    val exceptions: List<DateException> = emptyList(),
) {
    class Window(val dayOfWeek: Int, val startMin: Int, val endMin: Int) {
        init {
            require(dayOfWeek in 0..6) { "dayOfWeek $dayOfWeek" }
            require(startMin in 0..1439) { "startMin $startMin" }
            require(endMin in 0..1440) { "endMin $endMin" }
        }

        /** Length in minutes; 0 for an empty window. */
        val lengthMin: Int
            get() = when {
                endMin == startMin -> 0
                endMin > startMin -> endMin - startMin
                else -> 1440 - startMin + endMin
            }
    }

    /** A local date-time range [startIndex, endIndex): each is epochDay * 1440 + minuteOfDay. */
    class DateException(val startIndex: Long, val endIndex: Long, val block: Boolean) {
        init {
            require(endIndex >= startIndex) { "exception ends before it starts" }
        }

        fun covers(m: Long) = m in startIndex until endIndex
    }

    val isEmpty: Boolean get() = windows.isEmpty() && exceptions.isEmpty()

    private fun weeklyBlocked(m: Long): Boolean {
        val today = Math.floorDiv(m, CivilDate.MINUTES_PER_DAY)
        // A window that started yesterday may still be running, so look at yesterday as well as today.
        for (day in longArrayOf(today, today - 1)) {
            val dow = CivilDate.dayOfWeek(day)
            for (w in windows) {
                if (w.dayOfWeek != dow || w.lengthMin == 0) continue
                val start = day * CivilDate.MINUTES_PER_DAY + w.startMin
                if (m >= start && m < start + w.lengthMin) return true
            }
        }
        return false
    }

    /** True when the local minute index [m] is inside a blocking part of this schedule. */
    fun isBlocked(m: Long): Boolean {
        if (exceptions.any { it.block && it.covers(m) }) return true
        if (exceptions.any { !it.block && it.covers(m) }) return false
        return weeklyBlocked(m)
    }

    /**
     * The next local minute index after [m] (within [horizonDays]) at which [isBlocked] changes, or null when it
     * does not change in that time. Used to plan the next check instead of polling.
     */
    fun nextChange(m: Long, horizonDays: Int = 8): Long? {
        val now = isBlocked(m)
        val limit = m + horizonDays * CivilDate.MINUTES_PER_DAY
        val points = sortedSetOf<Long>()
        val firstDay = Math.floorDiv(m, CivilDate.MINUTES_PER_DAY) - 1
        for (d in firstDay..firstDay + horizonDays + 1) {
            val dow = CivilDate.dayOfWeek(d)
            for (w in windows) {
                if (w.dayOfWeek != dow || w.lengthMin == 0) continue
                val s = d * CivilDate.MINUTES_PER_DAY + w.startMin
                points += s
                points += s + w.lengthMin
            }
        }
        for (e in exceptions) {
            points += e.startIndex
            points += e.endIndex
        }
        for (p in points) {
            if (p <= m || p > limit) continue
            if (isBlocked(p) != now) return p
        }
        return null
    }
}
