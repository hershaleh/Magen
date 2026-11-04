package app.magen.lock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class LockProfile(
    val id: String,
    val name: String,
    val apps: Map<String, LockMode>,
)

object ProfileStore {
    private const val PREFS = "magen_profiles"
    private const val KEY_DATA = "profiles"
    private const val KEY_ACTIVE = "active"
    private const val KEY_WINDOWS = "windows"

    private lateinit var prefs: android.content.SharedPreferences
    var profiles by mutableStateOf<List<LockProfile>>(emptyList())
        private set
    var activeProfileId by mutableStateOf("")
        private set
    var windows by mutableStateOf<List<WeeklyProfileWindow>>(emptyList())
        private set
    var activeProfileName by mutableStateOf("")
    private var lastResolvedId: String? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        profiles = decodeProfiles(prefs.getString(KEY_DATA, null))
        if (profiles.isEmpty()) {
            val initial = LockProfile("default", "Default", Store.locked)
            profiles = listOf(initial)
            persistProfiles()
        }
        activeProfileId = prefs.getString(KEY_ACTIVE, null)
            ?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
        activeProfileName = profiles.firstOrNull { it.id == activeProfileId }?.name.orEmpty()
        windows = decodeWindows(prefs.getString(KEY_WINDOWS, null))
        refresh()
    }

    fun current(): LockProfile? = profiles.firstOrNull { it.id == activeProfileId }

    fun addProfile(name: String): Boolean {
        val clean = name.trim().take(32)
        if (clean.isBlank() || profiles.any { it.name.equals(clean, true) } || profiles.size >= 20) return false
        val item = LockProfile("p" + java.util.UUID.randomUUID().toString(), clean, current()?.apps ?: Store.locked)
        profiles = profiles + item
        persistProfiles()
        select(item.id)
        return true
    }

    fun renameProfile(id: String, name: String): Boolean {
        val clean = name.trim().take(32)
        if (clean.isBlank() || profiles.any { it.id != id && it.name.equals(clean, true) }) return false
        profiles = profiles.map { if (it.id == id) it.copy(name = clean) else it }
        persistProfiles()
        refresh()
        return true
    }

    fun deleteProfile(id: String): Boolean {
        if (profiles.size <= 1 || profiles.none { it.id == id }) return false
        profiles = profiles.filterNot { it.id == id }
        if (activeProfileId == id) select(profiles.first().id)
        persistProfiles()
        refresh()
        return true
    }

    fun select(id: String) {
        if (profiles.none { it.id == id }) return
        Store.clearMainUnlocked()
        activeProfileId = id
        prefs.edit().putString(KEY_ACTIVE, id).apply()
        refresh()
    }

    fun setAppMode(packageName: String, mode: LockMode?) {
        val profile = current() ?: return
        val apps = if (mode == null) profile.apps - packageName else profile.apps + (packageName to mode)
        profiles = profiles.map { if (it.id == profile.id) it.copy(apps = apps) else it }
        persistProfiles()
    }

    fun activeLocks(): Map<String, LockMode> {
        val resolved = resolveActiveProfileId(System.currentTimeMillis())
        val id = resolved?.takeIf { candidate -> profiles.any { it.id == candidate } } ?: activeProfileId
        if (lastResolvedId != null && lastResolvedId != id) Store.clearMainUnlocked()
        lastResolvedId = id
        return profiles.firstOrNull { it.id == id }?.apps ?: Store.locked
    }

    fun saveWindows(items: List<WeeklyProfileWindow>) {
        windows = items.take(100)
        val array = JSONArray()
        windows.forEach { w ->
            array.put(JSONObject().put("id", w.id).put("day", w.dayOfWeek)
                .put("start", w.startMinute).put("end", w.endMinute)
                .put("profile", w.profileId).put("priority", w.priority))
        }
        prefs.edit().putString(KEY_WINDOWS, array.toString()).apply()
        refresh()
    }

    fun resolveActiveProfileId(nowMillis: Long): String? {
        val schedule = ProfileSchedule(weekly = windows)
        return ScheduleEngine.resolve(schedule, activeProfileId, nowMillis, TimeZone.getDefault()).profileId
    }

    fun refresh() {
        activeProfileName = profiles.firstOrNull {
            it.id == (resolveActiveProfileId(System.currentTimeMillis()) ?: activeProfileId)
        }?.name.orEmpty()
    }

    private fun persistProfiles() {
        val array = JSONArray()
        profiles.forEach { p ->
            val apps = JSONObject()
            p.apps.forEach { (pkg, mode) -> apps.put(pkg, mode.name) }
            array.put(JSONObject().put("id", p.id).put("name", p.name).put("apps", apps))
        }
        prefs.edit().putString(KEY_DATA, array.toString()).apply()
    }

    private fun decodeProfiles(raw: String?): List<LockProfile> = runCatching {
        val a = JSONArray(raw ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val appsJson = o.optJSONObject("apps") ?: JSONObject()
            val apps = buildMap {
                val keys = appsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    runCatching { put(key, LockMode.valueOf(appsJson.getString(key))) }
                }
            }
            LockProfile(o.getString("id"), o.getString("name"), apps)
        }
    }.getOrDefault(emptyList())

    private fun decodeWindows(raw: String?): List<WeeklyProfileWindow> = runCatching {
        val a = JSONArray(raw ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            WeeklyProfileWindow(o.getString("id"), o.getInt("day"), o.getInt("start"),
                o.getInt("end"), o.getString("profile"), o.optInt("priority", 0))
        }
    }.getOrDefault(emptyList())
}
