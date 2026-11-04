package app.magen.lock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityLogTest {
    private val day = 86_400_000L
    private val t0 = 1_800_000_000_000L

    private fun one(type: String = "service_stopped", key: String = "guard", now: Long = t0) =
        SecurityLog.record(emptyList(), type, key, now, "msg", "tech")

    @Test
    fun severityFollowsTheImpactOfTheEventType() {
        assertEquals(Severity.CRITICAL, SecurityLog.severityFor("accessibility_disabled"))
        assertEquals(Severity.HIGH, SecurityLog.severityFor("clock_changed"))
        assertEquals(Severity.MEDIUM, SecurityLog.severityFor("background_limited"))
        assertEquals(Severity.LOW, SecurityLog.severityFor("app_blocked"))
        assertEquals("unknown is never silently low", Severity.MEDIUM, SecurityLog.severityFor("something_new"))
    }

    @Test
    fun aNewEventIsActiveUnreadAndHasItsFirstHistoryEntry() {
        val e = one()[0]
        assertEquals(EventStatus.ACTIVE, e.status)
        assertFalse(e.read)
        assertEquals(1, e.count)
        assertEquals(1, e.history.size)
        assertEquals(t0, e.firstAtMs)
        assertEquals(Severity.HIGH, e.severity)
    }

    @Test
    fun theSameOpenProblemIsMergedIntoOneEventWithACounter() {
        var log = one()
        log = SecurityLog.markRead(log, log[0].id)
        log = SecurityLog.record(log, "service_stopped", "guard", t0 + 1000)
        log = SecurityLog.record(log, "service_stopped", "guard", t0 + 2000)
        assertEquals(1, log.size)
        assertEquals(3, log[0].count)
        assertEquals(t0, log[0].firstAtMs)
        assertEquals(t0 + 2000, log[0].lastAtMs)
        assertFalse("a repeat makes it unread again", log[0].read)
        assertEquals("msg", log[0].message)
    }

    @Test
    fun differentTypeOrKeyIsADifferentEvent() {
        var log = one()
        log = SecurityLog.record(log, "service_stopped", "other", t0)
        log = SecurityLog.record(log, "permission_revoked", "guard", t0)
        assertEquals(3, log.size)
        assertEquals(setOf(1L, 2L, 3L), log.map { it.id }.toSet())
    }

    @Test
    fun aResolvedEventIsNotReopenedByARepeatButStartsANewEvent() {
        var log = one()
        log = SecurityLog.resolve(log, log[0].id, t0 + 10, verifiedGone = true)
        log = SecurityLog.record(log, "service_stopped", "guard", t0 + 20)
        assertEquals(2, log.size)
        assertEquals(EventStatus.RESOLVED, log[0].status)
        assertEquals(EventStatus.ACTIVE, log[1].status)
    }

    @Test
    fun anEventIsResolvedOnlyWhenTheCheckShowsTheProblemIsGone() {
        var log = one()
        val id = log[0].id
        log = SecurityLog.startRepair(log, id, t0 + 1, "restart service")
        assertEquals(EventStatus.IN_PROGRESS, log[0].status)
        log = SecurityLog.resolve(log, id, t0 + 2, verifiedGone = false)
        assertEquals(EventStatus.UNRESOLVED, log[0].status)
        assertNull(log[0].resolvedAtMs)
        assertTrue(log[0].isOpen)
        log = SecurityLog.resolve(log, id, t0 + 3, verifiedGone = true, note = "service running")
        assertEquals(EventStatus.RESOLVED, log[0].status)
        assertEquals(t0 + 3, log[0].resolvedAtMs)
        assertFalse(log[0].isOpen)
        assertTrue("the repair is marked successful by the check", log[0].repairs.last().succeeded)
        assertEquals(
            listOf(EventStatus.ACTIVE, EventStatus.IN_PROGRESS, EventStatus.UNRESOLVED, EventStatus.RESOLVED),
            log[0].history.map { it.status },
        )
    }

    @Test
    fun anOpenEventCanBeResolvedWithoutARepairWhenTheProblemWentAwayByItself() {
        var log = one()
        log = SecurityLog.resolve(log, log[0].id, t0 + 5, verifiedGone = true, note = "permission restored in Android settings")
        assertEquals(EventStatus.RESOLVED, log[0].status)
        assertTrue(log[0].repairs.isEmpty())
    }

    @Test
    fun aResolvedEventStaysResolved() {
        var log = one()
        log = SecurityLog.resolve(log, log[0].id, t0 + 1, true)
        val before = log[0].history.size
        log = SecurityLog.resolve(log, log[0].id, t0 + 2, false)
        log = SecurityLog.startRepair(log, log[0].id, t0 + 3, "again")
        assertEquals(EventStatus.RESOLVED, log[0].status)
        assertEquals(before, log[0].history.size)
    }

    @Test
    fun markAllAppliesToEveryEventEvenThoseAFilterWouldHide() {
        var log = one("accessibility_disabled", "a")
        log = SecurityLog.record(log, "app_blocked", "b", t0)
        val hidden = SecurityLog.filter(log, LogFilter(severities = setOf(Severity.CRITICAL)))
        assertEquals(1, hidden.size)
        log = SecurityLog.markAll(log, true)
        assertTrue(log.all { it.read })
        assertEquals(0, SecurityLog.unreadCount(log))
        log = SecurityLog.markAll(log, false)
        assertEquals(2, SecurityLog.unreadCount(log))
    }

    @Test
    fun unreadCriticalEventsStayProminentAfterTheScreenIsOpenedByDefault() {
        var log = one("accessibility_disabled", "a")
        log = SecurityLog.record(log, "app_blocked", "b", t0)
        val opened = SecurityLog.onScreenOpened(log, setOf(Severity.LOW, Severity.MEDIUM))
        assertTrue(SecurityLog.isProminent(opened.first { it.type == "accessibility_disabled" }))
        assertTrue(opened.first { it.type == "app_blocked" }.read)
    }

    @Test
    fun theSeverityMarkingCanBeChangedForEachLevelIncludingCritical() {
        val log = one("accessibility_disabled", "a")
        val opened = SecurityLog.onScreenOpened(log, setOf(Severity.CRITICAL))
        assertTrue(opened[0].read)
        assertFalse(SecurityLog.isProminent(opened[0], setOf(Severity.CRITICAL)))
    }

    @Test
    fun filterByTypeSeverityStatusDateAndQuery() {
        var log = one("service_stopped", "guard", t0)
        log = SecurityLog.record(log, "clock_changed", "device", t0 + 2 * day, "Clock moved back 5 hours")
        log = SecurityLog.record(log, "app_blocked", "com.example.game", t0 + 4 * day, "Blocked", "quota exhausted")
        log = SecurityLog.resolve(log, log[0].id, t0 + 5 * day, true)
        fun ids(f: LogFilter) = SecurityLog.filter(log, f).map { it.type }.toSet()
        assertEquals(setOf("clock_changed"), ids(LogFilter(types = setOf("clock_changed"))))
        assertEquals(setOf("service_stopped", "clock_changed"), ids(LogFilter(severities = setOf(Severity.HIGH))))
        assertEquals(setOf("service_stopped"), ids(LogFilter(statuses = setOf(EventStatus.RESOLVED))))
        assertEquals(setOf("app_blocked"), ids(LogFilter(fromMs = t0 + 3 * day)))
        assertEquals(setOf("service_stopped"), ids(LogFilter(toMs = t0 + 1 * day)))
        assertEquals(setOf("clock_changed"), ids(LogFilter(query = "  BACK 5 ")))
        assertEquals("by app", setOf("app_blocked"), ids(LogFilter(query = "com.example")))
        assertEquals("by technical text", setOf("app_blocked"), ids(LogFilter(query = "exhausted")))
        assertEquals(3, SecurityLog.filter(log, LogFilter()).size)
    }

    @Test
    fun unreadOnlyFilter() {
        var log = one("app_blocked", "a")
        log = SecurityLog.markRead(log, log[0].id)
        log = SecurityLog.record(log, "clock_changed", "d", t0)
        assertEquals(listOf("clock_changed"), SecurityLog.filter(log, LogFilter(unreadOnly = true)).map { it.type })
    }

    @Test
    fun sortingPutsTheNewestActivityFirst() {
        var log = one("app_blocked", "a", t0)
        log = SecurityLog.record(log, "clock_changed", "d", t0 + 100)
        log = SecurityLog.record(log, "app_blocked", "a", t0 + 200)
        assertEquals(listOf("app_blocked", "clock_changed"), SecurityLog.sortedNewestFirst(log).map { it.type })
    }

    @Test
    fun retentionDropsOnlyOldResolvedEventsAndKeepsEverythingWhenUnlimited() {
        var log = one("app_blocked", "old", t0)
        log = SecurityLog.resolve(log, log[0].id, t0, true)
        log = SecurityLog.record(log, "service_stopped", "openold", t0) // old but still open
        log = SecurityLog.record(log, "app_blocked", "recent", t0 + 40 * day)
        val now = t0 + 41 * day
        assertEquals(3, SecurityLog.prune(log, now, null).size)
        val kept = SecurityLog.prune(log, now, 30)
        assertEquals(setOf("openold", "recent"), kept.map { it.key }.toSet())
    }

    @Test
    fun clearRemovesTheWholeHistory() {
        assertTrue(SecurityLog.clear(one()).isEmpty())
    }

    @Test
    fun aMoreSevereRepeatRaisesTheSeverityButNeverLowersIt() {
        var log = one("app_blocked", "a")
        log = SecurityLog.record(log, "app_blocked", "a", t0 + 1, severity = Severity.CRITICAL)
        assertEquals(Severity.CRITICAL, log[0].severity)
        log = SecurityLog.record(log, "app_blocked", "a", t0 + 2, severity = Severity.LOW)
        assertEquals(Severity.CRITICAL, log[0].severity)
    }
}
