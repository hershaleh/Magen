package app.magen.lock.core

import app.magen.lock.core.BlockSchedule.DateException
import app.magen.lock.core.BlockSchedule.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleCodecTest {
    private val sched = BlockSchedule(
        listOf(Window(5, 22 * 60, 6 * 60), Window(0, 0, 1440)),
        listOf(DateException(100_000, 100_900, true), DateException(200_000, 200_100, false)),
    )
    private val quota = QuotaDef(QuotaPlan(30, mapOf(5 to 10, 6 to 0)), true, BlockSchedule(listOf(Window(1, 720, 780))))

    private fun sample() = RuleConfig(
        apps = mapOf(
            "com.a" to AppRule(sched, quota, "games"),
            "com.b;weird,pkg|x=1:2" to AppRule(null, QuotaDef(QuotaPlan(null, mapOf(2 to 5))), null),
            "com.c" to AppRule(sched, null, "games"),
        ),
        groups = mapOf("games" to GroupRule("Games; & more, 100%", QuotaDef(QuotaPlan(90)))),
        shabbat = ShabbatConfig(
            true, JewishTimes.Settings(inIsrael = false, includeSecondDay = true, candleLightingMin = 40, bufferBeforeMin = 5,
                twilightDepressionDeg = 7.5, bufferAfterMin = 3), 31.778, 35.235, 45,
        ),
    )

    private fun assertSameSchedule(a: BlockSchedule?, b: BlockSchedule?) {
        assertEquals(RuleCodec.encodeSchedule(a ?: BlockSchedule()), RuleCodec.encodeSchedule(b ?: BlockSchedule()))
    }

    @Test
    fun wholeConfigurationRoundTrips() {
        val c = sample()
        val back = RuleCodec.decode(RuleCodec.encode(c))!!
        assertEquals(c.apps.keys, back.apps.keys)
        for ((k, r) in c.apps) {
            assertSameSchedule(r.schedule, back.apps.getValue(k).schedule)
            assertEquals(r.quota?.let { RuleCodec.encodeQuota(it) }, back.apps.getValue(k).quota?.let { RuleCodec.encodeQuota(it) })
            assertEquals(r.groupId, back.apps.getValue(k).groupId)
        }
        assertEquals("Games; & more, 100%", back.groups.getValue("games").name)
        assertEquals(90, back.groups.getValue("games").quota.plan.uniformMin)
        val s = back.shabbat
        assertTrue(s.enabled); assertFalse(s.settings.inIsrael); assertTrue(s.settings.includeSecondDay)
        assertEquals(40, s.settings.candleLightingMin); assertEquals(5, s.settings.bufferBeforeMin)
        assertEquals(3, s.settings.bufferAfterMin); assertEquals(7.5, s.settings.twilightDepressionDeg, 0.0)
        assertEquals(31.778, s.latitude!!, 0.0); assertEquals(35.235, s.longitude!!, 0.0); assertEquals(45, s.warnMin)
    }

    @Test
    fun encodingIsStableSoARoundTripIsAFixedPoint() {
        val once = RuleCodec.encode(sample())
        assertEquals(once, RuleCodec.encode(RuleCodec.decode(once)!!))
    }

    @Test
    fun emptyConfigurationAndEmptyTextBothDecodeToNothingConfigured() {
        val c = RuleCodec.decode(RuleCodec.encode(RuleConfig()))!!
        assertTrue(c.apps.isEmpty() && c.groups.isEmpty())
        assertFalse(c.shabbat.enabled)
        assertFalse(c.shabbat.hasPlace)
        assertTrue(RuleCodec.decode("")!!.apps.isEmpty())
    }

    @Test
    fun quotaPartsRoundTripOnTheirOwn() {
        val q = RuleCodec.decodeQuota(RuleCodec.encodeQuota(quota))!!
        assertEquals(30, q.plan.uniformMin)
        assertEquals(mapOf(5 to 10, 6 to 0), q.plan.perDayMin)
        assertTrue(q.paused)
        assertFalse(q.breaks.isEmpty)
        assertNull(RuleCodec.decodeQuota(RuleCodec.encodeQuota(QuotaDef(QuotaPlan(null)))).let { it?.plan?.uniformMin })
    }

    @Test
    fun damagedTextIsRejectedNotGuessed() {
        for (bad in listOf(
            "garbage", "v=2", "v=1\napp", "v=1\nunknown=1", "v=1\napp=a;q=u=x", "v=1\napp=a;s=9,1,2|",
            "v=1\napp=a;g=missing", "v=1\napp=a\napp=a", "v=1\ngroup=g;n=x", "v=1\nshabbat=2;il=1;two=0;candle=18;bb=0;ba=0;dep=85;warn=30",
            "v=1\nshabbat=1;il=1", "v=1\napp=a;q=u=-5",
        )) assertNull("accepted: $bad", RuleCodec.decode(bad))
    }

    @Test
    fun anAppWithNoRulesIsNotStored() {
        val c = RuleConfig().withApp("x", AppRule())
        assertTrue(c.apps.isEmpty())
        assertNotNull(RuleConfig().withApp("x", AppRule(quota = quota)).apps["x"])
    }

    @Test
    fun deletingAGroupKeepsMembersTheirOwnRulesAndDropsEmptyOnes() {
        val c = RuleConfig(
            apps = mapOf("a" to AppRule(null, quota, "g"), "b" to AppRule(null, null, "g")),
            groups = mapOf("g" to GroupRule("G", QuotaDef(QuotaPlan(10)))),
        ).withoutGroup("g")
        assertTrue(c.groups.isEmpty())
        assertNull(c.apps["a"]!!.groupId)
        assertNotNull(c.apps["a"]!!.quota)
        assertFalse("b had only the group", "b" in c.apps)
    }

    @Test
    fun movingAnAppBetweenGroupsKeepsItsOtherRulesAndIgnoresUnknownGroups() {
        var c = RuleConfig(
            apps = mapOf("a" to AppRule(sched, quota, "g1")),
            groups = mapOf("g1" to GroupRule("1", QuotaDef(QuotaPlan(10))), "g2" to GroupRule("2", QuotaDef(QuotaPlan(20)))),
        )
        c = c.withGroupMembership("a", "g2")
        assertEquals("g2", c.apps["a"]!!.groupId)
        assertNotNull(c.apps["a"]!!.schedule)
        c = c.withGroupMembership("a", "nope")
        assertNull(c.apps["a"]!!.groupId)
    }

    @Test
    fun policyForCombinesTheAppRuleWithTheFlagsGivenByTheCaller() {
        val c = sample()
        val p = c.policyFor("com.a", essential = false, permanent = true, customRuleActive = true)
        assertTrue(p.permanent && p.customRuleActive && !p.essential)
        assertEquals("games", p.groupId)
        val none = c.policyFor("unknown", false, false, false)
        assertNull(none.schedule); assertNull(none.personalQuota); assertNull(none.groupId)
    }
}
