package app.magen.lock

import android.content.Context
import app.magen.lock.core.BackupFormat
import app.magen.lock.core.BackupValue
import app.magen.lock.core.RuleCodec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local backups of the settings (spec section 12). A backup holds the ordinary settings and the time rules, never
 * the code, recovery code, failure counters, time windows, usage counters or the security log. Files stay on the
 * device in the app's private folder; nothing is sent anywhere.
 */
object Backups {
    private const val DIR = "backups"
    private const val EXT = ".mbk"
    private const val KEEP = 10
    private const val RULES_KEY = "rules.config"

    class Item(val file: File, val name: String, val modifiedMs: Long)

    private fun dir(ctx: Context) = File(ctx.filesDir, DIR).apply { mkdirs() }

    /** The settings now, as backup values. */
    fun snapshot(ctx: Context): Map<String, BackupValue> {
        val m = LinkedHashMap<String, BackupValue>()
        for ((k, v) in ctx.getSharedPreferences("magen", Context.MODE_PRIVATE).all) {
            if (BackupFormat.isExcluded(k)) continue
            when (v) {
                is Boolean -> m[k] = BackupValue.Flag(v)
                is Int -> m[k] = BackupValue.Int32(v)
                is Long -> m[k] = BackupValue.Number(v)
                is String -> m[k] = BackupValue.Text(v)
                is Set<*> -> m[k] = BackupValue.Items(v.filterIsInstance<String>().toSet())
                else -> Unit
            }
        }
        m[RULES_KEY] = BackupValue.Text(RuleCodec.encode(RuleStore.config))
        return m
    }

    fun export(ctx: Context): String = BackupFormat.encode(snapshot(ctx))

    /** Saves a backup file now and trims the oldest beyond [KEEP]. Returns the file, or null when it could not be written. */
    fun create(ctx: Context, reason: String): File? = runCatching {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(dir(ctx), "magen-$stamp-${reason.filter { it.isLetterOrDigit() }.take(16)}$EXT")
        f.writeText(export(ctx))
        list(ctx).drop(KEEP).forEach { it.file.delete() }
        f
    }.getOrNull()

    fun list(ctx: Context): List<Item> =
        (dir(ctx).listFiles { f -> f.name.endsWith(EXT) } ?: emptyArray())
            .map { Item(it, it.name, it.lastModified()) }
            .sortedByDescending { it.modifiedMs }

    /** One automatic backup per day, made when the first tick of the day runs. */
    fun autoIfDue(ctx: Context, day: Long) {
        val sp = ctx.getSharedPreferences("magen_rules", Context.MODE_PRIVATE)
        if (sp.getLong("lastAutoBackupDay", Long.MIN_VALUE) >= day) return
        if (create(ctx, "auto") != null) sp.edit().putLong("lastAutoBackupDay", day).apply()
    }

    sealed class Plan {
        class Ready(val settings: Map<String, BackupValue>, val changes: List<BackupFormat.Change>, val weakening: List<BackupFormat.Change>) : Plan()
        class Invalid(val reason: BackupFormat.Reason) : Plan()
    }

    /** Validates [text] and works out what restoring it would change and which changes weaken protection. */
    fun plan(ctx: Context, text: String): Plan = when (val d = BackupFormat.decode(text)) {
        is BackupFormat.Decoded.Failed -> Plan.Invalid(d.reason)
        is BackupFormat.Decoded.Ok -> {
            val now = snapshot(ctx)
            Plan.Ready(d.settings, BackupFormat.diff(now, d.settings), BackupFormat.weakening(now, d.settings))
        }
    }

    /**
     * Restores the settings from a validated plan. A backup of the present state is made first. The rules go through
     * [RuleStore] (refused while Shabbat blocking is on); the other settings are written to the preferences and
     * [Store] is reloaded. Keys that the backup does not contain are left as they are. Returns false when refused.
     */
    fun restore(ctx: Context, plan: Plan.Ready): Boolean {
        if (RuleStore.readOnly) return false
        create(ctx, "beforerestore")
        val rules = (plan.settings[RULES_KEY] as? BackupValue.Text)?.s?.let { RuleCodec.decode(it) }
        if (plan.settings.containsKey(RULES_KEY) && rules == null) return false
        val e = ctx.getSharedPreferences("magen", Context.MODE_PRIVATE).edit()
        for ((k, v) in plan.settings) {
            if (k == RULES_KEY || BackupFormat.isExcluded(k)) continue
            when (v) {
                is BackupValue.Flag -> e.putBoolean(k, v.on)
                is BackupValue.Int32 -> e.putInt(k, v.n)
                is BackupValue.Number -> e.putLong(k, v.n)
                is BackupValue.Text -> e.putString(k, v.s)
                is BackupValue.Items -> e.putStringSet(k, HashSet(v.items))
            }
        }
        e.commit()
        Store.init(ctx)
        if (rules != null) RuleStore.restoreConfig(rules)
        RuleRuntime.reset()
        return true
    }
}
