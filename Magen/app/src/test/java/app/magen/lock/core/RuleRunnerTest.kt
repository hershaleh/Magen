package app.magen.lock.core

import app.magen.lock.core.BlockSchedule.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole flow without a device: configure, count foreground time, decide, warn, survive a restart. */
class RuleRunnerTest {
    private val min = 60_000L
    private val hour = 3_600_000L
    private val off = 2 * hour // UTC+2
    private val day = CivilDate.toEpochDay(2026, 11, 3) // Tuesday
    private val noonUtc = day * CivilDate.MS_PER_DAY + 10 * hour // 12:00 local
    private val app = "com.example.game"

    private fun cfg(q: Int? = null, sched: BlockSchedule? = null, group: Boolean = false, groupQ: Int = 60) = RuleConfig(
        apps = mapOf(app to AppRule(sched, q?.let { QuotaDef(QuotaPlan(it)) }, if (group) "g" else null)),
        groups = if (group) mapOf("g" to GroupRule("G", QuotaDef(QuotaPlan(groupQ)))) else emptyMap(),
    )

    /** Runs a foreground app for [ms] in 5-second ticks from [startElapsed]; returns the last step. */
    private fun use(r: RuleRunner, pkg: String?, ms: Long, startElapsed: Long, wall0: Long = noonUtc): RuleRunner.Step {
        var last: RuleRunner.Step? = null
        var t = 0L
        while (t <= ms) {
            last = r.step(wall0 + t, startElapsed + t, 1, off, pkg, false)
            t += 5_000
        }
        return last!!
    }

    @Test
    fun quotaIsCountedFromForegroundTimeAndBlocksWhenUsedUp() {
        val r = RuleRunner(cfg(q = 10))
        val s1 = use(r, app, 9 * min, 0)
        assertFalse(s1.decision.blocked)
        val s2 = use(r, app, 2 * min, 9 * min + 5_000, noonUtc + 9 * min + 5_000)
        assertTrue(s2.decision.blocked)
        assertEquals(BlockSource.PERSONAL_QUOTA, s2.decision.primary)
    }

    @Test
    fun anAppWithoutAnyRuleIsNeverBlockedOrCounted() {
        val r = RuleRunner(RuleConfig())
        val s = use(r, "com.other", 5 * min, 0)
        assertFalse(s.decision.blocked)
        assertTrue(r.ledger.personalMs.isEmpty())
    }

    @Test
    fun theWarningComesOnceWithFiveMinutesLeft() {
        val r = RuleRunner(cfg(q = 10))
        val warnings = ArrayList<RuleWarning>()
        var t = 0L
        while (t <= 9 * min) {
            warnings += r.step(noonUtc + t, t, 1, off, app, false).warnings
            t += 5_000
        }
        assertEquals(1, warnings.size)
        assertEquals(RuleWarning.Kind.QUOTA_PERSONAL, warnings[0].kind)
        assertTrue(warnings[0].remainingMs <= 5 * min)
    }

    @Test
    fun openingAnAppWithLessThanFiveMinutesLeftWarnsAtOnce() {
        val used = UsageLedger(day, mapOf(app to 8 * min))
        val r = RuleRunner(cfg(q = 10), used)
        val first = r.step(noonUtc, 0, 1, off, app, false)
        assertEquals(1, first.warnings.size)
        assertEquals(0, r.step(noonUtc + 5_000, 5_000, 1, off, app, false).warnings.size)
    }

    @Test
    fun theCountersSurviveARestartAndDoNotResetOnMidnightOfTheSameTrustedDay() {
        val r = RuleRunner(cfg(q = 10))
        use(r, app, 6 * min, 0)
        val saved = r.ledger
        val st = r.clock.state
        val restarted = RuleRunner(cfg(q = 10), saved, TrustedClock(st[0], st[1], st[2].toInt(), st[3]))
        val s = use(restarted, app, 5 * min, 6 * min + 5_000, noonUtc + 6 * min + 5_000)
        assertTrue("6 + 5 minutes exceeds 10", s.decision.blocked)
    }

    @Test
    fun aNewLocalDayClearsTheUsageAndLiftsTheQuotaBlock() {
        val r = RuleRunner(cfg(q = 5))
        assertTrue(use(r, app, 6 * min, 0).decision.blocked)
        val next = r.step(noonUtc + 13 * hour, 13 * hour, 1, off, app, false) // 01:00 next day
        assertFalse(next.decision.blocked)
        assertEquals(0L, r.ledger.personalUsed(app))
    }

    @Test
    fun settingTheClockBackDoesNotGiveTheQuotaBack() {
        val r = RuleRunner(cfg(q = 5))
        assertTrue(use(r, app, 6 * min, 0).decision.blocked)
        // Same boot: the wall clock is moved back a whole day while 10 more seconds pass.
        val s = r.step(noonUtc - 24 * hour, 6 * min + 10_000, 1, off, app, false)
        assertTrue(s.decision.blocked)
        assertTrue("the change is reported", s.clockChangedByMs < 0)
    }

    @Test
    fun groupQuotaAndPersonalQuotaBothApplyAndEitherBlocks() {
        val r = RuleRunner(cfg(q = 100, group = true, groupQ = 5))
        val s = use(r, app, 6 * min, 0)
        assertEquals(BlockSource.GROUP_QUOTA, s.decision.primary)
    }

    @Test
    fun pausedQuotaCountsNothingAndBlocksNothing() {
        val c = RuleConfig(mapOf(app to AppRule(null, QuotaDef(QuotaPlan(1), paused = true))))
        val r = RuleRunner(c)
        val s = use(r, app, 5 * min, 0)
        assertFalse(s.decision.blocked)
        assertEquals(0L, r.ledger.personalUsed(app))
    }

    @Test
    fun scheduleBlocksOnlyInsideItsWindow() {
        val dow = CivilDate.dayOfWeek(day)
        val r = RuleRunner(cfg(sched = BlockSchedule(listOf(Window(dow, 13 * 60, 14 * 60)))))
        assertFalse(r.step(noonUtc, 0, 1, off, app, false).decision.blocked)
        val s = r.step(noonUtc + 90 * min, 90 * min, 1, off, app, false) // 13:30
        assertEquals(BlockSource.SCHEDULE, s.decision.primary)
    }

    @Test
    fun essentialAppsAreNeverBlockedOrCounted() {
        val r = RuleRunner(cfg(q = 0))
        val s = r.step(noonUtc, 0, 1, off, app, essential = true)
        assertFalse(s.decision.blocked)
        var t = 5_000L
        while (t <= 2 * min) { assertFalse(r.step(noonUtc + t, t, 1, off, app, true).decision.blocked); t += 5_000 }
        assertEquals(0L, r.ledger.personalUsed(app))
    }

    @Test
    fun screenOffStopsTheCount() {
        val r = RuleRunner(cfg(q = 100))
        r.step(noonUtc, 0, 1, off, app, false)
        r.step(noonUtc + 30_000, 30_000, 1, off, null, false) // screen off
        r.step(noonUtc + 10 * min, 10 * min, 1, off, null, false)
        assertEquals(30_000L, r.ledger.personalUsed(app))
    }

    // ---- Shabbat -------------------------------------------------------------------------------

    private val jerLat = 31.778
    private val jerLon = 35.235
    private val shabbatCfg = ShabbatConfig(true, JewishTimes.Settings(inIsrael = true), jerLat, jerLon)
    private val fri = CivilDate.toEpochDay(2026, 11, 6)

    @Test
    fun shabbatBlocksEveryNonEssentialAppEvenWithoutAnyRuleOfItsOwn() {
        val r = RuleRunner(RuleConfig(shabbat = shabbatCfg))
        val sat = (fri + 1) * CivilDate.MS_PER_DAY + 8 * hour // Saturday 10:00 local
        val s = r.step(sat, 0, 1, off, "com.some.app", false)
        assertEquals(BlockSource.SHABBAT, s.decision.primary)
        assertTrue(s.shabbat.active)
        assertFalse(r.step(sat + 5_000, 5_000, 1, off, "com.some.app", true).decision.blocked)
    }

    @Test
    fun shabbatWinsOverAPausedQuotaAndAnActiveBreak() {
        val dow = 6
        val q = QuotaDef(QuotaPlan(1000), paused = true, breaks = BlockSchedule(listOf(Window(dow, 0, 1440))))
        val r = RuleRunner(RuleConfig(mapOf(app to AppRule(null, q)), shabbat = shabbatCfg))
        val sat = (fri + 1) * CivilDate.MS_PER_DAY + 8 * hour
        assertEquals(BlockSource.SHABBAT, r.step(sat, 0, 1, off, app, false).decision.primary)
    }

    @Test
    fun noBlockingOnAWeekdayAndRegularRulesReturnAfterShabbat() {
        val c = RuleConfig(mapOf(app to AppRule(null, QuotaDef(QuotaPlan(1)))), shabbat = shabbatCfg)
        val r = RuleRunner(c)
        val sun = (fri + 2) * CivilDate.MS_PER_DAY + 10 * hour
        val s = r.step(sun, 0, 1, off, app, false)
        assertFalse(s.shabbat.active)
        assertFalse("no usage yet, so the normal quota does not block", s.decision.blocked)
    }

    @Test
    fun aWindowThatHasBegunIsNotCutShortWhenThePlaceIsRemoved() {
        val r = RuleRunner(RuleConfig(shabbat = shabbatCfg))
        val sat = (fri + 1) * CivilDate.MS_PER_DAY + 8 * hour
        assertTrue(r.step(sat, 0, 1, off, "p", false).shabbat.active)
        r.config = RuleConfig(shabbat = ShabbatConfig(true, JewishTimes.Settings(inIsrael = true), null, null))
        val s = r.step(sat + 5_000, 5_000, 1, off, "p", false)
        assertTrue("still active", s.shabbat.active)
        assertEquals(BlockSource.SHABBAT, s.decision.primary)
    }

    @Test
    fun switchingTheFeatureOffDuringAWindowDoesNotLiftIt() {
        val r = RuleRunner(RuleConfig(shabbat = shabbatCfg))
        val sat = (fri + 1) * CivilDate.MS_PER_DAY + 8 * hour
        r.step(sat, 0, 1, off, "p", false)
        r.config = RuleConfig(shabbat = ShabbatConfig(false))
        assertTrue(r.step(sat + 5_000, 5_000, 1, off, "p", false).decision.blocked)
    }

    @Test
    fun aRestartInTheMiddleOfShabbatBlocksAgainFromTheClock() {
        val sat = (fri + 1) * CivilDate.MS_PER_DAY + 8 * hour
        val fresh = RuleRunner(RuleConfig(shabbat = shabbatCfg))
        assertTrue(fresh.step(sat, 100, 7, off, "p", false).decision.blocked)
    }

    @Test
    fun enabledWithoutAPlaceIsReportedAsNotVerifiedAndBlocksNothing() {
        val r = RuleRunner(RuleConfig(shabbat = ShabbatConfig(true)))
        val sat = (fri + 1) * CivilDate.MS_PER_DAY + 8 * hour
        val s = r.step(sat, 0, 1, off, "p", false)
        assertFalse(s.shabbat.verified)
        assertFalse(s.shabbat.active)
    }

    @Test
    fun theShabbatWarningComesOnceThirtyMinutesBeforeTheWindow() {
        val r = RuleRunner(RuleConfig(shabbat = shabbatCfg))
        val thu = (fri - 1) * CivilDate.MS_PER_DAY
        val first = r.step(thu + 10 * hour, 0, 1, off, null, false)
        assertTrue(first.warnings.isEmpty())
        val start = first.shabbat.nextStartMs!!
        val warnings = ArrayList<RuleWarning>()
        val base = thu + 10 * hour
        var t = start - 40 * min
        while (t < start) {
            warnings += r.step(t, t - base, 1, off, null, false).warnings
            t += 60_000
        }
        assertEquals(1, warnings.size)
        assertEquals(RuleWarning.Kind.SHABBAT_SOON, warnings[0].kind)
        assertTrue(warnings[0].remainingMs <= 30 * min)
    }
}
