package app.magen.lock.core

import java.security.MessageDigest

/** Verification of a stored secret (PIN or recovery code). Pure logic with no Android dependency. */
object Credential {
    /**
     * True only when [secret] hashes to [expected] with [salt]. An empty secret, or missing, empty or unusable
     * stored data (null/empty salt or hash, a hasher that throws), is never a match and never throws.
     */
    fun matches(secret: String, salt: ByteArray?, expected: String?, hasher: (String, ByteArray) -> String): Boolean {
        if (secret.isEmpty()) return false
        if (salt == null || salt.isEmpty()) return false
        if (expected.isNullOrEmpty()) return false
        val actual = try {
            hasher(secret, salt)
        } catch (e: Exception) {
            return false
        }
        return MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())
    }
}
