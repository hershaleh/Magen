package app.magen.lock.core

import app.magen.lock.core.BlockSchedule.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaTest {
    private val min = 60_000L
    private val day = CivilDate.toEpochDay(2026, 11, 3) // Tuesday = 2
    private val dow = CivilDate.dayOfWeek(day)
    private val m = day * 1440 + 12 * 60

    private fun quota(minutes: Int?, paused: Boolean = false, breaks: BlockSchedule = BlockSchedule()) =
        QuotaDef(QuotaPlan(minutes), paused, breaks)

    // ---- plan ----------------------------------------------------------------------------------

    @Test
    fun uniformPlanAppliesToEveryDayAndNoLimitMeansNoQuota() {
        val p = QuotaPlan(30)
        for (d in 0..6) assertEquals(30, p.limitMin(d))
        assertNull(QuotaPlan(null).limitMin(2))
        assertNull(Quotas.remainingMs(quota(null), 0, dow))
        assertFalse(Quotas.isExhausted(quota(null), 99 * min, dow, m))
    }

    @Test
    fun perDayPlanOverridesTheUniformValueOnlyForThatDay() {
        val p = QuotaPlan(uniformMin = 60, perDayMin = mapOf(5 to 10, 6 to 0))
        assertEquals(60, p.limitMin(2))
        assertEquals(10, p.limitMin(5))
        assertEquals(0, p.limitMin(6))
        assertEquals(10, QuotaPlan(null, mapOf(1 to 10)).limitMin(1))
        assertNull(QuotaPlan(null, mapOf(1 to 10)).limitMin(2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeQuotaIsRejected() {
        QuotaPlan(-1)
    }

    // ---- exhaustion ----------------------------------------------------------------------------

    @Test
    fun appIsBlockedExactlyWhenTheLimitIsReached() {
        val q = quota(30)
        assertFalse(Quotas.isExhausted(q, 30 * min - 1, dow, m))
        assertTrue(Quotas.isExhausted(q, 30 * min, dow, m))
        assertTrue(Quotas.isExhausted(q, 45 * min, dow, m))
        assertEquals(1L, Quotas.remainingMs(q, 30 * min - 1, dow))
        assertEquals(0L, Quotas.remainingMs(q, 45 * min, dow))
    }

    @Test
    fun zeroMinutesIsAFullBlockFromTheStart() {
        assertTrue(Quotas.isExhausted(quota(0), 0, dow, m))
    }

    @Test
    fun loweringTheQuotaBelowTheUsedTimeBlocksAtOnceAndRaisingItRestoresTheDifference() {
        val used = 20 * min
        assertFalse(Quotas.isExhausted(quota(30), used, dow, m))
        assertTrue("lowered below used", Quotas.isExhausted(quota(15), used, dow, m))
        assertEquals(10 * min, Quotas.remainingMs(quota(30), used, dow))
        assertEquals(25 * min, Quotas.remainingMs(quota(45), used, dow))
        assertFalse("raised again", Quotas.isExhausted(quota(45), used, dow, m))
    }

    // ---- pause and breaks ----------------------------------------------------------------------

    @Test
    fun pausedQuotaBlocksNothingAndResumingBringsTheBlockBack() {
        assertFalse(Quotas.isExhausted(quota(30, paused = true), 99 * min, dow, m))
        assertTrue(Quotas.isExhausted(quota(30, paused = false), 99 * min, dow, m))
    }

    @Test
    fun scheduledBreakPausesTheQuotaOnlyWhileItRuns() {
        val breaks = BlockSchedule(listOf(Window(dow, 12 * 60, 13 * 60)))
        val q = quota(30, breaks = breaks)
        assertTrue(Quotas.isPaused(q, day * 1440 + 12 * 60 + 30))
        assertFalse(Quotas.isExhausted(q, 99 * min, dow, day * 1440 + 12 * 60 + 30))
        assertTrue(Quotas.isExhausted(q, 99 * min, dow, day * 1440 + 13 * 60))
    }

    // ---- warning -------------------------------------------------------------------------------

    @Test
    fun warningIsDueOnceWhenFiveMinutesOrLessAreLeft() {
        assertFalse(Quotas.warningDue(6 * min, false))
        assertTrue(Quotas.warningDue(5 * min, false))
        assertTrue(Quotas.warningDue(1, false))
        assertFalse("already shown", Quotas.warningDue(2 * min, true))
        assertFalse("nothing left is a block, not a warning", Quotas.warningDue(0, false))
        assertFalse("no quota", Quotas.warningDue(null, false))
    }

    // ---- groups --------------------------------------------------------------------------------

    @Test
    fun anAppInTwoGroupsIsReported() {
        val ok = mapOf("games" to setOf("a", "b"), "social" to setOf("c"))
        assertTrue(QuotaGroups.conflicts(ok).isEmpty())
        val bad = mapOf("games" to setOf("a", "b"), "social" to setOf("b", "c"))
        assertEquals(setOf("b"), QuotaGroups.conflicts(bad).keys)
        assertEquals(setOf("games", "social"), QuotaGroups.conflicts(bad)["b"]!!.toSet())
    }

    // ---- ledger and tracker --------------------------------------------------------------------

    @Test
    fun onlyForegroundTimeIsCountedAndLeavingStopsTheCount() {
        val t = UsageTracker()
        t.sample(0, day, "a", null, true, false)
        t.sample(10_000, day, "a", null, true, false) // 10 s of a
        t.sample(20_000, day, null, null, true, false) // 10 s more of a, then nothing in front
        t.sample(50_000, day, null, null, true, false) // 30 s with no app: counts for nobody
        assertEquals(20_000L, t.ledger.personalUsed("a"))
    }

    @Test
    fun switchingBetweenAppsCreditsEachForItsOwnInterval() {
        val t = UsageTracker()
        t.sample(0, day, "a", "g", true, true)
        t.sample(10_000, day, "b", "g", true, true)
        t.sample(25_000, day, "a", "g", true, true)
        assertEquals(10_000L, t.ledger.personalUsed("a"))
        assertEquals(15_000L, t.ledger.personalUsed("b"))
        assertEquals("the shared group counts both", 25_000L, t.ledger.groupUsed("g"))
    }

    @Test
    fun theSameMomentSampledTwiceIsNotCountedTwice() {
        val t = UsageTracker()
        t.sample(0, day, "a", null, true, false)
        t.sample(10_000, day, "a", null, true, false)
        t.sample(10_000, day, "a", null, true, false)
        t.sample(10_000, day, "a", null, true, false)
        assertEquals(10_000L, t.ledger.personalUsed("a"))
    }

    @Test
    fun aLongSilenceIsCappedAndABackwardsClockCreditsNothing() {
        val t = UsageTracker(maxGapMs = 60_000L)
        t.sample(0, day, "a", null, true, false)
        t.sample(3_600_000, day, "a", null, true, false) // an hour without an event
        assertEquals(60_000L, t.ledger.personalUsed("a"))
        t.sample(1_000, day, "a", null, true, false) // reboot: elapsed restarted
        assertEquals(60_000L, t.ledger.personalUsed("a"))
    }

    @Test
    fun pausedQuotaAccumulatesNothingForItsOwnCounterOnly() {
        val t = UsageTracker()
        t.sample(0, day, "a", "g", personalCounts = false, groupCounts = true)
        t.sample(10_000, day, "a", "g", personalCounts = false, groupCounts = true)
        assertEquals(0L, t.ledger.personalUsed("a"))
        assertEquals(10_000L, t.ledger.groupUsed("g"))
    }

    @Test
    fun newDayClearsTheCountersButTheSameOrAnEarlierDayNeverDoes() {
        val t = UsageTracker()
        t.sample(0, day, "a", null, true, false)
        t.sample(10_000, day, "a", null, true, false)
        assertEquals(10_000L, t.ledger.personalUsed("a"))
        t.sample(20_000, day - 1, "a", null, true, false) // clock set back a day: usage stays
        assertEquals(20_000L, t.ledger.personalUsed("a"))
        t.sample(30_000, day + 1, "a", null, true, false)
        assertEquals(0L, t.ledger.personalUsed("a"))
        assertEquals(day + 1, t.ledger.day)
    }

    @Test
    fun movingAnAppToAnotherGroupKeepsEarlierGroupUsageAndThePersonalCounter() {
        val t = UsageTracker()
        t.sample(0, day, "a", "old", true, true)
        t.sample(10_000, day, "a", "new", true, true) // moved to "new" after 10 s in "old"
        t.sample(30_000, day, "a", "new", true, true)
        assertEquals(10_000L, t.ledger.groupUsed("old"))
        assertEquals(20_000L, t.ledger.groupUsed("new"))
        assertEquals(30_000L, t.ledger.personalUsed("a"))
    }

    @Test
    fun usageSurvivesRecreatingTheTrackerFromTheSavedLedger() {
        val t = UsageTracker()
        t.sample(0, day, "a", null, true, false)
        t.sample(10_000, day, "a", null, true, false)
        val restored = UsageTracker(t.ledger)
        restored.sample(500_000, day, "a", null, true, false) // first sample after a restart credits nothing
        restored.sample(510_000, day, "a", null, true, false)
        assertEquals(20_000L, restored.ledger.personalUsed("a"))
    }
}
