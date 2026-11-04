package app.magen.lock.core

/**
 * A clock that a change of the device's date or time cannot move (spec sections 6.1 and 9).
 *
 * Within one boot, time advances with the monotonic clock (SystemClock.elapsedRealtime), which the person cannot
 * set. The wall clock is compared with it only to DETECT a change. After a reboot there is no monotonic proof any
 * more: the wall clock is accepted unless it went BACK past what was already trusted, in which case trusted time
 * stays where it was (time while the device was off is unknown and not counted). Setting the clock FORWARD across
 * a reboot cannot be told from real time passing: that needs the Device Owner restriction against changing the
 * clock, and is a platform limit.
 *
 * Persist the four values and give them back to the constructor.
 */
class TrustedClock(
    private var anchorWall: Long = -1L,
    private var anchorElapsed: Long = -1L,
    private var bootId: Int = -1,
    private var lastTrusted: Long = -1L,
    private val toleranceMs: Long = 2 * 60_000L,
) {
    class Reading(
        /** Trusted time, epoch milliseconds. */
        val trustedWall: Long,
        /** Wall clock minus trusted time when it differs by more than the tolerance, else 0. Negative = set back. */
        val clockChangedByMs: Long,
        /** True for the first reading after a reboot. */
        val rebooted: Boolean,
    )

    val state: List<Long> get() = listOf(anchorWall, anchorElapsed, bootId.toLong(), lastTrusted)

    private fun sameBoot(nowElapsed: Long, nowBoot: Int): Boolean {
        if (anchorWall < 0) return false
        if (bootId >= 0 && nowBoot >= 0) return bootId == nowBoot && nowElapsed >= anchorElapsed
        return nowElapsed >= anchorElapsed
    }

    @Synchronized
    fun read(wall: Long, elapsed: Long, boot: Int): Reading {
        if (anchorWall < 0) {
            anchorWall = wall; anchorElapsed = elapsed; bootId = boot; lastTrusted = wall
            return Reading(wall, 0L, false)
        }
        if (sameBoot(elapsed, boot)) {
            val trusted = anchorWall + (elapsed - anchorElapsed)
            val delta = wall - trusted
            lastTrusted = trusted
            return Reading(trusted, if (kotlin.math.abs(delta) > toleranceMs) delta else 0L, false)
        }
        // A new boot: nothing monotonic to lean on. Accept the wall clock, unless it was set back.
        val setBack = wall < lastTrusted - toleranceMs
        val base = if (setBack) lastTrusted else wall
        anchorWall = base; anchorElapsed = elapsed; bootId = boot; lastTrusted = base
        return Reading(base, if (setBack) wall - base else 0L, true)
    }

    /** Takes the wall clock as the truth again, for when the person confirms that the clock was wrong. */
    @Synchronized
    fun acceptWallClock(wall: Long, elapsed: Long, boot: Int) {
        anchorWall = wall; anchorElapsed = elapsed; bootId = boot; lastTrusted = wall
    }
}
