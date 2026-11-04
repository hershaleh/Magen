package app.magen.lock.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
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
import app.magen.lock.R
import app.magen.lock.Store
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- tab: apps -------------------------------------------------------------------------------

private class AppItem(val pkg: String, val label: String, val icon: ImageBitmap, val manageable: Boolean, val isSystem: Boolean)

private val ESSENTIAL_PACKAGES = setOf(
    "android", "com.android.systemui", "com.android.settings",
    "com.google.android.permissioncontroller", "com.android.permissioncontroller",
    "com.android.packageinstaller", "com.google.android.packageinstaller",
    "com.android.providers.settings", "com.android.phone", "com.android.server.telecom",
)

private fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    // Include installed packages even when they do not publish a launcher icon/activity.
    // Magen itself is excluded so the protection UI cannot lock its own management surface.
    return pm.getInstalledApplications(0)
        .filter { it.packageName != ctx.packageName }
        .distinctBy { it.packageName }
        .mapNotNull { info ->
            runCatching {
                AppItem(
                    info.packageName,
                    info.loadLabel(pm).toString(),
                    pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap(),
                    info.packageName !in ESSENTIAL_PACKAGES,
                    (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

private const val FILTER_ALL = 0
private const val FILTER_SELECTED = 1
private const val FILTER_UNSELECTED = 2
private const val FILTER_SYSTEM = 3
private const val FILTER_USER = 4

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppsTab() {
    val ctx = LocalContext.current
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshKey++ }
    // null while the list is still loading, so "no apps" is not shown by mistake.
    val loaded by produceState<List<AppItem>?>(null, refreshKey) { value = withContext(Dispatchers.Default) { loadApps(ctx) } }
    val apps = loaded
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    var bulk by remember { mutableStateOf<Int?>(null) } // 1 = select shown, 2 = deselect shown
    var selectedPkg by rememberSaveable { mutableStateOf<String?>(null) }

    // Search and filter only decide what is displayed; they never touch the saved lock list.
    val locked = Store.locked
    val query = q.trim()
    val shown = remember(apps, query, filter, locked) {
        (apps ?: emptyList()).filter { a ->
            (query.isEmpty() || a.label.contains(query, ignoreCase = true) || a.pkg.contains(query, ignoreCase = true)) &&
                when (filter) {
                    FILTER_SELECTED -> a.pkg in locked
                    FILTER_UNSELECTED -> a.pkg !in locked
                    FILTER_SYSTEM -> a.isSystem
                    FILTER_USER -> !a.isSystem
                    else -> true
                }
        }
    }
    val total = apps?.size ?: 0
    val selectedCount = apps?.count { it.pkg in locked } ?: 0
    val toSelect = shown.filter { it.pkg !in locked }
    val toDeselect = shown.filter { it.pkg in locked }

    val selectedApp = apps?.firstOrNull { it.pkg == selectedPkg }
    if (selectedApp != null) {
        AppDetailsScreen(selectedApp, onBack = { selectedPkg = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Sp.l)) {
        Spacer(Modifier.height(Sp.l))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { refreshKey++ }) { Icon(Icons.Filled.Refresh, contentDescription = "רענון רשימת האפליקציות") }
        }
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
                FILTER_SYSTEM to R.string.filter_system,
                FILTER_USER to R.string.filter_user,
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
                            AppRow(item, showPkg = query.isNotEmpty(), onOpenDetails = { selectedPkg = item.pkg })
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
private fun AppRow(a: AppItem, showPkg: Boolean, onOpenDetails: () -> Unit) {
    val mode = Store.locked[a.pkg]
    val released = a.pkg in Store.mainUnlocked
    // A selected app is tinted, so the list can be scanned at a glance.
    val tint = if (mode != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(tint)
            .clickable(enabled = a.manageable, onClick = onOpenDetails)
            .heightIn(min = 56.dp)
            .padding(horizontal = Sp.m, vertical = Sp.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sp.m),
    ) {
        Image(a.icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f)) {
            Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (!a.manageable) "רכיב מערכת חיוני — לא ניתן לנעילה" else if (a.isSystem) "אפליקציית מערכת" else "אפליקציית משתמש",
                style = MaterialTheme.typography.labelSmall,
                color = if (!a.manageable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        IconButton(enabled = a.manageable, onClick = {
            Store.setMode(a.pkg, when (mode) {
                null -> LockMode.PIN
                LockMode.PIN -> LockMode.MAIN
                LockMode.MAIN -> null
            })
        }) { Icon(Icons.Filled.Tune, "הגדרות אפליקציה") }
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
private fun AppDetailsScreen(a: AppItem, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val mode = Store.locked[a.pkg]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sp.l), verticalArrangement = Arrangement.spacedBy(Sp.m)) {
        TextButton(onClick = onBack) { Text("חזרה לרשימת האפליקציות") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
            Image(a.icon, null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(a.label, style = MaterialTheme.typography.titleLarge)
                Text(a.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ModePill(mode)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text("שיטת חסימה") })
            FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("הגדרות נוספות") })
        }
        if (selectedTab == 0) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("כיצד לחסום את האפליקציה?", style = MaterialTheme.typography.titleMedium)
                    listOf(null, LockMode.PIN, LockMode.MAIN).forEach { option ->
                        val label = when (option) {
                            null -> "ללא חסימה"
                            LockMode.PIN -> "דרישת קוד"
                            LockMode.MAIN -> "פתיחה דרך מגן"
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = mode == option,
                                onClick = { Store.setMode(a.pkg, option) },
                            )
                            Text(label)
                        }
                    }
                    Text("בחירת שיטת החסימה נשמרת במגן ומשפיעה על נעילת האפליקציה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            var quota by rememberSaveable(a.pkg) { mutableStateOf(false) }
            var quotaMinutes by rememberSaveable(a.pkg) { mutableIntStateOf(60) }
            var pausePersonal by rememberSaveable(a.pkg) { mutableStateOf(false) }
            var schedule by rememberSaveable(a.pkg) { mutableStateOf(false) }
            var group by rememberSaveable(a.pkg) { mutableStateOf(false) }
            var groupName by rememberSaveable(a.pkg) { mutableIntStateOf(0) }
            var days by rememberSaveable(a.pkg) { mutableStateOf(setOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳")) }
            Text("הגדרות נוספות", style = MaterialTheme.typography.titleMedium)
            Text("תצוגת אפיון: הערכים כאן עדיין אינם מחוברים למנועי המכסות ולוחות הזמנים.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("מכסה אישית", style = MaterialTheme.typography.titleSmall)
                            Text("זמן שימוש יומי באפליקציה זו", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = quota, onCheckedChange = { quota = it })
                    }
                    if (quota) {
                        Text(if (quotaMinutes == 0) "0 דקות — חסימה לכל היום" else "מכסה: $quotaMinutes דקות ביום")
                        androidx.compose.material3.Slider(value = quotaMinutes.toFloat(), onValueChange = { quotaMinutes = it.toInt() }, valueRange = 0f..360f, steps = 35)
                        Text("שימוש היום: 42 דקות · איפוס בחצות המקומית", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.material3.Switch(checked = pausePersonal, onCheckedChange = { pausePersonal = it })
                        Text("השהיית המכסה האישית")
                        Text("התרעה ברירת מחדל: 5 דקות לפני סיום. כשהמכסה נגמרת — האפליקציה נחסמת מיד.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("מכסה קבוצתית", style = MaterialTheme.typography.titleSmall)
                            Text("שיתוף מכסה עם אפליקציות נוספות", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = group, onCheckedChange = { group = it })
                    }
                    if (group) {
                        listOf("רשתות חברתיות", "וידאו", "משחקים").forEachIndexed { i, label ->
                            Row(Modifier.fillMaxWidth().clickable { groupName = i }, verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(selected = groupName == i, onClick = { groupName = i })
                                Text(label)
                            }
                        }
                        Text("אפליקציה יכולה להשתייך לקבוצה אחת בלבד. מכסה אישית וקבוצתית יכולות לפעול יחד; סיום אחת מהן חוסם.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("לוח זמנים אישי", style = MaterialTheme.typography.titleSmall)
                            Text("חלונות שבהם האפליקציה מותרת", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = schedule, onCheckedChange = { schedule = it })
                    }
                    if (schedule) {
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳").forEach { day ->
                                FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day }, label = { Text(day) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                OutlinedTextField(value = "08:00", onValueChange = {}, label = { Text("משעה") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            Column(Modifier.weight(1f)) {
                                OutlinedTextField(value = "20:00", onValueChange = {}, label = { Text("עד שעה") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        TextButton(onClick = {}) { Text("הוספת חלון זמן נוסף") }
                        androidx.compose.material3.Switch(checked = false, onCheckedChange = {})
                        Text("חלון זה חוצה חצות", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("הפסקות וחריגים", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = "13:00", onValueChange = {}, label = { Text("תחילת הפסקה") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = "14:00", onValueChange = {}, label = { Text("סיום הפסקה") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = "", onValueChange = {}, label = { Text("תאריך חריג") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("הפסקה משהה ספירת מכסה בלבד. כאשר חריגים מתנגשים — החסימה גוברת.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

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
