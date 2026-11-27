package app.magen.lock

import android.content.Context
import app.magen.lock.core.AppRule
import app.magen.lock.core.EventStatus
import app.magen.lock.core.GroupRule
import app.magen.lock.core.QuotaDef
import app.magen.lock.core.QuotaPlan
import app.magen.lock.core.RuleCodec
import app.magen.lock.core.TrustedClock
import app.magen.lock.core.UsageLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** RuleStore against real (Robolectric) SharedPreferences: save, reload, refusal while read-only, damaged data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuleStoreTest {
    private lateinit var ctx: Context

    private fun prefs() = ctx.getSharedPreferences("magen_rules", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        prefs().edit().clear().commit()
        RuleStore.readOnly = false
        RuleStore.init(ctx)
        RuleRuntime.reset()
    }

    private fun sampleRule() = AppRule(null, QuotaDef(QuotaPlan(25)), null)

    @Test
    fun aSavedRuleIsStillThereAfterTheStoreIsInitialisedAgain() {
        assertTrue(RuleStore.updateConfig { it.withApp("com.a", sampleRule()) })
        RuleStore.init(ctx)
        assertEquals(25, RuleStore.config.apps["com.a"]!!.quota!!.plan.uniformMin)
    }

    @Test
    fun theStoredTextIsWhatTheCodecWrites() {
        RuleStore.updateConfig { it.withApp("com.a", sampleRule()) }
        assertEquals(RuleCodec.encode(RuleStore.config), prefs().getString("config", null))
    }

    @Test
    fun changesAreRefusedWhileReadOnlyAndNothingIsWritten() {
        RuleStore.updateConfig { it.withApp("com.a", sampleRule()) }
        RuleStore.readOnly = true
        assertFalse(RuleStore.updateConfig { it.withApp("com.b", sampleRule()) })
        assertFalse(RuleStore.restoreConfig(RuleStore.config.withoutGroup("x")))
        assertFalse("com.b" in RuleStore.config.apps)
        RuleStore.init(ctx)
        assertFalse("com.b" in RuleStore.config.apps)
        RuleStore.readOnly = false
        assertTrue(RuleStore.updateConfig { it.withApp("com.b", sampleRule()) })
    }

    @Test
    fun groupsAndMembershipSurviveAReload() {
        RuleStore.updateConfig { it.withGroup("g", GroupRule("Games", QuotaDef(QuotaPlan(60)))).withApp("com.a", sampleRule()).withGroupMembership("com.a", "g") }
        RuleStore.init(ctx)
        assertEquals("g", RuleStore.config.apps["com.a"]!!.groupId)
        assertEquals("Games", RuleStore.config.groups["g"]!!.name)
    }

    @Test
    fun unreadableStoredRulesAreKeptForDiagnosisResetAndLogged() {
        prefs().edit().putString("config", "v=1\nthis is damaged").commit()
        RuleStore.init(ctx)
        assertTrue(RuleStore.config.apps.isEmpty())
        assertEquals("v=1\nthis is damaged", prefs().getString("config.unreadable", null))
        val e = RuleStore.events.single { it.type == "settings_corrupt" }
        assertEquals(EventStatus.ACTIVE, e.status)
    }

    @Test
    fun theSecurityLogIsSavedAndMergesRepeats() {
        RuleStore.record("clock_changed", "device", 1_000, "m", "t")
        RuleStore.record("clock_changed", "device", 2_000)
        RuleStore.init(ctx)
        val e = RuleStore.events.single()
        assertEquals(2, e.count)
        assertEquals(2_000L, e.lastAtMs)
    }

    @Test
    fun countersClockAndNoticesAreSavedAndRestored() {
        val clock = TrustedClock()
        clock.read(1_800_000_000_000L, 5_000L, 3)
        RuleStore.saveRuntime(UsageLedger(20_000, mapOf("com.a" to 7_000L), mapOf("g" to 9_000L)), clock, setOf("q:com.a"), 20_000, 1234L)
        val ledger = RuleStore.loadLedger()
        assertEquals(7_000L, ledger.personalUsed("com.a"))
        assertEquals(9_000L, ledger.groupUsed("g"))
        assertEquals(20_000L, ledger.day)
        assertEquals(clock.state, RuleStore.loadClock().state)
        assertEquals(setOf("q:com.a") to 20_000L, RuleStore.loadWarned())
        assertEquals(1234L, RuleStore.loadLatch())
    }

    @Test
    fun missingOrBrokenRuntimeStateStartsFresh() {
        assertEquals(Long.MIN_VALUE, RuleStore.loadLedger().day)
        assertEquals(-1L, RuleStore.loadClock().state[0])
        prefs().edit().putString("ledger", "garbage").putString("clock", "1,2").commit()
        assertEquals(Long.MIN_VALUE, RuleStore.loadLedger().day)
        assertEquals(-1L, RuleStore.loadClock().state[0])
        assertNotNull(RuleStore.loadWarned())
    }

    @Test
    fun theRuntimeStepOnAFreshInstallBlocksNothingAndSavesItsState() {
        val step = RuleRuntime.step(ctx, "com.some.app", false)
        assertFalse(step.decision.blocked)
        RuleRuntime.flush()
        assertTrue(prefs().getString("clock", "")!!.split(",").size == 4)
    }
}
