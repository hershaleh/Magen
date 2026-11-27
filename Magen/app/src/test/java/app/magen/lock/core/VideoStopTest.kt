package app.magen.lock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStopTest {
    @Test
    fun offByDefaultAndScopesWork() {
        assertFalse(VideoRule().applies("com.x"))
        assertTrue(VideoRule(true).applies("com.x"))
        val only = VideoRule(true, VideoScope.ONLY_LISTED, setOf("com.a"))
        assertTrue(only.applies("com.a")); assertFalse(only.applies("com.b"))
        val except = VideoRule(true, VideoScope.ALL_EXCEPT_LISTED, setOf("com.a"))
        assertFalse(except.applies("com.a")); assertTrue(except.applies("com.b"))
        assertFalse(VideoRule(false, VideoScope.ONLY_LISTED, setOf("com.a")).applies("com.a"))
    }

    @Test
    fun detectsPlayerPartsAndIgnoresOrdinaryScreens() {
        assertTrue(VideoDetector.detect(VideoSignals(viewIds = listOf("com.app:id/exo_player_view"))))
        assertTrue(VideoDetector.detect(VideoSignals(classNames = listOf("android.widget.VideoView"))))
        assertTrue(VideoDetector.detect(VideoSignals(descriptions = listOf("Play video"))))
        assertTrue(VideoDetector.detect(VideoSignals(descriptions = listOf("הפעל סרטון"))))
        assertFalse(VideoDetector.detect(VideoSignals(viewIds = listOf("com.app:id/title", "com.app:id/list"), classNames = listOf("android.widget.TextView"), descriptions = listOf("Settings"))))
        assertFalse(VideoDetector.detect(VideoSignals()))
    }

    @Test
    fun customRuleBlocksThroughTheCentralEngine() {
        val cfg = RuleConfig().withVideo(VideoRule(true))
        val day = CivilDate.toEpochDay(2026, 11, 3)
        val ctx = EvalContext(day * 1440 + 720, CivilDate.dayOfWeek(day))
        val d = BlockEngine.decide(cfg.policyFor("com.x", false, false, true), ctx)
        assertEquals(BlockSource.CUSTOM_RULE, d.primary)
        assertFalse(BlockEngine.decide(cfg.policyFor("com.x", true, false, true), ctx).blocked)
        assertFalse(BlockEngine.decide(cfg.policyFor("com.x", false, false, false), ctx).blocked)
    }

    @Test
    fun videoRuleRoundTripsAndDefaultsStayOutOfTheText() {
        val rule = VideoRule(true, VideoScope.ONLY_LISTED, setOf("com.a", "com.b"))
        val back = RuleCodec.decode(RuleCodec.encode(RuleConfig().withVideo(rule)))
        assertNotNull(back); assertEquals(rule, back!!.video)
        assertFalse(RuleCodec.encode(RuleConfig()).contains("video="))
        assertNull(RuleCodec.decode("v=1\nvideo=1;scope=9;pk="))
        assertEquals(VideoRule(), RuleCodec.decode("v=1")!!.video)
    }
}
