package app.magen.lock

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleEngineTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(day: Int, hour: Int): Long = Calendar.getInstance(utc).apply {
        set(2026, Calendar.NOVEMBER, day, hour, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun weeklyWindowAppliesOnlyToItsApp() {
        val schedule = AppSchedule(weekly = listOf(
            WeeklyAppWindow("work", "com.example.work", 2, 480, 960, ScheduleAction.LOCK),
        ))
        assertEquals(true, ScheduleEngine.evaluate(schedule, "com.example.work", false, at(3, 9), utc).locked)
        assertEquals(false, ScheduleEngine.evaluate(schedule, "com.example.other", false, at(3, 9), utc).locked)
        assertEquals(false, ScheduleEngine.evaluate(schedule, "com.example.work", false, at(3, 17), utc).locked)
    }

    @Test fun overnightWindowContinuesIntoNextDay() {
        val schedule = AppSchedule(weekly = listOf(
            WeeklyAppWindow("night", "com.example.app", 1, 1320, 360, ScheduleAction.LOCK),
        ))
        assertEquals(true, ScheduleEngine.evaluate(schedule, "com.example.app", false, at(3, 2), utc).locked)
        assertEquals(false, ScheduleEngine.evaluate(schedule, "com.example.app", false, at(3, 7), utc).locked)
    }

    @Test fun dateOverrideTakesPrecedenceOverWeeklyWindow() {
        val date = at(3, 12)
        val epochDay = Math.floorDiv(date, 86400000L)
        val schedule = AppSchedule(
            weekly = listOf(WeeklyAppWindow("weekly", "com.example.app", 2, 0, 1439, ScheduleAction.LOCK)),
            dateOverrides = listOf(DateAppOverride("holiday", "com.example.app", epochDay, 0, 1439, ScheduleAction.ALLOW)),
        )
        val result = ScheduleEngine.evaluate(schedule, "com.example.app", true, date, utc)
        assertEquals(false, result.locked)
        assertEquals(ScheduleDecision.Reason.DATE_OVERRIDE, result.reason)
    }

    @Test fun higherPriorityWinsOverlappingWeeklyWindows() {
        val schedule = AppSchedule(weekly = listOf(
            WeeklyAppWindow("normal", "com.example.app", 2, 480, 1200, ScheduleAction.ALLOW),
            WeeklyAppWindow("locked", "com.example.app", 2, 720, 1080, ScheduleAction.LOCK, 5),
        ))
        assertEquals(true, ScheduleEngine.evaluate(schedule, "com.example.app", false, at(3, 14), utc).locked)
    }

    @Test fun fallsBackToBaseSettingOutsideWindows() {
        val schedule = AppSchedule()
        assertEquals(true, ScheduleEngine.evaluate(schedule, "com.example.app", true, at(3, 12), utc).locked)
        assertEquals(false, ScheduleEngine.evaluate(schedule, "com.example.app", false, at(3, 12), utc).locked)
    }
}
