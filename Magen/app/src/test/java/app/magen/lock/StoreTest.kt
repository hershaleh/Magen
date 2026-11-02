package app.magen.lock

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Store against a real (Robolectric) SharedPreferences and android.util.Base64: PIN and recovery checks with
 * missing or damaged data, failed-attempt counting, lockout, protective time windows from older versions,
 * and custom keywords. No device is involved, but this is Robolectric, not real Android.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreTest {
    private lateinit var ctx: Context

    private fun prefs() = ctx.getSharedPreferences("magen", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        prefs().edit().clear().commit()
        Session.authed = false
        Session.tripUntil = 0L
        Session.tripPending = false
        Session.uninstallPending = false
        Session.uninstallLeft = false
        Store.init(ctx)
    }

    private fun fails() = prefs().getInt("fails", 0)

    // ---- PIN ------------------------------------------------------------------------------------

    @Test
    fun checkPinWithNoStoredPinFailsSafelyAndCountsAsAWrongAttempt() {
        assertFalse(Store.hasPin)
        assertFalse(Store.checkPin("1234"))
        assertEquals(1, fails())
    }

    @Test
    fun correctPinSucceedsAndResetsTheFailureCount() {
        Store.setPin("4821")
        assertFalse(Store.checkPin("0000"))
        assertEquals(1, fails())
        assertTrue(Store.checkPin("4821"))
        assertEquals(0, fails())
    }

    @Test
    fun wrongPinIsCounted() {
        Store.setPin("4821")
        assertFalse(Store.checkPin("1111"))
        assertFalse(Store.checkPin("2222"))
        assertEquals(2, fails())
    }

    @Test
    fun emptyPinNeverSucceeds() {
        Store.setPin("4821")
        assertFalse(Store.checkPin(""))
        assertEquals(1, fails())
    }

    @Test
    fun missingHashFailsInsteadOfCrashing() {
        Store.setPin("4821")
        prefs().edit().remove("hash").commit()
        assertFalse(Store.checkPin("4821"))
        assertEquals(1, fails())
    }

    @Test
    fun missingSaltFailsInsteadOfCrashing() {
        Store.setPin("4821")
        prefs().edit().remove("salt").commit()
        assertFalse(Store.checkPin("4821"))
    }

    @Test
    fun emptySaltFailsInsteadOfCrashing() {
        Store.setPin("4821")
        prefs().edit().putString("salt", "").commit()
        assertFalse(Store.checkPin("4821"))
    }

    @Test
    fun invalidBase64SaltFailsInsteadOfCrashing() {
        Store.setPin("4821")
        prefs().edit().putString("salt", "%%% not base64 %%%").commit()
        assertFalse(Store.checkPin("4821"))
        assertEquals(1, fails())
    }

    @Test
    fun garbageHashFailsInsteadOfCrashing() {
        Store.setPin("4821")
        prefs().edit().putString("hash", "!!").commit()
        assertFalse(Store.checkPin("4821"))
    }

    @Test
    fun damagedDataThatCountsAsFailuresEventuallyLocksTheKeypad() {
        Store.setPin("4821")
        prefs().edit().remove("hash").commit()
        repeat(5) { assertFalse(Store.checkPin("4821")) }
        assertTrue(Store.lockRemaining() > 0)
    }

    // ---- lockout --------------------------------------------------------------------------------

    @Test
    fun fiveWrongPinsLockTheKeypadAndEvenTheRightPinIsRefusedWhileLocked() {
        Store.setPin("4821")
        repeat(5) { assertFalse(Store.checkPin("0000")) }
        assertTrue("lock should be active", Store.lockRemaining() in 1..30)
        assertFalse(Store.checkPin("4821"))
    }

    @Test
    fun settingANewPinClearsTheLockout() {
        Store.setPin("4821")
        repeat(5) { Store.checkPin("0000") }
        assertTrue(Store.lockRemaining() > 0)
        Store.setPin("7777")
        assertEquals(0, Store.lockRemaining())
        assertTrue(Store.checkPin("7777"))
    }

    @Test
    fun lockoutFromAnOlderVersionIsReadAndCapped() {
        // An older version wrote only the wall-clock end. A far-future value must not lock for hours.
        val now = System.currentTimeMillis()
        prefs().edit().putLong("lockUntil", now + 10 * 3_600_000L).remove("lockDur").commit()
        Store.init(ctx)
        val left = Store.lockRemaining()
        assertTrue("capped to 15 minutes, was $left", left in 1..900)
    }

    @Test
    fun lockoutFromAnOlderVersionThatAlreadyEndedIsNotActive() {
        prefs().edit().putLong("lockUntil", System.currentTimeMillis() - 5_000L).remove("lockDur").commit()
        Store.init(ctx)
        assertEquals(0, Store.lockRemaining())
    }

    @Test
    fun lockoutKeepsTheOldWallClockKeyForOlderVersions() {
        Store.setPin("4821")
        repeat(5) { Store.checkPin("0000") }
        assertTrue(prefs().getLong("lockUntil", 0L) > System.currentTimeMillis())
        assertEquals(30_000L, prefs().getLong("lockDur", 0L))
    }

    // ---- recovery code --------------------------------------------------------------------------

    @Test
    fun recoveryCodeWorksInAnyCaseAndWithoutDashes() {
        val code = Store.newRecoveryCode()
        assertTrue(Store.checkRecovery(code))
        assertTrue(Store.checkRecovery(code.lowercase().replace("-", "")))
        assertFalse(Store.checkRecovery("AAAA-AAAA-AAAA"))
    }

    @Test
    fun recoveryWithMissingOrDamagedDataFailsAndIsCounted() {
        val code = Store.newRecoveryCode()
        prefs().edit().putString("rsalt", "%%% not base64 %%%").commit()
        assertFalse(Store.checkRecovery(code))
        assertEquals(1, fails())
        prefs().edit().remove("rsalt").commit()
        assertFalse(Store.checkRecovery(code))
        assertEquals(2, fails())
    }

    @Test
    fun recoveryWithoutACodeFailsWithoutCounting() {
        assertFalse(Store.hasRecovery)
        assertFalse(Store.checkRecovery("AAAA-AAAA-AAAA"))
    }

    // ---- uninstall approval window -------------------------------------------------------------

    @Test
    fun approvalWindowOpensAndClosesAndKeepsTheOldWallClockKey() {
        assertFalse(Store.uninstallAllowed())
        Store.allowUninstall(10)
        assertTrue(Store.uninstallAllowed())
        assertTrue(prefs().getLong("allowedUntil", 0L) > System.currentTimeMillis())
        assertEquals(600_000L, prefs().getLong("allowedDur", 0L))
        Store.allowUninstall(0)
        assertFalse(Store.uninstallAllowed())
    }

    @Test
    fun approvalWindowSurvivesReloadWithinTheSameBoot() {
        Store.allowUninstall(10)
        Store.init(ctx)
        assertTrue(Store.uninstallAllowed())
    }

    @Test
    fun approvalWindowFromAnOlderVersionStillOpenIsReadAsOpen() {
        prefs().edit().putLong("allowedUntil", System.currentTimeMillis() + 5 * 60_000L).remove("allowedDur").commit()
        Store.init(ctx)
        assertTrue(Store.uninstallAllowed())
    }

    @Test
    fun approvalWindowFromAnOlderVersionThatEndedIsClosedForGood() {
        prefs().edit().putLong("allowedUntil", System.currentTimeMillis() - 1_000L).remove("allowedDur").commit()
        Store.init(ctx)
        assertFalse(Store.uninstallAllowed())
        assertEquals(0L, prefs().getLong("allowedUntil", -1L))
        Store.init(ctx)
        assertFalse(Store.uninstallAllowed())
    }


    // ---- active-rule status and app-initiated trips ---------------------------------------------

    @Test
    fun noConfiguredRulesMeansThereIsNothingToEnforce() {
        assertFalse(hasAnyRule())
    }

    @Test
    fun eachConfiguredRuleMakesProtectionNonEmpty() {
        Store.setMode("example.app", LockMode.PIN)
        assertTrue(hasAnyRule())
        Store.setMode("example.app", null)
        assertFalse(hasAnyRule())

        Store.setBlockPlay(true)
        assertTrue(hasAnyRule())
        Store.setBlockPlay(false)
        assertFalse(hasAnyRule())

        Store.setBlockApk(true)
        assertTrue(hasAnyRule())
        Store.setBlockApk(false)
        assertFalse(hasAnyRule())

        Store.setBlockedSetting("install", true)
        assertTrue(hasAnyRule())
        Store.setBlockedSetting("install", false)
        assertFalse(hasAnyRule())

        Store.addKeyword("Settings > Internet")
        assertTrue(hasAnyRule())
        Store.removeKeyword("Internet")
        assertFalse(hasAnyRule())
    }

    @Test
    fun tripExceptionIsCreatedAndCanBeCancelledAfterLaunchFailure() {
        Session.trip()
        assertTrue(Session.tripUntil > android.os.SystemClock.elapsedRealtime())
        Session.cancelTrip()
        assertEquals(0L, Session.tripUntil)
        assertFalse(Session.tripPending)
    }

    @Test
    fun uninstallApprovalIsNotActiveAfterExplicitClosure() {
        Store.allowUninstall(5)
        assertTrue(Store.uninstallAllowed())
        Store.allowUninstall(0)
        assertFalse(Store.uninstallAllowed())
        Store.init(ctx)
        assertFalse(Store.uninstallAllowed())
    }

    // ---- custom keywords ------------------------------------------------------------------------

    @Test
    fun customKeywordKeepsTheLastPartOfAPathAndIsPersisted() {
        Store.addKeyword("Network & internet > Internet")
        assertTrue("Internet" in Store.customKeywords)
        assertTrue("Internet" in prefs().getStringSet("customKeywords", emptySet())!!)
        Store.removeKeyword("Internet")
        assertFalse("Internet" in Store.customKeywords)
    }

    @Test
    fun unusableCustomKeywordIsIgnored() {
        Store.addKeyword(" > ")
        assertTrue(Store.customKeywords.isEmpty())
    }
}
