package app.magen.lock.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CivilDateTest {
    @Test
    fun epochDayRoundTripsAndMatchesJavaTimeForTwoCenturies() {
        var d = LocalDate.of(1950, 1, 1).toEpochDay()
        val end = LocalDate.of(2150, 12, 31).toEpochDay()
        while (d <= end) {
            val ld = LocalDate.ofEpochDay(d)
            val (y, m, day) = CivilDate.fromEpochDay(d)
            assertEquals("$ld", Triple(ld.year, ld.monthValue, ld.dayOfMonth), Triple(y, m, day))
            assertEquals(d, CivilDate.toEpochDay(y, m, day))
            assertEquals("dow $ld", ld.dayOfWeek.value % 7, CivilDate.dayOfWeek(d))
            d += 1
        }
    }

    @Test
    fun knownDates() {
        assertEquals(0L, CivilDate.toEpochDay(1970, 1, 1))
        assertEquals(4, CivilDate.dayOfWeek(0))
        assertEquals(6, CivilDate.dayOfWeek(CivilDate.toEpochDay(2026, 9, 12))) // Saturday
    }

    @Test
    fun localDayAndMinuteFollowTheZoneOffset() {
        val utc = CivilDate.toEpochDay(2026, 3, 1) * CivilDate.MS_PER_DAY + 23 * 3_600_000L + 30 * 60_000L // 23:30 UTC
        assertEquals(CivilDate.toEpochDay(2026, 3, 1), CivilDate.localEpochDay(utc, 0))
        assertEquals(CivilDate.toEpochDay(2026, 3, 2), CivilDate.localEpochDay(utc, 2 * 3_600_000L))
        assertEquals(90, CivilDate.localMinuteOfDay(utc, 2 * 3_600_000L)) // 01:30
        assertEquals(CivilDate.toEpochDay(2026, 3, 1) * 1440 + 23 * 60 + 30, CivilDate.localMinuteIndex(utc, 0))
    }
}
