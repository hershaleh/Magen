package app.magen.lock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateCodecTest {
    @Test
    fun ledgerRoundTripsIncludingAwkwardNames() {
        val l = UsageLedger(20_000, mapOf("com.a" to 5_000L, "w;e,i|r=d" to 1L), mapOf("g;1" to 99L))
        val back = StateCodec.decodeLedger(StateCodec.encodeLedger(l))!!
        assertEquals(l.day, back.day)
        assertEquals(l.personalMs, back.personalMs)
        assertEquals(l.groupMs, back.groupMs)
    }

    @Test
    fun emptyLedgerTextIsAFreshLedgerAndDamagedTextIsRejected() {
        assertEquals(Long.MIN_VALUE, StateCodec.decodeLedger("")!!.day)
        for (bad in listOf("x", "p|a|1", "d|1\nd|2", "d|1\np|a", "d|1\np|a|-5", "d|1\np|a|zz", "d|notaday")) {
            assertNull("accepted $bad", StateCodec.decodeLedger(bad))
        }
    }

    private fun events(): List<SecurityEvent> {
        var log = SecurityLog.record(emptyList(), "service_stopped", "guard;1", 1_000, "Service, stopped: now", "trace|line\nnext")
        log = SecurityLog.startRepair(log, 1, 2_000, "restart; service")
        log = SecurityLog.resolve(log, 1, 3_000, true, "back: ok")
        log = SecurityLog.record(log, "clock_changed", "device", 4_000)
        log = SecurityLog.record(log, "clock_changed", "device", 5_000)
        return SecurityLog.markRead(log, 2)
    }

    @Test
    fun securityLogRoundTripsWithRepairsHistoryAndFlags() {
        val e = events()
        val back = StateCodec.decodeEvents(StateCodec.encodeEvents(e))!!
        assertEquals(e.size, back.size)
        for (i in e.indices) {
            val a = e[i]; val b = back[i]
            assertEquals(a.id, b.id); assertEquals(a.type, b.type); assertEquals(a.key, b.key)
            assertEquals(a.severity, b.severity); assertEquals(a.status, b.status)
            assertEquals(a.firstAtMs, b.firstAtMs); assertEquals(a.lastAtMs, b.lastAtMs); assertEquals(a.count, b.count)
            assertEquals(a.message, b.message); assertEquals(a.technical, b.technical)
            assertEquals(a.read, b.read); assertEquals(a.resolvedAtMs, b.resolvedAtMs)
            assertEquals(a.repairs.map { Triple(it.atMs, it.action, it.succeeded) }, b.repairs.map { Triple(it.atMs, it.action, it.succeeded) })
            assertEquals(a.history.map { Triple(it.atMs, it.status, it.note) }, b.history.map { Triple(it.atMs, it.status, it.note) })
        }
        assertEquals(EventStatus.RESOLVED, back[0].status)
        assertTrue(back[0].repairs.single().succeeded)
        assertEquals(2, back[1].count)
        assertTrue(back[1].read)
    }

    @Test
    fun encodingIsAFixedPointAndEmptyLogIsEmpty() {
        val once = StateCodec.encodeEvents(events())
        assertEquals(once, StateCodec.encodeEvents(StateCodec.decodeEvents(once)!!))
        assertTrue(StateCodec.decodeEvents("")!!.isEmpty())
        assertEquals("", StateCodec.encodeEvents(emptyList()))
    }

    @Test
    fun damagedEventsAreRejected() {
        for (bad in listOf("garbage", "id=1", "id=x;t=a;k=b;sv=LOW;st=ACTIVE;f=1;l=1;c=1;m=;x=;rd=0;rs=;r=;h=",
            "id=1;t=a;k=b;sv=NOPE;st=ACTIVE;f=1;l=1;c=1;m=;x=;rd=0;rs=;r=;h=",
            "id=1;t=a;k=b;sv=LOW;st=ACTIVE;f=1;l=1;c=1;m=;x=;rd=0;rs=;r=1:2;h=")) {
            assertNull("accepted $bad", StateCodec.decodeEvents(bad))
        }
    }
}
