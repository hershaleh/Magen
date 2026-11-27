package app.magen.lock.core

import app.magen.lock.core.BlockSchedule.DateException
import app.magen.lock.core.BlockSchedule.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockScheduleTest {
    // 2026-11-01 is a Sunday.
    private val sun = CivilDate.toEpochDay(2026, 11, 1)
    private fun at(dayOffset: Int, h: Int, m: Int = 0) = (sun + dayOffset) * 1440 + h * 60 + m

    @Test
    fun emptyScheduleNeverBlocks() {
        assertFalse(BlockSchedule().isBlocked(at(0, 12)))
        assertTrue(BlockSchedule().isEmpty)
    }

    @Test
    fun simpleDayWindowHasInclusiveStartAndExclusiveEnd() {
        val s = BlockSchedule(listOf(Window(0, 9 * 60, 17 * 60))) // Sunday 09:00-17:00
        assertFalse(s.isBlocked(at(0, 8, 59)))
        assertTrue(s.isBlocked(at(0, 9)))
        assertTrue(s.isBlocked(at(0, 16, 59)))
        assertFalse(s.isBlocked(at(0, 17)))
        assertFalse("another weekday", s.isBlocked(at(1, 10)))
        assertTrue("the same weekday a week later", s.isBlocked(at(7, 10)))
    }

    @Test
    fun windowThatCrossesMidnightRunsIntoTheNextDay() {
        val s = BlockSchedule(listOf(Window(5, 22 * 60, 6 * 60))) // Friday 22:00 - Saturday 06:00
        assertFalse(s.isBlocked(at(5, 21, 59)))
        assertTrue(s.isBlocked(at(5, 22)))
        assertTrue(s.isBlocked(at(5, 23, 59)))
        assertTrue(s.isBlocked(at(6, 0)))
        assertTrue(s.isBlocked(at(6, 5, 59)))
        assertFalse(s.isBlocked(at(6, 6)))
        assertFalse("the Saturday evening is not part of it", s.isBlocked(at(6, 23)))
    }

    @Test
    fun endOfDayAndWholeDayWindows() {
        val toMidnight = BlockSchedule(listOf(Window(2, 20 * 60, 1440)))
        assertTrue(toMidnight.isBlocked(at(2, 23, 59)))
        assertFalse(toMidnight.isBlocked(at(3, 0)))
        val wholeDay = BlockSchedule(listOf(Window(3, 0, 1440)))
        assertTrue(wholeDay.isBlocked(at(3, 0)))
        assertTrue(wholeDay.isBlocked(at(3, 23, 59)))
        assertFalse(wholeDay.isBlocked(at(4, 0)))
    }

    @Test
    fun emptyWindowBlocksNothing() {
        val s = BlockSchedule(listOf(Window(1, 600, 600)))
        assertFalse(s.isBlocked(at(1, 10)))
        assertEquals(0, Window(1, 600, 600).lengthMin)
    }

    @Test
    fun severalWindowsInADayAndDifferentDays() {
        val s = BlockSchedule(listOf(Window(1, 8 * 60, 9 * 60), Window(1, 13 * 60, 14 * 60), Window(2, 8 * 60, 9 * 60)))
        assertTrue(s.isBlocked(at(1, 8, 30)))
        assertFalse(s.isBlocked(at(1, 10)))
        assertTrue(s.isBlocked(at(1, 13, 30)))
        assertTrue(s.isBlocked(at(2, 8, 30)))
        assertFalse(s.isBlocked(at(3, 8, 30)))
    }

    @Test
    fun blockingExceptionAddsBlockingOutsideTheWeeklyPattern() {
        val s = BlockSchedule(emptyList(), listOf(DateException(at(10, 12), at(10, 15), true)))
        assertFalse(s.isBlocked(at(10, 11, 59)))
        assertTrue(s.isBlocked(at(10, 12)))
        assertFalse(s.isBlocked(at(10, 15)))
    }

    @Test
    fun liftingExceptionRemovesWeeklyBlocking() {
        val s = BlockSchedule(
            listOf(Window(0, 0, 1440)),
            listOf(DateException(at(7, 10), at(7, 12), false)),
        )
        assertTrue(s.isBlocked(at(0, 10)))
        assertTrue(s.isBlocked(at(7, 9, 59)))
        assertFalse(s.isBlocked(at(7, 10)))
        assertFalse(s.isBlocked(at(7, 11, 59)))
        assertTrue(s.isBlocked(at(7, 12)))
    }

    @Test
    fun exceptionCanCrossMidnightAndSpanDays() {
        val s = BlockSchedule(emptyList(), listOf(DateException(at(3, 22), at(5, 6), true)))
        assertTrue(s.isBlocked(at(3, 23)))
        assertTrue(s.isBlocked(at(4, 12)))
        assertTrue(s.isBlocked(at(5, 5, 59)))
        assertFalse(s.isBlocked(at(5, 6)))
    }

    @Test
    fun whenBlockingAndLiftingExceptionsOverlapBlockingWins() {
        val s = BlockSchedule(
            emptyList(),
            listOf(DateException(at(2, 0), at(2, 12), false), DateException(at(2, 6), at(2, 8), true)),
        )
        assertTrue(s.isBlocked(at(2, 7)))
        assertFalse(s.isBlocked(at(2, 9)))
    }

    @Test
    fun nextChangeFindsTheNextBoundaryThatChangesTheState() {
        val s = BlockSchedule(listOf(Window(0, 9 * 60, 17 * 60)))
        assertEquals(at(0, 9), s.nextChange(at(0, 8)))
        assertEquals(at(0, 17), s.nextChange(at(0, 9)))
        assertEquals(at(7, 9), s.nextChange(at(0, 17)))
    }

    @Test
    fun nextChangeIsNullWhenNothingChangesInTheHorizon() {
        assertNull(BlockSchedule().nextChange(at(0, 0)))
        assertNull(BlockSchedule(listOf(Window(0, 0, 1440), Window(1, 0, 1440), Window(2, 0, 1440), Window(3, 0, 1440),
            Window(4, 0, 1440), Window(5, 0, 1440), Window(6, 0, 1440))).nextChange(at(0, 5)))
    }

    @Test
    fun adjacentWindowsAcrossMidnightDoNotReportAFalseChange() {
        val s = BlockSchedule(listOf(Window(1, 20 * 60, 1440), Window(2, 0, 6 * 60)))
        assertEquals(at(2, 6), s.nextChange(at(1, 21)))
    }
}
