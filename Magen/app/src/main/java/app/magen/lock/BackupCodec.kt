package app.magen.lock

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    private const val FORMAT = "MagenBackup"
    private const val VERSION = 1
    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private val forbiddenKeys = setOf(
        "pin", "hash", "salt", "rhash", "rsalt", "recoveryCode",
        "deviceOwner", "keeperOwner", "accessibilityEnabled",
    )

    fun encode(components: JSONObject, passphrase: CharArray? = null): ByteArray {
        validateComponents(components)
        val payload = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("components", components)
        val password = passphrase?.takeIf { it.isNotEmpty() }
        if (password == null) return payload.toString().toByteArray(Charsets.UTF_8)

        if (password.size < 8) throw BackupException("Encryption password must contain at least 8 characters")
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(FORMAT.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", FORMAT)
            .put("encrypted", true)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", ITERATIONS)
            .put("salt", Base64.getEncoder().encodeToString(salt))
            .put("nonce", Base64.getEncoder().encodeToString(nonce))
            .put("data", Base64.getEncoder().encodeToString(encrypted))
            .toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray, passphrase: CharArray? = null): JSONObject {
        if (bytes.isEmpty() || bytes.size > 10 * 1024 * 1024) throw BackupException("Backup file is empty or too large")
        try {
            val outer = JSONObject(bytes.toString(Charsets.UTF_8))
            if (outer.optString("format") != FORMAT) throw BackupException("This is not a Magen backup")
            val document = if (outer.optBoolean("encrypted", false)) {
                val password = passphrase ?: throw BackupException("This backup is encrypted; enter its password")
                if (outer.optString("kdf") != "PBKDF2WithHmacSHA256" ||
                    outer.optInt("iterations") != ITERATIONS
                ) throw BackupException("Unsupported encryption settings")
                val salt = Base64.getDecoder().decode(outer.getString("salt"))
                val nonce = Base64.getDecoder().decode(outer.getString("nonce"))
                val encrypted = Base64.getDecoder().decode(outer.getString("data"))
                if (salt.size != 16 || nonce.size != 12) throw BackupException("Invalid encryption metadata")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, nonce))
                cipher.updateAAD(FORMAT.toByteArray(Charsets.UTF_8))
                val plain = try {
                    cipher.doFinal(encrypted)
                } catch (badTag: AEADBadTagException) {
                    throw BackupException("Incorrect password or damaged backup", badTag)
                }
                JSONObject(plain.toString(Charsets.UTF_8))
            } else outer

            if (document.optString("format") != FORMAT) throw BackupException("Invalid backup payload")
            if (document.optInt("schemaVersion") != VERSION) throw BackupException("Unsupported backup version")
            val components = document.optJSONObject("components") ?: throw BackupException("Backup data is missing")
            validateComponents(components)
            return JSONObject(components.toString())
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("Backup file is damaged or invalid", e)
        }
    }

    private fun validateComponents(components: JSONObject) {
        fun inspect(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key.lowercase() in forbiddenKeys) {
                            throw BackupException("Backup contains a prohibited secret or system-owner field")
                        }
                        inspect(value.opt(key))
                    }
                }
                is JSONArray -> for (index in 0 until value.length()) inspect(value.opt(index))
            }
        }
        inspect(components)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
