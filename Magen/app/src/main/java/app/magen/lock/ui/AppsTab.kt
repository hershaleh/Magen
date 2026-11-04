package app.magen.lock.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.magen.lock.LockMode
import app.magen.lock.ScheduleAction
import app.magen.lock.WeeklyAppWindow
import app.magen.lock.UsageTracker
import app.magen.lock.R
import app.magen.lock.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---- tab: apps -------------------------------------------------------------------------------

private class AppItem(val pkg: String, val label: String, val icon: ImageBitmap)

private fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map {
            AppItem(
                it.activityInfo.packageName,
                it.loadLabel(pm).toString(),
                it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

private const val FILTER_ALL = 0
private const val FILTER_SELECTED = 1
private const val FILTER_UNSELECTED = 2

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppsTab() {
    val ctx = LocalContext.current
    // null while the list is still loading, so "no apps" is not shown by mistake.
    val loaded by produceState<List<AppItem>?>(null) { value = withContext(Dispatchers.Default) { loadApps(ctx) } }
    val apps = loaded
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    var bulk by remember { mutableStateOf<Int?>(null) } // 1 = select shown, 2 = deselect shown
    var selectedApp by remember { mutableStateOf<AppItem?>(null) }

    if (selectedApp != null) {
        AppSettingsScreen(selectedApp!!, onBack = { selectedApp = null })
        return
    }

    // Search and filter only decide what is displayed; they never touch the saved lock list.
    val locked = Store.locked
    val query = q.trim()
    val shown = remember(apps, query, filter, locked) {
        (apps ?: emptyList()).filter { a ->
            (query.isEmpty() || a.label.contains(query, ignoreCase = true) || a.pkg.contains(query, ignoreCase = true)) &&
                when (filter) {
                    FILTER_SELECTED -> a.pkg in locked
                    FILTER_UNSELECTED -> a.pkg !in locked
                    else -> true
                }
        }
    }
    val total = apps?.size ?: 0
    val selectedCount = apps?.count { it.pkg in locked } ?: 0
    val toSelect = shown.filter { it.pkg !in locked }
    val toDeselect = shown.filter { it.pkg in locked }

    Column(Modifier.fillMaxSize().padding(horizontal = Sp.l)) {
        Spacer(Modifier.height(Sp.l))
        Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.apps_summary, selectedCount, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.apps_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (q.isNotEmpty()) {
                { IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.search_clear)) } }
            } else {
                null
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = Sp.m),
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Sp.s),
            modifier = Modifier.padding(vertical = Sp.s),
        ) {
            listOf(
                FILTER_ALL to R.string.filter_all,
                FILTER_SELECTED to R.string.filter_selected,
                FILTER_UNSELECTED to R.string.filter_unselected,
            ).forEach { (id, label) ->
                FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(stringResource(label)) })
            }
        }
        // Bulk actions always sit here, above the list, and only change the apps that are shown.
        if (apps != null && shown.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = Sp.s), horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                OutlineButton(
                    stringResource(R.string.bulk_select, toSelect.size),
                    onClick = { bulk = 1 },
                    modifier = Modifier.weight(1f),
                    enabled = toSelect.isNotEmpty(),
                )
                OutlineButton(
                    stringResource(R.string.bulk_deselect, toDeselect.size),
                    onClick = { bulk = 2 },
                    modifier = Modifier.weight(1f),
                    enabled = toDeselect.isNotEmpty(),
                )
            }
        }
        when {
            apps == null -> Box(Modifier.fillMaxWidth().padding(Sp.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            apps.isEmpty() -> EmptyState(stringResource(R.string.apps_none))
            shown.isEmpty() -> EmptyState(stringResource(R.string.apps_no_results)) {
                OutlineButton(
                    stringResource(R.string.apps_clear_search),
                    onClick = {
                        q = ""
                        filter = FILTER_ALL
                    },
                )
            }
            else -> Surface(
                Modifier.weight(1f).fillMaxWidth().padding(bottom = Sp.l),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                LazyColumn {
                    itemsIndexed(shown, key = { _, item -> item.pkg }) { i, item ->
                        Column {
                            AppRow(item, showPkg = query.isNotEmpty(), onOpen = { selectedApp = item })
                            if (i < shown.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
    }

    val action = bulk
    if (action != null) {
        // Only the apps in the current results are changed; hidden apps and their modes stay as they are.
        val targets = if (action == 1) toSelect else toDeselect
        MagenDialog(
            title = stringResource(R.string.bulk_confirm_title),
            onDismiss = { bulk = null },
            confirmText = stringResource(R.string.confirm),
            onConfirm = {
                bulk = null
                targets.forEach { a -> Store.setMode(a.pkg, if (action == 1) LockMode.PIN else null) }
            },
            dismissText = stringResource(R.string.cancel),
        ) {
            DialogText(stringResource(if (action == 1) R.string.bulk_select_msg else R.string.bulk_deselect_msg, targets.size))
        }
    }
}

@Composable
private fun AppRow(a: AppItem, showPkg: Boolean, onOpen: () -> Unit) {
    val mode = Store.locked[a.pkg]
    val released = a.pkg in Store.mainUnlocked
    // A selected app is tinted, so the list can be scanned at a glance.
    val tint = if (mode != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(tint)
            .clickable(onClick = onOpen)
            .heightIn(min = 56.dp)
            .padding(horizontal = Sp.m, vertical = Sp.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sp.m),
    ) {
        Image(a.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f)) {
            Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (showPkg) {
                Text(
                    a.pkg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Package names are always left-to-right, also in the Hebrew layout.
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ModePill(mode)
        if (mode == LockMode.MAIN) {
            IconButton(onClick = { Store.toggleMainUnlocked(a.pkg) }) {
                Icon(
                    if (released) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    stringResource(if (released) R.string.release_on else R.string.release_off),
                    tint = if (released) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}


@Composable
private fun AppSettingsScreen(a: AppItem, onBack: () -> Unit) {
    var tab by rememberSaveable(a.pkg) { mutableIntStateOf(0) }
    val mode = Store.locked[a.pkg]
    Column(Modifier.fillMaxSize().padding(horizontal = Sp.l)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Sp.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sp.m),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
            Image(a.icon, null, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
            Text(a.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.app_tab_method)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.app_tab_more)) })
        }
        if (tab == 0) {
            Column(Modifier.weight(1f).padding(vertical = Sp.m), verticalArrangement = Arrangement.spacedBy(Sp.m)) {
                Text(stringResource(R.string.app_method_help), style = MaterialTheme.typography.bodyMedium)
                listOf(LockMode.MAIN, LockMode.PIN).forEach { choice ->
                    val selected = mode == choice
                    Button(
                        onClick = { Store.setMode(a.pkg, choice) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !selected,
                    ) {
                        Text(stringResource(if (choice == LockMode.MAIN) R.string.mode_main else R.string.mode_pin) +
                            if (selected) "  ✓" else "")
                    }
                }
                OutlinedButton(
                    onClick = { Store.setMode(a.pkg, null) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = mode != null,
                ) { Text(stringResource(R.string.app_unblock)) }
            }
        } else {
            AppScheduleEditor(a.pkg)
        }
    }
}

@Composable
private fun AppScheduleEditor(packageName: String) {
    val schedule = Store.scheduleFor(packageName)
    val context = LocalContext.current
    val initialUsageAccess = UsageTracker.hasAccess(context)
    val hasUsageAccess by produceState(initialUsageAccess, packageName) {
        value = initialUsageAccess
        while (true) {
            delay(2_000)
            value = UsageTracker.hasAccess(context)
        }
    }
    var usageMinutes by remember(packageName) {
        mutableStateOf(Store.usageLimitFor(packageName)?.toString().orEmpty())
    }
    val usedMillis by produceState(0L, packageName, hasUsageAccess) {
        value = if (hasUsageAccess) UsageTracker.todayUsageMillis(context, packageName, forceRefresh = true) else 0L
        while (hasUsageAccess) {
            delay(10_000)
            value = UsageTracker.todayUsageMillis(context, packageName, forceRefresh = true)
        }
    }
    var day by remember(packageName) { mutableStateOf("1") }
    var start by remember(packageName) { mutableStateOf("08:00") }
    var end by remember(packageName) { mutableStateOf("16:00") }
    var action by remember(packageName) { mutableStateOf(ScheduleAction.ALLOW) }

    Column(
        Modifier.fillMaxSize().padding(vertical = Sp.m),
        verticalArrangement = Arrangement.spacedBy(Sp.m),
    ) {
        Text(stringResource(R.string.app_schedule_help), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
            OutlinedTextField(
                value = day, onValueChange = { day = it.filter(Char::isDigit).take(1) },
                label = { Text(stringResource(R.string.schedule_day)) }, modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = start, onValueChange = { start = it.take(5) },
                label = { Text(stringResource(R.string.schedule_start)) }, modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = end, onValueChange = { end = it.take(5) },
                label = { Text(stringResource(R.string.schedule_end)) }, modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
            FilterChip(
                selected = action == ScheduleAction.ALLOW,
                onClick = { action = ScheduleAction.ALLOW },
                label = { Text(stringResource(R.string.schedule_allow)) },
            )
            FilterChip(
                selected = action == ScheduleAction.LOCK,
                onClick = { action = ScheduleAction.LOCK },
                label = { Text(stringResource(R.string.schedule_lock)) },
            )
        }
        val dayNumber = day.toIntOrNull()
        val startMinute = parseScheduleMinute(start)
        val endMinute = parseScheduleMinute(end)
        Button(
            onClick = {
                val next = schedule.weekly + WeeklyAppWindow(
                    java.util.UUID.randomUUID().toString(), packageName,
                    dayNumber!!, startMinute!!, endMinute!!, action,
                )
                Store.setSchedule(packageName, schedule.copy(weekly = next))
            },
            enabled = dayNumber != null && dayNumber in 1..7 && startMinute != null &&
                endMinute != null && startMinute != endMinute,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.schedule_add)) }

        if (schedule.weekly.isNotEmpty()) {
            Text(stringResource(R.string.schedule_saved), style = MaterialTheme.typography.titleSmall)
            schedule.weekly.forEach { rule ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(
                            R.string.app_schedule_summary,
                            rule.dayOfWeek, formatScheduleMinute(rule.startMinute),
                            formatScheduleMinute(rule.endMinute),
                            if (rule.action == ScheduleAction.LOCK) stringResource(R.string.schedule_lock)
                            else stringResource(R.string.schedule_allow),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        Store.setSchedule(packageName, schedule.copy(weekly = schedule.weekly.filterNot { it.id == rule.id }))
                    }) { Text(stringResource(R.string.schedule_remove)) }
                }
            }
        }
        Text(stringResource(R.string.schedule_calendar_later), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        androidx.compose.material3.HorizontalDivider()
        Text(stringResource(R.string.usage_limit_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.usage_limit_desc), style = MaterialTheme.typography.bodySmall)
        if (!hasUsageAccess) {
            Text(stringResource(R.string.usage_permission_needed), color = MaterialTheme.colorScheme.error)
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.usage_open_settings)) }
        } else {
            Text(stringResource(R.string.usage_today_used, usedMillis / 60_000L))
            OutlinedTextField(
                value = usageMinutes,
                onValueChange = { usageMinutes = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.usage_limit_minutes)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                Button(
                    onClick = { Store.setUsageLimit(packageName, usageMinutes.toIntOrNull()) },
                    enabled = usageMinutes.toIntOrNull()?.let { it in 0..1440 } == true,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.usage_limit_save)) }
                TextButton(onClick = {
                    Store.setUsageLimit(packageName, null)
                    usageMinutes = ""
                }) { Text(stringResource(R.string.app_unblock)) }
            }
        }
    }
}

private fun parseScheduleMinute(value: String): Int? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun formatScheduleMinute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)

@Composable
private fun ModePill(mode: LockMode?) {
    val label = when (mode) {
        null -> R.string.mode_off
        LockMode.PIN -> R.string.mode_pin
        LockMode.MAIN -> R.string.mode_main
    }
    val bg = when (mode) {
        null -> MaterialTheme.colorScheme.surfaceVariant
        LockMode.PIN -> MaterialTheme.colorScheme.primaryContainer
        LockMode.MAIN -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
    }
    val fg = when (mode) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        LockMode.PIN -> MaterialTheme.colorScheme.onPrimaryContainer
        LockMode.MAIN -> MaterialTheme.colorScheme.tertiary
    }
    // The icon repeats the meaning of the colour, so the mode is readable without colour.
    val icon: ImageVector? = when (mode) {
        null -> null
        LockMode.PIN -> Icons.Filled.Lock
        LockMode.MAIN -> Icons.Filled.Key
    }
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sp.xs),
        ) {
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
            Text(stringResource(label), color = fg, style = MaterialTheme.typography.labelMedium)
        }
    }
}
