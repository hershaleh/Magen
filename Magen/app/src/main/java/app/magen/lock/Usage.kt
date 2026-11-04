package app.magen.lock

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

object UsageLimitPolicy {
    fun isReached(limitMinutes: Int?, usedMillis: Long): Boolean =
        limitMinutes != null && limitMinutes > 0 && usedMillis >= limitMinutes * 60_000L
}

object UsageTracker {
    private data class Cached(val checkedAt: Long, val usedMillis: Long)
    private val cache = ConcurrentHashMap<String, Cached>()
    private const val CACHE_MS = 20_000L

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun todayUsageMillis(context: Context, packageName: String, forceRefresh: Boolean = false): Long {
        val nowElapsed = SystemClock.elapsedRealtime()
        val cached = cache[packageName]
        if (!forceRefresh && cached != null && nowElapsed - cached.checkedAt < CACHE_MS) {
            return cached.usedMillis
        }
        if (!hasAccess(context)) return 0L

        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val used = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis())
                .orEmpty()
                .filter { it.packageName == packageName }
                .sumOf { it.totalTimeInForeground }
        }.getOrDefault(0L)
        cache[packageName] = Cached(nowElapsed, used)
        return used
    }

    fun isLimitReached(limitMinutes: Int?, usedMillis: Long): Boolean =
        UsageLimitPolicy.isReached(limitMinutes, usedMillis)
}
