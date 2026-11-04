package app.magen.lock

import java.util.Calendar
import java.util.TimeZone

enum class ScheduleAction { LOCK, ALLOW }

data class WeeklyAppWindow(
    val id: String,
    val packageName: String,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val action: ScheduleAction,
    val priority: Int = 0,
) {
    init {
        require(id.isNotBlank())
        require(packageName.isNotBlank())
        require(dayOfWeek in 1..7)
        require(startMinute in 0..1439)
        require(endMinute in 0..1439)
        require(startMinute != endMinute)
    }
}

data class DateAppOverride(
    val id: String,
    val packageName: String,
    /** Local calendar date encoded as a UTC epoch day. */
    val epochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val action: ScheduleAction,
    val priority: Int = 0,
) {
    init {
        require(id.isNotBlank())
        require(packageName.isNotBlank())
        require(startMinute in 0..1439)
        require(endMinute in 0..1439)
        require(startMinute != endMinute)
    }
}

data class AppSchedule(
    val weekly: List<WeeklyAppWindow> = emptyList(),
    val dateOverrides: List<DateAppOverride> = emptyList(),
)

data class ScheduleDecision(
    val locked: Boolean,
    val reason: Reason,
    val ruleId: String? = null,
) {
    enum class Reason { BASE_SETTING, WEEKLY_WINDOW, DATE_OVERRIDE }
}

/** Pure per-app schedule evaluation. Exact-date overrides beat weekly windows. */
object ScheduleEngine {
    fun evaluate(
        schedule: AppSchedule,
        packageName: String,
        baseLocked: Boolean,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): ScheduleDecision {
        val now = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val today = isoDay(now.get(Calendar.DAY_OF_WEEK))
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val epochDay = localEpochDay(now)

        val dateRule = schedule.dateOverrides.asSequence()
            .filter { it.packageName == packageName && isDateOverrideActive(it, epochDay, minute) }
            .sortedWith(compareByDescending<DateAppOverride> { it.priority }.thenBy { it.id })
            .firstOrNull()
        if (dateRule != null) {
            return ScheduleDecision(dateRule.action == ScheduleAction.LOCK, ScheduleDecision.Reason.DATE_OVERRIDE, dateRule.id)
        }

        val yesterday = if (today == 1) 7 else today - 1
        val weeklyRule = schedule.weekly.asSequence()
            .filter { it.packageName == packageName && isActive(it, today, yesterday, minute) }
            .sortedWith(compareByDescending<WeeklyAppWindow> { it.priority }.thenBy { it.id })
            .firstOrNull()
        if (weeklyRule != null) {
            return ScheduleDecision(weeklyRule.action == ScheduleAction.LOCK, ScheduleDecision.Reason.WEEKLY_WINDOW, weeklyRule.id)
        }
        return ScheduleDecision(baseLocked, ScheduleDecision.Reason.BASE_SETTING)
    }

    private fun isActive(rule: WeeklyAppWindow, today: Int, yesterday: Int, minute: Int): Boolean =
        if (rule.endMinute > rule.startMinute) {
            rule.dayOfWeek == today && minute >= rule.startMinute && minute < rule.endMinute
        } else {
            (rule.dayOfWeek == today && minute >= rule.startMinute) ||
                (rule.dayOfWeek == yesterday && minute < rule.endMinute)
        }

    /**
     * A date override's epochDay identifies the day on which its window starts.
     * For an overnight window, the after-midnight portion belongs to the previous
     * date's override; it must not accidentally activate on the same date before
     * the configured start time.
     */
    private fun isDateOverrideActive(rule: DateAppOverride, todayEpochDay: Long, minute: Int): Boolean =
        if (rule.endMinute > rule.startMinute) {
            rule.epochDay == todayEpochDay && minute >= rule.startMinute && minute < rule.endMinute
        } else {
            (rule.epochDay == todayEpochDay && minute >= rule.startMinute) ||
                (rule.epochDay == todayEpochDay - 1 && minute < rule.endMinute)
        }

    private fun isoDay(calendarDay: Int): Int =
        if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1

    private fun localEpochDay(calendar: Calendar): Long {
        val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        }
        return Math.floorDiv(utcDate.timeInMillis, 86_400_000L)
    }
}
