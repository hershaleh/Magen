package app.magen.lock.ui

import androidx.activity.compose.BackHandler
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.magen.lock.Bio
import app.magen.lock.Catalog
import app.magen.lock.GuardService
import app.magen.lock.Keeper
import app.magen.lock.LockMode
import app.magen.lock.Policy
import app.magen.lock.R
import app.magen.lock.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Process-wide "master code entered" flag, so it survives rotation and language changes. */
object Session {
    var authed by mutableStateOf(false)
    var leftAt = 0L

    /** Set by [trip] just before Magen sends the person to a system screen (Settings, uninstall, Keeper). */
    var tripUntil = 0L
    var tripPending = false

    /** Set by the help screen to ask the main screen to open a given tab once it is shown again. */
    var goTab by mutableStateOf<Int?>(null)

    /**
     * The code is asked for again every time Magen leaves the screen, except for a trip that Magen itself
     * started (to switch on a permission, say). The exception lasts three minutes and is used up on return.
     */
    fun trip() {
        tripUntil = android.os.SystemClock.elapsedRealtime() + 3 * 60_000L
    }

    /** Closes the exception again when the screen could not be opened after all. */
    fun cancelTrip() {
        tripUntil = 0L
        tripPending = false
    }

    /** True from "Approve uninstall" until the person comes back to Magen without having uninstalled it. */
    var uninstallPending = false
    var uninstallLeft = false

    /** Set when the person came back from the uninstall screen without uninstalling: show the real state once. */
    var uninstallNotice by mutableStateOf(false)
    var showGuide by mutableStateOf(false)
    var skipSetup by mutableStateOf(false)
    /** A freshly created recovery code that must be shown (once) before the app continues. */
    var recoveryToShow by mutableStateOf<String?>(null)
}

object Lang {
    fun current(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    fun set(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }
}

@Composable
fun Root() {
    val perms = rememberPerms()
    val needsSetup = !perms.acc || !perms.admin || (Store.level == "strong" && !perms.owner)
    val recovery = Session.recoveryToShow
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            recovery != null -> RecoveryCodeScreen(recovery) {
                Session.recoveryToShow = null
                Session.authed = true
            }
            // Basic help stays open for someone who has no code yet; otherwise it follows the code check.
            Session.showGuide && (Session.authed || !Store.hasPin) -> GuideScreen { Session.showGuide = false }
            !Store.hasPin -> Onboarding()
            !Session.authed -> PinGate(title = stringResource(R.string.enter_code), subtitle = null, allowAlt = true) {
                Session.authed = true
            }
            needsSetup && !Session.skipSetup -> SetupWizard()
            else -> MainScaffold()
        }
        if (Session.uninstallNotice && Session.authed) UninstallCancelledDialog()
    }
}

/** Shown after the uninstall screen was left without uninstalling: what Android reports right now. */
@Composable
private fun UninstallCancelledDialog() {
    val ctx = LocalContext.current
    val yes = stringResource(R.string.state_yes)
    val no = stringResource(R.string.state_no)
    val owner = remember { Policy.isOwner(ctx) }
    val admin = remember { Policy.isAdmin(ctx) }
    val running = remember { GuardService.instance != null }
    AlertDialog(
        onDismissRequest = { Session.uninstallNotice = false },
        title = { Text(stringResource(R.string.uninstall_cancelled_title)) },
        text = {
            Text(
                stringResource(
                    R.string.uninstall_cancelled_body,
                    if (owner) yes else no,
                    if (admin) yes else no,
                    if (running) yes else no,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                Session.uninstallNotice = false
                Session.goTab = 0
            }) { Text(stringResource(R.string.nav_home)) }
        },
        dismissButton = { TextButton(onClick = { Session.uninstallNotice = false }) { Text(stringResource(R.string.ok)) } },
    )
}

// ---- onboarding ------------------------------------------------------------------------------

@Composable
fun Onboarding() {
    var step by rememberSaveable { mutableIntStateOf(0) }
    if (step == 0) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            ShieldBadge(112.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.welcome_sub),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            LanguagePicker()
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { step = 1 },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.start)) }
        }
    } else if (step == 1) {
        LevelStep { step = 2 }
    } else {
        CreatePinFlow {
            Store.setPin(it)
            // The recovery code is shown once by Root before the app continues.
            Session.recoveryToShow = Store.newRecoveryCode()
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun LanguagePicker(modifier: Modifier = Modifier) {
    var cur by remember { mutableStateOf(Lang.current()) }
    // Wraps onto a second line on narrow screens instead of squeezing a chip into a sliver.
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("" to R.string.lang_system, "he" to R.string.lang_he, "en" to R.string.lang_en).forEach { (tag, label) ->
            FilterChip(
                selected = if (tag.isEmpty()) cur.isEmpty() else cur.startsWith(tag),
                onClick = {
                    cur = tag
                    Lang.set(tag)
                },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

// ---- main scaffold ---------------------------------------------------------------------------

@Composable
fun MainScaffold() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var changingPin by remember { mutableStateOf(false) }
    var recoveryMode by remember { mutableStateOf(false) }
    LaunchedEffect(Session.goTab) {
        Session.goTab?.let {
            tab = it
            Session.goTab = null
        }
    }

    if (changingPin) {
        ChangePin { changingPin = false }
    } else if (recoveryMode) {
        NewRecovery { recoveryMode = false }
    } else {
        val items = listOf(
            R.string.nav_home to Icons.Filled.Home,
            R.string.nav_apps to Icons.Filled.Apps,
            R.string.nav_times to Icons.Filled.Schedule,
            R.string.nav_protection to Icons.Filled.Security,
            R.string.nav_more to Icons.Filled.MoreHoriz,
        )
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    items.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icon, null) },
                            label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            },
        ) { inner ->
            Box(Modifier.padding(inner).statusBarsPadding()) {
                when (tab) {
                    0 -> HomeTab(onNavigate = { tab = it })
                    1 -> AppsTab()
                    2 -> TimesTab()
                    3 -> ProtectionTab(onOpenHome = { tab = 0 })
                    else -> MoreTab(
                        onChangePin = { changingPin = true },
                        onRecovery = { recoveryMode = true },
                        onOpenHome = { tab = 0 },
                        onOpenProtection = { tab = 3 },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangePin(onClose: () -> Unit) {
    var verified by remember { mutableStateOf(false) }
    if (!verified) {
        PinGate(title = stringResource(R.string.enter_code), subtitle = null, onCancel = onClose) { verified = true }
    } else {
        CreatePinFlow(onCancel = onClose) {
            Store.setPin(it)
            onClose()
        }
    }
}

@Composable
private fun NewRecovery(onClose: () -> Unit) {
    var code by remember { mutableStateOf<String?>(null) }
    val c = code
    if (c == null) {
        PinGate(title = stringResource(R.string.enter_code), subtitle = null, onCancel = onClose) { code = Store.newRecoveryCode() }
    } else {
        RecoveryCodeScreen(c, onClose)
    }
}

@Composable
internal fun TabColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

// ---- tab: protection -------------------------------------------------------------------------

@Composable
fun PermCard(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes desc: Int,
    on: Boolean,
    onEnable: (() -> Unit)?,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(icon)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (on) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                        Text(stringResource(R.string.status_on), color = MaterialTheme.colorScheme.secondary)
                    }
                } else if (onEnable != null) {
                    FilledTonalButton(onClick = onEnable) { Text(stringResource(R.string.btn_enable)) }
                }
            }
            extra?.invoke(this)
        }
    }
}

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

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.apps_summary, selectedCount, total),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
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
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            listOf(
                FILTER_ALL to R.string.filter_all,
                FILTER_SELECTED to R.string.filter_selected,
                FILTER_UNSELECTED to R.string.filter_unselected,
            ).forEach { (id, label) ->
                FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(stringResource(label)) })
            }
        }
        if (apps != null && shown.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { bulk = 1 },
                    enabled = toSelect.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.bulk_select, toSelect.size), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                OutlinedButton(
                    onClick = { bulk = 2 },
                    enabled = toDeselect.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.bulk_deselect, toDeselect.size), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
        when {
            apps == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            apps.isEmpty() -> Text(
                stringResource(R.string.apps_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            shown.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.apps_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                OutlinedButton(
                    onClick = {
                        q = ""
                        filter = FILTER_ALL
                    },
                    shape = RoundedCornerShape(16.dp),
                ) { Text(stringResource(R.string.apps_clear_search)) }
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(shown, key = { it.pkg }) { AppRow(it, showPkg = query.isNotEmpty()) }
            }
        }
    }

    val action = bulk
    if (action != null) {
        // Only the apps in the current results are changed; hidden apps and their modes stay as they are.
        val targets = if (action == 1) toSelect else toDeselect
        AlertDialog(
            onDismissRequest = { bulk = null },
            title = { Text(stringResource(R.string.bulk_confirm_title)) },
            text = {
                Text(
                    stringResource(if (action == 1) R.string.bulk_select_msg else R.string.bulk_deselect_msg, targets.size),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    bulk = null
                    targets.forEach { a -> Store.setMode(a.pkg, if (action == 1) LockMode.PIN else null) }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { bulk = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AppRow(a: AppItem, showPkg: Boolean) {
    val mode = Store.locked[a.pkg]
    val released = a.pkg in Store.mainUnlocked
    Surface(
        onClick = {
            Store.setMode(
                a.pkg,
                when (mode) {
                    null -> LockMode.PIN
                    LockMode.PIN -> LockMode.MAIN
                    LockMode.MAIN -> null
                },
            )
        },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(a.icon, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                if (showPkg) {
                    Text(
                        a.pkg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Package names are always left-to-right, also in the Hebrew layout.
                        style = MaterialTheme.typography.labelSmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
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
}

@Composable
private fun ModePill(mode: LockMode?) {
    val (label, bg, fg) = when (mode) {
        null -> Triple(R.string.mode_off, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        LockMode.PIN -> Triple(R.string.mode_pin, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        LockMode.MAIN -> Triple(R.string.mode_main, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f), MaterialTheme.colorScheme.tertiary)
    }
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(
            stringResource(label),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

// ---- tab: rules ------------------------------------------------------------------------------

@Composable
fun ProtectionTab(onOpenHome: () -> Unit) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    if (advanced) {
        AdvancedProtection(onBack = { advanced = false }, onOpenHome = onOpenHome)
        return
    }
    val ctx = LocalContext.current
    // Read again every time the person returns from Android settings.
    val p = rememberPerms()
    val running = GuardService.instance != null
    val fix: (Fix) -> Unit = { openFix(ctx, it) {} }

    TabColumn {
        Text(stringResource(R.string.protection_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard(stringResource(R.string.grp_play), stringResource(R.string.grp_play_desc)) {
            SwitchRow(
                Icons.Filled.Storefront,
                stringResource(R.string.rule_play),
                stringResource(R.string.rule_play_desc),
                Store.blockPlay,
            ) { Store.setBlockPlay(it) }
            StatusLine(playLine(running), fix)
        }

        SectionCard(stringResource(R.string.grp_apk), stringResource(R.string.grp_apk_desc)) {
            SwitchRow(
                Icons.Filled.Android,
                stringResource(R.string.rule_apk),
                stringResource(R.string.rule_apk_desc),
                Store.blockApk,
            ) { Store.setBlockApk(it) }
            StatusLine(apkLine(ctx, p, running), fix)
        }

        SectionCard(stringResource(R.string.grp_settings), stringResource(R.string.grp_settings_desc)) {
            StatusLine(settingsLine(running), fix)
        }

        SettingsGroups()

        PickFromScreenCard()

        CustomKeywordsCard()

        HowToCard()

        SectionCard(stringResource(R.string.grp_self), stringResource(R.string.grp_self_desc)) {
            SwitchRow(
                Icons.Filled.Security,
                stringResource(R.string.protect_title),
                stringResource(R.string.protect_desc),
                Store.protect,
            ) { Store.setProtect(it) }
            StatusLine(selfLine(p, running), fix)
        }

        SectionCard(stringResource(R.string.protection_adv_title), stringResource(R.string.protection_adv_desc)) {
            StatusLine(ownerLine(p), fix)
            StatusLine(adminLine(p), fix)
            OutlinedButton(
                onClick = { advanced = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) { Text(stringResource(R.string.protection_adv_open)) }
            TextButton(
                onClick = { Session.showGuide = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.guide_open)) }
        }
    }
}

/** Sub-screen of the Protection tab: protection level, comparison, strong-mode guide, admin/owner pointers. */
@Composable
private fun AdvancedProtection(onBack: () -> Unit, onOpenHome: () -> Unit) {
    BackHandler(onBack = onBack)
    TabColumn {
        TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) }
        Text(stringResource(R.string.protection_adv_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard(stringResource(R.string.level_title)) {
            LevelSelector(Store.level) { Store.setLevel(it) }
            CompareDialogLink()
            OutlinedButton(
                onClick = { Session.showGuide = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.guide_open)) }
        }

        SectionCard(stringResource(R.string.adv_perms_title), stringResource(R.string.adv_perms_desc)) {
            OutlinedButton(
                onClick = onOpenHome,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text(stringResource(R.string.adv_perms_open)) }
        }
    }
}

// ---- tab: times ------------------------------------------------------------------------------

/**
 * The only time-based behaviour Magen has today is the short uninstall-approval window, shown here read-only.
 * Schedules, profiles and usage limits do not exist yet, so nothing here saves or enforces them.
 */
@Composable
fun TimesTab() {
    // Re-read the clock so the status flips by itself when the approval window ends.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(15_000)
        }
    }
    val until = Store.allowedUntil
    val active = now < until
    TabColumn {
        Text(stringResource(R.string.times_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard(stringResource(R.string.times_now_title), stringResource(R.string.times_now_desc)) {
            Text(
                if (active) {
                    stringResource(
                        R.string.times_pause_active,
                        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(until)),
                    )
                } else {
                    stringResource(R.string.times_pause_none)
                },
                color = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }

        SectionCard(stringResource(R.string.times_soon_title), stringResource(R.string.times_soon_body)) {
            listOf(R.string.times_item_schedules, R.string.times_item_profiles, R.string.times_item_limits).forEach {
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Schedule,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(stringResource(it), modifier = Modifier.padding(start = 10.dp))
                }
            }
            Text(
                stringResource(R.string.times_not_enforced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ---- tab: more -------------------------------------------------------------------------------

@Composable
fun MoreTab(onChangePin: () -> Unit, onRecovery: () -> Unit, onOpenHome: () -> Unit, onOpenProtection: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var confirmOwner by remember { mutableStateOf(false) }
    var ownerCleared by remember { mutableStateOf(false) }
    var confirmRoot by remember { mutableStateOf(false) }
    var confirmKeeper by remember { mutableStateOf(false) }
    var rootRebootNeeded by remember { mutableStateOf(false) }
    // The root removal moved the file, but Android only lets go of the role after a reboot.
    var rootPending by remember { mutableStateOf(false) }
    var rootError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var ownerError by remember { mutableStateOf<String?>(null) }
    var uninstallError by remember { mutableStateOf<String?>(null) }
    // Set when Android's own removal failed because this phone's firmware lacks the telephony provider.
    val removalPrefs = ctx.getSharedPreferences("magen", Context.MODE_PRIVATE)
    var normalBroken by remember { mutableStateOf(removalPrefs.getBoolean("normalRemovalBroken", false)) }
    val perms = rememberPerms()

    TabColumn {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard(stringResource(R.string.more_code_title)) {
            SwitchRow(Icons.Filled.Lock, stringResource(R.string.relock_screen_off), stringResource(R.string.relock_desc), Store.relockOnScreenOff) {
                Store.setRelock(it)
            }
            if (Bio.available(ctx)) {
                SwitchRow(
                    Icons.Filled.Fingerprint,
                    stringResource(R.string.bio_title),
                    stringResource(R.string.bio_desc),
                    Store.biometric,
                ) { on ->
                    if (!on) {
                        Store.setBiometric(false)
                    } else {
                        val act = Bio.activity(ctx)
                        if (act != null) {
                            Bio.prompt(act, ctx.getString(R.string.bio_prompt_title), ctx.getString(R.string.cancel)) {
                                Store.setBiometric(true)
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = onChangePin,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.change_pin)) }
            OutlinedButton(
                onClick = onRecovery,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(if (Store.hasRecovery) R.string.recovery_regen else R.string.recovery_create)) }
            Text(
                stringResource(R.string.recovery_desc_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionCard(stringResource(R.string.more_perms_title), stringResource(R.string.more_perms_desc)) {
            OutlinedButton(
                onClick = onOpenHome,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.more_perms_open)) }
            // Shortcuts that only open the matching Android screen; nothing is changed from here.
            listOf(Fix.ACCESSIBILITY, Fix.NOTIFICATIONS, Fix.BATTERY).forEach { f ->
                TextButton(
                    onClick = { openFix(ctx, f) {} },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(fixLabel(f))) }
            }
            OutlinedButton(
                onClick = onOpenProtection,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text(stringResource(R.string.more_to_protection)) }
            Text(
                stringResource(R.string.more_to_protection_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionCard(stringResource(R.string.more_guides_title), stringResource(R.string.more_guides_desc)) {
            OutlinedButton(
                onClick = { Session.showGuide = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.help_open)) }
        }

        Text(
            stringResource(R.string.more_release_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            stringResource(R.string.more_release_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val staleBlock = remember(perms) { Policy.staleUninstallBlock(ctx) }
        if (staleBlock) {
            SectionCard(stringResource(R.string.stale_block_title), stringResource(R.string.stale_block_body)) {
                Text(
                    ADB_CMD,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                OutlinedButton(
                    onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("adb", ADB_CMD))
                        toastCopied(ctx)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.copy_cmd)) }
            }
        }

        run {
            val canRelease = perms.owner && !ownerCleared
            SectionCard(stringResource(R.string.remove_owner_section_title), stringResource(R.string.remove_owner_section_desc)) {
                Text(
                    stringResource(R.string.owner_versions_info, android.os.Build.VERSION.RELEASE, android.os.Build.VERSION.SDK_INT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (rootPending && perms.owner) {
                    Text(
                        stringResource(R.string.remove_owner_reboot_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (!canRelease) {
                    Text(
                        stringResource(R.string.remove_owner_not_owner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    stringResource(R.string.rel_normal_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (normalBroken) {
                    Text(
                        stringResource(R.string.remove_owner_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    TextButton(
                        onClick = { confirmOwner = true },
                        enabled = canRelease,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.remove_owner_try_anyway)) }
                } else {
                    OutlinedButton(
                        onClick = { confirmOwner = true },
                        enabled = canRelease,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) { Text(stringResource(R.string.remove_owner)) }
                }
                Text(
                    stringResource(R.string.rel_root_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(
                    onClick = { confirmRoot = true },
                    enabled = canRelease,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.remove_owner_root), color = MaterialTheme.colorScheme.error) }
                Text(
                    stringResource(R.string.rel_keeper_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    OutlinedButton(
                        onClick = { confirmKeeper = true },
                        enabled = canRelease,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) { Text(stringResource(R.string.remove_owner_keeper)) }
                }
            }
        }

        SectionCard(stringResource(R.string.approve_uninstall), stringResource(R.string.approve_desc)) {
            Button(
                onClick = { confirm = true },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Icon(Icons.Filled.DeleteForever, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.approve_uninstall), color = Color.White)
            }
        }

        SectionCard(stringResource(R.string.language)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Icons.Filled.Language)
                LanguagePicker(Modifier.weight(1f))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                // Leaving to the browser counts as a trip, and a missing browser must not crash the app.
                try {
                    Session.trip()
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mitmachim.top/user/%D7%94%D7%A2%D7%A8%D7%A9%D7%9C%D7%94")))
                } catch (e: Exception) {
                    Session.cancelTrip()
                    android.widget.Toast.makeText(ctx, ctx.getString(R.string.link_open_failed), android.widget.Toast.LENGTH_LONG).show()
                }
            }.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.credit_hearshal),
                contentDescription = stringResource(R.string.credit_hearshal),
                modifier = Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape),
            )
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.credit_hearshal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.approve_confirm_title)) },
            text = { Text(stringResource(R.string.approve_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    approveUninstall(ctx) { uninstallError = it }
                }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmOwner) {
        AlertDialog(
            onDismissRequest = { confirmOwner = false },
            title = { Text(stringResource(R.string.remove_owner_confirm_title)) },
            text = { Text(stringResource(R.string.remove_owner_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOwner = false
                    val ok = Policy.releaseOwner(ctx)
                    if (ok) {
                        removalPrefs.edit().remove("normalRemovalBroken").apply()
                        ownerCleared = true
                        Store.setLevel("regular")
                        android.widget.Toast.makeText(ctx, ctx.getString(R.string.remove_owner_done), android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        // A dialog (not a Toast) so the exact Android error stays on screen and can be copied.
                        ownerError = Policy.lastReleaseOwnerError ?: "unknown"
                        if (ownerError!!.contains("enforce_managed", ignoreCase = true) ||
                            ownerError!!.contains("telephony", ignoreCase = true)
                        ) {
                            removalPrefs.edit().putBoolean("normalRemovalBroken", true).apply()
                            normalBroken = true
                        }
                    }
                }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmOwner = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmKeeper) {
        AlertDialog(
            onDismissRequest = { confirmKeeper = false },
            title = { Text(stringResource(R.string.keeper_confirm_title)) },
            text = { Text(stringResource(R.string.keeper_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmKeeper = false
                    if (!Keeper.installed(ctx)) {
                        ownerError = ctx.getString(R.string.keeper_missing)
                    } else if (!Policy.isActiveAdmin(ctx, Keeper.receiver())) {
                        ownerError = ctx.getString(R.string.keeper_inactive)
                    } else {
                        val result = Policy.transferOwner(ctx, Keeper.receiver())
                        if (result == null) {
                            // Success is shown only once Android no longer lists Magen as Device Owner AND lists
                            // Magen Keeper as the Device Owner.
                            verifyTransferToKeeper(ctx) { gone ->
                                if (gone) {
                                    Store.setLevel("regular")
                                    ownerCleared = true
                                    android.widget.Toast.makeText(ctx, ctx.getString(R.string.keeper_done), android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    ownerError = ctx.getString(R.string.transfer_not_confirmed)
                                }
                            }
                        } else {
                            ownerError = result
                        }
                    }
                }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmKeeper = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmRoot) {
        AlertDialog(
            onDismissRequest = { confirmRoot = false },
            title = { Text(stringResource(R.string.root_confirm_title)) },
            text = { Text(stringResource(R.string.root_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRoot = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { Policy.releaseOwnerViaRoot(ctx) }
                        if (result == null) {
                            Store.setLevel("regular")
                            rootRebootNeeded = true
                        } else {
                            rootError = result
                        }
                    }
                }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRoot = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (rootRebootNeeded) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.root_done_title)) },
            text = { Text(stringResource(R.string.root_done_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { Policy.rebootViaRoot() } }
                }) { Text(stringResource(R.string.root_reboot)) }
            },
            dismissButton = { TextButton(onClick = { rootRebootNeeded = false; rootPending = true }) { Text(stringResource(R.string.root_later)) } },
        )
    }

    val rootErr = rootError
    if (rootErr != null) {
        AlertDialog(
            onDismissRequest = { rootError = null },
            title = { Text(stringResource(R.string.root_failed_title)) },
            text = { Text(rootErr) },
            confirmButton = { TextButton(onClick = { rootError = null }) { Text(stringResource(R.string.confirm)) } },
        )
    }

    val err = ownerError
    if (err != null) {
        AlertDialog(
            onDismissRequest = { ownerError = null },
            title = { Text(stringResource(R.string.remove_owner_failed)) },
            text = {
                Column {
                    Text(err)
                    if (err.contains("telephony", ignoreCase = true)) {
                        Text(
                            stringResource(R.string.remove_owner_telephony_hint),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ownerError = null }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("error", err))
                    toastCopied(ctx)
                }) { Text(stringResource(R.string.error_copy)) }
            },
        )
    }

    val uninstallErr = uninstallError
    if (uninstallErr != null) {
        AlertDialog(
            onDismissRequest = { uninstallError = null },
            title = { Text(stringResource(R.string.uninstall_failed_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(uninstallErr) } },
            confirmButton = { TextButton(onClick = { uninstallError = null }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("uninstall", uninstallErr))
                    toastCopied(ctx)
                }) { Text(stringResource(R.string.error_copy)) }
            },
        )
    }
}

private fun approveUninstall(ctx: Context, onError: (String) -> Unit) {
    // Release first. The 10-minute approval window (which pauses Magen's protection) opens only after
    // Android confirmed the release, so a failed attempt leaves the protection untouched.
    if (!Policy.release(ctx)) {
        onError(
            ctx.getString(R.string.uninstall_failed_lead) + "\n\n" +
                (Policy.lastUninstallError ?: "No details from Android.") + "\n" + Policy.stateSummary(ctx),
        )
        return
    }
    Store.allowUninstall(10)
    Session.uninstallPending = true
    Session.uninstallLeft = false
    Session.trip()
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        // The uninstall screen could not be opened: close the window and the exception again. The permissions
        // were already released, and the message says so.
        Session.cancelTrip()
        Session.uninstallPending = false
        Store.allowUninstall(0)
        onError(ctx.getString(R.string.uninstall_open_failed) + "\n\n" + e.javaClass.name + ": " + (e.message ?: ""))
    }
}

// ---- lock screen content ---------------------------------------------------------------------

@Composable
fun LockContent(pkg: String, onUnlocked: () -> Unit, onOpenMain: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mode = Store.locked[pkg]
    val label = remember(pkg) {
        runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
    val icon = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(160, 160).asImageBitmap() }.getOrNull()
    }

    androidx.compose.runtime.LaunchedEffect(mode) { if (mode == null) onUnlocked() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (mode == LockMode.MAIN) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppBadge(icon, 96.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.lock_pin_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lock_main_desc, label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onOpenMain,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text(stringResource(R.string.open_magen)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) }
            }
        } else {
            PinGate(
                title = stringResource(R.string.lock_pin_title),
                subtitle = stringResource(R.string.lock_pin_sub, label),
                icon = { AppBadge(icon) },
                onCancel = onBack,
                allowAlt = true,
                onSuccess = onUnlocked,
            )
        }
    }
}

@Composable
fun BlockContent(@StringRes reason: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val reasonText = remember(reason) { runCatching { ctx.getString(reason) }.getOrNull() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ShieldBadge(104.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.blocked_message),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (reasonText != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    reasonText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(R.string.ok)) }
        }
    }
}
