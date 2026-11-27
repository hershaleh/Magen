package app.magen.lock

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.magen.lock.core.Credential
import app.magen.lock.core.CustomKeyword
import app.magen.lock.core.Deadline
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
    /** Length is stored so the keypad can submit as soon as a complete PIN has been entered. */
    var pinLength by mutableIntStateOf(0)
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
    /** Master switch for user-configured blocking rules; self-protection and uninstall safety remain separate. */
    var blockingEnabled by mutableStateOf(true)
        private set
    var relockOnScreenOff by mutableStateOf(true)
        private set
    /** Wall-clock end of the uninstall approval window, for display only. Decisions use [uninstallDeadline]. */
    var allowedUntil by mutableLongStateOf(0L)
        private set
    @Volatile
    private var uninstallDeadline: Deadline = Deadline.NONE

    /** "regular" or "strong". Only records what the user chose; real status comes from [Policy]. */
    var level by mutableStateOf("regular")
        private set
    var customKeywords by mutableStateOf<Set<String>>(emptySet())
        private set
    var biometric by mutableStateOf(false)
        private set
    var hasRecovery by mutableStateOf(false)
        private set

    fun init(ctx: Context) {
        app = ctx.applicationContext
        sp = app.getSharedPreferences("magen", Context.MODE_PRIVATE)
        hasPin = sp.contains("hash")
        pinLength = sp.getInt("pinLength", 0).coerceIn(0, 8)
        locked = (sp.getStringSet("locked", emptySet()) ?: emptySet()).mapNotNull { s ->
            val i = s.lastIndexOf(':')
            if (i < 0) null else runCatching { s.substring(0, i) to LockMode.valueOf(s.substring(i + 1)) }.getOrNull()
        }.toMap()
        blockPlay = sp.getBoolean("blockPlay", false)
        blockApk = sp.getBoolean("blockApk", false)
        blockedSettings = sp.getStringSet("blockedSettings", emptySet()) ?: emptySet()
        protect = sp.getBoolean("protect", true)
        blockingEnabled = sp.getBoolean("blockingEnabled", true)
        relockOnScreenOff = sp.getBoolean("relock", true)
        allowedUntil = sp.getLong("allowedUntil", 0L)
        uninstallDeadline = loadDeadline("allowedUntil", "allowed", UNINSTALL_LEGACY_CAP_MS)
        level = sp.getString("level", "regular") ?: "regular"
        customKeywords = sp.getStringSet("customKeywords", emptySet()) ?: emptySet()
        biometric = sp.getBoolean("bio", false)
        hasRecovery = sp.contains("rhash")
        EventLog.init(app)
        Policy.apply()
    }

    @JvmName("applyLevel")
    fun setLevel(l: String) {
        val changed = level != l
        level = l
        sp.edit().putString("level", l).apply()
        if (changed) EventLog.record("protection.level", "רמת ההגנה שנבחרה עודכנה", "נבחר מצב: $l. מצב האכיפה בפועל נבדק בנפרד.", "protection_level", EventSeverity.LOW)
    }

    fun addKeyword(raw: String) {
        // Accepts a path such as "Network & internet > Internet" and keeps the last part, the page itself.
        val k = CustomKeyword.parse(raw) ?: return
        if (k in customKeywords) return
        customKeywords = customKeywords + k
        sp.edit().putStringSet("customKeywords", customKeywords).apply()
        EventLog.record("custom_rule.add", "נוסף כלל מותאם", "נוסף כלל מותאם אישית.", "custom_rule_add", EventSeverity.LOW)
    }

    fun removeKeyword(k: String) {
        if (k !in customKeywords) return
        customKeywords = customKeywords - k
        sp.edit().putStringSet("customKeywords", customKeywords).apply()
        EventLog.record("custom_rule.remove", "הוסר כלל מותאם", "הוסר כלל מותאם אישית.", "custom_rule_remove", EventSeverity.LOW)
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
            .putInt("pinLength", pin.length)
            .putInt("fails", 0)
            .apply()
        saveDeadline("lockUntil", "lock", Deadline.NONE)
        hasPin = true
        pinLength = pin.length
    }

    /** Seconds the keypad stays disabled after too many wrong attempts. */
    @Synchronized
    fun lockRemaining(): Int {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val d = loadDeadline("lockUntil", "lock", LOCK_LEGACY_CAP_MS)
        val ms = d.remainingMs(now, elapsed, bootCount)
        // Record progress (and close an ended lockout for good), so a reboot or a changed clock cannot restore it.
        val seen = d.observe(now, elapsed, bootCount)
        if (seen !== d) saveDeadline("lockUntil", "lock", seen)
        return if (ms > 0) ((ms + 999) / 1000).toInt() else 0
    }

    fun checkPin(pin: String): Boolean {
        if (lockRemaining() > 0) return false
        // Missing or damaged stored data is a failed attempt, never a crash and never a success.
        val ok = Credential.matches(pin, decode("salt"), sp.getString("hash", null)) { p, s -> hash(p, s) }
        if (ok && pinLength == 0) {
            // One-time migration for installations created before PIN length was persisted.
            pinLength = pin.length
            sp.edit().putInt("pinLength", pin.length).apply()
        }
        return registerAttempt(ok)
    }

    /** Counts a wrong attempt (PIN or recovery code) and starts the lockout delay after five misses. */
    private fun registerAttempt(ok: Boolean): Boolean {
        var lockDeadline: Deadline? = null
        val e = sp.edit()
        if (ok) {
            e.putInt("fails", 0)
        } else {
            val f = sp.getInt("fails", 0) + 1
            e.putInt("fails", f)
            if (f >= 5) {
                val delay = minOf(30L * (1L shl minOf(f - 5, 5)), 900L)
                lockDeadline = Deadline.start(delay * 1000, System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount)
            }
        }
        e.apply()
        lockDeadline?.let { saveDeadline("lockUntil", "lock", it) }
        return ok
    }

    // ---- recovery code -----------------------------------------------------------------------

    /** Creates a fresh one-time recovery code, stores only its hash and returns it for display (XXXX-XXXX-XXXX). */
    fun newRecoveryCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val raw = (1..12).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        sp.edit()
            .putString("rsalt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("rhash", hash(raw, salt))
            .apply()
        hasRecovery = true
        return raw.chunked(4).joinToString("-")
    }

    fun checkRecovery(input: String): Boolean {
        if (lockRemaining() > 0 || !hasRecovery) return false
        val raw = input.uppercase().filter { it.isLetterOrDigit() }
        val ok = Credential.matches(raw, decode("rsalt"), sp.getString("rhash", null)) { p, s -> hash(p, s) }
        return registerAttempt(ok)
    }

    @JvmName("applyBiometric")
    fun setBiometric(on: Boolean) {
        val changed = biometric != on
        biometric = on
        sp.edit().putBoolean("bio", on).apply()
        if (changed) EventLog.record("security.biometric", "העדפת זיהוי ביומטרי עודכנה", if (on) "העדפת זיהוי ביומטרי הופעלה." else "העדפת זיהוי ביומטרי כובתה.", "biometric_preference", EventSeverity.LOW)
    }

    // ---- app locking -------------------------------------------------------------------------

    fun setMode(pkg: String, mode: LockMode?) {
        val changed = locked[pkg] != mode
        locked = if (mode == null) locked - pkg else locked + (pkg to mode)
        if (mode != LockMode.MAIN) mainUnlocked = mainUnlocked - pkg
        sp.edit().putStringSet("locked", locked.map { "${it.key}:${it.value.name}" }.toSet()).apply()
        if (changed) EventLog.record("app.lock", "הגדרת חסימת אפליקציה עודכנה", if (mode == null) "הוסרה הגדרת חסימה מחבילת $pkg." else "עודכנה הגדרת חסימה לחבילת $pkg: $mode.", "app_lock:$pkg", EventSeverity.LOW)
    }

    fun toggleMainUnlocked(pkg: String) {
        mainUnlocked = if (pkg in mainUnlocked) mainUnlocked - pkg else mainUnlocked + pkg
    }

    fun clearMainUnlocked() {
        mainUnlocked = emptySet()
    }

    // ---- rules -------------------------------------------------------------------------------

    /** True when at least one user-configured protection rule exists. */
    fun hasAnyRule(): Boolean =
        locked.isNotEmpty() ||
            blockPlay ||
            blockApk ||
            blockedSettings.isNotEmpty() ||
            customKeywords.isNotEmpty()

    @JvmName("applyBlockPlay")
    fun setBlockPlay(on: Boolean) {
        val changed = blockPlay != on
        blockPlay = on
        sp.edit().putBoolean("blockPlay", on).apply()
        if (changed) EventLog.record("rule.play", "הגדרת חסימת Play עודכנה", if (on) "הופעלה העדפת חסימת Play." else "כובתה העדפת חסימת Play.", "block_play", EventSeverity.LOW)
    }

    @JvmName("applyBlockApk")
    fun setBlockApk(on: Boolean) {
        val changed = blockApk != on
        blockApk = on
        sp.edit().putBoolean("blockApk", on).apply()
        Policy.apply()
        if (changed) EventLog.record("rule.apk", "הגדרת חסימת APK עודכנה", if (on) "הופעלה העדפת חסימת התקנת APK." else "כובתה העדפת חסימת התקנת APK.", "block_apk", EventSeverity.LOW)
    }

    fun setBlockedSetting(id: String, on: Boolean) {
        val changed = (id in blockedSettings) != on
        blockedSettings = if (on) blockedSettings + id else blockedSettings - id
        sp.edit().putStringSet("blockedSettings", blockedSettings).apply()
        Policy.apply()
        if (changed) EventLog.record("setting.block.$id", "הגדרת חסימת מערכת עודכנה", if (on) "נשמרה העדפת חסימה עבור $id." else "הוסרה העדפת חסימה עבור $id.", "blocked_setting:$id", EventSeverity.LOW)
    }

    fun updateBlockingEnabled(on: Boolean) {
        val changed = blockingEnabled != on
        blockingEnabled = on
        sp.edit().putBoolean("blockingEnabled", on).apply()
        // Strong-mode restrictions are updated and verified by the existing policy layer.
        Policy.apply()
        if (changed) EventLog.record("blocking.master", "מתג החסימות עודכן", if (on) "הופעלה העדפת החסימות שהוגדרו." else "כובתה העדפת החסימות שהוגדרו.", "blocking_master", EventSeverity.LOW)
    }

    @JvmName("applyProtect")
    fun setProtect(on: Boolean) {
        val changed = protect != on
        protect = on
        sp.edit().putBoolean("protect", on).apply()
        Policy.apply()
        if (changed) EventLog.record("protection.self", "העדפת ההגנה העצמית עודכנה", if (on) "הופעלה העדפת ההגנה העצמית." else "כובתה העדפת ההגנה העצמית.", "self_protection_preference", EventSeverity.MEDIUM)
    }

    fun setRelock(on: Boolean) {
        val changed = relockOnScreenOff != on
        relockOnScreenOff = on
        sp.edit().putBoolean("relock", on).apply()
        if (changed) EventLog.record("security.relock", "העדפת נעילה מחדש עודכנה", if (on) "הופעלה נעילה מחדש בעת כיבוי המסך." else "כובתה נעילה מחדש בעת כיבוי המסך.", "relock_preference", EventSeverity.LOW)
    }

    // ---- uninstall approval ------------------------------------------------------------------

    @Synchronized
    fun uninstallAllowed(): Boolean {
        val d = uninstallDeadline
        // The approval window pauses protection, so after a reboot the wall clock may only shorten it.
        val seen = d.observe(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount, wallMayShorten = true)
        if (seen !== d) {
            // Progress recorded, or the window ended and is closed for good (see lockRemaining).
            uninstallDeadline = seen
            allowedUntil = seen.wallUntil
            saveDeadline("allowedUntil", "allowed", seen)
        }
        return seen.isSet
    }

    @Synchronized
    fun allowUninstall(minutes: Int) {
        val d = Deadline.start(minutes * 60_000L, System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount)
        uninstallDeadline = d
        allowedUntil = d.wallUntil
        saveDeadline("allowedUntil", "allowed", d)
        Policy.apply()
    }

    // ---- protective time windows ---------------------------------------------------------------

    private const val LOCK_LEGACY_CAP_MS = 15 * 60_000L
    private const val UNINSTALL_LEGACY_CAP_MS = 10 * 60_000L

    /**
     * Identifies the current boot, so a monotonic end time is only trusted within the boot that wrote it.
     * Read once: it cannot change while this process lives (a reboot ends the process). -1 when unknown.
     */
    private val bootCount: Int by lazy {
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching { Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
        } else {
            -1
        }
    }

    /**
     * The wall-clock key ([wallKey]) keeps the value older versions wrote and read; the extra keys
     * (<prefix>Elapsed, <prefix>Boot, <prefix>Dur, <prefix>Chk) are optional. Without them the window is read as legacy data.
     */
    private fun loadDeadline(wallKey: String, prefix: String, legacyCapMs: Long): Deadline {
        val wall = sp.getLong(wallKey, 0L)
        if (!sp.contains(prefix + "Dur")) return Deadline.legacy(wall, legacyCapMs)
        return Deadline(wall, sp.getLong(prefix + "Elapsed", -1L), sp.getInt(prefix + "Boot", -1), sp.getLong(prefix + "Dur", legacyCapMs), sp.getLong(prefix + "Chk", -1L))
    }

    private fun saveDeadline(wallKey: String, prefix: String, d: Deadline) {
        sp.edit()
            .putLong(wallKey, d.wallUntil)
            .putLong(prefix + "Elapsed", d.elapsedUntil)
            .putInt(prefix + "Boot", d.bootId)
            .putLong(prefix + "Dur", d.durationMs)
            .putLong(prefix + "Chk", d.checkpointElapsed)
            .apply()
    }

    /** Decodes a stored Base64 value; null when it is missing or not valid Base64. */
    private fun decode(key: String): ByteArray? {
        val s = sp.getString(key, null) ?: return null
        return try {
            Base64.decode(s, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
