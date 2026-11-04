package app.magen.lock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedClockTest {
    private val h = 3_600_000L
    private val t0 = 1_800_000_000_000L

    @Test
    fun firstReadingTrustsTheWallClock() {
        val c = TrustedClock()
        val r = c.read(t0, 1_000, 5)
        assertEquals(t0, r.trustedWall)
        assertEquals(0L, r.clockChangedByMs)
        assertFalse(r.rebooted)
    }

    @Test
    fun trustedTimeAdvancesWithTheMonotonicClockWithinABoot() {
        val c = TrustedClock()
        c.read(t0, 1_000, 5)
        val r = c.read(t0 + h, 1_000 + h, 5)
        assertEquals(t0 + h, r.trustedWall)
        assertEquals(0L, r.clockChangedByMs)
    }

    @Test
    fun clockSetBackIsDetectedAndIgnored() {
        val c = TrustedClock()
        c.read(t0, 1_000, 5)
        val r = c.read(t0 + h - 5 * h, 1_000 + h, 5) // wall set back 5 hours while 1 hour passed
        assertEquals("trusted time did not move back", t0 + h, r.trustedWall)
        assertEquals(-5 * h, r.clockChangedByMs)
    }

    @Test
    fun clockSetForwardIsDetectedAndIgnoredWithinTheBoot() {
        val c = TrustedClock()
        c.read(t0, 1_000, 5)
        val r = c.read(t0 + 30 * h, 1_000 + h, 5)
        assertEquals(t0 + h, r.trustedWall)
        assertEquals(29 * h, r.clockChangedByMs)
    }

    @Test
    fun smallDriftIsNotReportedAsAChange() {
        val c = TrustedClock()
        c.read(t0, 1_000, 5)
        val r = c.read(t0 + h + 30_000, 1_000 + h, 5)
        assertEquals(0L, r.clockChangedByMs)
    }

    @Test
    fun afterARebootTheWallClockIsAcceptedWhenItDidNotGoBack() {
        val c = TrustedClock()
        c.read(t0, 1_000, 5)
        c.read(t0 + h, 1_000 + h, 5)
        val r = c.read(t0 + 8 * h, 500, 6)
        assertTrue(r.rebooted)
        assertEquals(t0 + 8 * h, r.trustedWall)
        assertEquals(0L, r.clockChangedByMs)
    }

    @Test
    fun afterARebootWithTheClockSetBackTrustedTimeStaysWhereItWas() {
        val c = TrustedClock()
        c.read(t0, 1_000, 5)
        c.read(t0 + h, 1_000 + h, 5)
        val r = c.read(t0 - 10 * h, 500, 6)
        assertTrue(r.rebooted)
        assertEquals(t0 + h, r.trustedWall)
        assertEquals(-11 * h, r.clockChangedByMs)
        // and it keeps counting from there, not from the false wall clock
        val later = c.read(t0 - 10 * h + 2 * h, 500 + 2 * h, 6)
        assertEquals(t0 + 3 * h, later.trustedWall)
    }

    @Test
    fun repeatedRebootsWithABackwardsClockNeverGainTime() {
        val c = TrustedClock()
        c.read(t0, 1_000, 5)
        var trusted = c.read(t0 + h, 1_000 + h, 5).trustedWall
        for (boot in 6..12) {
            val r = c.read(t0 - 20 * h, 100, boot)
            assertTrue(r.trustedWall >= trusted)
            trusted = r.trustedWall
        }
        assertEquals(t0 + h, trusted)
    }

    @Test
    fun unknownBootIdFallsBackToTheMonotonicClockGoingBackwards() {
        val c = TrustedClock()
        c.read(t0, 5_000, -1)
        assertFalse(c.read(t0 + h, 5_000 + h, -1).rebooted)
        assertTrue("elapsed went backwards: a reboot", c.read(t0 + 2 * h, 100, -1).rebooted)
    }

    @Test
    fun persistedStateGivesTheSameBehaviourAfterARestartOfTheProcess() {
        val c = TrustedClock()
        c.read(t0, 1_000, 5)
        val s = c.state
        val again = TrustedClock(s[0], s[1], s[2].toInt(), s[3])
        val r = again.read(t0 + h - 6 * h, 1_000 + h, 5)
        assertEquals(t0 + h, r.trustedWall)
        assertEquals(-6 * h, r.clockChangedByMs)
    }

    @Test
    fun acceptingTheWallClockTakesItAsTheTruthAgain() {
        val c = TrustedClock()
        c.read(t0, 1_000, 5)
        c.acceptWallClock(t0 + 40 * h, 2_000, 5)
        assertEquals(t0 + 40 * h, c.read(t0 + 40 * h, 2_000, 5).trustedWall)
    }
}
