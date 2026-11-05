package app.magen.lock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.magen.lock.Backups
import app.magen.lock.R
import app.magen.lock.RuleRuntime
import app.magen.lock.RuleStore
import app.magen.lock.core.AppRule
import app.magen.lock.core.EventStatus
import app.magen.lock.core.GroupRule
import app.magen.lock.core.JewishTimes
import app.magen.lock.core.LogFilter
import app.magen.lock.core.QuotaDef
import app.magen.lock.core.QuotaPlan
import app.magen.lock.core.SecurityEvent
import app.magen.lock.core.SecurityLog
import app.magen.lock.core.Severity
import app.magen.lock.core.ShabbatConfig
import java.text.DateFormat
import java.util.Date

private enum class Page { HUB, APPS, GROUPS, SHABBAT, LOG, BACKUP }

/** Entry screen of the time rules (spec sections 6 to 12). Every page behind it is plain additive UI over [RuleStore]. */
@Composable
fun RulesScreen(onClose: () -> Unit) {
    var page by remember { mutableStateOf(Page.HUB) }
    BackHandler { if (page == Page.HUB) onClose() else page = Page.HUB }
    TabColumn {
        TextButton(onClick = { if (page == Page.HUB) onClose() else page = Page.HUB }) { Text(stringResource(R.string.rules_back)) }
        Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (RuleStore.readOnly) Text(stringResource(R.string.rules_readonly), color = MaterialTheme.colorScheme.error)
        when (page) {
            Page.HUB -> {
                val unread = SecurityLog.unreadCount(RuleStore.events)
                HubRow(stringResource(R.string.rules_apps)) { page = Page.APPS }
                HubRow(stringResource(R.string.rules_groups)) { page = Page.GROUPS }
                HubRow(stringResource(R.string.rules_shabbat)) { page = Page.SHABBAT }
                HubRow(stringResource(R.string.rules_log) + if (unread > 0) " ($unread)" else "") { page = Page.LOG }
                HubRow(stringResource(R.string.rules_backup)) { page = Page.BACKUP }
            }
            Page.APPS -> AppsRules()
            Page.GROUPS -> GroupsRules()
            Page.SHABBAT -> ShabbatRules()
            Page.LOG -> LogPage()
            Page.BACKUP -> BackupPage()
        }
    }
}

@Composable
private fun HubRow(text: String, onClick: () -> Unit) {
    SectionCard(text) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
    }
}

// ---- per-app rules -------------------------------------------------------------------------------

@Composable
private fun AppsRules() {
    val ctx = LocalContext.current
    val apps = remember { launchableApps(ctx) }
    var editing by remember { mutableStateOf<PickApp?>(null) }
    val e = editing
    if (e != null) {
        AppRuleEditor(e) { editing = null }
        return
    }
    Text(stringResource(R.string.rules_pick_app), style = MaterialTheme.typography.titleSmall)
    for (a in apps) {
        val has = RuleStore.config.apps[a.pkg]?.isEmpty == false
        Row(Modifier.fillMaxWidth().clickable { editing = a }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.label, Modifier.weight(1f))
            Text(stringResource(if (has) R.string.rules_has_rule else R.string.rules_no_rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}

@Composable
private fun AppRuleEditor(app: PickApp, onDone: () -> Unit) {
    val old = RuleStore.config.apps[app.pkg]
    val sched = remember { ScheduleForm(old?.schedule) }
    val quota = remember { QuotaForm(old?.quota) }
    var refused by remember { mutableStateOf(false) }
    BackHandler(onBack = onDone)
    Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    SectionCard(stringResource(R.string.sched_windows)) { sched.Content() }
    SectionCard(stringResource(R.string.quota_on)) { quota.Content() }
    if (refused) Text(stringResource(R.string.rules_refused), color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val ok = RuleStore.updateConfig { it.withApp(app.pkg, AppRule(sched.toSchedule(), quota.toDef(), old?.groupId)) }
            if (ok) onDone() else refused = true
        }, enabled = !RuleStore.readOnly) { Text(stringResource(R.string.rules_save)) }
        OutlinedButton(onClick = onDone) { Text(stringResource(R.string.rules_cancel)) }
    }
}

// ---- shared limits -------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupsRules() {
    val ctx = LocalContext.current
    val apps = remember { launchableApps(ctx) }
    var editId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val id = editId
    if (creating || id != null) {
        val g = id?.let { RuleStore.config.groups[it] }
        val gid = id ?: remember { "g" + System.currentTimeMillis() }
        var name by remember { mutableStateOf(g?.name ?: "") }
        val quota = remember { QuotaForm(g?.quota ?: QuotaDef(QuotaPlan(60, emptyMap()))) }
        val members = remember { mutableStateOf(RuleStore.config.apps.filterValues { it.groupId == gid }.keys) }
        var refused by remember { mutableStateOf(false) }
        val close = { creating = false; editId = null }
        BackHandler(onBack = close)
        TextBox(stringResource(R.string.group_name), name, { name = it }, Modifier.fillMaxWidth())
        SectionCard(stringResource(R.string.quota_on)) { quota.Content() }
        SectionCard(stringResource(R.string.group_members)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in apps) FilterChip(
                    selected = a.pkg in members.value,
                    onClick = { members.value = if (a.pkg in members.value) members.value - a.pkg else members.value + a.pkg },
                    label = { Text(a.label) },
                )
            }
        }
        if (refused) Text(stringResource(R.string.rules_refused), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !RuleStore.readOnly && name.isNotBlank() && quota.toDef() != null, onClick = {
                val def = quota.toDef() ?: return@Button
                val ok = RuleStore.updateConfig { c ->
                    var n = c.withGroup(gid, GroupRule(name.trim(), def))
                    for (p in apps.map { it.pkg }) {
                        val inGroup = p in members.value
                        val cur = n.apps[p]?.groupId
                        if (inGroup && cur != gid) n = n.withGroupMembership(p, gid)
                        if (!inGroup && cur == gid) n = n.withGroupMembership(p, null)
                    }
                    n
                }
                if (ok) close() else refused = true
            }) { Text(stringResource(R.string.rules_save)) }
            OutlinedButton(onClick = close) { Text(stringResource(R.string.rules_cancel)) }
            if (id != null) TextButton(enabled = !RuleStore.readOnly, onClick = {
                if (RuleStore.updateConfig { it.withoutGroup(id) }) close()
            }) { Text(stringResource(R.string.group_delete)) }
        }
        return
    }
    Button(onClick = { creating = true }, enabled = !RuleStore.readOnly) { Text(stringResource(R.string.group_new)) }
    for ((gid, g) in RuleStore.config.groups) {
        Row(Modifier.fillMaxWidth().clickable { editId = gid }.padding(vertical = 10.dp)) { Text(g.name, Modifier.weight(1f)) }
        HorizontalDivider()
    }
}

// ---- Shabbat -------------------------------------------------------------------------------------

private class City(val name: String, val lat: Double, val lon: Double)

private val CITIES = listOf(
    City("ירושלים / Jerusalem", 31.7683, 35.2137), City("תל אביב / Tel Aviv", 32.0853, 34.7818),
    City("חיפה / Haifa", 32.7940, 34.9896), City("באר שבע / Beer Sheva", 31.2518, 34.7913),
    City("בני ברק / Bnei Brak", 32.0840, 34.8338), City("אילת / Eilat", 29.5577, 34.9519),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShabbatRules() {
    val c = RuleStore.config.shabbat
    val s = c.settings
    var enabled by remember { mutableStateOf(c.enabled) }
    var israel by remember { mutableStateOf(s.inIsrael) }
    var second by remember { mutableStateOf(s.includeSecondDay) }
    var candle by remember { mutableStateOf(s.candleLightingMin.toString()) }
    var before by remember { mutableStateOf(s.bufferBeforeMin.toString()) }
    var after by remember { mutableStateOf(s.bufferAfterMin.toString()) }
    var twilight by remember { mutableStateOf(s.twilightDepressionDeg.toString()) }
    var warn by remember { mutableStateOf(c.warnMin.toString()) }
    var lat by remember { mutableStateOf(c.latitude?.toString() ?: "") }
    var lon by remember { mutableStateOf(c.longitude?.toString() ?: "") }
    var refused by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    if (RuleRuntime.shabbatActive) Text(stringResource(R.string.shabbat_active), fontWeight = FontWeight.Bold)
    if (RuleRuntime.shabbatUnverified) Text(stringResource(R.string.shabbat_unverified), color = MaterialTheme.colorScheme.error)
    SwitchRow(stringResource(R.string.shabbat_on), enabled) { enabled = it }
    SwitchRow(stringResource(R.string.shabbat_israel), israel) { israel = it }
    SwitchRow(stringResource(R.string.shabbat_second), second) { second = it }
    NumField(stringResource(R.string.shabbat_candle), candle, { candle = it }, Modifier.fillMaxWidth())
    NumField(stringResource(R.string.shabbat_buffer_before), before, { before = it }, Modifier.fillMaxWidth())
    NumField(stringResource(R.string.shabbat_buffer_after), after, { after = it }, Modifier.fillMaxWidth())
    TextBox(stringResource(R.string.shabbat_twilight), twilight, { twilight = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, Modifier.fillMaxWidth())
    NumField(stringResource(R.string.shabbat_warn), warn, { warn = it }, Modifier.fillMaxWidth())
    Text(stringResource(R.string.shabbat_place_hint), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.shabbat_city), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (city in CITIES) FilterChip(selected = false, onClick = { lat = city.lat.toString(); lon = city.lon.toString() }, label = { Text(city.name) })
    }
    TextBox(stringResource(R.string.shabbat_lat), lat, { lat = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }.take(10) }, Modifier.fillMaxWidth())
    TextBox(stringResource(R.string.shabbat_lon), lon, { lon = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }.take(10) }, Modifier.fillMaxWidth())
    val latV = lat.toDoubleOrNull()?.takeIf { it in -66.0..66.0 }
    val lonV = lon.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    if (refused) Text(stringResource(R.string.rules_refused), color = MaterialTheme.colorScheme.error)
    if (saved) Text(stringResource(R.string.rules_saved))
    Button(enabled = !RuleStore.readOnly, onClick = {
        val settings = JewishTimes.Settings(
            inIsrael = israel, includeSecondDay = second,
            candleLightingMin = (candle.toIntOrNull() ?: 18).coerceIn(0, 120),
            bufferBeforeMin = (before.toIntOrNull() ?: 0).coerceIn(0, 120),
            twilightDepressionDeg = (twilight.toDoubleOrNull() ?: 8.5).coerceIn(3.0, 12.0),
            bufferAfterMin = (after.toIntOrNull() ?: 0).coerceIn(0, 120),
        )
        val cfg = ShabbatConfig(enabled, settings, latV, if (latV == null) null else lonV, (warn.toIntOrNull() ?: 30).coerceIn(0, 240))
        val ok = RuleStore.updateConfig { it.withShabbat(cfg) }
        refused = !ok; saved = ok
    }) { Text(stringResource(R.string.rules_save)) }
}

// ---- security log --------------------------------------------------------------------------------

@Composable
private fun sevLabel(s: Severity) = stringResource(
    when (s) { Severity.CRITICAL -> R.string.sev_critical; Severity.HIGH -> R.string.sev_high; Severity.MEDIUM -> R.string.sev_medium; Severity.LOW -> R.string.sev_low },
)

@Composable
private fun stLabel(s: EventStatus) = stringResource(
    when (s) { EventStatus.ACTIVE -> R.string.st_active; EventStatus.IN_PROGRESS -> R.string.st_in_progress; EventStatus.RESOLVED -> R.string.st_resolved; EventStatus.UNRESOLVED -> R.string.st_unresolved },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogPage() {
    var sev by remember { mutableStateOf<Severity?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var retention by remember { mutableStateOf(RuleStore.retentionDays?.toString() ?: "") }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    val shown = SecurityLog.sortedNewestFirst(
        SecurityLog.filter(RuleStore.events, LogFilter(severities = sev?.let { setOf(it) }, query = query.ifBlank { null }, unreadOnly = unreadOnly)),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = sev == null, onClick = { sev = null }, label = { Text(stringResource(R.string.log_filter_all)) })
        for (s in Severity.values()) FilterChip(selected = sev == s, onClick = { sev = s }, label = { Text(sevLabel(s)) })
        FilterChip(selected = unreadOnly, onClick = { unreadOnly = !unreadOnly }, label = { Text(stringResource(R.string.log_unread)) })
    }
    TextBox(stringResource(R.string.log_search), query, { query = it }, Modifier.fillMaxWidth())
    if (shown.isEmpty()) Text(stringResource(R.string.log_empty))
    for (e in shown) {
        SectionCard(e.message.ifBlank { e.type }, subtitle = sevLabel(e.severity) + " · " + stLabel(e.status) + " · " + fmt.format(Date(e.lastAtMs))) {
            if (e.count > 1) Text(stringResource(R.string.log_count, e.count), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = {
                    open = if (open == e.id) null else e.id
                    RuleStore.mutateEvents { SecurityLog.markRead(it, e.id) }
                }) { Text(stringResource(R.string.log_details)) }
            }
            if (open == e.id) Text(e.technical.ifBlank { e.type + " / " + e.key }, style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { RuleStore.mutateEvents { SecurityLog.markAll(it, true) } }) { Text(stringResource(R.string.log_mark_all)) }
        OutlinedButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.log_clear)) }
    }
    NumField(stringResource(R.string.log_retention), retention, {
        retention = it
        RuleStore.retentionDays = it.toIntOrNull()?.takeIf { d -> d > 0 }
    }, Modifier.fillMaxWidth())
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        text = { Text(stringResource(R.string.log_clear_confirm)) },
        confirmButton = { TextButton(onClick = { RuleStore.mutateEvents { SecurityLog.clear(it) }; confirmClear = false }) { Text(stringResource(R.string.log_clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.rules_cancel)) } },
    )
}

// ---- backup --------------------------------------------------------------------------------------

@Composable
private fun BackupPage() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var plan by remember { mutableStateOf<Backups.Plan?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pasted by remember { mutableStateOf("") }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val invalid = stringResource(R.string.backup_invalid)
    val done = stringResource(R.string.backup_done)
    val refused = stringResource(R.string.rules_refused)

    Text(stringResource(R.string.backup_note), style = MaterialTheme.typography.bodySmall)
    Button(onClick = { Backups.create(ctx, "manual"); tick++ }) { Text(stringResource(R.string.backup_create)) }
    msg?.let { Text(it) }
    Text(stringResource(R.string.backup_list), style = MaterialTheme.typography.titleSmall)
    val items = remember(tick) { Backups.list(ctx) }
    if (items.isEmpty()) Text(stringResource(R.string.backup_none))
    for (b in items) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.format(Date(b.modifiedMs)), Modifier.weight(1f))
            TextButton(onClick = {
                plan = runCatching { Backups.plan(ctx, b.file.readText()) }.getOrElse { Backups.Plan.Invalid(app.magen.lock.core.BackupFormat.Reason.DAMAGED) }
            }) { Text(stringResource(R.string.backup_restore)) }
        }
    }
    Spacer(Modifier.height(8.dp))
    TextBox(stringResource(R.string.backup_paste), pasted, { pasted = it }, Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pasted = Backups.export(ctx) }) { Text(stringResource(R.string.backup_export)) }
        OutlinedButton(enabled = pasted.isNotBlank(), onClick = { plan = Backups.plan(ctx, pasted) }) { Text(stringResource(R.string.backup_from_text)) }
    }

    when (val p = plan) {
        null -> Unit
        is Backups.Plan.Invalid -> AlertDialog(
            onDismissRequest = { plan = null },
            text = { Text(invalid) },
            confirmButton = { TextButton(onClick = { plan = null }) { Text(stringResource(R.string.rules_back)) } },
        )
        is Backups.Plan.Ready -> AlertDialog(
            onDismissRequest = { plan = null },
            title = { Text(stringResource(R.string.backup_changes, p.changes.size)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (p.weakening.isNotEmpty()) {
                        Text(stringResource(R.string.backup_weaker), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        for (w in p.weakening.take(12)) Text("• " + w.key, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    msg = if (Backups.restore(ctx, p)) done else refused
                    plan = null
                }) { Text(stringResource(if (p.weakening.isEmpty()) R.string.backup_restore else R.string.backup_confirm)) }
            },
            dismissButton = { TextButton(onClick = { plan = null }) { Text(stringResource(R.string.rules_cancel)) } },
        )
    }
}
