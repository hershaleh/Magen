package app.magen.lock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
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
    var biometric by mutableStateOf(false)
        private set
    var hasRecovery by mutableStateOf(false)
        private set
    var appSchedules by mutableStateOf<Map<String, AppSchedule>>(emptyMap())
        private set
    var usageLimits by mutableStateOf<Map<String, Int>>(emptyMap())
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
        // The uninstall approval window exists only while the system uninstall screen is in the
        // foreground. It must never survive a process restart: otherwise a force-stop/process death
        // while that screen is open could leave protection paused after the user cancels.
        allowedUntil = 0L
        sp.edit().remove("allowedUntil").apply()
        level = sp.getString("level", "regular") ?: "regular"
        customKeywords = sp.getStringSet("customKeywords", emptySet()) ?: emptySet()
        biometric = sp.getBoolean("bio", false)
        hasRecovery = sp.contains("rhash")
        appSchedules = decodeSchedules(sp.getString("appSchedules", null))
        usageLimits = decodeUsageLimits(sp.getString("usageLimits", null))
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
        val ok = registerAttempt(MessageDigest.isEqual(hash(pin, salt).toByteArray(), want.toByteArray()))
        if (!ok) ActivityLog.record("PIN_FAILED")
        return ok
    }

    /** Counts a wrong attempt (PIN or recovery code) and starts the lockout delay after five misses. */
    private fun registerAttempt(ok: Boolean): Boolean {
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
        val salt = Base64.decode(sp.getString("rsalt", "") ?: "", Base64.NO_WRAP)
        val want = sp.getString("rhash", "") ?: ""
        return registerAttempt(MessageDigest.isEqual(hash(raw, salt).toByteArray(), want.toByteArray()))
    }

    @JvmName("applyBiometric")
    fun setBiometric(on: Boolean) {
        biometric = on
        sp.edit().putBoolean("bio", on).apply()
    }

    // ---- app locking -------------------------------------------------------------------------

    fun setMode(pkg: String, mode: LockMode?) {
        if (pkg == app.packageName || locked[pkg] == mode) return
        locked = if (mode == null) locked - pkg else locked + (pkg to mode)
        ActivityLog.record(if (mode == null) "APP_UNBLOCKED" else "APP_BLOCK_METHOD_SET", pkg)
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

    // ---- per-app schedules -------------------------------------------------------------------

    fun scheduleFor(packageName: String): AppSchedule =
        if (packageName == app.packageName) AppSchedule() else appSchedules[packageName] ?: AppSchedule()

    fun scheduleDecision(packageName: String, nowMillis: Long = System.currentTimeMillis()): ScheduleDecision {
        if (packageName == app.packageName) return ScheduleDecision(false, ScheduleDecision.Reason.BASE_SETTING)
        return ScheduleEngine.evaluate(
            scheduleFor(packageName), packageName, packageName in locked, nowMillis,
        )
    }

    fun setSchedule(packageName: String, schedule: AppSchedule) {
        if (packageName == app.packageName || schedule == scheduleFor(packageName)) return
        appSchedules = if (schedule.weekly.isEmpty() && schedule.dateOverrides.isEmpty()) {
            appSchedules - packageName
        } else {
            appSchedules + (packageName to schedule)
        }
        val root = JSONArray()
        appSchedules.forEach { (pkg, value) ->
            val weekly = JSONArray()
            value.weekly.forEach { rule ->
                weekly.put(JSONObject()
                    .put("id", rule.id).put("day", rule.dayOfWeek)
                    .put("start", rule.startMinute).put("end", rule.endMinute)
                    .put("action", rule.action.name).put("priority", rule.priority))
            }
            val dates = JSONArray()
            value.dateOverrides.forEach { rule ->
                dates.put(JSONObject()
                    .put("id", rule.id).put("date", rule.epochDay)
                    .put("start", rule.startMinute).put("end", rule.endMinute)
                    .put("action", rule.action.name).put("priority", rule.priority))
            }
            root.put(JSONObject().put("package", pkg).put("weekly", weekly).put("dates", dates))
        }
        sp.edit().putString("appSchedules", root.toString()).apply()
        ActivityLog.record("APP_SCHEDULE_CHANGED", packageName)
    }

    fun usageLimitFor(packageName: String): Int? =
        if (packageName == app.packageName) null else usageLimits[packageName]

    fun setUsageLimit(packageName: String, minutes: Int?) {
        if (packageName == app.packageName || usageLimits[packageName] == minutes) return
        usageLimits = if (minutes == null || minutes <= 0) usageLimits - packageName
        else usageLimits + (packageName to minutes.coerceAtMost(1440))
        val json = JSONObject()
        usageLimits.forEach { (pkg, limit) -> json.put(pkg, limit) }
        sp.edit().putString("usageLimits", json.toString()).apply()
        ActivityLog.record("APP_USAGE_LIMIT_CHANGED", packageName)
    }

    fun isUsageLimitReached(packageName: String): Boolean {
        val limit = usageLimitFor(packageName) ?: return false
        // Fail closed: a configured quota is not silently bypassed if Android revokes usage access.
        if (!UsageTracker.hasAccess(app)) return true
        return UsageTracker.isLimitReached(limit, UsageTracker.todayUsageMillis(app, packageName))
    }

    private fun decodeUsageLimits(raw: String?): Map<String, Int> = runCatching {
        val json = JSONObject(raw ?: "{}")
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val minutes = json.optInt(pkg, 0)
                if (pkg != app.packageName && minutes in 1..1440) put(pkg, minutes)
            }
        }
    }.getOrDefault(emptyMap())

    private fun decodeSchedules(raw: String?): Map<String, AppSchedule> = runCatching {
        val root = JSONArray(raw ?: "[]")
        val result = mutableMapOf<String, AppSchedule>()
        for (i in 0 until root.length()) {
            val item = root.getJSONObject(i)
            val pkg = item.getString("package")
            if (pkg != app.packageName) {
                val weeklyJson = item.optJSONArray("weekly") ?: JSONArray()
                val weekly = (0 until weeklyJson.length()).mapNotNull { index ->
                    runCatching {
                        val w = weeklyJson.getJSONObject(index)
                        WeeklyAppWindow(
                            w.getString("id"), pkg, w.getInt("day"), w.getInt("start"), w.getInt("end"),
                            ScheduleAction.valueOf(w.getString("action")), w.optInt("priority", 0),
                        )
                    }.getOrNull()
                }
                val datesJson = item.optJSONArray("dates") ?: JSONArray()
                val dates = (0 until datesJson.length()).mapNotNull { index ->
                    runCatching {
                        val d = datesJson.getJSONObject(index)
                        DateAppOverride(
                            d.getString("id"), pkg, d.getLong("date"), d.getInt("start"), d.getInt("end"),
                            ScheduleAction.valueOf(d.getString("action")), d.optInt("priority", 0),
                        )
                    }.getOrNull()
                }
                result[pkg] = AppSchedule(weekly, dates)
            }
        }
        result
    }.getOrDefault(emptyMap())

    // ---- uninstall approval ------------------------------------------------------------------

    fun uninstallAllowed() = System.currentTimeMillis() < allowedUntil

    fun allowUninstall(minutes: Int) {
        ActivityLog.record("UNINSTALL_APPROVAL_WINDOW", "$minutes minutes")
        allowedUntil = System.currentTimeMillis() + minutes * 60_000L
        sp.edit().putLong("allowedUntil", allowedUntil).apply()
        Policy.apply()
    }
}
