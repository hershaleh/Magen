package app.magen.lock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class LockMode { PIN, MAIN }

/** All persisted settings. Fields are Compose state so the UI reacts to changes. */
object Store {
    lateinit var app: Context
        private set
    private lateinit var sp: SharedPreferences

    var hasPin by mutableStateOf(false)
        private set
    var locked by mutableStateOf<Map<String, LockMode>>(emptyMap())
        private set
    var mainUnlocked by mutableStateOf<Set<String>>(emptySet())
        private set
    var blockPlay by mutableStateOf(false)
        private set
    var blockApk by mutableStateOf(false)
        private set
    var blockedSettings by mutableStateOf<Set<String>>(emptySet())
        private set
    var protect by mutableStateOf(true)
        private set
    var relockOnScreenOff by mutableStateOf(true)
        private set
    var allowedUntil by mutableLongStateOf(0L)
        private set

    /** "regular" or "strong". Only records what the user chose; real status comes from [Policy]. */
    var level by mutableStateOf("regular")
        private set
    var customKeywords by mutableStateOf<Set<String>>(emptySet())
        private set

    fun init(ctx: Context) {
        app = ctx.applicationContext
        sp = app.getSharedPreferences("magen", Context.MODE_PRIVATE)
        hasPin = sp.contains("hash")
        locked = (sp.getStringSet("locked", emptySet()) ?: emptySet()).mapNotNull { s ->
            val i = s.lastIndexOf(':')
            if (i < 0) null else runCatching { s.substring(0, i) to LockMode.valueOf(s.substring(i + 1)) }.getOrNull()
        }.toMap()
        blockPlay = sp.getBoolean("blockPlay", false)
        blockApk = sp.getBoolean("blockApk", false)
        blockedSettings = sp.getStringSet("blockedSettings", emptySet()) ?: emptySet()
        protect = sp.getBoolean("protect", true)
        relockOnScreenOff = sp.getBoolean("relock", true)
        allowedUntil = sp.getLong("allowedUntil", 0L)
        level = sp.getString("level", "regular") ?: "regular"
        customKeywords = sp.getStringSet("customKeywords", emptySet()) ?: emptySet()
        Policy.apply()
    }

    @JvmName("applyLevel")
    fun setLevel(l: String) {
        level = l
        sp.edit().putString("level", l).apply()
    }

    fun addKeyword(raw: String) {
        // Accepts a path such as "Network & internet > Internet" and keeps the last part, the page itself.
        val k = raw.split(Regex("[>›»→<‹]")).map { it.trim() }.lastOrNull { it.isNotEmpty() }?.take(60) ?: return
        customKeywords = customKeywords + k
        sp.edit().putStringSet("customKeywords", customKeywords).apply()
    }

    fun removeKeyword(k: String) {
        customKeywords = customKeywords - k
        sp.edit().putStringSet("customKeywords", customKeywords).apply()
    }

    // ---- master code -------------------------------------------------------------------------

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", hash(pin, salt))
            .putInt("fails", 0)
            .putLong("lockUntil", 0L)
            .apply()
        hasPin = true
    }

    /** Seconds the keypad stays disabled after too many wrong attempts. */
    fun lockRemaining(): Int {
        val ms = sp.getLong("lockUntil", 0L) - System.currentTimeMillis()
        return if (ms > 0) ((ms + 999) / 1000).toInt() else 0
    }

    fun checkPin(pin: String): Boolean {
        if (lockRemaining() > 0) return false
        val salt = Base64.decode(sp.getString("salt", "") ?: "", Base64.NO_WRAP)
        val want = sp.getString("hash", "") ?: ""
        val ok = MessageDigest.isEqual(hash(pin, salt).toByteArray(), want.toByteArray())
        val e = sp.edit()
        if (ok) {
            e.putInt("fails", 0)
        } else {
            val f = sp.getInt("fails", 0) + 1
            e.putInt("fails", f)
            if (f >= 5) {
                val delay = minOf(30L * (1L shl minOf(f - 5, 5)), 900L)
                e.putLong("lockUntil", System.currentTimeMillis() + delay * 1000)
            }
        }
        e.apply()
        return ok
    }

    // ---- app locking -------------------------------------------------------------------------

    fun setMode(pkg: String, mode: LockMode?) {
        locked = if (mode == null) locked - pkg else locked + (pkg to mode)
        if (mode != LockMode.MAIN) mainUnlocked = mainUnlocked - pkg
        sp.edit().putStringSet("locked", locked.map { "${it.key}:${it.value.name}" }.toSet()).apply()
    }

    fun toggleMainUnlocked(pkg: String) {
        mainUnlocked = if (pkg in mainUnlocked) mainUnlocked - pkg else mainUnlocked + pkg
    }

    fun clearMainUnlocked() {
        mainUnlocked = emptySet()
    }

    // ---- rules -------------------------------------------------------------------------------

    @JvmName("applyBlockPlay")
    fun setBlockPlay(on: Boolean) {
        blockPlay = on
        sp.edit().putBoolean("blockPlay", on).apply()
    }

    @JvmName("applyBlockApk")
    fun setBlockApk(on: Boolean) {
        blockApk = on
        sp.edit().putBoolean("blockApk", on).apply()
        Policy.apply()
    }

    fun setBlockedSetting(id: String, on: Boolean) {
        blockedSettings = if (on) blockedSettings + id else blockedSettings - id
        sp.edit().putStringSet("blockedSettings", blockedSettings).apply()
        Policy.apply()
    }

    @JvmName("applyProtect")
    fun setProtect(on: Boolean) {
        protect = on
        sp.edit().putBoolean("protect", on).apply()
        Policy.apply()
    }

    fun setRelock(on: Boolean) {
        relockOnScreenOff = on
        sp.edit().putBoolean("relock", on).apply()
    }

    // ---- uninstall approval ------------------------------------------------------------------

    fun uninstallAllowed() = System.currentTimeMillis() < allowedUntil

    fun allowUninstall(minutes: Int) {
        allowedUntil = System.currentTimeMillis() + minutes * 60_000L
        sp.edit().putLong("allowedUntil", allowedUntil).apply()
        Policy.apply()
    }
}
