package app.magen.lock.core

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunset and the evening twilight at a place, from the NOAA solar position formulas. Accurate to about a minute
 * outside polar regions, which is the precision the Shabbat window needs; a place where the sun does not set (or
 * the twilight never ends) gives null. Pure logic, no Android dependency.
 */
object SolarTimes {
    /** Zenith angle of the sun's centre at sunset: 90 degrees plus refraction and the sun's radius. */
    const val SUNSET_ZENITH = 90.833

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    /**
     * UTC milliseconds of the evening moment when the sun's centre is at [zenithDeg] from straight up, on the civil
     * [epochDay] at the place ([latitude] north positive, [longitude] east positive, degrees). Use
     * [SUNSET_ZENITH] for sunset and 90 + [depressionDeg] for a twilight (for example 98.5 for 8.5 degrees).
     */
    fun eveningMillis(epochDay: Long, latitude: Double, longitude: Double, zenithDeg: Double = SUNSET_ZENITH): Long? {
        // Local solar noon in UTC minutes is about 720 - 4 * longitude; the time of the sun's position is taken there.
        var utcMinutes = 720.0 - 4.0 * longitude + 360.0
        repeat(2) {
            val jd = epochDay + 2_440_587.5 + utcMinutes / 1440.0
            val t = (jd - 2_451_545.0) / 36_525.0
            val l0 = (280.46646 + t * (36_000.76983 + 0.0003032 * t)).mod(360.0)
            val m = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
            val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
            val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
            val omega = 125.04 - 1934.136 * t
            val lambda = l0 + c - 0.00569 - 0.00478 * sin(rad(omega))
            val seconds = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))
            val obliq = 23.0 + (26.0 + seconds / 60.0) / 60.0 + 0.00256 * cos(rad(omega))
            val decl = asin(sin(rad(obliq)) * sin(rad(lambda)))
            val y = tan(rad(obliq) / 2).let { it * it }
            val eqTime = 4.0 * deg(
                y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
                    0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m)),
            )
            val cosHa = cos(rad(zenithDeg)) / (cos(rad(latitude)) * cos(decl)) - tan(rad(latitude)) * tan(decl)
            if (cosHa < -1.0 || cosHa > 1.0) return null
            val ha = deg(acos(cosHa))
            utcMinutes = 720.0 - 4.0 * (longitude - ha) - eqTime
        }
        return epochDay * CivilDate.MS_PER_DAY + Math.round(utcMinutes * 60_000.0)
    }
}
