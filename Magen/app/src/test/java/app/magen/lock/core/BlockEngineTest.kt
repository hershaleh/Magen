package app.magen.lock.core

import app.magen.lock.core.BlockSchedule.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEngineTest {
    private val min = 60_000L
    private val day = CivilDate.toEpochDay(2026, 11, 3)
    private val dow = CivilDate.dayOfWeek(day)
    private val noon = day * 1440 + 12 * 60

    private fun ctx(
        personal: Long = 0, group: Long = 0, groups: Map<String, QuotaDef> = emptyMap(), shabbat: Boolean = false,
        minute: Long = noon,
    ) = EvalContext(minute, dow, personal, group, groups, shabbat)

    private fun q(minutes: Int, paused: Boolean = false, breaks: BlockSchedule = BlockSchedule()) =
        QuotaDef(QuotaPlan(minutes), paused, breaks)

    @Test
    fun nothingConfiguredNeverBlocks() {
        val d = BlockEngine.decide(AppPolicy(), ctx())
        assertFalse(d.blocked)
        assertNull(d.primary)
    }

    @Test
    fun eachSourceBlocksOnItsOwnWithItsOwnReason() {
        assertEquals(listOf(BlockSource.PERMANENT), BlockEngine.decide(AppPolicy(permanent = true), ctx()).sources)
        assertEquals(listOf(BlockSource.CUSTOM_RULE), BlockEngine.decide(AppPolicy(customRuleActive = true), ctx()).sources)
        assertEquals(listOf(BlockSource.SHABBAT), BlockEngine.decide(AppPolicy(), ctx(shabbat = true)).sources)
        val sched = BlockSchedule(listOf(Window(dow, 11 * 60, 13 * 60)))
        assertEquals(listOf(BlockSource.SCHEDULE), BlockEngine.decide(AppPolicy(schedule = sched), ctx()).sources)
        assertEquals(
            listOf(BlockSource.PERSONAL_QUOTA),
            BlockEngine.decide(AppPolicy(personalQuota = q(10)), ctx(personal = 10 * min)).sources,
        )
        assertEquals(
            listOf(BlockSource.GROUP_QUOTA),
            BlockEngine.decide(AppPolicy(groupId = "g"), ctx(group = 10 * min, groups = mapOf("g" to q(10)))).sources,
        )
    }

    @Test
    fun anUnexhaustedQuotaAndAnInactiveScheduleDoNotBlock() {
        val sched = BlockSchedule(listOf(Window(dow, 18 * 60, 20 * 60)))
        val app = AppPolicy(schedule = sched, personalQuota = q(30), groupId = "g")
        assertFalse(BlockEngine.decide(app, ctx(personal = 5 * min, group = 5 * min, groups = mapOf("g" to q(30)))).blocked)
    }

    @Test
    fun severalSourcesAreAllReportedInPriorityOrder() {
        val app = AppPolicy(permanent = true, personalQuota = q(10), customRuleActive = true)
        val d = BlockEngine.decide(app, ctx(personal = 20 * min, shabbat = true))
        assertEquals(
            listOf(BlockSource.SHABBAT, BlockSource.PERMANENT, BlockSource.PERSONAL_QUOTA, BlockSource.CUSTOM_RULE),
            d.sources,
        )
        assertEquals(BlockSource.SHABBAT, d.primary)
    }

    @Test
    fun pausingTheQuotaDoesNotLiftAnotherSource() {
        val sched = BlockSchedule(listOf(Window(dow, 0, 1440)))
        val app = AppPolicy(schedule = sched, personalQuota = q(10, paused = true))
        val d = BlockEngine.decide(app, ctx(personal = 99 * min))
        assertEquals("only the schedule is left", listOf(BlockSource.SCHEDULE), d.sources)
        assertTrue(d.blocked)
    }

    @Test
    fun endingOneSourceLeavesTheAppBlockedWhileAnotherStillBlocks() {
        val app = AppPolicy(permanent = true, personalQuota = q(30))
        // The quota is not exhausted, but the permanent lock still blocks.
        assertTrue(BlockEngine.decide(app, ctx(personal = 1 * min)).blocked)
    }

    @Test
    fun shabbatBlocksEvenWhileAQuotaIsPausedOrInABreak() {
        val breaks = BlockSchedule(listOf(Window(dow, 0, 1440)))
        val app = AppPolicy(personalQuota = q(30, paused = true, breaks = breaks))
        assertTrue(BlockEngine.decide(app, ctx(shabbat = true)).blocked)
    }

    @Test
    fun bothQuotasAreChecked_eitherOneBlocks() {
        val app = AppPolicy(personalQuota = q(30), groupId = "g")
        val groups = mapOf("g" to q(10))
        assertEquals(listOf(BlockSource.GROUP_QUOTA), BlockEngine.decide(app, ctx(personal = 5 * min, group = 12 * min, groups = groups)).sources)
        assertEquals(listOf(BlockSource.PERSONAL_QUOTA), BlockEngine.decide(app, ctx(personal = 31 * min, group = 1 * min, groups = groups)).sources)
        assertEquals(2, BlockEngine.decide(app, ctx(personal = 31 * min, group = 12 * min, groups = groups)).sources.size)
    }

    @Test
    fun pausingTheGroupQuotaLeavesThePersonalQuotaInForce() {
        val app = AppPolicy(personalQuota = q(10), groupId = "g")
        val groups = mapOf("g" to q(10, paused = true))
        val d = BlockEngine.decide(app, ctx(personal = 20 * min, group = 20 * min, groups = groups))
        assertEquals(listOf(BlockSource.PERSONAL_QUOTA), d.sources)
    }

    @Test
    fun anUnknownGroupIdBlocksNothing() {
        assertFalse(BlockEngine.decide(AppPolicy(groupId = "missing"), ctx(group = 99 * min)).blocked)
    }

    @Test
    fun essentialAppsAreNeverBlockedByAnySource() {
        val app = AppPolicy(essential = true, permanent = true, customRuleActive = true, personalQuota = q(0))
        assertFalse(BlockEngine.decide(app, ctx(shabbat = true, personal = 99 * min)).blocked)
    }

    @Test
    fun scheduleThatCrossesMidnightIsEvaluatedAtTheRightMinute() {
        val sched = BlockSchedule(listOf(Window((dow + 6) % 7, 22 * 60, 6 * 60))) // starts the day before
        val app = AppPolicy(schedule = sched)
        assertTrue(BlockEngine.decide(app, ctx(minute = day * 1440 + 3 * 60)).blocked)
        assertFalse(BlockEngine.decide(app, ctx(minute = day * 1440 + 7 * 60)).blocked)
    }
}
