package app.magen.lock.core

import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for [Credential.matches]: stored data that is missing or damaged must never verify and never throw. */
class CredentialTest {
    private val salt = byteArrayOf(1, 2, 3, 4)
    private val fake: (String, ByteArray) -> String = { s, b -> "$s/${b.size}" }

    // The same PBKDF2 settings as Store.hash, with the JVM's Base64 (Store uses android.util.Base64).
    private val pbkdf2: (String, ByteArray) -> String = { s, b ->
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(s.toCharArray(), b, 1_000, 256)).encoded
        Base64.getEncoder().encodeToString(bytes)
    }

    @Test
    fun correctSecretMatches() {
        assertTrue(Credential.matches("1234", salt, "1234/4", fake))
    }

    @Test
    fun wrongSecretDoesNotMatch() {
        assertFalse(Credential.matches("9999", salt, "1234/4", fake))
    }

    @Test
    fun missingSaltNeverMatches() {
        assertFalse(Credential.matches("1234", null, "1234/4", fake))
    }

    @Test
    fun emptySaltNeverMatches() {
        assertFalse(Credential.matches("1234", ByteArray(0), "1234/0", fake))
    }

    @Test
    fun missingOrEmptyStoredHashNeverMatches() {
        assertFalse(Credential.matches("1234", salt, null, fake))
        assertFalse(Credential.matches("1234", salt, "", fake))
        // An empty result from the hasher must not match an empty/missing stored value either.
        assertFalse(Credential.matches("", salt, "", { _, _ -> "" }))
    }

    @Test
    fun hasherThatThrowsIsAFailedMatchNotACrash() {
        assertFalse(Credential.matches("1234", salt, "x", { _, _ -> throw IllegalArgumentException("bad") }))
        assertFalse(Credential.matches("1234", salt, "x", { _, _ -> throw IllegalStateException("bad") }))
    }

    @Test
    fun realPbkdf2RoundTripMatchesOnlyTheRightSecret() {
        val stored = pbkdf2("4821", salt)
        assertTrue(Credential.matches("4821", salt, stored, pbkdf2))
        assertFalse(Credential.matches("4822", salt, stored, pbkdf2))
        assertFalse(Credential.matches("4821", byteArrayOf(9, 9, 9, 9), stored, pbkdf2))
    }

    @Test
    fun realPbkdf2WithEmptySaltWouldThrowButIsRejectedFirst() {
        // This is the crash the guard prevents: PBEKeySpec refuses an empty salt.
        var threw = false
        try {
            pbkdf2("1234", ByteArray(0))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("PBEKeySpec should reject an empty salt on this JVM", threw)
        assertFalse(Credential.matches("1234", ByteArray(0), "anything", pbkdf2))
    }

    @Test
    fun differentLengthStoredHashDoesNotMatch() {
        assertFalse(Credential.matches("1234", salt, "1234/4-extra", fake))
    }

    @Test
    fun emptySecretNeverMatches() {
        assertFalse(Credential.matches("", salt, "/4", fake))
    }
}
