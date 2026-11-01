package app.magen.lock.core

import app.magen.lock.core.InstallerScreen.Node
import app.magen.lock.core.InstallerScreen.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for [InstallerScreen]. Texts are normalised (lower-case) as Catalog.norm produces them. */
class InstallerScreenTest {
    private fun text(t: String) = Node(t, clickable = false)
    private fun button(t: String) = Node(t, clickable = true)

    // ---- realistic windows ----------------------------------------------------------------------

    private fun uninstallDialogModern(appName: String = "calculator") = listOf(
        text(appName), text("do you want to uninstall this app?"), button("cancel"), button("uninstall"),
    )

    private fun uninstallDialogClassic(appName: String = "calculator") = listOf(
        text(appName), text("do you want to uninstall this app?"), button("cancel"), button("ok"),
    )

    private fun installScreen(appName: String) = listOf(
        text(appName), text("do you want to install this app?"), button("cancel"), button("install"),
    )

    @Test
    fun modernUninstallDialogIsRecognised() {
        assertTrue(InstallerScreen.isUninstall(uninstallDialogModern()))
    }

    @Test
    fun classicUninstallDialogWithOkAndCancelIsRecognised() {
        assertTrue(InstallerScreen.isUninstall(uninstallDialogClassic()))
    }

    @Test
    fun hebrewUninstallDialogIsRecognised() {
        val nodes = listOf(text("מחשבון"), text("האם ברצונך להסיר את ההתקנה של האפליקציה הזו?"), button("ביטול"), button("אישור"))
        assertTrue(InstallerScreen.isUninstall(nodes))
    }

    @Test
    fun viewIdOfTheUninstallFlowCountsAsStructure() {
        val nodes = listOf(
            Node("", false, "com.android.packageinstaller:id/uninstall_activity_snippet"),
            text("do you want to uninstall this app?"),
        )
        assertEquals(setOf(Signal.STRUCTURE, Signal.MESSAGE), InstallerScreen.signals(nodes))
        assertTrue(InstallerScreen.isUninstall(nodes))
    }

    // ---- misleading app names -------------------------------------------------------------------

    @Test
    fun installScreensOfMisleadinglyNamedAppsAreNeverUninstall() {
        val names = listOf(
            "uninstall", "uninstaller pro", "super uninstall tool", "הסרטים שלי", "הסר", "הסרת התקנה",
            "do you want to uninstall this app?", "do you want to uninstall everything", "האם ברצונך להסיר", "uninstall app",
        )
        for (name in names) {
            assertFalse("install screen of '$name'", InstallerScreen.isUninstall(installScreen(name)))
        }
    }

    @Test
    fun anAppNameAloneProvidesAtMostOneSignal() {
        for (name in listOf("uninstall", "uninstall app", "do you want to uninstall this app?", "הסר")) {
            val nodes = listOf(text(name), text(name))
            assertTrue("name '$name'", InstallerScreen.signals(nodes).size <= 1)
            assertFalse(InstallerScreen.isUninstall(nodes))
        }
    }

    @Test
    fun aMisleadingNameInAClickableRowStillProvidesOnlyOneSignal() {
        val nodes = listOf(button("uninstall"), button("uninstall"))
        assertEquals(setOf(Signal.BUTTON), InstallerScreen.signals(nodes))
        assertFalse(InstallerScreen.isUninstall(nodes))
    }

    @Test
    fun installQuestionVetoesEverythingEvenWithEnoughOtherSignals() {
        val nodes = listOf(
            Node("", false, "x:id/uninstall_confirm"), text("do you want to uninstall this app?"),
            text("do you want to install this app?"), button("cancel"), button("ok"),
        )
        assertTrue(InstallerScreen.isInstallPrompt(nodes))
        assertFalse(InstallerScreen.isUninstall(nodes))
    }

    @Test
    fun updateQuestionIsAlsoAnInstallPrompt() {
        assertTrue(InstallerScreen.isInstallPrompt(listOf(text("do you want to update this app?"))))
        assertTrue(InstallerScreen.isInstallPrompt(listOf(text("האם ברצונך להתקין את האפליקציה הזו?"))))
    }

    @Test
    fun misleadingNameOnTheModernUninstallDialogDoesNotChangeTheOutcome() {
        assertTrue(InstallerScreen.isUninstall(uninstallDialogModern("uninstaller pro")))
        assertTrue(InstallerScreen.isUninstall(uninstallDialogModern("הסרטים שלי")))
    }

    // ---- not enough evidence --------------------------------------------------------------------

    @Test
    fun noNodesIsNotUninstall() {
        assertFalse(InstallerScreen.isUninstall(emptyList()))
    }

    @Test
    fun questionAloneIsNotEnough() {
        assertFalse(InstallerScreen.isUninstall(listOf(text("do you want to uninstall this app?"))))
    }

    @Test
    fun uninstallButtonAloneIsNotEnough() {
        assertFalse(InstallerScreen.isUninstall(listOf(button("uninstall"), button("cancel"))))
    }

    @Test
    fun okAndCancelAloneAreNotEnough() {
        assertFalse(InstallerScreen.isUninstall(listOf(button("ok"), button("cancel"))))
    }

    @Test
    fun okWithoutCancelDoesNotCountAsConfirm() {
        val nodes = listOf(text("do you want to uninstall this app?"), button("ok"))
        assertFalse(InstallerScreen.isUninstall(nodes))
    }

    @Test
    fun partialMatchesNeverCount() {
        assertNull(InstallerScreen.kindOf(text("uninstaller pro")))
        assertNull(InstallerScreen.kindOf(text("הסרטים שלי")))
        assertNull(InstallerScreen.kindOf(text("how to uninstall this app")))
        assertNull(InstallerScreen.kindOf(button("uninstall everything")))
        assertNull(InstallerScreen.kindOf(Node("uninstall", false, null).let { text("") }))
    }

    // ---- one node, one signal -------------------------------------------------------------------

    @Test
    fun eachNodeProvidesAtMostOneKind() {
        assertEquals(Signal.BUTTON, InstallerScreen.kindOf(button("uninstall")))
        assertEquals(Signal.TITLE, InstallerScreen.kindOf(text("uninstall")))
        assertEquals(Signal.MESSAGE, InstallerScreen.kindOf(text("do you want to uninstall this app?")))
        assertEquals(Signal.STRUCTURE, InstallerScreen.kindOf(Node("uninstall", true, "x:id/uninstall_confirm")))
    }

    @Test
    fun trailingPunctuationIsIgnored() {
        assertEquals(Signal.TITLE, InstallerScreen.kindOf(text("uninstall app?")))
    }

    // ---- independence of the signals ------------------------------------------------------------

    @Test
    fun appNameEqualToAnUninstallTextNeverCompletesAnInstallScreen() {
        // The name shows up as text and as content description, in a row that may itself be clickable.
        for (name in listOf("uninstall", "uninstall app", "הסר", "do you want to uninstall this app?")) {
            for (clickable in listOf(false, true)) {
                val label = Node(name, clickable)
                val nodes = listOf(label, label, button("cancel"), button("install"))
                assertFalse("name '$name' clickable=$clickable", InstallerScreen.isUninstall(nodes))
            }
        }
    }

    @Test
    fun appNameThatLooksLikeTheConfirmationSentenceIsOnlyOneSignalEvenWithoutAnInstallQuestion() {
        val nodes = listOf(button("do you want to uninstall this app?"), button("cancel"), button("install"))
        assertEquals(setOf(Signal.MESSAGE), InstallerScreen.signals(nodes))
        assertFalse(InstallerScreen.isUninstall(nodes))
    }

    @Test
    fun appNameThatLooksLikeAnOkButtonAddsNoSecondSignalToAnInstallScreen() {
        val nodes = listOf(button("ok"), button("cancel"), button("install"), text("do you want to install this app?"))
        assertFalse(InstallerScreen.isUninstall(nodes))
    }

    @Test
    fun oneNameNeverProvidesTwoDifferentKinds() {
        // Whatever a single text is, it maps to at most one kind, so two signals always need two different nodes.
        val names = listOf(
            "uninstall", "uninstall app", "do you want to uninstall this app?", "הסר", "ok", "cancel",
            "האם ברצונך להסיר", "uninstall everything", "",
        )
        for (name in names) for (clickable in listOf(false, true)) {
            val kinds = InstallerScreen.signals(listOf(Node(name, clickable))) - setOf(Signal.CONFIRM)
            assertTrue("'$name' clickable=$clickable gave $kinds", kinds.size <= 1)
        }
    }

    @Test
    fun aTextAndItsDuplicateAsContentDescriptionCountOnce() {
        val nodes = listOf(text("do you want to uninstall this app?"), text("do you want to uninstall this app?"))
        assertEquals(setOf(Signal.MESSAGE), InstallerScreen.signals(nodes))
    }

    // ---- view id containing "uninstall" in a window that is not the uninstall dialog ---------------

    @Test
    fun viewIdAloneIsNotEnough() {
        assertFalse(InstallerScreen.isUninstall(listOf(Node("", false, "pkg:id/uninstall_icon"))))
    }

    @Test
    fun viewIdWithOnlyAGenericOkCancelPairIsNotEnough() {
        val nodes = listOf(Node("", false, "pkg:id/uninstall_icon"), text("delete this file?"), button("ok"), button("cancel"))
        assertEquals(setOf(Signal.STRUCTURE, Signal.CONFIRM), InstallerScreen.signals(nodes))
        assertFalse(InstallerScreen.isUninstall(nodes))
    }

    @Test
    fun viewIdOnAnInstallScreenNeverAllowsIt() {
        val nodes = installScreen("calculator") + Node("", false, "pkg:id/uninstall_icon") + button("uninstall")
        assertTrue(InstallerScreen.isInstallPrompt(nodes))
        assertFalse(InstallerScreen.isUninstall(nodes))
    }

    @Test
    fun viewIdWithAnUninstallWordedTitleIsAccepted() {
        val nodes = listOf(Node("", false, "pkg:id/uninstall_icon"), text("uninstall app"))
        assertEquals(setOf(Signal.STRUCTURE, Signal.TITLE), InstallerScreen.signals(nodes))
        assertTrue(InstallerScreen.isUninstall(nodes))
    }

    // ---- several nodes with similar text --------------------------------------------------------

    @Test
    fun manySimilarTextsStillCountOncePerKind() {
        val nodes = listOf(
            text("do you want to uninstall a"), text("do you want to uninstall b"), text("do you want to uninstall c"),
            button("uninstall everything"), button("uninstall all"),
        )
        assertEquals(setOf(Signal.MESSAGE), InstallerScreen.signals(nodes))
        assertFalse(InstallerScreen.isUninstall(nodes))
    }

    @Test
    fun updateDialogWithEnoughUninstallLookingNodesIsStillBlocked() {
        val nodes = listOf(
            text("do you want to update this app?"), text("do you want to uninstall this app?"),
            button("cancel"), button("uninstall"),
        )
        assertFalse(InstallerScreen.isUninstall(nodes))
    }

    @Test
    fun installDialogInHebrewIsAlsoAVeto() {
        val nodes = listOf(text("האם ברצונך להתקין את האפליקציה הזו?"), text("האם ברצונך להסיר"), button("הסר"))
        assertFalse(InstallerScreen.isUninstall(nodes))
    }

    // ---- Magen's own approved uninstall --------------------------------------------------------

    private fun ownApproved(nodes: List<Node>, mentions: Boolean = true, approval: Boolean = true) =
        InstallerScreen.isApprovedMagenUninstall(nodes, mentions, approval)

    @Test
    fun ownUninstallDialogIsApprovedInEnglishWhenTheWindowIsOpen() {
        assertTrue(ownApproved(uninstallDialogModern("magen")))
        assertTrue(ownApproved(uninstallDialogClassic("magen")))
    }

    @Test
    fun ownUninstallDialogIsApprovedInHebrewWhenTheWindowIsOpen() {
        val nodes = listOf(text("מגן"), text("האם ברצונך להסיר את ההתקנה של האפליקציה הזו?"), button("ביטול"), button("אישור"))
        assertTrue(ownApproved(nodes))
    }

    @Test
    fun ownUninstallWithAnUnfamiliarButtonWordingIsApprovedButIsNotACommonUninstall() {
        val nodes = listOf(text("magen"), text("שאלה בניסוח לא מוכר"), button("בטל"), button("הסר התקנה"))
        assertTrue(InstallerScreen.signals(nodes).isEmpty())
        assertFalse("not a generic uninstall screen", InstallerScreen.isUninstall(nodes))
        assertTrue("but Magen's own, approved", ownApproved(nodes))
    }

    @Test
    fun noExceptionWhenTheApprovalWindowIsNotOpen() {
        assertFalse(ownApproved(uninstallDialogModern("magen"), approval = false))
    }

    @Test
    fun noExceptionForOtherAppsEvenWhileTheWindowIsOpen() {
        assertFalse(ownApproved(uninstallDialogModern("calculator"), mentions = false))
        assertFalse(ownApproved(listOf(text("calculator"), text("הסר"), button("הסר")), mentions = false))
    }

    @Test
    fun noExceptionForInstallScreensEvenOfAnAppNamedMagen() {
        assertFalse(ownApproved(installScreen("magen")))
        assertFalse(ownApproved(listOf(text("מגן"), text("האם ברצונך להתקין את האפליקציה הזו?"), button("ביטול"), button("התקן"))))
    }

    @Test
    fun noExceptionForUpdateScreens() {
        val nodes = listOf(text("magen"), text("do you want to update this app?"), button("cancel"), button("update"))
        assertFalse(ownApproved(nodes))
    }

    @Test
    fun anInstallActionAloneIsAVetoEvenWithoutAnInstallQuestion() {
        val nodes = listOf(text("magen"), text("uninstall"), button("install"))
        assertTrue(InstallerScreen.hasInstallAction(nodes))
        assertFalse(ownApproved(nodes))
        assertFalse(ownApproved(listOf(text("מגן"), text("הסר"), button("התקנה"))))
    }

    @Test
    fun aScreenThatNamesMagenWithoutAnyUninstallWordingGetsNoException() {
        assertFalse(ownApproved(listOf(text("magen"), button("cancel"), button("continue"))))
        assertFalse(ownApproved(listOf(text("magen"))))
        assertFalse(ownApproved(emptyList()))
    }

    @Test
    fun anInstallActionLabelThatIsNotClickableIsNotAnInstallAction() {
        assertFalse(InstallerScreen.hasInstallAction(listOf(text("install"))))
        assertTrue(InstallerScreen.hasInstallAction(listOf(button("install"))))
    }
}
