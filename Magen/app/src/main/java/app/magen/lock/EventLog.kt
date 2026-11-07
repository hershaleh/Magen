package app.magen.lock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class EventSeverity { CRITICAL, HIGH, MEDIUM, LOW }
enum class EventState { ACTIVE, IN_PROGRESS, RESOLVED, UNRESOLVED }

data class MagenEvent(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val technical: String,
    val severity: EventSeverity,
    val state: EventState,
    val createdAt: Long,
    val updatedAt: Long,
    val occurrences: Int,
    val read: Boolean,
    val history: List<String>,
)

/** Local, persistent event history. It never accepts or stores PINs, recovery codes, hashes or salts. */
object EventLog {
    private val lock = Any()
    private var app: Context? = null

    var events by mutableStateOf<List<MagenEvent>>(emptyList())
        private set

    fun init(context: Context) {
        synchronized(lock) {
            app = context.applicationContext
            events = load()
        }
    }

    fun record(
        type: String,
        title: String,
        message: String,
        technical: String = "",
        severity: EventSeverity = EventSeverity.LOW,
        state: EventState = EventState.RESOLVED,
    ) {
        synchronized(lock) {
            if (app == null) return
            val now = System.currentTimeMillis()
            val current = events
            val existingIndex = current.indexOfLast {
                it.type == type && it.technical == technical && it.state != EventState.RESOLVED
            }
            val next = if (existingIndex >= 0 && state != EventState.RESOLVED) {
                val old = current[existingIndex]
                val updated = old.copy(
                    message = message,
                    severity = moreSevere(old.severity, severity),
                    state = state,
                    updatedAt = now,
                    occurrences = old.occurrences + 1,
                    read = false,
                    history = (old.history + "${formatTime(now)} · אירוע חוזר (${old.occurrences + 1})").takeLast(50),
                )
                current.toMutableList().apply { this[existingIndex] = updated }
            } else {
                current + MagenEvent(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = title,
                    message = message,
                    technical = technical,
                    severity = severity,
                    state = state,
                    createdAt = now,
                    updatedAt = now,
                    occurrences = 1,
                    read = false,
                    history = listOf("${formatTime(now)} · נוצר"),
                )
            }
            events = next.sortedByDescending { it.updatedAt }
            persist()
        }
    }

    fun updateState(id: String, state: EventState, note: String) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            events = events.map { event ->
                if (event.id == id) event.copy(
                    state = state,
                    updatedAt = now,
                    read = false,
                    history = (event.history + "${formatTime(now)} · $note").takeLast(50),
                ) else event
            }
            persist()
        }
    }

    fun resolve(type: String, technical: String, note: String) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            var changed = false
            events = events.map { event ->
                if (event.type == type && event.technical == technical && event.state != EventState.RESOLVED) {
                    changed = true
                    event.copy(
                        state = EventState.RESOLVED,
                        updatedAt = now,
                        read = false,
                        history = (event.history + "${formatTime(now)} · $note").takeLast(50),
                    )
                } else event
            }.sortedByDescending { it.updatedAt }
            if (changed) persist()
        }
    }

    fun markRead(id: String, read: Boolean = true) {
        synchronized(lock) {
            events = events.map { if (it.id == id) it.copy(read = read) else it }
            persist()
        }
    }

    fun markAllRead() {
        synchronized(lock) {
            events = events.map { it.copy(read = true) }
            persist()
        }
    }

    fun markAllUnread() {
        synchronized(lock) {
            events = events.map { it.copy(read = false) }
            persist()
        }
    }

    fun clearAll() {
        synchronized(lock) {
            events = emptyList()
            persist()
        }
    }

    fun find(id: String): MagenEvent? = events.firstOrNull { it.id == id }

    fun filtered(
        severity: EventSeverity? = null,
        state: EventState? = null,
        unreadOnly: Boolean = false,
        query: String = "",
    ): List<MagenEvent> {
        val q = query.trim().lowercase(Locale.ROOT)
        return events.filter { event ->
            (severity == null || event.severity == severity) &&
                (state == null || event.state == state) &&
                (!unreadOnly || !event.read) &&
                (q.isBlank() || listOf(event.title, event.message, event.technical, event.type)
                    .any { it.lowercase(Locale.ROOT).contains(q) })
        }.sortedByDescending { it.updatedAt }
    }

    fun unreadCount(): Int = events.count { !it.read }

    fun exportText(): String = buildString {
        appendLine("Magen — דוח אירועים")
        appendLine("נוצר: ${formatTime(System.currentTimeMillis())}")
        appendLine("מספר אירועים: ${events.size}")
        appendLine()
        events.sortedByDescending { it.updatedAt }.forEach { event ->
            appendLine("[${severityLabel(event.severity)}] ${event.title}")
            appendLine("מצב: ${stateLabel(event.state)} · מופעים: ${event.occurrences}")
            appendLine("נוצר: ${formatTime(event.createdAt)} · עודכן: ${formatTime(event.updatedAt)}")
            appendLine(event.message)
            if (event.technical.isNotBlank()) appendLine("פרטים טכניים: ${event.technical}")
            event.history.forEach { appendLine("  $it") }
            appendLine()
        }
    }

    private fun moreSevere(a: EventSeverity, b: EventSeverity): EventSeverity =
        if (severityRank(a) >= severityRank(b)) a else b

    private fun severityRank(value: EventSeverity): Int = when (value) {
        EventSeverity.CRITICAL -> 4
        EventSeverity.HIGH -> 3
        EventSeverity.MEDIUM -> 2
        EventSeverity.LOW -> 1
    }

    private fun persist() {
        app?.getSharedPreferences("magen_events", Context.MODE_PRIVATE)
            ?.edit()?.putString("events", JSONArray().apply {
                events.forEach { event ->
                    put(JSONObject().apply {
                        put("id", event.id)
                        put("type", event.type)
                        put("title", event.title)
                        put("message", event.message)
                        put("technical", event.technical)
                        put("severity", event.severity.name)
                        put("state", event.state.name)
                        put("createdAt", event.createdAt)
                        put("updatedAt", event.updatedAt)
                        put("occurrences", event.occurrences)
                        put("read", event.read)
                        put("history", JSONArray(event.history))
                    })
                }
            }.toString())?.apply()
    }

    private fun load(): List<MagenEvent> {
        val raw = app?.getSharedPreferences("magen_events", Context.MODE_PRIVATE)
            ?.getString("events", null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { index ->
                val item = json.optJSONObject(index) ?: return@mapNotNull null
                val historyJson = item.optJSONArray("history") ?: JSONArray()
                MagenEvent(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    type = item.optString("type", "unknown"),
                    title = item.optString("title", "אירוע"),
                    message = item.optString("message"),
                    technical = item.optString("technical"),
                    severity = runCatching { EventSeverity.valueOf(item.optString("severity", "MEDIUM")) }.getOrDefault(EventSeverity.MEDIUM),
                    state = runCatching { EventState.valueOf(item.optString("state", "UNRESOLVED")) }.getOrDefault(EventState.UNRESOLVED),
                    createdAt = item.optLong("createdAt", 0L),
                    updatedAt = item.optLong("updatedAt", item.optLong("createdAt", 0L)),
                    occurrences = item.optInt("occurrences", 1).coerceAtLeast(1),
                    read = item.optBoolean("read", false),
                    history = (0 until historyJson.length()).map { historyJson.optString(it) },
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }
}

fun formatEventTime(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(time))

private fun formatTime(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(time))

fun severityLabel(value: EventSeverity): String = when (value) {
    EventSeverity.CRITICAL -> "קריטי"
    EventSeverity.HIGH -> "גבוה"
    EventSeverity.MEDIUM -> "בינוני"
    EventSeverity.LOW -> "נמוך"
}

fun stateLabel(value: EventState): String = when (value) {
    EventState.ACTIVE -> "פעיל"
    EventState.IN_PROGRESS -> "בטיפול"
    EventState.RESOLVED -> "נפתר"
    EventState.UNRESOLVED -> "לא נפתר"
}
