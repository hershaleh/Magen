package app.magen.lock

import java.util.Calendar
import java.util.TimeZone

/**
 * Pure schedule model. Days use ISO numbering: Monday=1 ... Sunday=7.
 * A window whose end is earlier than its start continues into the following day.
 */
data class WeeklyProfileWindow(
    val id: String,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val profileId: String,
    val priority: Int = 0,
) {
    init {
        require(dayOfWeek in 1..7) { "dayOfWeek must be 1..7" }
        require(startMinute in 0..1439) { "startMinute must be 0..1439" }
        require(endMinute in 0..1439) { "endMinute must be 0..1439" }
        require(startMinute != endMinute) { "A schedule window cannot have zero or 24-hour length" }
        require(profileId.isNotBlank()) { "profileId cannot be blank" }
    }
}

data class DateProfileOverride(
    /** Local calendar date represented as a UTC-independent epoch day. */
    val epochDay: Long,
    /** null means use the manually selected default profile for this date. */
    val profileId: String?,
)

data class ProfileSchedule(
    val weekly: List<WeeklyProfileWindow> = emptyList(),
    val dateOverrides: List<DateProfileOverride> = emptyList(),
)

data class ScheduleResolution(
    val profileId: String?,
    val source: Source,
    val matchedWindowId: String? = null,
) {
    enum class Source { DATE_OVERRIDE, WEEKLY, DEFAULT }
}

object ScheduleEngine {
    /**
     * Resolves the profile for a local wall-clock instant.
     * Exact-date exceptions override weekly windows. For overlapping weekly windows,
     * higher priority wins, then the most recently started window, then stable ID order.
     */
    fun resolve(
        schedule: ProfileSchedule,
        defaultProfileId: String?,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): ScheduleResolution {
        val now = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val todayEpochDay = epochDay(now)

        schedule.dateOverrides.lastOrNull { it.epochDay == todayEpochDay }?.let {
            return ScheduleResolution(it.profileId, ScheduleResolution.Source.DATE_OVERRIDE)
        }

        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val today = isoDay(now.get(Calendar.DAY_OF_WEEK))
        val yesterday = if (today == 1) 7 else today - 1

        val active = schedule.weekly.mapNotNull { window ->
            val isActive = if (window.endMinute > window.startMinute) {
                window.dayOfWeek == today && minute >= window.startMinute && minute < window.endMinute
            } else {
                (window.dayOfWeek == today && minute >= window.startMinute) ||
                    (window.dayOfWeek == yesterday && minute < window.endMinute)
            }
            if (isActive) window else null
        }.sortedWith(
            compareByDescending<WeeklyProfileWindow> { it.priority }
                .thenByDescending { it.startMinute }
                .thenBy { it.id },
        ).firstOrNull()

        return if (active != null) {
            ScheduleResolution(active.profileId, ScheduleResolution.Source.WEEKLY, active.id)
        } else {
            ScheduleResolution(defaultProfileId, ScheduleResolution.Source.DEFAULT)
        }
    }

    private fun isoDay(calendarDay: Int): Int =
        if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1

    private fun epochDay(calendar: Calendar): Long {
        val copy = calendar.clone() as Calendar
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return Math.floorDiv(copy.timeInMillis, 86_400_000L)
    }
}
