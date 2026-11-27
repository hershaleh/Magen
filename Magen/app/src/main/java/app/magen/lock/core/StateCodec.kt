package app.magen.lock.core

/**
 * Text forms of the usage ledger and the security log, for storing them in preferences. Reading is strict: an
 * unreadable value gives null so the caller can keep what it had.
 */
object StateCodec {
    private fun esc(s: String) = RuleCodec.esc(s)
    private fun unesc(s: String) = RuleCodec.unesc(s)

    // ---- usage ledger: "d=<day>" then "p|<pkg>|<ms>" and "g|<id>|<ms>" lines -----------------------

    fun encodeLedger(l: UsageLedger): String {
        val lines = ArrayList<String>()
        lines += "d|${l.day}"
        for ((k, v) in l.personalMs.toSortedMap()) lines += "p|${esc(k)}|$v"
        for ((k, v) in l.groupMs.toSortedMap()) lines += "g|${esc(k)}|$v"
        return lines.joinToString("\n")
    }

    fun decodeLedger(text: String): UsageLedger? {
        if (text.isEmpty()) return UsageLedger()
        var day: Long? = null
        val p = LinkedHashMap<String, Long>()
        val g = LinkedHashMap<String, Long>()
        try {
            for (line in text.split("\n")) {
                val f = line.split("|")
                when (f.firstOrNull()) {
                    "d" -> { if (f.size != 2 || day != null) return null; day = f[1].toLong() }
                    "p", "g" -> {
                        if (f.size != 3) return null
                        val ms = f[2].toLong()
                        if (ms < 0) return null
                        (if (f[0] == "p") p else g)[unesc(f[1]) ?: return null] = ms
                    }
                    else -> return null
                }
            }
        } catch (e: NumberFormatException) {
            return null
        }
        return UsageLedger(day ?: return null, p, g)
    }

    // ---- security log: one event per line -------------------------------------------------------

    private fun encodeEvent(e: SecurityEvent): String = listOf(
        "id=${e.id}", "t=${esc(e.type)}", "k=${esc(e.key)}", "sv=${e.severity.name}", "st=${e.status.name}",
        "f=${e.firstAtMs}", "l=${e.lastAtMs}", "c=${e.count}", "m=${esc(e.message)}", "x=${esc(e.technical)}",
        "rd=${if (e.read) 1 else 0}", "rs=${e.resolvedAtMs ?: ""}",
        "r=" + e.repairs.joinToString(",") { "${it.atMs}:${esc(it.action)}:${if (it.succeeded) 1 else 0}" },
        "h=" + e.history.joinToString(",") { "${it.atMs}:${it.status.name}:${esc(it.note)}" },
    ).joinToString(";")

    fun encodeEvents(events: List<SecurityEvent>): String = events.joinToString("\n") { encodeEvent(it) }

    private fun decodeEvent(line: String): SecurityEvent? {
        val f = LinkedHashMap<String, String>()
        for (part in line.split(";")) {
            val i = part.indexOf('=')
            if (i <= 0) return null
            f[part.substring(0, i)] = part.substring(i + 1)
        }
        try {
            fun s(k: String) = unesc(f[k] ?: throw NumberFormatException(k)) ?: throw NumberFormatException(k)
            val repairs = (f["r"] ?: "").let { v ->
                if (v.isEmpty()) emptyList() else v.split(",").map {
                    val p = it.split(":")
                    if (p.size != 3) return null
                    RepairAttempt(p[0].toLong(), unesc(p[1]) ?: return null, p[2] == "1")
                }
            }
            val history = (f["h"] ?: "").let { v ->
                if (v.isEmpty()) emptyList() else v.split(",").map {
                    val p = it.split(":")
                    if (p.size != 3) return null
                    StatusChange(p[0].toLong(), EventStatus.valueOf(p[1]), unesc(p[2]) ?: return null)
                }
            }
            return SecurityEvent(
                id = f.getValue("id").toLong(), type = s("t"), key = s("k"),
                severity = Severity.valueOf(f.getValue("sv")), status = EventStatus.valueOf(f.getValue("st")),
                firstAtMs = f.getValue("f").toLong(), lastAtMs = f.getValue("l").toLong(), count = f.getValue("c").toInt(),
                message = s("m"), technical = s("x"), read = f.getValue("rd") == "1",
                resolvedAtMs = f["rs"]?.takeIf { it.isNotEmpty() }?.toLong(), repairs = repairs, history = history,
            )
        } catch (e: IllegalArgumentException) { // NumberFormatException is one
            return null
        } catch (e: NoSuchElementException) {
            return null
        }
    }

    fun decodeEvents(text: String): List<SecurityEvent>? {
        if (text.isEmpty()) return emptyList()
        return text.split("\n").map { decodeEvent(it) ?: return null }
    }
}
