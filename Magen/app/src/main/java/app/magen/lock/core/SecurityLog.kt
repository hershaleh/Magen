package app.magen.lock.core

/** Impact of an event (spec section 11.1). Declared from most to least severe. */
enum class Severity { CRITICAL, HIGH, MEDIUM, LOW }

/** An event is only RESOLVED once the system has checked that the problem is gone. */
enum class EventStatus { ACTIVE, IN_PROGRESS, RESOLVED, UNRESOLVED }

/** One change in the life of an event: when, what it became, and a short note. */
class StatusChange(val atMs: Long, val status: EventStatus, val note: String)

/** One attempt to repair the problem, and whether re-checking proved it worked. */
class RepairAttempt(val atMs: Long, val action: String, val succeeded: Boolean)

/**
 * A security-log entry. Identical problems that keep coming back are merged into one event with a counter
 * ([count], [firstAtMs], [lastAtMs]) instead of filling the log, see [SecurityLog.record].
 */
class SecurityEvent(
    val id: Long,
    /** Machine type, such as "service_stopped" or "clock_changed". The meaning of the type decides the severity. */
    val type: String,
    /** Distinguishes events of one type: the app or component concerned. Same type and key merge. */
    val key: String,
    val severity: Severity,
    val status: EventStatus,
    val firstAtMs: Long,
    val lastAtMs: Long,
    val count: Int = 1,
    val message: String = "",
    val technical: String = "",
    val read: Boolean = false,
    val resolvedAtMs: Long? = null,
    val repairs: List<RepairAttempt> = emptyList(),
    val history: List<StatusChange> = emptyList(),
) {
    fun copy(
        severity: Severity = this.severity,
        status: EventStatus = this.status,
        lastAtMs: Long = this.lastAtMs,
        count: Int = this.count,
        message: String = this.message,
        technical: String = this.technical,
        read: Boolean = this.read,
        resolvedAtMs: Long? = this.resolvedAtMs,
        repairs: List<RepairAttempt> = this.repairs,
        history: List<StatusChange> = this.history,
    ) = SecurityEvent(id, type, key, severity, status, firstAtMs, lastAtMs, count, message, technical, read, resolvedAtMs, repairs, history)

    val isOpen: Boolean get() = status == EventStatus.ACTIVE || status == EventStatus.IN_PROGRESS || status == EventStatus.UNRESOLVED
}

/** What to show; every field left null means "no filter". */
class LogFilter(
    val types: Set<String>? = null,
    val severities: Set<Severity>? = null,
    val statuses: Set<EventStatus>? = null,
    val fromMs: Long? = null,
    val toMs: Long? = null,
    /** Matched, ignoring case, against the type, message, technical text and key (an app or an error text). */
    val query: String? = null,
    val unreadOnly: Boolean = false,
)

/**
 * The rules of the security log, as plain functions over an immutable list so each rule is testable. Storage and
 * screens call these. There is deliberately no function to mark an event handled by hand and none to delete a
 * single event: an event is resolved only by [resolve] after a successful check, and history is cleared whole
 * ([clear]) or by retention ([prune]).
 */
object SecurityLog {
    /** Severity by the impact of the event type. Unknown types are MEDIUM, so nothing is silently low. */
    fun severityFor(type: String): Severity = when (type) {
        "device_owner_lost", "device_admin_lost", "accessibility_disabled", "app_data_cleared", "protection_bypass",
        "enforcement_failed" -> Severity.CRITICAL
        "service_stopped", "permission_revoked", "clock_changed", "uninstall_attempt", "restriction_not_applied",
        "shabbat_check_failed" -> Severity.HIGH
        "background_limited", "system_updated", "blocked_app_opened", "backup_failed" -> Severity.MEDIUM
        "app_blocked", "quota_warning", "service_restarted", "backup_created" -> Severity.LOW
        else -> Severity.MEDIUM
    }

    private fun nextId(events: List<SecurityEvent>) = (events.maxOfOrNull { it.id } ?: 0L) + 1

    /**
     * Records that [type] happened for [key] at [nowMs]. When an OPEN event of the same type and key exists it gains a
     * repeat (count, last time, unread again) instead of a new entry; otherwise a new ACTIVE event is added.
     */
    fun record(
        events: List<SecurityEvent>, type: String, key: String, nowMs: Long, message: String = "", technical: String = "",
        severity: Severity = severityFor(type),
    ): List<SecurityEvent> {
        val i = events.indexOfFirst { it.type == type && it.key == key && it.isOpen }
        if (i >= 0) {
            val e = events[i]
            val merged = e.copy(
                lastAtMs = nowMs, count = e.count + 1, read = false,
                message = message.ifEmpty { e.message }, technical = technical.ifEmpty { e.technical },
                severity = if (severity < e.severity) severity else e.severity,
            )
            return events.toMutableList().also { it[i] = merged }
        }
        val first = StatusChange(nowMs, EventStatus.ACTIVE, "detected")
        return events + SecurityEvent(nextId(events), type, key, severity, EventStatus.ACTIVE, nowMs, nowMs, 1, message, technical, history = listOf(first))
    }

    private fun update(events: List<SecurityEvent>, id: Long, f: (SecurityEvent) -> SecurityEvent) =
        events.map { if (it.id == id) f(it) else it }

    /** Marks an event as being repaired. */
    fun startRepair(events: List<SecurityEvent>, id: Long, nowMs: Long, action: String): List<SecurityEvent> =
        update(events, id) {
            if (it.status == EventStatus.RESOLVED) it
            else it.copy(
                status = EventStatus.IN_PROGRESS,
                repairs = it.repairs + RepairAttempt(nowMs, action, false),
                history = it.history + StatusChange(nowMs, EventStatus.IN_PROGRESS, action),
            )
        }

    /**
     * Records the outcome of a re-check after a repair or of noticing that the problem went away by itself (for
     * example the person restored a permission in Android settings). RESOLVED only when [verifiedGone] is true;
     * otherwise the event is UNRESOLVED and stays open. Nothing resolves an event without this check.
     */
    fun resolve(events: List<SecurityEvent>, id: Long, nowMs: Long, verifiedGone: Boolean, note: String = ""): List<SecurityEvent> =
        update(events, id) { e ->
            if (e.status == EventStatus.RESOLVED) return@update e
            val repairs = if (e.repairs.isNotEmpty() && verifiedGone) {
                e.repairs.dropLast(1) + RepairAttempt(e.repairs.last().atMs, e.repairs.last().action, true)
            } else {
                e.repairs
            }
            if (verifiedGone) {
                e.copy(status = EventStatus.RESOLVED, resolvedAtMs = nowMs, repairs = repairs, history = e.history + StatusChange(nowMs, EventStatus.RESOLVED, note.ifEmpty { "verified gone" }))
            } else {
                e.copy(status = EventStatus.UNRESOLVED, history = e.history + StatusChange(nowMs, EventStatus.UNRESOLVED, note.ifEmpty { "still present after check" }))
            }
        }

    fun markRead(events: List<SecurityEvent>, id: Long, read: Boolean = true) = update(events, id) { it.copy(read = read) }

    /** Applies to EVERY event, also those a filter hides. */
    fun markAll(events: List<SecurityEvent>, read: Boolean) = events.map { it.copy(read = read) }

    /**
     * Unread critical events stay prominent after the screen is opened, unless the person turned that off for the
     * severity ([markReadOnOpen] holds the severities that are marked read by opening the screen).
     */
    fun isProminent(e: SecurityEvent, markReadOnOpen: Set<Severity> = emptySet()): Boolean =
        !e.read && e.severity == Severity.CRITICAL && Severity.CRITICAL !in markReadOnOpen

    /** What opening the log screen marks as read: only severities the person chose, never critical by default. */
    fun onScreenOpened(events: List<SecurityEvent>, markReadOnOpen: Set<Severity>) =
        events.map { if (it.severity in markReadOnOpen) it.copy(read = true) else it }

    fun filter(events: List<SecurityEvent>, f: LogFilter): List<SecurityEvent> {
        val q = f.query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return events.filter { e ->
            (f.types == null || e.type in f.types) &&
                (f.severities == null || e.severity in f.severities) &&
                (f.statuses == null || e.status in f.statuses) &&
                (f.fromMs == null || e.lastAtMs >= f.fromMs) &&
                (f.toMs == null || e.firstAtMs <= f.toMs) &&
                (!f.unreadOnly || !e.read) &&
                (q == null || listOf(e.type, e.message, e.technical, e.key).any { it.lowercase().contains(q) })
        }
    }

    /** Newest activity first; ties broken by severity, then id. */
    fun sortedNewestFirst(events: List<SecurityEvent>): List<SecurityEvent> =
        events.sortedWith(compareByDescending<SecurityEvent> { it.lastAtMs }.thenBy { it.severity }.thenByDescending { it.id })

    /** Retention: drops events whose last activity is older than [retentionDays] days. Null keeps everything. Open events are never dropped. */
    fun prune(events: List<SecurityEvent>, nowMs: Long, retentionDays: Int?): List<SecurityEvent> {
        if (retentionDays == null) return events
        val cutoff = nowMs - retentionDays * 86_400_000L
        return events.filter { it.isOpen || it.lastAtMs >= cutoff }
    }

    /** The whole history, cleared by hand. */
    fun clear(@Suppress("UNUSED_PARAMETER") events: List<SecurityEvent>): List<SecurityEvent> = emptyList()

    fun unreadCount(events: List<SecurityEvent>) = events.count { !it.read }
}
