package app.magen.lock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [Deadline], the window behind the PIN lockout and the uninstall approval.
 * "Wall" is the changeable wall clock, "elapsed" is elapsedRealtime (restarts at every boot), "boot" is BOOT_COUNT.
 */
class DeadlineTest {
    private val sec = 1_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    // A 10-minute window started at wall=1_000_000, elapsed=500_000, boot 7.
    private val w0 = 1_000_000L
    private val e0 = 500_000L
    private val start = Deadline.start(10 * minute, nowWall = w0, nowElapsed = e0, bootId = 7)

    // ---- basics -----------------------------------------------------------------------------------

    @Test
    fun noneIsNeverActive() {
        assertEquals(0L, Deadline.NONE.remainingMs(5L, 5L, 1))
        assertFalse(Deadline.NONE.isActive(Long.MAX_VALUE, 0L, 1))
        assertFalse(Deadline.NONE.isSet)
        assertTrue(Deadline.NONE.observe(1L, 1L, 1) === Deadline.NONE)
    }

    @Test
    fun zeroLengthWindowIsAlreadyClosed() {
        val closed = Deadline.start(0L, w0, e0, 7)
        assertFalse(closed.isActive(w0, e0, 7))
        assertFalse(closed.isActive(w0 - hour, e0, 7))
        assertTrue(closed.observe(w0, e0, 7) === Deadline.NONE)
    }

    @Test
    fun remainingIsNeverNegativeOrAboveTheOriginalLength() {
        assertEquals(0L, start.remainingMs(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2, 7))
        // After a reboot the huge wall-clock value is ignored: the recorded remaining (here the full length) stays.
        assertEquals(10 * minute, start.remainingMs(Long.MAX_VALUE / 2, 0L, 8))
        assertEquals(10 * minute, start.remainingMs(w0, e0 - hour, 7))
    }

    // ---- within one boot --------------------------------------------------------------------------

    @Test
    fun sameBootCountsDownWithTheMonotonicClock() {
        assertEquals(10 * minute, start.remainingMs(w0, e0, 7))
        assertEquals(9 * minute, start.remainingMs(w0 + minute, e0 + minute, 7))
    }

    @Test
    fun sameBootExpiresAtTheEnd() {
        assertEquals(0L, start.remainingMs(w0 + 10 * minute, e0 + 10 * minute, 7))
        assertFalse(start.isActive(w0 + 11 * minute, e0 + 11 * minute, 7))
    }

    @Test
    fun sameBootIgnoresTheWallClockMovedBackwards() {
        assertEquals(9 * minute, start.remainingMs(w0 - hour, e0 + minute, 7))
    }

    @Test
    fun sameBootIgnoresTheWallClockMovedForwards() {
        assertEquals(9 * minute, start.remainingMs(w0 + 24 * hour, e0 + minute, 7))
        assertTrue(start.isActive(w0 + 24 * hour, e0 + minute, 7))
    }

    @Test
    fun aWindowEndedByMonotonicTimeIsNotRevivedByAWallClockMovedBackInTheSameBoot() {
        assertEquals(0L, start.remainingMs(w0 - hour, e0 + 11 * minute, 7))
        assertTrue(start.observe(w0 - hour, e0 + 11 * minute, 7) === Deadline.NONE)
    }

    // ---- checkpoints ------------------------------------------------------------------------------

    @Test
    fun observeKeepsTheSameInstanceUntilEnoughTimeHasPassedForACheckpoint() {
        assertTrue(start.observe(w0 + sec, e0 + sec, 7) === start)
        val cp = start.observe(w0 + 6 * sec, e0 + 6 * sec, 7)
        assertFalse(cp === start)
        assertEquals(e0 + 6 * sec, cp.checkpointElapsed)
        assertEquals(start.elapsedUntil, cp.elapsedUntil)
        assertEquals(10 * minute - 6 * sec, cp.remainingMs(w0 + 6 * sec, e0 + 6 * sec, 7))
    }

    // ---- reboot -----------------------------------------------------------------------------------

    @Test
    fun afterRebootTheWallClockIsNotUsedAtAll() {
        // Boot 8, no checkpoint yet: the window keeps what was recorded, whatever the wall clock says.
        val year = 365 * 24 * hour
        assertEquals(10 * minute, start.remainingMs(w0 - year, 123L, 8))
        assertEquals(10 * minute, start.remainingMs(w0 + 5 * hour, 123L, 8))
    }

    @Test
    fun afterRebootTheWindowContinuesFromTheLastCheckpoint() {
        val seen = start.observe(w0 + 3 * minute, e0 + 3 * minute, 7)
        assertEquals(7 * minute, seen.remainingMs(w0 - 24 * hour, 10_000L, 8))
        assertEquals(7 * minute, seen.remainingMs(w0 + 24 * hour, 10_000L, 8))
    }

    @Test
    fun observeAfterRebootRebasesOntoTheNewBootAndCountsDownThere() {
        val seen = start.observe(w0 + 3 * minute, e0 + 3 * minute, 7)
        val rebased = seen.observe(w0 - hour, 10_000L, 8)
        assertEquals(8, rebased.bootId)
        assertEquals(10_000L + 7 * minute, rebased.elapsedUntil)
        assertEquals(10_000L, rebased.checkpointElapsed)
        assertEquals(6 * minute, rebased.remainingMs(w0 - hour, 10_000L + minute, 8))
    }

    @Test
    fun repeatedRebootsAndRolledBackClocksNeverRenewTheWindow() {
        val remainders = ArrayList<Long>()

        // Open a window and let 3 minutes pass in boot 7 (the window is looked at, so a checkpoint is recorded).
        var d = start.observe(w0 + 3 * minute, e0 + 3 * minute, 7)
        remainders += d.remainingMs(w0 + 3 * minute, e0 + 3 * minute, 7)

        // Reboot before the window ends (boot 8), clock set back a day.
        var wall = w0 - 24 * hour
        d = d.observe(wall, 10_000L, 8)
        remainders += d.remainingMs(wall, 10_000L, 8)

        // Two more minutes pass in boot 8.
        wall += 2 * minute
        d = d.observe(wall, 10_000L + 2 * minute, 8)
        remainders += d.remainingMs(wall, 10_000L + 2 * minute, 8)

        // Another reboot (boot 9), clock set back even further.
        wall = w0 - 48 * hour
        d = d.observe(wall, 20_000L, 9)
        remainders += d.remainingMs(wall, 20_000L, 9)

        // A third reboot (boot 10) before anything looked at the window, clock back to the original time.
        remainders += d.remainingMs(w0, 5_000L, 10)

        assertEquals(listOf(7 * minute, 7 * minute, 5 * minute, 5 * minute, 5 * minute), remainders)
        for (i in 1 until remainders.size) {
            assertTrue("window grew at step $i: $remainders", remainders[i] <= remainders[i - 1])
        }
        assertTrue(remainders.all { it <= 10 * minute })
    }

    @Test
    fun manyRebootsWithoutAnyProgressStillNeverExceedTheOriginalLength() {
        var d: Deadline = start
        for (boot in 8..40) {
            d = d.observe(w0 - boot * hour, 1_000L, boot)
            assertTrue(d.remainingMs(w0 - boot * hour, 1_000L, boot) <= 10 * minute)
        }
    }

    @Test
    fun anEndedWindowStaysClosedAfterRebootAndRolledBackClock() {
        val ended = start.observe(w0 + 11 * minute, e0 + 11 * minute, 7)
        assertTrue(ended === Deadline.NONE)
        assertEquals(0L, ended.remainingMs(w0 - hour, 123L, 8))
        assertEquals(0L, ended.remainingMs(w0, e0, 7))
    }

    // ---- boot id unknown --------------------------------------------------------------------------

    @Test
    fun unknownBootIsRecognisedByElapsedGoingBackwards() {
        val noBoot = Deadline.start(10 * minute, w0, e0, bootId = -1)
        // Elapsed moved forward: same boot, counts down monotonically whatever the wall clock says.
        assertEquals(9 * minute, noBoot.remainingMs(w0 - hour, e0 + minute, -1))
        // Elapsed went backwards: rebooted, so the recorded remaining is kept, the wall clock is ignored.
        assertEquals(10 * minute, noBoot.remainingMs(w0 - hour, 100L, -1))
        assertEquals(10 * minute, noBoot.remainingMs(w0 + hour, 100L, -1))
    }

    @Test
    fun aMissingBootIdNowDoesNotCountAsAKnownBoot() {
        // Stored in boot 7, but BOOT_COUNT could not be read this time (-1): fall back to the elapsed check.
        assertEquals(9 * minute, start.remainingMs(w0, e0 + minute, -1))
        assertEquals(10 * minute, start.remainingMs(w0, 100L, -1))
    }

    // ---- data from an older version ---------------------------------------------------------------

    @Test
    fun legacyDataUsesTheWallClockCappedByTheGivenMaximum() {
        val now = 5_000_000L
        assertEquals(5 * minute, Deadline.legacy(now + 5 * minute, 10 * minute).remainingMs(now, 1L, 3))
        assertEquals(10 * minute, Deadline.legacy(now + 5 * hour, 10 * minute).remainingMs(now, 1L, 3))
        assertEquals(0L, Deadline.legacy(now - minute, 10 * minute).remainingMs(now, 1L, 3))
    }

    @Test
    fun legacyWithoutAnEndIsInactiveAndNotSet() {
        assertEquals(0L, Deadline.legacy(0L, 10 * minute).remainingMs(5_000_000L, 1L, 3))
        assertFalse(Deadline.legacy(0L, 10 * minute).isSet)
        assertTrue(Deadline.legacy(5L, 10 * minute).isSet)
    }

    @Test
    fun legacyDataIsConvertedToTheMonotonicFormWhenFirstObserved() {
        val now = 5_000_000L
        val old = Deadline.legacy(now + 5 * minute, 10 * minute)
        val converted = old.observe(now, 40_000L, 3)
        assertEquals(40_000L + 5 * minute, converted.elapsedUntil)
        assertEquals(3, converted.bootId)
        // Now a rolled-back wall clock no longer matters.
        assertEquals(4 * minute, converted.remainingMs(now - 10 * hour, 40_000L + minute, 3))
    }

    @Test
    fun endedLegacyDataIsClosedForGood() {
        val now = 5_000_000L
        assertTrue(Deadline.legacy(now - minute, 10 * minute).observe(now, 1L, 3) === Deadline.NONE)
    }
}
