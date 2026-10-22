package app.magen.keeper

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Keeper's small private store: its own code, and the apps (with signing certificate) that handed it ownership. */
object KeeperStore {
    private fun sp(c: Context) = c.getSharedPreferences("keeper", Context.MODE_PRIVATE)

    fun hasPin(c: Context) = sp(c).contains("hash")

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(c).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", hash(pin, salt))
            .putInt("fails", 0)
            .putLong("lockUntil", 0L)
            .apply()
    }

    fun lockRemaining(c: Context): Int {
        val ms = sp(c).getLong("lockUntil", 0L) - System.currentTimeMillis()
        return if (ms > 0) ((ms + 999) / 1000).toInt() else 0
    }

    fun checkPin(c: Context, pin: String): Boolean {
        if (lockRemaining(c) > 0) return false
        val p = sp(c)
        val salt = Base64.decode(p.getString("salt", "") ?: "", Base64.NO_WRAP)
        val want = p.getString("hash", "") ?: ""
        val ok = MessageDigest.isEqual(hash(pin, salt).toByteArray(), want.toByteArray())
        val e = p.edit()
        if (ok) {
            e.putInt("fails", 0)
        } else {
            val f = p.getInt("fails", 0) + 1
            e.putInt("fails", f)
            if (f >= 5) {
                val delay = minOf(30L * (1L shl minOf(f - 5, 5)), 900L)
                e.putLong("lockUntil", System.currentTimeMillis() + delay * 1000)
            }
        }
        e.apply()
        return ok
    }

    // ---- apps that handed ownership to Keeper ------------------------------------------------

    /** SHA-256 of the app's signing certificate(s), or null if it cannot be read. */
    fun certOf(c: Context, pkg: String): String? {
        return try {
            val info = c.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info.signingInfo?.apkContentsSigners ?: return null
            signers.map { s ->
                MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
                    .joinToString("") { b -> "%02x".format(b) }
            }.sorted().joinToString(",")
        } catch (e: Exception) {
            null
        }
    }

    private fun pins(c: Context): Set<String> = sp(c).getStringSet("pins", emptySet()) ?: emptySet()

    fun addPin(c: Context, pkg: String, cert: String) {
        sp(c).edit().putStringSet("pins", pins(c) + "$pkg|$cert").apply()
    }

    /** True when this exact app (same package AND same signature) once handed its ownership to Keeper. */
    fun isPinned(c: Context, pkg: String, cert: String) = "$pkg|$cert" in pins(c)

    fun pinnedPackages(c: Context): List<String> = pins(c).map { it.substringBefore('|') }.distinct().sorted()
}
