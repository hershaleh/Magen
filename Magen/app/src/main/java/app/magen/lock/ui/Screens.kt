package app.magen.lock.ui

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
import app.magen.lock.LockMode
import app.magen.lock.Policy
import app.magen.lock.R
import app.magen.lock.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Process-wide "master code entered" flag, so it survives rotation and language changes. */
object Session {
    var authed by mutableStateOf(false)
    var leftAt = 0L
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
            Session.showGuide -> GuideScreen { Session.showGuide = false }
            !Store.hasPin -> Onboarding()
            !Session.authed -> PinGate(title = stringResource(R.string.enter_code), subtitle = null, allowAlt = true) {
                Session.authed = true
            }
            needsSetup && !Session.skipSetup -> SetupWizard()
            else -> MainScaffold()
        }
    }
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
fun LanguagePicker() {
    var cur by remember { mutableStateOf(Lang.current()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

    if (changingPin) {
        ChangePin { changingPin = false }
    } else if (recoveryMode) {
        NewRecovery { recoveryMode = false }
    } else {
        val items = listOf(
            R.string.nav_home to Icons.Filled.Security,
            R.string.nav_apps to Icons.Filled.Apps,
            R.string.nav_rules to Icons.Filled.Tune,
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
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            },
        ) { inner ->
            Box(Modifier.padding(inner).statusBarsPadding()) {
                when (tab) {
                    0 -> HomeTab()
                    1 -> AppsTab()
                    2 -> RulesTab()
                    else -> MoreTab(onChangePin = { changingPin = true }, onRecovery = { recoveryMode = true })
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
private fun TabColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

// ---- tab: protection -------------------------------------------------------------------------

@Composable
fun HomeTab() {
    val p = rememberPerms()
    val done = (if (p.acc) 1 else 0) + (if (p.admin) 1 else 0)

    TabColumn {
        HeroCard {
            Icon(
                if (done == 2) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(if (done == 2) R.string.status_active else R.string.status_incomplete),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                stringResource(R.string.status_steps, done),
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        PermissionCards(p, showOwner = true)
    }
}

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

@Composable
fun AppsTab() {
    val ctx = LocalContext.current
    val apps by produceState(emptyList<AppItem>()) { value = withContext(Dispatchers.Default) { loadApps(ctx) } }
    var q by remember { mutableStateOf("") }
    val shown = remember(apps, q) { apps.filter { it.label.contains(q, ignoreCase = true) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.apps_count, Store.locked.size),
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(shown, key = { it.pkg }) { AppRow(it) }
        }
    }
}

@Composable
private fun AppRow(a: AppItem) {
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
            Text(a.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            ModePill(mode)
            if (mode == LockMode.MAIN) {
                IconButton(onClick = { Store.toggleMainUnlocked(a.pkg) }) {
                    Icon(
                        if (released) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        null,
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
fun RulesTab() {
    TabColumn {
        Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard(stringResource(R.string.rules_installs)) {
            SwitchRow(
                Icons.Filled.Storefront,
                stringResource(R.string.rule_play),
                stringResource(R.string.rule_play_desc),
                Store.blockPlay,
            ) { Store.setBlockPlay(it) }
            SwitchRow(
                Icons.Filled.Android,
                stringResource(R.string.rule_apk),
                stringResource(R.string.rule_apk_desc),
                Store.blockApk,
            ) { Store.setBlockApk(it) }
        }

        SettingsGroups()

        PickFromScreenCard()

        CustomKeywordsCard()

        HowToCard()
    }
}

// ---- tab: more -------------------------------------------------------------------------------

@Composable
fun MoreTab(onChangePin: () -> Unit, onRecovery: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var confirmOwner by remember { mutableStateOf(false) }
    var ownerCleared by remember { mutableStateOf(false) }
    var ownerError by remember { mutableStateOf<String?>(null) }
    val perms = rememberPerms()

    TabColumn {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard(stringResource(R.string.level_title)) {
            LevelSelector(Store.level) { Store.setLevel(it) }
            CompareDialogLink()
            OutlinedButton(
                onClick = { Session.showGuide = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.guide_open)) }
        }

        SectionCard(stringResource(R.string.language)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Icons.Filled.Language)
                LanguagePicker()
            }
        }

        SectionCard(stringResource(R.string.security)) {
            SwitchRow(Icons.Filled.Lock, stringResource(R.string.relock_screen_off), null, Store.relockOnScreenOff) {
                Store.setRelock(it)
            }
            SwitchRow(
                Icons.Filled.Security,
                stringResource(R.string.protect_title),
                stringResource(R.string.protect_desc),
                Store.protect,
            ) { Store.setProtect(it) }
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
        }

        if (perms.owner && !ownerCleared) {
            SectionCard(stringResource(R.string.remove_owner_section_title), stringResource(R.string.remove_owner_section_desc)) {
                OutlinedButton(
                    onClick = { confirmOwner = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text(stringResource(R.string.remove_owner)) }
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

        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mitmachim.top/user/%D7%94%D7%A2%D7%A8%D7%A9%D7%9C%D7%94")))
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
                    approveUninstall(ctx)
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
                        ownerCleared = true
                        Store.setLevel("regular")
                        android.widget.Toast.makeText(ctx, ctx.getString(R.string.remove_owner_done), android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        // A dialog (not a Toast) so the exact Android error stays on screen and can be copied.
                        ownerError = Policy.lastReleaseOwnerError ?: "unknown"
                    }
                }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmOwner = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    val err = ownerError
    if (err != null) {
        AlertDialog(
            onDismissRequest = { ownerError = null },
            title = { Text(stringResource(R.string.remove_owner_failed)) },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = { ownerError = null }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("error", err))
                }) { Text(stringResource(R.string.error_copy)) }
            },
        )
    }
}

private fun approveUninstall(ctx: Context) {
    Store.allowUninstall(10)
    Policy.release(ctx)
    ctx.startActivity(
        Intent(Intent.ACTION_DELETE, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
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
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(stringResource(R.string.ok)) }
        }
    }
}
