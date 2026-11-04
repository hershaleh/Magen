package app.magen.lock.core

import java.security.MessageDigest

/** A setting value in a backup. */
sealed class BackupValue {
    data class Flag(val on: Boolean) : BackupValue()
    data class Number(val n: Long) : BackupValue()
    data class Text(val s: String) : BackupValue()
    data class Items(val items: Set<String>) : BackupValue()
}

/**
 * The settings backup file (spec section 12): a versioned, checksummed text file that can be validated before
 * anything is restored. Secrets are never written: the code, its salt and hash, the recovery code, failure
 * counters and the time windows are excluded by [EXCLUDED_KEYS]/[isExcluded] whatever the caller passes in.
 * The security log and usage counters are not part of a backup, so restoring cannot reset them.
 *
 * Format: line 1 "MAGEN-BACKUP", line 2 "version=N", line 3 "sha256=hex" of the lines after it, then one setting
 * per line as "key|F|1", "key|N|42", "key|T|text" or "key|S|item<TAB>item", with `\`, tab, newline and `|`
 * escaped. Pure logic, no Android dependency.
 */
object BackupFormat {
    const val MAGIC = "MAGEN-BACKUP"
    const val CURRENT_VERSION = 1

    /** Keys that must never be exported or imported: credentials, lockouts, and protective time windows. */
    val EXCLUDED_KEYS = setOf(
        "hash", "salt", "rhash", "rsalt", "fails", "lockUntil", "allowedUntil",
        "lockElapsed", "lockBoot", "lockDur", "lockChk", "allowedElapsed", "allowedBoot", "allowedDur", "allowedChk",
    )

    fun isExcluded(key: String) = key in EXCLUDED_KEYS || key.startsWith("clock") || key.startsWith("usage")

    private fun esc(s: String) = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '|' -> append("\\p")
            else -> append(c)
        }
    }

    private fun unesc(s: String): String? {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') { sb.append(c); i++; continue }
            if (i + 1 >= s.length) return null
            when (s[i + 1]) {
                '\\' -> sb.append('\\')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'p' -> sb.append('|')
                else -> return null
            }
            i += 2
        }
        return sb.toString()
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun line(key: String, v: BackupValue): String = when (v) {
        is BackupValue.Flag -> "${esc(key)}|F|${if (v.on) 1 else 0}"
        is BackupValue.Number -> "${esc(key)}|N|${v.n}"
        is BackupValue.Text -> "${esc(key)}|T|${esc(v.s)}"
        is BackupValue.Items -> "${esc(key)}|S|${v.items.sorted().joinToString("\t") { esc(it) }}"
    }

    fun encode(settings: Map<String, BackupValue>, version: Int = CURRENT_VERSION): String {
        val body = settings.filterKeys { !isExcluded(it) }.toSortedMap().map { line(it.key, it.value) }.joinToString("\n")
        return "$MAGIC\nversion=$version\nsha256=${sha256(body)}\n$body"
    }

    sealed class Decoded {
        class Ok(val version: Int, val settings: Map<String, BackupValue>) : Decoded()
        class Failed(val reason: Reason) : Decoded()
    }

    enum class Reason { NOT_A_BACKUP, DAMAGED, CHECKSUM_MISMATCH, UNSUPPORTED_VERSION, CONTAINS_SECRETS }

    /** Validates structure, integrity and version before returning anything. A backup that holds excluded keys is refused. */
    fun decode(text: String): Decoded {
        val lines = text.split("\n")
        if (lines.size < 3 || lines[0] != MAGIC) return Decoded.Failed(Reason.NOT_A_BACKUP)
        val version = lines[1].removePrefix("version=").toIntOrNull()?.takeIf { lines[1].startsWith("version=") }
            ?: return Decoded.Failed(Reason.DAMAGED)
        val sum = lines[2].takeIf { it.startsWith("sha256=") }?.removePrefix("sha256=") ?: return Decoded.Failed(Reason.DAMAGED)
        val body = lines.drop(3).joinToString("\n")
        if (sha256(body) != sum) return Decoded.Failed(Reason.CHECKSUM_MISMATCH)
        if (version < 1 || version > CURRENT_VERSION) return Decoded.Failed(Reason.UNSUPPORTED_VERSION)
        val out = LinkedHashMap<String, BackupValue>()
        if (body.isNotEmpty()) for (l in lines.drop(3)) {
            val parts = l.split("|")
            if (parts.size != 3) return Decoded.Failed(Reason.DAMAGED)
            val key = unesc(parts[0]) ?: return Decoded.Failed(Reason.DAMAGED)
            if (key.isEmpty() || key in out) return Decoded.Failed(Reason.DAMAGED)
            if (isExcluded(key)) return Decoded.Failed(Reason.CONTAINS_SECRETS)
            val v: BackupValue = when (parts[1]) {
                "F" -> when (parts[2]) { "1" -> BackupValue.Flag(true); "0" -> BackupValue.Flag(false); else -> return Decoded.Failed(Reason.DAMAGED) }
                "N" -> BackupValue.Number(parts[2].toLongOrNull() ?: return Decoded.Failed(Reason.DAMAGED))
                "T" -> BackupValue.Text(unesc(parts[2]) ?: return Decoded.Failed(Reason.DAMAGED))
                "S" -> BackupValue.Items(
                    if (parts[2].isEmpty()) emptySet()
                    else parts[2].split("\t").map { unesc(it) ?: return Decoded.Failed(Reason.DAMAGED) }.toSet(),
                )
                else -> return Decoded.Failed(Reason.DAMAGED)
            }
            out[key] = v
        }
        return Decoded.Ok(version, out)
    }

    /** A difference between what is set now and what a backup would put back. */
    class Change(val key: String, val current: BackupValue?, val restored: BackupValue?)

    fun diff(current: Map<String, BackupValue>, backup: Map<String, BackupValue>): List<Change> =
        (current.keys + backup.keys).filter { !isExcluded(it) }.sorted().mapNotNull { k ->
            if (current[k] == backup[k]) null else Change(k, current[k], backup[k])
        }

    /** Flags where TRUE means protection is on, and sets where every extra item is more protection. */
    private val PROTECTIVE_FLAGS = setOf("protect", "blockPlay", "blockApk", "relock")
    private val PROTECTIVE_SETS = setOf("locked", "blockedSettings", "customKeywords")

    /**
     * Changes that would make protection WEAKER than it is now: a protective switch turned off, or items removed
     * from a protective set. Restoring an old backup must show these and ask first (spec section 12).
     */
    fun weakening(current: Map<String, BackupValue>, backup: Map<String, BackupValue>): List<Change> =
        diff(current, backup).filter { c ->
            val now = c.current
            val then = c.restored
            when (c.key) {
                in PROTECTIVE_FLAGS -> now == BackupValue.Flag(true) && then != BackupValue.Flag(true)
                in PROTECTIVE_SETS -> {
                    val nowItems = (now as? BackupValue.Items)?.items ?: emptySet()
                    val thenItems = (then as? BackupValue.Items)?.items ?: emptySet()
                    nowItems.any { it !in thenItems }
                }
                else -> false
            }
        }
}
