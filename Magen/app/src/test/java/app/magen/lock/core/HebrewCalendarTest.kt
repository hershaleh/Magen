package app.magen.lock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HebrewCalendarTest {
    private fun g(y: Int, m: Int, d: Int) = CivilDate.toEpochDay(y, m, d)
    private fun hebrew(y: Int, m: Int, d: Int) = HebrewCalendar.toEpochDay(y, m, d)

    @Test
    fun knownHolidayDatesMatchPublishedCalendars() {
        assertEquals(g(2025, 9, 23), hebrew(5786, 7, 1)) // Rosh Hashana 5786
        assertEquals(g(2026, 4, 2), hebrew(5786, 1, 15)) // Pesach 5786
        assertEquals(g(2026, 5, 22), hebrew(5786, 3, 6)) // Shavuot 5786
        assertEquals(g(2026, 9, 12), hebrew(5787, 7, 1)) // Rosh Hashana 5787
        assertEquals(g(2026, 9, 21), hebrew(5787, 7, 10)) // Yom Kippur 5787
        assertEquals(g(2026, 9, 26), hebrew(5787, 7, 15)) // Sukkot 5787
        assertEquals(g(2027, 4, 22), hebrew(5787, 1, 15)) // Pesach 5787
        assertEquals(g(2027, 6, 11), hebrew(5787, 3, 6)) // Shavuot 5787
        assertEquals(g(2027, 10, 2), hebrew(5788, 7, 1)) // Rosh Hashana 5788
    }

    @Test
    fun conversionBackGivesTheSameHebrewDate() {
        val h = HebrewCalendar.fromEpochDay(g(2026, 9, 12))
        assertEquals(Triple(5787, 7, 1), Triple(h.year, h.month, h.day))
        val p = HebrewCalendar.fromEpochDay(g(2027, 4, 22))
        assertEquals(Triple(5787, 1, 15), Triple(p.year, p.month, p.day))
    }

    @Test
    fun everyDayOfThreeCenturiesRoundTripsAndStepsByExactlyOneDay() {
        var prev: HebrewCalendar.Date? = null
        for (e in g(1900, 1, 1)..g(2200, 12, 31)) {
            val h = HebrewCalendar.fromEpochDay(e)
            assertEquals("epoch $e", e, HebrewCalendar.toEpochDay(h.year, h.month, h.day))
            assertTrue("day ${h.day} of ${h.year}/${h.month}", h.day in 1..HebrewCalendar.daysInMonth(h.year, h.month))
            val p = prev
            if (p != null) {
                val sameMonth = h.year == p.year && h.month == p.month && h.day == p.day + 1
                val nextMonth = h.day == 1 && p.day == HebrewCalendar.daysInMonth(p.year, p.month)
                assertTrue("not consecutive at $e", sameMonth || nextMonth)
            }
            prev = h
        }
    }

    @Test
    fun yearLengthsAreTheSixLegalOnesAndLeapYearsFollowTheNineteenYearCycle() {
        val legal = setOf(353, 354, 355, 383, 384, 385)
        for (y in 5600..5900) {
            val len = HebrewCalendar.daysInYear(y)
            assertTrue("year $y has $len days", len in legal)
            assertEquals("leap year $y length", HebrewCalendar.isLeapYear(y), len >= 383)
        }
        assertEquals("7 leap years in every 19", 7, (5788..5806).count { HebrewCalendar.isLeapYear(it) })
        assertTrue(HebrewCalendar.isLeapYear(5784))
        assertTrue(HebrewCalendar.isLeapYear(5787)) // Adar II 5787, which is why Pesach 5787 is late in April
        assertFalse(HebrewCalendar.isLeapYear(5786))
    }

    @Test
    fun monthLengthsAddUpToTheYearLength() {
        for (y in 5700..5800) {
            val last = if (HebrewCalendar.isLeapYear(y)) 13 else 12
            val sum = (1..last).sumOf { HebrewCalendar.daysInMonth(y, it) }
            assertEquals("year $y", HebrewCalendar.daysInYear(y), sum)
        }
    }
}
