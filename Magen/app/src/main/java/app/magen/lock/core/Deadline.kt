package app.magen.lock.core

/**
 * A protective time window (PIN lockout, uninstall approval) that cannot be stretched or cut short by changing the
 * wall clock or by rebooting.
 *
 * What can be trusted:
 * - Within one boot, SystemClock.elapsedRealtime is monotonic and the person cannot change it. The remaining time
 *   is measured with it, and a checkpoint of how far the window had got is recorded now and then ([observe]).
 * - After a reboot there is NO trusted time source: elapsedRealtime restarts and the wall clock can be set to
 *   anything. So after a reboot the wall clock is not used at all. The window continues from the last recorded
 *   checkpoint. Consequences, all deliberate: rebooting (with or without a changed clock, any number of times)
 *   can never give a window more time than it had left at its last checkpoint; time while the device is off is
 *   not counted; and time after the last checkpoint is lost (at most [CHECKPOINT_INTERVAL_MS] while the device
 *   is in use, longer if nothing looked at the window).
 * - [bootId] (Settings.Global.BOOT_COUNT) tells boots apart. When it is unknown (-1), a reboot is recognised by
 *   elapsedRealtime going backwards, which is less reliable.
 * - Data from an older version has only a wall-clock end. It is read with the wall clock, capped by a maximum,
 *   and converted to the monotonic form the first time it is observed.
 *
 * Pure logic with no Android dependency, so it can be tested on the JVM.
 */
class Deadline(
    /** Wall-clock end (epoch milliseconds), 0 when none. Informational once the window is monotonic. */
    val wallUntil: Long,
    /** elapsedRealtime end in the boot [bootId], or -1 for data from an older version. */
    val elapsedUntil: Long,
    /** Boot in which [elapsedUntil] is valid, or -1 when unknown. */
    val bootId: Int,
    /** Original length; the upper bound for the remaining time. */
    val durationMs: Long,
    /** elapsedRealtime (in [bootId]) when progress was last recorded, or -1 meaning "at the start". */
    val checkpointElapsed: Long = -1L,
) {
    /** True when a window was ever started (even if it has ended). */
    val isSet: Boolean get() = wallUntil > 0L || elapsedUntil >= 0L

    private val isMonotonic: Boolean get() = elapsedUntil >= 0L

    private val cap: Long get() = maxOf(durationMs, 0L)

    private fun checkpoint(): Long = if (checkpointElapsed >= 0L) checkpointElapsed else elapsedUntil - durationMs

    private fun sameBoot(nowElapsed: Long, nowBootId: Int): Boolean {
        if (!isMonotonic) return false
        if (bootId >= 0 && nowBootId >= 0) return bootId == nowBootId
        // Boot unknown: elapsedRealtime never goes backwards within a boot.
        return nowElapsed >= checkpoint()
    }

    /** Milliseconds left, never negative and never more than the original length. */
    fun remainingMs(nowWall: Long, nowElapsed: Long, nowBootId: Int): Long {
        if (!isSet) return 0L
        val raw = when {
            !isMonotonic -> wallUntil - nowWall
            sameBoot(nowElapsed, nowBootId) -> elapsedUntil - nowElapsed
            else -> elapsedUntil - checkpoint()
        }
        return maxOf(minOf(raw, cap), 0L)
    }

    fun isActive(nowWall: Long, nowElapsed: Long, nowBootId: Int): Boolean =
        remainingMs(nowWall, nowElapsed, nowBootId) > 0L

    /**
     * The window as it should be persisted after looking at it now:
     * [NONE] once it has ended (so it can never come back to life), a version rebased onto the current boot
     * after a reboot or for old data, a version with a fresh checkpoint when enough time has passed, or this
     * same instance when nothing needs saving.
     */
    fun observe(nowWall: Long, nowElapsed: Long, nowBootId: Int): Deadline {
        if (!isSet) return this
        val left = remainingMs(nowWall, nowElapsed, nowBootId)
        if (left <= 0L) return NONE
        if (isMonotonic && sameBoot(nowElapsed, nowBootId)) {
            return if (nowElapsed - checkpoint() >= CHECKPOINT_INTERVAL_MS) {
                Deadline(wallUntil, elapsedUntil, bootId, durationMs, nowElapsed)
            } else {
                this
            }
        }
        return Deadline(nowWall + left, nowElapsed + left, nowBootId, durationMs, nowElapsed)
    }

    companion object {
        /** Progress is recorded at most this often, so frequent callers do not write storage every time. */
        const val CHECKPOINT_INTERVAL_MS = 5_000L

        val NONE = Deadline(0L, -1L, -1, 0L)

        fun start(durationMs: Long, nowWall: Long, nowElapsed: Long, bootId: Int) =
            Deadline(nowWall + durationMs, nowElapsed + durationMs, bootId, durationMs, nowElapsed)

        /** A window saved by an older version: only the wall-clock end is known, so [capMs] bounds it. */
        fun legacy(wallUntil: Long, capMs: Long) = Deadline(wallUntil, -1L, -1, capMs)
    }
}
