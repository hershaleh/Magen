package app.magen.lock.core

/**
 * Shabbat and Jewish holiday blocking windows (spec section 8), computed locally from the date and the place, so no
 * network is needed on any Shabbat.
 *
 * Rest days: every Saturday, Rosh Hashana (both days, everywhere), Yom Kippur, the first and last days of Sukkot
 * (with Shemini Atzeret), the first and last days of Pesach, and Shavuot. Chol HaMoed is not a rest day. Outside
 * Israel, or in Israel with [Settings.includeSecondDay], each of those yom tov days (except Rosh Hashana, which is
 * already two days, and Yom Kippur, which is one) is observed for two days.
 *
 * A window runs from before the evening that starts the first rest day of an unbroken run (Shabbat next to a
 * holiday is one window) to after the stars come out at its end. The default offsets are choices, not rules: see
 * [Settings]. Defaults the person has not decided are exposed as settings so they can be changed.
 */
object JewishTimes {
    class Settings(
        val inIsrael: Boolean = true,
        /** In Israel only: also treat the second day of yom tov as a rest day, as outside Israel. */
        val includeSecondDay: Boolean = false,
        /** Candle lighting, minutes before sunset. 18 is the usual custom; Jerusalem uses 40. */
        val candleLightingMin: Int = 18,
        /** Extra margin so blocking starts before the entry, in minutes. */
        val bufferBeforeMin: Int = 0,
        /** Depression of the sun below the horizon that counts as the end (8.5 degrees is a common choice). */
        val twilightDepressionDeg: Double = 8.5,
        /** Extra margin after the end, in minutes. */
        val bufferAfterMin: Int = 0,
    ) {
        val twoDayYomTov: Boolean get() = !inIsrael || includeSecondDay
    }

    /** True when the civil day [epochDay] is Shabbat or a yom tov day. */
    fun isRestDay(epochDay: Long, s: Settings): Boolean {
        if (CivilDate.dayOfWeek(epochDay) == 6) return true
        val h = HebrewCalendar.fromEpochDay(epochDay)
        val two = s.twoDayYomTov
        return when (h.month) {
            HebrewCalendar.TISHRI -> h.day == 1 || h.day == 2 || h.day == 10 ||
                h.day == 15 || (two && h.day == 16) || h.day == 22 || (two && h.day == 23)
            HebrewCalendar.NISAN -> h.day == 15 || (two && h.day == 16) || h.day == 21 || (two && h.day == 22)
            HebrewCalendar.SIVAN -> h.day == 6 || (two && h.day == 7)
            else -> false
        }
    }

    /** One blocking window, UTC milliseconds, [startMs, endMs). */
    class Window(val startMs: Long, val endMs: Long) {
        fun contains(ms: Long) = ms >= startMs && ms < endMs
    }

    private fun windowForRun(first: Long, last: Long, lat: Double, lon: Double, s: Settings): Window? {
        val eve = SolarTimes.eveningMillis(first - 1, lat, lon) ?: return null
        val end = SolarTimes.eveningMillis(last, lat, lon, 90.0 + s.twilightDepressionDeg) ?: return null
        return Window(eve - (s.candleLightingMin + s.bufferBeforeMin) * 60_000L, end + s.bufferAfterMin * 60_000L)
    }

    /** All windows that overlap the days [fromDay]..[toDay], taken as whole UTC days (a window may start before or end after them). */
    fun windows(fromDay: Long, toDay: Long, lat: Double, lon: Double, s: Settings = Settings()): List<Window> {
        val out = ArrayList<Window>()
        var d = fromDay - 3
        val stop = toDay + 3
        while (d <= stop) {
            if (isRestDay(d, s)) {
                var last = d
                while (isRestDay(last + 1, s)) last++
                windowForRun(d, last, lat, lon, s)?.let { w ->
                    val touches = w.endMs > fromDay * CivilDate.MS_PER_DAY && w.startMs < (toDay + 1) * CivilDate.MS_PER_DAY
                    if (touches) out += w
                }
                d = last + 1
            } else {
                d++
            }
        }
        return out
    }

    /**
     * True when [nowMs] lies inside a window. [offsetMs] is the device time zone's offset at that moment, used only
     * to find the civil day at the place.
     */
    fun isActive(nowMs: Long, offsetMs: Long, lat: Double, lon: Double, s: Settings = Settings()): Boolean {
        val day = CivilDate.localEpochDay(nowMs, offsetMs)
        return windows(day - 1, day + 1, lat, lon, s).any { it.contains(nowMs) }
    }

    /** The first window that has not ended yet at [nowMs], looking up to [horizonDays] ahead, or null. */
    fun next(nowMs: Long, offsetMs: Long, lat: Double, lon: Double, s: Settings = Settings(), horizonDays: Int = 400): Window? {
        val day = CivilDate.localEpochDay(nowMs, offsetMs)
        return windows(day - 1, day + horizonDays, lat, lon, s).filter { it.endMs > nowMs }.minByOrNull { it.startMs }
    }
}
