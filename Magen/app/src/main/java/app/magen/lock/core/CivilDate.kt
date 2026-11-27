package app.magen.lock.core

/**
 * Calendar arithmetic on whole days since 1970-01-01 ("epoch day") without java.time, which is not available on
 * API 23-25 without desugaring. Pure logic, no Android dependency.
 */
object CivilDate {
    const val MINUTES_PER_DAY = 1440L
    const val MS_PER_DAY = 86_400_000L

    /** Day of week of [epochDay]: 0 = Sunday ... 6 = Saturday (1970-01-01 was a Thursday). */
    fun dayOfWeek(epochDay: Long): Int = Math.floorMod(epochDay + 4, 7L).toInt()

    fun toEpochDay(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1L else year.toLong()
        val era = Math.floorDiv(y, 400L)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    /** Returns (year, month 1-12, day 1-31). */
    fun fromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
        val z = epochDay + 719_468
        val era = Math.floorDiv(z, 146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return Triple(y, m, d)
    }

    /** Epoch day of the local civil date that contains [utcMillis] in a zone [offsetMs] ahead of UTC. */
    fun localEpochDay(utcMillis: Long, offsetMs: Long): Long = Math.floorDiv(utcMillis + offsetMs, MS_PER_DAY)

    /** Minute of the local day (0-1439) for [utcMillis] in a zone [offsetMs] ahead of UTC. */
    fun localMinuteOfDay(utcMillis: Long, offsetMs: Long): Int =
        (Math.floorMod(utcMillis + offsetMs, MS_PER_DAY) / 60_000L).toInt()

    /** Minutes since the local epoch (epoch day * 1440 + minute of day): one monotonic number per local minute. */
    fun localMinuteIndex(utcMillis: Long, offsetMs: Long): Long = Math.floorDiv(utcMillis + offsetMs, 60_000L)
}
