package app.magen.lock.core

/**
 * The Hebrew calendar by arithmetic (Dershowitz and Reingold, "Calendrical Calculations"), so the holiday
 * calendar needs no network and no data file. Months are numbered from Nisan: Nisan = 1 ... Elul = 6, Tishri = 7,
 * Heshvan = 8, Kislev = 9, Tevet = 10, Shevat = 11, Adar = 12, Adar II = 13 (leap years only).
 * A Hebrew day starts at sunset, but every date here is the civil day it falls on from midnight to midnight.
 */
object HebrewCalendar {
    const val NISAN = 1
    const val SIVAN = 3
    const val TISHRI = 7

    /** Days from 1970-01-01 to the fixed-day origin used by the algorithm (R.D. 1 = 0001-01-01 Gregorian). */
    private const val RD_OF_EPOCH_DAY_0 = 719_163L
    private const val HEBREW_EPOCH_RD = -1_373_427L

    class Date(val year: Int, val month: Int, val day: Int)

    fun isLeapYear(year: Int): Boolean = Math.floorMod(7 * year + 1, 19) < 7

    private fun lastMonth(year: Int) = if (isLeapYear(year)) 13 else 12

    private fun elapsedDays(year: Int): Long {
        val monthsElapsed = Math.floorDiv(235L * year - 234, 19L)
        val partsElapsed = 12_084 + 13_753 * monthsElapsed
        val days = 29 * monthsElapsed + Math.floorDiv(partsElapsed, 25_920L)
        return if (Math.floorMod(3 * (days + 1), 7L) < 3) days + 1 else days
    }

    private fun yearLengthCorrection(year: Int): Int {
        val ny0 = elapsedDays(year - 1)
        val ny1 = elapsedDays(year)
        val ny2 = elapsedDays(year + 1)
        return when {
            ny2 - ny1 == 356L -> 2
            ny1 - ny0 == 382L -> 1
            else -> 0
        }
    }

    private fun newYearRd(year: Int): Long = HEBREW_EPOCH_RD + elapsedDays(year) + yearLengthCorrection(year)

    fun daysInYear(year: Int): Int = (newYearRd(year + 1) - newYearRd(year)).toInt()

    fun daysInMonth(year: Int, month: Int): Int {
        val len = daysInYear(year)
        return when {
            month == 2 || month == 4 || month == 6 || month == 10 || month == 13 -> 29
            month == 12 && !isLeapYear(year) -> 29
            month == 8 && len % 10 != 5 -> 29
            month == 9 && len % 10 == 3 -> 29
            else -> 30
        }
    }

    private fun toRd(year: Int, month: Int, day: Int): Long {
        var rd = newYearRd(year) + day - 1
        if (month < TISHRI) {
            for (m in TISHRI..lastMonth(year)) rd += daysInMonth(year, m)
            for (m in NISAN until month) rd += daysInMonth(year, m)
        } else {
            for (m in TISHRI until month) rd += daysInMonth(year, m)
        }
        return rd
    }

    /** Epoch day (days since 1970-01-01) of a Hebrew date. */
    fun toEpochDay(year: Int, month: Int, day: Int): Long = toRd(year, month, day) - RD_OF_EPOCH_DAY_0

    fun fromEpochDay(epochDay: Long): Date {
        val rd = epochDay + RD_OF_EPOCH_DAY_0
        var year = Math.floorDiv((rd - HEBREW_EPOCH_RD) * 98_496L, 35_975_351L).toInt() - 1
        while (newYearRd(year + 1) <= rd) year++
        var month = if (rd < toRd(year, NISAN, 1)) TISHRI else NISAN
        while (rd > toRd(year, month, daysInMonth(year, month))) month++
        return Date(year, month, (rd - toRd(year, month, 1) + 1).toInt())
    }
}
