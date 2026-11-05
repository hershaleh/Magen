package app.magen.lock.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.magen.lock.R
import app.magen.lock.core.BlockSchedule
import app.magen.lock.core.CivilDate
import app.magen.lock.core.QuotaDef
import app.magen.lock.core.QuotaPlan
import java.text.DateFormatSymbols
import java.util.Locale

// ---- shared helpers ------------------------------------------------------------------------------

internal class PickApp(val pkg: String, val label: String)

/** Launchable apps other than Magen, sorted by name. */
internal fun launchableApps(ctx: Context): List<PickApp> {
    val pm = ctx.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { PickApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .sortedBy { it.label.lowercase() }
}

/** Short weekday name for [dow] (0 = Sunday) in the current language. */
internal fun dayName(dow: Int): String = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays[dow + 1]

internal fun fmtTime(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

/** "H:MM" or "HH:MM" to minutes. "24:00" is accepted when [allow24] is true. */
internal fun parseTime(s: String, allow24: Boolean = false): Int? {
    val p = s.trim().split(":")
    if (p.size != 2) return null
    val h = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    if (m !in 0..59 || h < 0) return null
    val total = h * 60 + m
    return if (total in 0..1439 || (allow24 && total == 1440)) total else null
}

/** "YYYY-MM-DD" to an epoch day, or null when it is not a real date. */
internal fun parseDate(s: String): Long? {
    val p = s.trim().split("-")
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    if (y !in 1970..2200 || m !in 1..12 || d !in 1..31) return null
    val e = CivilDate.toEpochDay(y, m, d)
    val (yy, mm, dd) = CivilDate.fromEpochDay(e)
    return if (yy == y && mm == m && dd == d) e else null
}

internal fun fmtDate(epochDay: Long): String {
    val (y, m, d) = CivilDate.fromEpochDay(epochDay)
    return "%04d-%02d-%02d".format(y, m, d)
}

@Composable
internal fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, error: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
internal fun TextBox(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, error: Boolean = false, hint: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (hint != null) { { Text(hint) } } else null,
        singleLine = true,
        isError = error,
        modifier = modifier,
    )
}

@Composable
internal fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---- schedule form -------------------------------------------------------------------------------

/** Edits a [BlockSchedule]: weekly windows (also across midnight) and exceptions for dates. */
@OptIn(ExperimentalLayoutApi::class)
internal class ScheduleForm(initial: BlockSchedule?) {
    val windows = mutableStateListOf<BlockSchedule.Window>().apply { initial?.windows?.let { addAll(it) } }
    val exceptions = mutableStateListOf<BlockSchedule.DateException>().apply { initial?.exceptions?.let { addAll(it) } }

    private var day by mutableStateOf(0)
    private var start by mutableStateOf("22:00")
    private var end by mutableStateOf("06:00")
    private var exStartDate by mutableStateOf("")
    private var exStartTime by mutableStateOf("00:00")
    private var exEndDate by mutableStateOf("")
    private var exEndTime by mutableStateOf("23:59")
    private var exBlock by mutableStateOf(true)

    fun toSchedule(): BlockSchedule? = if (windows.isEmpty() && exceptions.isEmpty()) null else BlockSchedule(windows.toList(), exceptions.toList())

    @Composable
    fun Content() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sched_windows), style = MaterialTheme.typography.titleSmall)
            if (windows.isEmpty()) Text(stringResource(R.string.sched_none), style = MaterialTheme.typography.bodySmall)
            windows.sortedWith(compareBy({ it.dayOfWeek }, { it.startMin })).forEach { w ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    val cross = w.endMin <= w.startMin
                    Text(
                        "${dayName(w.dayOfWeek)} ${fmtTime(w.startMin)}–${if (w.endMin == 1440) "24:00" else fmtTime(w.endMin)}" +
                            if (cross && w.lengthMin > 0) " ${stringResource(R.string.sched_next_day)}" else "",
                        Modifier.weight(1f),
                    )
                    TextButton(onClick = { windows.remove(w) }) { Text(stringResource(R.string.remove)) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in 0..6) FilterChip(selected = day == d, onClick = { day = d }, label = { Text(dayName(d)) })
            }
            val s = parseTime(start)
            val e = parseTime(end, allow24 = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextBox(stringResource(R.string.sched_from), start, { start = it }, Modifier.weight(1f), error = s == null, hint = "HH:MM")
                TextBox(stringResource(R.string.sched_to), end, { end = it }, Modifier.weight(1f), error = e == null, hint = "HH:MM")
            }
            Text(stringResource(R.string.sched_cross_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(
                enabled = s != null && e != null && s != e,
                onClick = { windows.add(BlockSchedule.Window(day, s!!, e!!)) },
            ) { Text(stringResource(R.string.sched_add_window)) }

            Text(stringResource(R.string.sched_exceptions), style = MaterialTheme.typography.titleSmall)
            exceptions.toList().forEach { x ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    val a = x.startIndex
                    val b = x.endIndex
                    Text(
                        (if (x.block) stringResource(R.string.sched_ex_block) else stringResource(R.string.sched_ex_allow)) + ": " +
                            "${fmtDate(Math.floorDiv(a, 1440L))} ${fmtTime(Math.floorMod(a, 1440L).toInt())} → " +
                            "${fmtDate(Math.floorDiv(b, 1440L))} ${fmtTime(Math.floorMod(b, 1440L).toInt())}",
                        Modifier.weight(1f),
                    )
                    TextButton(onClick = { exceptions.remove(x) }) { Text(stringResource(R.string.remove)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextBox(stringResource(R.string.sched_ex_from_date), exStartDate, { exStartDate = it }, Modifier.weight(1f), error = exStartDate.isNotEmpty() && parseDate(exStartDate) == null, hint = "YYYY-MM-DD")
                TextBox(stringResource(R.string.sched_from), exStartTime, { exStartTime = it }, Modifier.weight(1f), error = parseTime(exStartTime) == null, hint = "HH:MM")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextBox(stringResource(R.string.sched_ex_to_date), exEndDate, { exEndDate = it }, Modifier.weight(1f), error = exEndDate.isNotEmpty() && parseDate(exEndDate) == null, hint = "YYYY-MM-DD")
                TextBox(stringResource(R.string.sched_to), exEndTime, { exEndTime = it }, Modifier.weight(1f), error = parseTime(exEndTime) == null, hint = "HH:MM")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = exBlock, onClick = { exBlock = true }, label = { Text(stringResource(R.string.sched_ex_block)) })
                FilterChip(selected = !exBlock, onClick = { exBlock = false }, label = { Text(stringResource(R.string.sched_ex_allow)) })
            }
            val d1 = parseDate(exStartDate)
            val d2 = parseDate(exEndDate)
            val t1 = parseTime(exStartTime)
            val t2 = parseTime(exEndTime)
            val ok = d1 != null && d2 != null && t1 != null && t2 != null &&
                d2 * 1440 + t2 > d1 * 1440 + t1
            TextButton(
                enabled = ok,
                onClick = { exceptions.add(BlockSchedule.DateException(d1!! * 1440 + t1!!, d2!! * 1440 + t2!!, exBlock)) },
            ) { Text(stringResource(R.string.sched_add_exception)) }
        }
    }
}

// ---- quota form ----------------------------------------------------------------------------------

/** Edits a [QuotaDef]: one value for every day or one per weekday, pause, and scheduled breaks. */
internal class QuotaForm(initial: QuotaDef?) {
    var enabled by mutableStateOf(initial != null)
    var uniform by mutableStateOf(initial?.plan?.uniformMin?.toString() ?: "")
    var perDay by mutableStateOf(initial?.plan?.perDayMin?.isNotEmpty() == true)
    val days = mutableStateListOf<String>().apply { for (d in 0..6) add(initial?.plan?.perDayMin?.get(d)?.toString() ?: "") }
    var paused by mutableStateOf(initial?.paused ?: false)
    val breaks = ScheduleForm(initial?.breaks)

    /** The quota to save, or null when the quota is switched off. */
    fun toDef(): QuotaDef? {
        if (!enabled) return null
        val u = if (perDay) null else uniform.toIntOrNull()
        val per = if (perDay) (0..6).mapNotNull { d -> days[d].toIntOrNull()?.let { d to it } }.toMap() else emptyMap()
        return QuotaDef(QuotaPlan(u, per), paused, breaks.toSchedule() ?: BlockSchedule())
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun Content() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwitchRow(stringResource(R.string.quota_on), enabled) { enabled = it }
            if (enabled) {
                SwitchRow(stringResource(R.string.quota_per_day), perDay) { perDay = it }
                if (!perDay) {
                    NumField(stringResource(R.string.quota_minutes), uniform, { uniform = it }, Modifier.fillMaxWidth())
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (d in 0..6) NumField(dayName(d), days[d], { days[d] = it }, Modifier.fillMaxWidth(0.3f))
                    }
                }
                Text(stringResource(R.string.quota_zero_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow(stringResource(R.string.quota_paused), paused) { paused = it }
                Text(stringResource(R.string.quota_breaks), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.quota_breaks_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                breaks.Content()
            }
        }
    }
}
