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

    @Test fun weeklyWindowSelectsProfile() {
        val schedule = ProfileSchedule(weekly = listOf(WeeklyProfileWindow("tue", 2, 480, 960, "work")))
        assertEquals("work", ScheduleEngine.resolve(schedule, "default", at(3, 9), utc).profileId)
        assertEquals("default", ScheduleEngine.resolve(schedule, "default", at(3, 17), utc).profileId)
    }

    @Test fun overnightWindowContinuesIntoNextDay() {
        val schedule = ProfileSchedule(weekly = listOf(WeeklyProfileWindow("night", 1, 1320, 360, "night")))
        assertEquals("night", ScheduleEngine.resolve(schedule, "default", at(3, 2), utc).profileId)
        assertEquals("default", ScheduleEngine.resolve(schedule, "default", at(3, 7), utc).profileId)
    }

    @Test fun higherPriorityWinsOverlap() {
        val schedule = ProfileSchedule(weekly = listOf(
            WeeklyProfileWindow("normal", 2, 480, 1200, "normal"),
            WeeklyProfileWindow("focus", 2, 720, 1080, "focus", 5),
        ))
        assertEquals("focus", ScheduleEngine.resolve(schedule, "default", at(3, 14), utc).profileId)
    }
}
