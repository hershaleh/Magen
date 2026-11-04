package app.magen.lock.core

import app.magen.lock.core.BackupFormat.Decoded
import app.magen.lock.core.BackupFormat.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private val sample = mapOf(
        "protect" to BackupValue.Flag(true),
        "blockApk" to BackupValue.Flag(false),
        "level" to BackupValue.Text("strong"),
        "quota" to BackupValue.Number(45),
        "locked" to BackupValue.Items(setOf("com.a", "com.b")),
        "customKeywords" to BackupValue.Items(setOf("Network & internet", "a|b\tc")),
        "note" to BackupValue.Text("line1\nline2 \\ | end"),
        "empty" to BackupValue.Items(emptySet()),
    )

    private fun ok(text: String) = (BackupFormat.decode(text) as Decoded.Ok)
    private fun reason(text: String) = (BackupFormat.decode(text) as Decoded.Failed).reason

    @Test
    fun everyKindOfValueRoundTripsIncludingAwkwardText() {
        val d = ok(BackupFormat.encode(sample))
        assertEquals(sample, d.settings)
        assertEquals(BackupFormat.CURRENT_VERSION, d.version)
    }

    @Test
    fun emptyBackupRoundTrips() {
        assertTrue(ok(BackupFormat.encode(emptyMap())).settings.isEmpty())
    }

    @Test
    fun secretsAndTimeWindowsAreNeverWritten() {
        val withSecrets = sample + mapOf(
            "hash" to BackupValue.Text("h"), "salt" to BackupValue.Text("s"), "rhash" to BackupValue.Text("r"),
            "rsalt" to BackupValue.Text("r"), "fails" to BackupValue.Number(3), "lockUntil" to BackupValue.Number(1),
            "allowedUntil" to BackupValue.Number(1), "lockElapsed" to BackupValue.Number(1), "clockAnchor" to BackupValue.Number(1),
            "usageToday" to BackupValue.Number(1),
        )
        val text = BackupFormat.encode(withSecrets)
        assertEquals(sample, ok(text).settings)
        for (k in listOf("hash|", "salt|", "rhash|", "rsalt|", "fails|", "lockUntil|", "allowedUntil|", "clockAnchor|", "usageToday|")) {
            assertFalse("$k leaked", text.lines().any { it.startsWith(k) })
        }
    }

    @Test
    fun aFileThatContainsASecretKeyIsRefusedEvenWithAValidChecksum() {
        val body = "hash|T|abc"
        val sum = java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(Reason.CONTAINS_SECRETS, reason("MAGEN-BACKUP\nversion=1\nsha256=$sum\n$body"))
    }

    @Test
    fun notABackupAndDamagedFilesAreRejected() {
        assertEquals(Reason.NOT_A_BACKUP, reason(""))
        assertEquals(Reason.NOT_A_BACKUP, reason("hello\nworld\nfoo"))
        assertEquals(Reason.DAMAGED, reason("MAGEN-BACKUP\nversion=x\nsha256=00\n"))
        assertEquals(Reason.DAMAGED, reason("MAGEN-BACKUP\nversion=1\nnotasum\n"))
    }

    @Test
    fun anyChangeToTheContentBreaksTheChecksum() {
        val text = BackupFormat.encode(sample)
        assertEquals(Reason.CHECKSUM_MISMATCH, reason(text.replace("|T|strong", "|T|weak")))
        assertEquals(Reason.CHECKSUM_MISMATCH, reason(text + "\nextra|F|1"))
        assertEquals(Reason.CHECKSUM_MISMATCH, reason(text.substring(0, text.length - 5)))
    }

    @Test
    fun aNewerVersionThanThisAppUnderstandsIsRefused() {
        val newer = BackupFormat.encode(sample, version = BackupFormat.CURRENT_VERSION + 1)
        assertEquals(Reason.UNSUPPORTED_VERSION, reason(newer))
        assertEquals(Reason.UNSUPPORTED_VERSION, reason(BackupFormat.encode(sample, version = 0)))
    }

    @Test
    fun malformedLinesAreDamagedNotACrash() {
        fun withBody(body: String): String {
            val sum = java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
            return "MAGEN-BACKUP\nversion=1\nsha256=$sum\n$body"
        }
        assertEquals(Reason.DAMAGED, reason(withBody("onlytwo|F")))
        assertEquals(Reason.DAMAGED, reason(withBody("k|F|2")))
        assertEquals(Reason.DAMAGED, reason(withBody("k|N|notanumber")))
        assertEquals(Reason.DAMAGED, reason(withBody("k|X|1")))
        assertEquals(Reason.DAMAGED, reason(withBody("k|T|bad\\q")))
        assertEquals(Reason.DAMAGED, reason(withBody("k|F|1\nk|F|0")))
    }

    // ---- restoring -----------------------------------------------------------------------------

    @Test
    fun diffListsOnlyWhatWouldChange() {
        val current = mapOf("protect" to BackupValue.Flag(true), "level" to BackupValue.Text("strong"), "gone" to BackupValue.Flag(true))
        val backup = mapOf("protect" to BackupValue.Flag(true), "level" to BackupValue.Text("regular"), "added" to BackupValue.Number(1))
        val keys = BackupFormat.diff(current, backup).map { it.key }
        assertEquals(listOf("added", "gone", "level"), keys)
    }

    @Test
    fun restoringAnOlderBackupThatTurnsProtectionOffIsFlagged() {
        val current = mapOf(
            "protect" to BackupValue.Flag(true), "blockApk" to BackupValue.Flag(true),
            "locked" to BackupValue.Items(setOf("a", "b", "c")), "blockedSettings" to BackupValue.Items(setOf("x")),
        )
        val old = mapOf(
            "protect" to BackupValue.Flag(false), "blockApk" to BackupValue.Flag(true),
            "locked" to BackupValue.Items(setOf("a")), "blockedSettings" to BackupValue.Items(setOf("x", "y")),
        )
        val weak = BackupFormat.weakening(current, old).map { it.key }
        assertEquals(listOf("locked", "protect"), weak)
    }

    @Test
    fun aBackupThatOnlyAddsProtectionIsNotFlagged() {
        val current = mapOf("protect" to BackupValue.Flag(false), "locked" to BackupValue.Items(setOf("a")))
        val better = mapOf("protect" to BackupValue.Flag(true), "locked" to BackupValue.Items(setOf("a", "b")))
        assertTrue(BackupFormat.weakening(current, better).isEmpty())
    }

    @Test
    fun aMissingProtectiveKeyInTheBackupCountsAsWeakerThanASetOne() {
        val current = mapOf("blockPlay" to BackupValue.Flag(true), "customKeywords" to BackupValue.Items(setOf("k")))
        assertEquals(listOf("blockPlay", "customKeywords"), BackupFormat.weakening(current, emptyMap()).map { it.key })
    }

    @Test
    fun nonProtectiveDifferencesAreNotWeakening() {
        val current = mapOf("level" to BackupValue.Text("strong"), "theme" to BackupValue.Text("dark"))
        val old = mapOf("level" to BackupValue.Text("regular"), "theme" to BackupValue.Text("light"))
        assertTrue(BackupFormat.weakening(current, old).isEmpty())
        assertEquals(2, BackupFormat.diff(current, old).size)
    }
}
