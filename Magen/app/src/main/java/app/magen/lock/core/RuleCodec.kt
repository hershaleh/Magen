package app.magen.lock.core

/**
 * Text form of [RuleConfig], so it can be stored in one preference value and carried inside a backup
 * ([BackupValue]) without a JSON library. Reading is strict: anything unreadable gives null and the caller keeps
 * the previous configuration instead of guessing.
 */
object RuleCodec {
    // ---- small helpers ---------------------------------------------------------------------------

    internal fun esc(s: String) = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            ';' -> append("\\s")
            ',' -> append("\\c")
            ':' -> append("\\k")
            '|' -> append("\\p")
            '=' -> append("\\e")
            '\n' -> append("\\n")
            else -> append(c)
        }
    }

    internal fun unesc(s: String): String? {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') { sb.append(c); i++; continue }
            if (i + 1 >= s.length) return null
            sb.append(
                when (s[i + 1]) {
                    '\\' -> '\\'; 's' -> ';'; 'c' -> ','; 'k' -> ':'; 'p' -> '|'; 'e' -> '='; 'n' -> '\n'
                    else -> return null
                },
            )
            i += 2
        }
        return sb.toString()
    }

    // ---- schedule: "w:dow,start,end;w:...|e:start,end,1;..." ------------------------------------

    fun encodeSchedule(s: BlockSchedule): String {
        val w = s.windows.joinToString(";") { "${it.dayOfWeek},${it.startMin},${it.endMin}" }
        val e = s.exceptions.joinToString(";") { "${it.startIndex},${it.endIndex},${if (it.block) 1 else 0}" }
        return "$w|$e"
    }

    fun decodeSchedule(text: String): BlockSchedule? {
        val parts = text.split("|")
        if (parts.size != 2) return null
        return try {
            val windows = if (parts[0].isEmpty()) emptyList() else parts[0].split(";").map {
                val p = it.split(",")
                if (p.size != 3) return null
                BlockSchedule.Window(p[0].toInt(), p[1].toInt(), p[2].toInt())
            }
            val exceptions = if (parts[1].isEmpty()) emptyList() else parts[1].split(";").map {
                val p = it.split(",")
                if (p.size != 3) return null
                BlockSchedule.DateException(p[0].toLong(), p[1].toLong(), when (p[2]) { "1" -> true; "0" -> false; else -> return null })
            }
            BlockSchedule(windows, exceptions)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // ---- quota: "u=30;d=2:10,5:0;p=1;b=<schedule escaped>" ------------------------------------

    fun encodeQuota(q: QuotaDef): String {
        val parts = ArrayList<String>()
        q.plan.uniformMin?.let { parts += "u=$it" }
        if (q.plan.perDayMin.isNotEmpty()) parts += "d=" + q.plan.perDayMin.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }
        parts += "p=${if (q.paused) 1 else 0}"
        if (!q.breaks.isEmpty) parts += "b=" + esc(encodeSchedule(q.breaks))
        return parts.joinToString(";")
    }

    fun decodeQuota(text: String): QuotaDef? {
        var uniform: Int? = null
        val perDay = HashMap<Int, Int>()
        var paused = false
        var breaks = BlockSchedule()
        try {
            for (part in text.split(";")) {
                val kv = part.split("=", limit = 2)
                if (kv.size != 2) return null
                when (kv[0]) {
                    "u" -> uniform = kv[1].toInt()
                    "d" -> for (item in kv[1].split(",")) {
                        val p = item.split(":")
                        if (p.size != 2) return null
                        perDay[p[0].toInt()] = p[1].toInt()
                    }
                    "p" -> paused = when (kv[1]) { "1" -> true; "0" -> false; else -> return null }
                    "b" -> breaks = decodeSchedule(unesc(kv[1]) ?: return null) ?: return null
                    else -> return null
                }
            }
            return QuotaDef(QuotaPlan(uniform, perDay), paused, breaks)
        } catch (e: IllegalArgumentException) {
            return null
        }
    }

    // ---- whole configuration ---------------------------------------------------------------------

    private const val VERSION = "1"

    fun encode(c: RuleConfig): String {
        val lines = ArrayList<String>()
        lines += "v=$VERSION"
        for ((pkg, r) in c.apps.toSortedMap()) {
            lines += buildString {
                append("app=").append(esc(pkg))
                r.schedule?.takeIf { !it.isEmpty }?.let { append(";s=").append(esc(encodeSchedule(it))) }
                r.quota?.let { append(";q=").append(esc(encodeQuota(it))) }
                r.groupId?.let { append(";g=").append(esc(it)) }
            }
        }
        for ((id, g) in c.groups.toSortedMap()) lines += "group=${esc(id)};n=${esc(g.name)};q=${esc(encodeQuota(g.quota))}"
        val s = c.shabbat
        lines += buildString {
            append("shabbat=").append(if (s.enabled) 1 else 0)
            append(";il=").append(if (s.settings.inIsrael) 1 else 0)
            append(";two=").append(if (s.settings.includeSecondDay) 1 else 0)
            append(";candle=").append(s.settings.candleLightingMin)
            append(";bb=").append(s.settings.bufferBeforeMin)
            append(";ba=").append(s.settings.bufferAfterMin)
            append(";dep=").append(Math.round(s.settings.twilightDepressionDeg * 10))
            append(";warn=").append(s.warnMin)
            s.latitude?.let { append(";lat=").append(it) }
            s.longitude?.let { append(";lon=").append(it) }
        }
        return lines.joinToString("\n")
    }

    /** Fields of one line: "key=value;key=value", values escaped. Null when a field has no "=". */
    private fun fields(line: String): Map<String, String>? {
        val m = LinkedHashMap<String, String>()
        for (part in line.split(";")) {
            val i = part.indexOf('=')
            if (i <= 0) return null
            m[part.substring(0, i)] = part.substring(i + 1)
        }
        return m
    }

    fun decode(text: String): RuleConfig? {
        if (text.isEmpty()) return RuleConfig()
        val lines = text.split("\n")
        if (lines.firstOrNull() != "v=$VERSION") return null
        val apps = LinkedHashMap<String, AppRule>()
        val groups = LinkedHashMap<String, GroupRule>()
        var shabbat = ShabbatConfig()
        try {
            for (line in lines.drop(1)) {
                val f = fields(line) ?: return null
                when {
                    "app" in f -> {
                        val pkg = unesc(f.getValue("app")) ?: return null
                        val sched = f["s"]?.let { decodeSchedule(unesc(it) ?: return null) ?: return null }
                        val quota = f["q"]?.let { decodeQuota(unesc(it) ?: return null) ?: return null }
                        val gid = f["g"]?.let { unesc(it) ?: return null }
                        if (pkg in apps) return null
                        apps[pkg] = AppRule(sched, quota, gid)
                    }
                    "group" in f -> {
                        val id = unesc(f.getValue("group")) ?: return null
                        val name = unesc(f["n"] ?: return null) ?: return null
                        val quota = decodeQuota(unesc(f["q"] ?: return null) ?: return null) ?: return null
                        if (id in groups) return null
                        groups[id] = GroupRule(name, quota)
                    }
                    "shabbat" in f -> {
                        fun flag(k: String): Boolean = when (f[k]) {
                            "1" -> true
                            "0" -> false
                            else -> throw NumberFormatException("flag $k")
                        }
                        shabbat = ShabbatConfig(
                            enabled = flag("shabbat"),
                            settings = JewishTimes.Settings(
                                inIsrael = flag("il"), includeSecondDay = flag("two"),
                                candleLightingMin = f["candle"]!!.toInt(), bufferBeforeMin = f["bb"]!!.toInt(),
                                twilightDepressionDeg = f["dep"]!!.toLong() / 10.0, bufferAfterMin = f["ba"]!!.toInt(),
                            ),
                            latitude = f["lat"]?.toDouble(), longitude = f["lon"]?.toDouble(), warnMin = f["warn"]!!.toInt(),
                        )
                    }
                    else -> return null
                }
            }
        } catch (e: NumberFormatException) {
            return null
        } catch (e: NullPointerException) {
            return null
        }
        // Membership must point at an existing group, and no app may be in two (it cannot be by construction).
        if (apps.values.any { it.groupId != null && it.groupId !in groups }) return null
        return RuleConfig(apps, groups, shabbat)
    }
}
