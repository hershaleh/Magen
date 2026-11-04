package app.magen.lock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JewishTimesTest {
    private fun g(y: Int, m: Int, d: Int) = CivilDate.toEpochDay(y, m, d)
    private val il = JewishTimes.Settings(inIsrael = true)
    private val ilTwo = JewishTimes.Settings(inIsrael = true, includeSecondDay = true)
    private val dia = JewishTimes.Settings(inIsrael = false)
    private val min = 60_000L
    private val hour = 3_600_000L

    // Jerusalem (UTC+2 in winter, UTC+3 in summer) and New York.
    private val jerLat = 31.778
    private val jerLon = 35.235

    private fun utc(day: Long, h: Int, m: Int = 0) = day * CivilDate.MS_PER_DAY + h * hour + m * min

    // ---- rest days -----------------------------------------------------------------------------

    @Test
    fun everySaturdayIsARestDayAndOrdinaryDaysAreNot() {
        assertTrue(JewishTimes.isRestDay(g(2026, 11, 7), il))
        assertFalse(JewishTimes.isRestDay(g(2026, 11, 6), il))
        assertFalse(JewishTimes.isRestDay(g(2026, 11, 8), il))
    }

    @Test
    fun rosh_hashanaIsTwoDaysEverywhereAndYomKippurOne() {
        for (s in listOf(il, ilTwo, dia)) {
            assertTrue(JewishTimes.isRestDay(g(2026, 9, 12), s))
            assertTrue(JewishTimes.isRestDay(g(2026, 9, 13), s))
            assertTrue(JewishTimes.isRestDay(g(2026, 9, 21), s))
            assertFalse(JewishTimes.isRestDay(g(2026, 9, 22), s))
        }
    }

    @Test
    fun secondYomTovDayDependsOnIsraelAndTheSetting() {
        val sukkot2 = g(2026, 9, 27) // 16 Tishri, a Sunday
        assertFalse("Israel", JewishTimes.isRestDay(sukkot2, il))
        assertTrue("Israel with second day", JewishTimes.isRestDay(sukkot2, ilTwo))
        assertTrue("outside Israel", JewishTimes.isRestDay(sukkot2, dia))
        val simchat = g(2026, 10, 4) // 23 Tishri, a Sunday
        assertFalse(JewishTimes.isRestDay(simchat, il))
        assertTrue(JewishTimes.isRestDay(simchat, dia))
        val pesach2 = g(2027, 4, 23) // 16 Nisan, a Friday
        assertFalse(JewishTimes.isRestDay(pesach2, il))
        assertTrue(JewishTimes.isRestDay(pesach2, dia))
        val shavuot2 = g(2027, 6, 12) // 7 Sivan is a Saturday: a rest day either way
        assertTrue(JewishTimes.isRestDay(shavuot2, il))
    }

    @Test
    fun firstAndLastDaysAreRestDaysButCholHaMoedIsNot() {
        assertTrue(JewishTimes.isRestDay(g(2026, 9, 26), il)) // 15 Tishri
        assertTrue(JewishTimes.isRestDay(g(2026, 10, 3), il)) // 22 Tishri (also Saturday)
        for (d in 28..30) assertFalse("chol hamoed Sep $d", JewishTimes.isRestDay(g(2026, 9, d), il)) // 17-19 Tishri
        assertFalse(JewishTimes.isRestDay(g(2026, 9, 29), dia))
        assertTrue(JewishTimes.isRestDay(g(2027, 4, 22), il)) // 15 Nisan
        assertTrue(JewishTimes.isRestDay(g(2027, 4, 28), il)) // 21 Nisan
        assertFalse(JewishTimes.isRestDay(g(2027, 4, 26), il)) // chol hamoed Pesach
        assertTrue(JewishTimes.isRestDay(g(2027, 6, 11), il)) // 6 Sivan
    }

    // ---- sunset and twilight -------------------------------------------------------------------

    private fun minutesOfDay(ms: Long, offsetHours: Int) = Math.floorMod(ms / min + offsetHours * 60L, 1440L).toInt()

    @Test
    fun jerusalemSunsetIsWithinAFewMinutesOfTheKnownTimes() {
        val summer = SolarTimes.eveningMillis(g(2026, 6, 21), jerLat, jerLon)!!
        assertEquals((19 * 60 + 49).toDouble(), minutesOfDay(summer, 3).toDouble(), 4.0)
        val winter = SolarTimes.eveningMillis(g(2026, 12, 21), jerLat, jerLon)!!
        assertEquals((16 * 60 + 36).toDouble(), minutesOfDay(winter, 2).toDouble(), 5.0)
        val ny = SolarTimes.eveningMillis(g(2026, 6, 21), 40.71, -74.0)!!
        assertEquals((20 * 60 + 31).toDouble(), minutesOfDay(ny, -4).toDouble(), 4.0)
    }

    @Test
    fun twilightComesAfterSunsetAndSunsetLaterInSummer() {
        val d = g(2026, 9, 12)
        val sunset = SolarTimes.eveningMillis(d, jerLat, jerLon)!!
        val tzeit = SolarTimes.eveningMillis(d, jerLat, jerLon, 98.5)!!
        assertTrue("tzeit ${(tzeit - sunset) / min} min after sunset", tzeit - sunset in 30 * min..60 * min)
    }

    @Test
    fun noSunsetInTheMidnightSunGivesNull() {
        assertEquals(null, SolarTimes.eveningMillis(g(2026, 6, 21), 69.65, 18.96))
    }

    // ---- windows -------------------------------------------------------------------------------

    @Test
    fun anOrdinaryShabbatIsOneWindowFromBeforeSunsetFridayToAfterTwilightSaturday() {
        val fri = g(2026, 11, 6)
        val sat = fri + 1
        val ws = JewishTimes.windows(fri, sat, jerLat, jerLon, il)
        assertEquals(1, ws.size)
        val w = ws[0]
        val sunsetFri = SolarTimes.eveningMillis(fri, jerLat, jerLon)!!
        val tzeitSat = SolarTimes.eveningMillis(sat, jerLat, jerLon, 98.5)!!
        assertEquals(sunsetFri - 18 * min, w.startMs)
        assertEquals(tzeitSat, w.endMs)
        assertTrue("about 25 hours", w.endMs - w.startMs in 24 * hour..26 * hour)
    }

    @Test
    fun marginsAndCandleLightingMoveTheWindowEdges() {
        val fri = g(2026, 11, 6)
        val base = JewishTimes.windows(fri, fri + 1, jerLat, jerLon, il)[0]
        val moved = JewishTimes.windows(
            fri, fri + 1, jerLat, jerLon,
            JewishTimes.Settings(candleLightingMin = 40, bufferBeforeMin = 10, bufferAfterMin = 5),
        )[0]
        assertEquals(base.startMs - 32 * min, moved.startMs)
        assertEquals(base.endMs + 5 * min, moved.endMs)
    }

    @Test
    fun aHolidayNextToShabbatIsOneContinuousWindow() {
        // Shavuot 2027: Friday 11 June and Shabbat 12 June.
        val ws = JewishTimes.windows(g(2027, 6, 9), g(2027, 6, 14), jerLat, jerLon, il)
        assertEquals(1, ws.size)
        val sunsetThu = SolarTimes.eveningMillis(g(2027, 6, 10), jerLat, jerLon)!!
        assertEquals(sunsetThu - 18 * min, ws[0].startMs)
        assertTrue("about 49 hours", ws[0].endMs - ws[0].startMs in 48 * hour..50 * hour)
    }

    @Test
    fun roshHashanaFollowedByShabbatIsOneWindowAndDiasporaSukkotHasTwoPairsOfDays() {
        val rh = JewishTimes.windows(g(2026, 9, 10), g(2026, 9, 14), jerLat, jerLon, il)
        assertEquals(1, rh.size) // Sat 12 + Sun 13
        assertTrue(rh[0].endMs - rh[0].startMs in 48 * hour..50 * hour)
        val ny = JewishTimes.windows(g(2026, 9, 25), g(2026, 10, 6), 40.71, -74.0, dia)
        // Sep 26-27 (Sat+Sun, one window), Oct 3-4 (Sat+Sun, one window)
        assertEquals(2, ny.size)
    }

    @Test
    fun aNormalWeekWithNoHolidayHasExactlyOneWindow() {
        assertEquals(1, JewishTimes.windows(g(2026, 11, 1), g(2026, 11, 7), jerLat, jerLon, il).size)
    }

    // ---- isActive ------------------------------------------------------------------------------

    @Test
    fun activeFromBeforeSunsetOnFridayUntilAfterTwilightOnSaturday() {
        val fri = g(2026, 11, 6)
        val off = 2 * hour
        val sunsetFri = SolarTimes.eveningMillis(fri, jerLat, jerLon)!!
        val tzeitSat = SolarTimes.eveningMillis(fri + 1, jerLat, jerLon, 98.5)!!
        assertFalse("Friday noon", JewishTimes.isActive(utc(fri, 10), off, jerLat, jerLon, il))
        assertFalse("20 min before candle lighting", JewishTimes.isActive(sunsetFri - 40 * min, off, jerLat, jerLon, il))
        assertTrue("at candle lighting", JewishTimes.isActive(sunsetFri - 18 * min, off, jerLat, jerLon, il))
        assertTrue("Saturday noon", JewishTimes.isActive(utc(fri + 1, 10), off, jerLat, jerLon, il))
        assertTrue("just before the end", JewishTimes.isActive(tzeitSat - 1, off, jerLat, jerLon, il))
        assertFalse("at the end", JewishTimes.isActive(tzeitSat, off, jerLat, jerLon, il))
        assertFalse("Sunday noon", JewishTimes.isActive(utc(fri + 2, 10), off, jerLat, jerLon, il))
    }

    @Test
    fun cholHaMoedNoonIsNotBlockedAndYomTovNoonIs() {
        val off = 3 * hour
        assertFalse(JewishTimes.isActive(utc(g(2026, 9, 29), 9), off, jerLat, jerLon, il))
        assertTrue(JewishTimes.isActive(utc(g(2026, 9, 26), 9), off, jerLat, jerLon, il))
        assertTrue("second day outside Israel", JewishTimes.isActive(utc(g(2026, 9, 27), 9), off, jerLat, jerLon, dia))
        assertFalse("second day in Israel", JewishTimes.isActive(utc(g(2026, 9, 27), 9), off, jerLat, jerLon, il))
    }

    @Test
    fun nextFindsTheComingWindowAndIsNullOnlyWhenThereIsNone() {
        val now = utc(g(2026, 11, 3), 10)
        val n = JewishTimes.next(now, 2 * hour, jerLat, jerLon, il)
        assertNotNull(n)
        assertTrue(n!!.startMs > now)
        assertTrue(n.startMs < now + 5 * CivilDate.MS_PER_DAY)
    }

    @Test
    fun aPlaceWithoutASunsetDoesNotCrashAndSimplyHasNoWindowForThoseDays() {
        val ws = JewishTimes.windows(g(2026, 6, 19), g(2026, 6, 21), 69.65, 18.96, il)
        assertTrue(ws.isEmpty())
    }
}
