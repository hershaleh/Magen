package app.magen.lock

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class ActivityEvent(
    val timestamp: Long,
    val type: String,
    val detail: String,
)

object ActivityLog {
    private const val MAX_EVENTS = 500
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var file: File? = null

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, "activity.log")
    }

    /** Queue-only write: never performs disk I/O on the accessibility or UI thread. */
    fun record(type: String, detail: String = "") {
        val target = file ?: return
        val safeType = type.take(48).filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        val safeDetail = detail.take(180).replace('\n', ' ').replace('\r', ' ')
        executor.execute {
            runCatching {
                target.parentFile?.mkdirs()
                target.appendText(
                    JSONObject()
                        .put("time", System.currentTimeMillis())
                        .put("type", safeType)
                        .put("detail", safeDetail)
                        .toString() + "\n",
                )
                trim(target)
            }
        }
    }

    fun readLatest(): List<ActivityEvent> {
        val target = file ?: return emptyList()
        return runCatching {
            if (!target.exists()) return emptyList()
            target.readLines().takeLast(MAX_EVENTS).mapNotNull { line ->
                runCatching {
                    val item = JSONObject(line)
                    ActivityEvent(item.getLong("time"), item.getString("type"), item.optString("detail"))
                }.getOrNull()
            }.asReversed()
        }.getOrDefault(emptyList())
    }

    fun clear() {
        val target = file ?: return
        executor.execute { runCatching { target.delete() } }
    }

    private fun trim(target: File) {
        val lines = target.readLines()
        if (lines.size > MAX_EVENTS) {
            val temporary = File(target.parentFile, "activity.tmp")
            temporary.writeText(lines.takeLast(MAX_EVENTS).joinToString("\n", postfix = "\n"))
            if (!temporary.renameTo(target)) {
                target.writeText(lines.takeLast(MAX_EVENTS).joinToString("\n", postfix = "\n"))
                temporary.delete()
            }
        }
    }
}
