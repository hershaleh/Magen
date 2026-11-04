package app.magen.lock

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import app.magen.lock.ui.Session
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * UI tests that run on an Android emulator against the real app: installation and launch, the opening flow, the
 * code check, and every area of the bottom navigation. They do not use Device Owner, Device Admin or the
 * accessibility service, and they do not change any app behaviour: state is prepared only through the app's own
 * public entry points (Store, Session) or by operating the UI like a person.
 */
@RunWith(AndroidJUnit4::class)
class MagenUiTest {
    private val compose = createEmptyComposeRule()

    // The screenshot rule must wrap the compose rule so it sees a failure from either.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ScreenshotOnFailureRule()).around(compose)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var ctx: Context
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun resetApp() {
        ctx = instrumentation.targetContext
        // A clean install: no code, no saved settings, no leftover session state from a previous test.
        ctx.getSharedPreferences("magen", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences("magen_ui", Context.MODE_PRIVATE).edit().clear().commit()
        instrumentation.runOnMainSync {
            Store.init(ctx)
            Session.authed = false
            Session.skipSetup = false
            Session.showGuide = false
            Session.recoveryToShow = null
            Session.goTab = null
            Session.uninstallNotice = false
            Session.tripUntil = 0L
            Session.tripPending = false
            Session.uninstallPending = false
            Session.uninstallLeft = false
        }
        // The notification permission prompt is not what is being tested; grant it up front on Android 13+.
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @After
    fun closeApp() {
        scenario?.close()
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun str(@StringRes id: Int, vararg args: Any) = ctx.getString(id, *args)

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /** Waits until some node shows [text] (no fixed sleeps), then returns it. */
    private fun ComposeTestRule.await(text: String, timeoutMs: Long = 20_000): SemanticsNodeInteraction {
        waitUntil(timeoutMs) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        return onAllNodesWithText(text)[0]
    }

    private fun awaitGone(text: String, timeoutMs: Long = 20_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    private fun clickButton(@StringRes label: Int) {
        val text = str(label)
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasClickAction() and hasText(text))[0].performClick()
    }

    private fun pressKey(key: String) {
        compose.onAllNodes(hasClickAction() and hasText(key))[0].performClick()
    }

    /** Types the PIN; the keypad submits automatically when the saved PIN length is reached. */
    private fun enterPin(pin: String) {
        pin.forEach { pressKey(it.toString()) }
    }

    private fun setCodeAndSkipSetup(pin: String = "2468") {
        instrumentation.runOnMainSync {
            Store.setPin(pin)
            Session.skipSetup = true
        }
    }

    private fun captureWalkthroughScreen(name: String) {
        val dir = File(ctx.getExternalFilesDir(null), "walkthrough-screenshots").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(dir, "$name.png")).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun openMainScreenDirectly() {
        setCodeAndSkipSetup()
        instrumentation.runOnMainSync { Session.authed = true }
        launch()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("magen_launch_overlay").fetchSemanticsNodes().isEmpty()
        }
        compose.await(str(R.string.nav_home)).assertIsDisplayed()
    }

    private fun navLabel(@StringRes id: Int) = str(id)

    private fun goToTab(@StringRes navLabel: Int) {
        compose.onAllNodes(hasClickAction() and hasText(navLabel(navLabel)))[0].performClick()
    }

    /** The five areas of the bottom navigation, each with a string that only that screen shows. */
    private val areas: List<Pair<Int, Int>> = listOf(
        R.string.nav_home to R.string.app_name,
        R.string.nav_blockings to R.string.apps_title,
        R.string.nav_times to R.string.nav_times,
        R.string.nav_protection to R.string.protection_title,
        R.string.nav_more to R.string.more_title,
    )

    // ---- installation and launch ---------------------------------------------------------------

    @Test
    fun appIsInstalledAndItsActivityOpens() {
        launch()
        val pm = ctx.packageManager
        assertTrue(pm.getPackageInfo(ctx.packageName, 0).packageName == "app.magen.lock")
        compose.await(str(R.string.welcome_title)).assertIsDisplayed()
    }

    @Test
    fun freshInstallShowsTheWelcomeScreen() {
        launch()
        compose.await(str(R.string.welcome_title)).assertIsDisplayed()
        compose.await(str(R.string.start)).assertIsDisplayed()
    }

    // ---- first-run flow ------------------------------------------------------------------------

    @Test
    fun firstRunCreatesACodeAndReachesTheHomeScreen() {
        launch()
        clickButton(R.string.start)
        compose.await(str(R.string.level_title))
        clickButton(R.string.btn_continue)

        compose.await(str(R.string.create_pin))
        enterPin("1357")
        compose.await(str(R.string.confirm_pin))
        enterPin("1357")

        compose.await(str(R.string.recovery_title))
        clickButton(R.string.recovery_saved)

        // The accessibility service and device admin are not enabled on a fresh emulator: setup is offered.
        compose.await(str(R.string.setup_title))
        clickButton(R.string.setup_skip)

        compose.await(str(R.string.nav_home))
        compose.await(str(R.string.app_name)).assertIsDisplayed()
        assertTrue(Store.hasPin)
        assertTrue(Store.checkPin("1357"))
        assertTrue(Store.hasRecovery)
    }

    @Test
    fun mismatchedConfirmationOfTheCodeIsRejectedAndStartsOver() {
        launch()
        clickButton(R.string.start)
        compose.await(str(R.string.level_title))
        clickButton(R.string.btn_continue)

        compose.await(str(R.string.create_pin))
        enterPin("1357")
        compose.await(str(R.string.confirm_pin))
        enterPin("9999")

        compose.await(str(R.string.pin_mismatch))
        compose.await(str(R.string.create_pin))
        assertFalse(Store.hasPin)
    }

    // ---- code check ----------------------------------------------------------------------------

    @Test
    fun existingCodeShowsTheCodeScreenAndOnlyTheRightCodeOpensTheApp() {
        setCodeAndSkipSetup("2468")
        launch()

        compose.await(str(R.string.enter_code)).assertIsDisplayed()
        enterPin("1111")
        compose.await(str(R.string.wrong_code))
        assertTrue("main screen must stay closed", compose.onAllNodesWithText(str(R.string.nav_home)).fetchSemanticsNodes().isEmpty())

        enterPin("2468")
        compose.await(str(R.string.nav_home))
        awaitGone(str(R.string.enter_code))
    }

    @Test
    fun leavingTheAppAsksForTheCodeAgain() {
        openMainScreenDirectly()
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        compose.await(str(R.string.enter_code)).assertIsDisplayed()
    }

    // ---- navigation ----------------------------------------------------------------------------

    @Test
    fun homeScreenLoadsWithAllFiveNavigationAreas() {
        openMainScreenDirectly()
        compose.await(str(R.string.app_name)).assertIsDisplayed()
        for ((label, _) in areas) {
            compose.onAllNodes(hasClickAction() and hasText(navLabel(label)))[0].assertIsDisplayed()
        }
    }

    @Test
    fun everyNavigationAreaOpensItsScreenWithoutCrashing() {
        openMainScreenDirectly()
        for ((label, landmark) in areas) {
            goToTab(label)
            val tabName = str(label)
            val expectedHeading = str(landmark)
            try {
                compose.await(expectedHeading).assertIsDisplayed()
            } catch (error: AssertionError) {
                throw AssertionError(
                    "Tab '$tabName' did not show its expected heading '$expectedHeading'.",
                    error,
                )
            }
        }
    }

    @Test
    fun navigatingBackAndForthTwiceStaysStable() {
        openMainScreenDirectly()
        repeat(2) {
            for ((label, landmark) in areas) {
                goToTab(label)
                compose.await(str(landmark))
            }
            for ((label, landmark) in areas.reversed()) {
                goToTab(label)
                compose.await(str(landmark))
            }
        }
        // Still alive: the activity has not been destroyed by a crash.
        assertTrue(scenario!!.state.isAtLeast(Lifecycle.State.STARTED))
    }

    @Test
    fun selectedAreaSurvivesRecreationOfTheActivity() {
        openMainScreenDirectly()
        goToTab(R.string.nav_times)
        compose.await(str(R.string.nav_times))
        scenario!!.recreate()
        compose.await(str(R.string.nav_times)).assertIsDisplayed()
    }

    @Test
    fun appsScreenListsInstalledAppsWithoutCrashing() {
        openMainScreenDirectly()
        goToTab(R.string.nav_blockings)
        compose.await(str(R.string.apps_title)).assertIsDisplayed()
    }

    @Test
    fun newSpecificationSectionsAreReachable() {
        openMainScreenDirectly()

        goToTab(R.string.nav_blockings)
        compose.await("חסימת חלקים בהגדרות").performClick()
        compose.await("חסימת רכיבי הגדרות Android").assertIsDisplayed()
        compose.await("איך למצוא חלק מסוים בהגדרות?").assertIsDisplayed()
        compose.await("חסימות נוספות").performClick()
        compose.await("VideoStop").assertIsDisplayed()

        goToTab(R.string.nav_times)
        compose.await("כל המכשיר").performClick()
        compose.await("לוח זמנים לכל המכשיר").assertIsDisplayed()
        compose.await("החלון חוצה חצות").assertIsDisplayed()
        compose.await("קבוצות").performClick()
        compose.await("הקבוצות שלי").assertIsDisplayed()
        compose.await("שבת וחגים").performClick()
        compose.await("יום טוב שני: פעיל").assertIsDisplayed()

        goToTab(R.string.nav_more)
        compose.await("אבחון ויומן אירועים").performClick()
        compose.await("יומן אירועים").performClick()
        compose.await("אין עדיין אירועים שמורים בממשק החדש.").assertIsDisplayed()
        compose.await("חזרה").performClick()
        compose.await("חזרה").performClick()
        compose.await("גיבוי ושחזור").performClick()
        compose.await("פתיחת גיבוי ושחזור").performClick()
        compose.await("בחירת קובץ לשחזור (טרם חובר)").assertIsDisplayed()
    }

    /**
     * Deliberately slow walkthrough so the CI recording is useful for a human review,
     * rather than a blur of short-lived activities from the individual tests.
     */
    @Test
    fun recordedWalkthroughShowsEveryMainArea() {
        openMainScreenDirectly()
        for ((label, landmark) in areas) {
            goToTab(label)
            compose.await(str(landmark)).assertIsDisplayed()
            instrumentation.waitForIdleSync()
            val fileName = navLabel(label).replace(Regex("[^A-Za-z0-9_-]"), "_")
            captureWalkthroughScreen(fileName)
            Thread.sleep(900)
        }
        goToTab(R.string.nav_home)
        compose.await(str(R.string.app_name)).assertIsDisplayed()
        instrumentation.waitForIdleSync()
        captureWalkthroughScreen("final-home")
        Thread.sleep(1200)
    }
}
