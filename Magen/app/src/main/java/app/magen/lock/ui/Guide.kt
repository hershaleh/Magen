package app.magen.lock.ui

import androidx.compose.foundation.layout.ColumnScope
import android.app.admin.DevicePolicyManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.magen.lock.Catalog
import app.magen.lock.GuardService
import app.magen.lock.Keeper
import app.magen.lock.Policy
import app.magen.lock.SGroup
import app.magen.lock.R
import app.magen.lock.Store

const val ADB_CMD = "adb shell dpm set-device-owner app.magen.lock/.AdminReceiver"

// ---- permission state ------------------------------------------------------------------------

class Perms(val acc: Boolean, val admin: Boolean, val owner: Boolean, val battery: Boolean = true)

/** Current permission state, re-read every time the app comes back to the foreground. */
@Composable
fun rememberPerms(): Perms {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    return remember(tick) { readPerms(ctx) }
}

/** Reads the permission state from Android right now (no caching). */
fun readPerms(ctx: Context): Perms {
    val battery = runCatching {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }.getOrDefault(true)
    return Perms(Policy.accessibilityOn(ctx), Policy.isAdmin(ctx), Policy.isOwner(ctx), battery)
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes desc: Int,
    on: Boolean,
    onEnable: (() -> Unit)?,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ListRow(
        title = stringResource(title),
        supporting = stringResource(desc),
        icon = icon,
        iconTint = if (on) sevColor(Sev.OK) else MaterialTheme.colorScheme.onSurfaceVariant,
        trailing = {
            if (on) {
                StatusChip(Sev.OK, stringResource(R.string.status_on))
            } else if (onEnable != null) {
                TonalButton(stringResource(R.string.btn_enable), onEnable)
            }
        },
        below = extra,
    )
}

@Composable
fun PermissionCards(p: Perms, showOwner: Boolean) {
    val ctx = LocalContext.current
    var showRestrictedHelp by remember { mutableStateOf(false) }
    val keeperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            // Keeper says it handed ownership over; believe it only when Android lists Magen as owner.
            verifyOwnership(ctx, expectOwner = true) { owner ->
                if (owner) {
                    Store.setLevel("strong")
                    Toast.makeText(ctx, ctx.getString(R.string.keeper_taken), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(ctx, ctx.getString(R.string.keeper_take_unconfirmed), Toast.LENGTH_LONG).show()
                }
            }
        } else {
            val reason = res.data?.getStringExtra("error") ?: ctx.getString(R.string.keeper_take_failed)
            Toast.makeText(ctx, reason, Toast.LENGTH_LONG).show()
        }
    }
    // Android's setting can be on while the service itself is not running; the row must not say "on" then.
    val accRunning = GuardService.instance != null

    GroupCard {
        PermissionRow(
            icon = Icons.Filled.Accessibility,
            title = R.string.perm_access_title,
            desc = R.string.perm_access_desc,
            on = p.acc && accRunning,
            onEnable = {
                try {
                    Session.trip()
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (error: Exception) {
                    Session.cancelTrip()
                    Toast.makeText(ctx, ctx.getString(R.string.settings_open_failed), Toast.LENGTH_LONG).show()
                }
            },
        ) {
            if (p.acc && !accRunning) {
                Spacer(Modifier.height(Sp.xs))
                Text(
                    stringResource(R.string.perm_access_stalled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!p.acc) {
                TextAction(stringResource(R.string.restricted_help_title), { showRestrictedHelp = true })
            }
        }
        RowDivider()

        PermissionRow(
            icon = Icons.Filled.AdminPanelSettings,
            title = R.string.perm_admin_title,
            desc = R.string.perm_admin_desc,
            on = p.admin,
            onEnable = {
                val adminComponent = Policy.admin(ctx)
                val request = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ctx.getString(R.string.admin_desc))
                }
                // Launch the system's dedicated device-admin approval screen, not general Settings.
                try {
                    Session.trip()
                    ctx.startActivity(request)
                } catch (error: Exception) {
                    Session.cancelTrip()
                    Toast.makeText(ctx, ctx.getString(R.string.admin_open_failed), Toast.LENGTH_LONG).show()
                }
            },
        )
        RowDivider()

        // Recommended, not required: keeps the guard from being stopped by battery savers.
        PermissionRow(
            icon = Icons.Filled.BatteryChargingFull,
            title = R.string.perm_battery_title,
            desc = R.string.perm_battery_desc,
            on = p.battery,
            onEnable = {
                val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    Session.trip()
                    ctx.startActivity(direct)
                } catch (error: Exception) {
                    try {
                        Session.trip()
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (second: Exception) {
                        Session.cancelTrip()
                        Toast.makeText(ctx, ctx.getString(R.string.settings_open_failed), Toast.LENGTH_LONG).show()
                    }
                }
            },
        ) {
            if (!p.battery) {
                Text(
                    stringResource(R.string.perm_battery_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showOwner) {
            RowDivider()
            PermissionRow(
                icon = Icons.Filled.Lock,
                title = R.string.perm_owner_title,
                desc = R.string.perm_owner_desc,
                on = p.owner,
                onEnable = null,
            ) {
                if (!p.owner) {
                    Spacer(Modifier.height(Sp.xs))
                    Text(
                        stringResource(R.string.perm_owner_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextAction(stringResource(R.string.guide_open), { Session.showGuide = true })
                    if (android.os.Build.VERSION.SDK_INT >= 28 && Keeper.installed(ctx)) {
                        if (p.admin) {
                            TonalButton(stringResource(R.string.keeper_take), {
                                Session.trip()
                                runCatching { keeperLauncher.launch(Keeper.handoverIntent()) }.onFailure {
                                    Session.cancelTrip()
                                    Toast.makeText(ctx, ctx.getString(R.string.keeper_take_failed), Toast.LENGTH_LONG).show()
                                }
                            })
                        } else {
                            // Android only hands ownership to an app that is already an enabled device admin.
                            Text(
                                stringResource(R.string.keeper_need_admin),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRestrictedHelp) {
        MagenDialog(
            title = stringResource(R.string.restricted_help_title),
            onDismiss = { showRestrictedHelp = false },
            confirmText = stringResource(R.string.confirm),
            onConfirm = { showRestrictedHelp = false },
        ) {
            DialogText(stringResource(R.string.restricted_help))
        }
    }
}

// ---- first-run permission wizard -------------------------------------------------------------

/** Shown right after the code is entered whenever a required permission is still missing. */
@Composable
fun SetupWizard() {
    val p = rememberPerms()
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShieldBadge(72.dp)
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.setup_sub), color = MaterialTheme.colorScheme.onSurfaceVariant)
        PermissionCards(p, showOwner = Store.level == "strong")
        TextButton(onClick = { Session.skipSetup = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_skip))
        }
    }
}

// ---- protection level ------------------------------------------------------------------------

@Composable
fun LevelSelector(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LevelCard(selected == "regular", R.string.level_regular, R.string.level_regular_sub, Icons.Filled.Shield, Modifier.weight(1f)) {
            onSelect("regular")
        }
        LevelCard(selected == "strong", R.string.level_strong, R.string.level_strong_sub, Icons.Filled.Lock, Modifier.weight(1f)) {
            onSelect("strong")
        }
    }
}

@Composable
private fun LevelCard(
    selected: Boolean,
    @StringRes title: Int,
    @StringRes sub: Int,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CompareDialogLink() {
    var show by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.compare_what_means))
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(stringResource(R.string.compare_title)) },
            text = {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { CompareTable() }
            },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@Composable
fun CompareTable() {
    val rows = listOf(
        Triple(R.string.cmp1_label, R.string.cmp1_reg, R.string.cmp1_str),
        Triple(R.string.cmp2_label, R.string.cmp2_reg, R.string.cmp2_str),
        Triple(R.string.cmp3_label, R.string.cmp3_reg, R.string.cmp3_str),
        Triple(R.string.cmp4_label, R.string.cmp4_reg, R.string.cmp4_str),
        Triple(R.string.cmp5_label, R.string.cmp5_reg, R.string.cmp5_str),
        Triple(R.string.cmp6_label, R.string.cmp6_reg, R.string.cmp6_str),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { (label, reg, str) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(label), fontWeight = FontWeight.Bold)
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompareCell(R.string.level_regular, reg, MaterialTheme.colorScheme.surfaceVariant, Modifier.weight(1f))
                    CompareCell(R.string.level_strong, str, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompareCell(@StringRes tag: Int, @StringRes text: Int, bg: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = bg, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(10.dp)) {
            Text(
                stringResource(tag),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(text), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Onboarding step: pick regular or strong protection. */
@Composable
fun LevelStep(onNext: () -> Unit) {
    var sel by remember { mutableStateOf(Store.level) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.level_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LevelSelector(sel) { sel = it }
        CompareDialogLink()
        if (sel == "strong") {
            OutlinedButton(
                onClick = { Session.showGuide = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.guide_open)) }
        }
        Button(
            onClick = {
                Store.setLevel(sel)
                onNext()
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(R.string.btn_continue)) }
    }
}

// ---- strong mode guide -----------------------------------------------------------------------

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun GuideScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val clipboard = LocalClipboardManager.current
    val copyCtx = LocalContext.current
    val steps = listOf(
        R.string.g1_t to R.string.g1_b,
        R.string.g2_t to R.string.g2_b,
        R.string.g3_t to R.string.g3_b,
        R.string.g4_t to R.string.g4_b,
        R.string.g5_t to R.string.g5_b,
        R.string.g6_t to R.string.g6_b,
    )
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back)) }
                Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.help_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionCard(stringResource(R.string.help_map_title)) {
                Text(stringResource(R.string.help_map_body), style = MaterialTheme.typography.bodyMedium)
                // Each link closes this screen and opens the tab that exists in the main menu.
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        Tabs.HOME to R.string.nav_home, Tabs.APPS to R.string.nav_apps, Tabs.PROTECTION to R.string.nav_protection,
                        Tabs.TIMES to R.string.nav_times, Tabs.SETTINGS to R.string.nav_settings,
                    ).forEach { (tab, label) ->
                        TextButton(onClick = {
                            Session.goTab = tab
                            onClose()
                        }) { Text(stringResource(label)) }
                    }
                }
            }
            SectionCard(stringResource(R.string.help_regular_title)) {
                Text(stringResource(R.string.help_regular_body), style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard(stringResource(R.string.help_status_title)) {
                Text(stringResource(R.string.help_status_body), style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard(stringResource(R.string.help_strong_title)) {
                Text(stringResource(R.string.help_strong_body), style = MaterialTheme.typography.bodyMedium)
                CompareDialogLink()
            }
            SectionCard(stringResource(R.string.help_release_title)) {
                Text(stringResource(R.string.help_release_body), style = MaterialTheme.typography.bodyMedium)
            }

            Text(stringResource(R.string.guide_steps_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text(stringResource(R.string.guide_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)

            steps.forEachIndexed { i, (t, b) ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(t), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(b),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (i == 3 || i == 4) {
                                val cmd = if (i == 3) "adb devices" else ADB_CMD
                                CommandBox(cmd)
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(cmd))
                                    toastCopied(copyCtx)
                                }) {
                                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.copy_cmd))
                                }
                            }
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.guide_keeper_title)) {
                Text(stringResource(R.string.guide_keeper_body), style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard(stringResource(R.string.guide_trouble_title)) {
                Text(stringResource(R.string.guide_trouble_body), style = MaterialTheme.typography.bodyMedium)
            }
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)) {
                Text(
                    stringResource(R.string.guide_warn),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

/** Commands are always left-to-right, even in the Hebrew layout. */
@Composable
private fun CommandBox(cmd: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                cmd,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Left,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
    }
}

// ---- custom settings keywords ----------------------------------------------------------------

/** One-tap blocking: opens Settings with a floating button that offers the current page title to block. */
@Composable
fun PickFromScreenCard() {
    val ctx = LocalContext.current
    SectionCard(stringResource(R.string.pick_title), stringResource(R.string.pick_desc)) {
        Button(
            onClick = {
                val guard = GuardService.instance
                if (guard == null) {
                    Toast.makeText(ctx, R.string.pick_need_service, Toast.LENGTH_LONG).show()
                } else {
                    // The hint is shown, and pick mode armed, only after Settings really opened.
                    try {
                        Session.trip()
                        ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        guard.armCapture()
                        Toast.makeText(ctx, R.string.pick_started, Toast.LENGTH_LONG).show()
                    } catch (error: Exception) {
                        Session.cancelTrip()
                        Toast.makeText(ctx, ctx.getString(R.string.settings_open_failed), Toast.LENGTH_LONG).show()
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text(stringResource(R.string.pick_start)) }
    }
}

// Page titles that appear on (almost) every Settings screen. A typed word equal to one of these would make the
// running scan close ALL Settings screens, so the entry is refused here (the "pick from screen" list does the same).
private val GENERIC_KEYWORDS = setOf(
    "settings", "search settings", "navigate up", "back", "more options", "search",
    "הגדרות", "חיפוש בהגדרות", "נווט למעלה", "חזרה", "אפשרויות נוספות", "חיפוש",
)

/** Mirrors [Store.addKeyword]: the last part of "A > B" is what would be saved. */
private fun keywordTooGeneral(raw: String): Boolean {
    val k = raw.split(Regex("[>›»→<‹]")).map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: return false
    return k.length < 2 || Catalog.norm(k).trim() in GENERIC_KEYWORDS
}

@Composable
fun CustomKeywordsCard() {
    var text by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.custom_title), stringResource(R.string.custom_desc)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    rejected = false
                },
                singleLine = true,
                isError = rejected,
                supportingText = if (rejected) {
                    { Text(stringResource(R.string.custom_generic_rejected)) }
                } else {
                    null
                },
                placeholder = { Text(stringResource(R.string.custom_hint)) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = {
                if (keywordTooGeneral(text)) {
                    rejected = true
                } else {
                    Store.addKeyword(text)
                    text = ""
                }
            }) { Text(stringResource(R.string.custom_add)) }
        }
        Store.customKeywords.sorted().forEach { k ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(k, Modifier.weight(1f))
                IconButton(onClick = { Store.removeKeyword(k) }) { Icon(Icons.Filled.Close, stringResource(R.string.remove_item)) }
            }
        }
    }
}

// ---- grouped settings switches ---------------------------------------------------------------

@Composable
fun SettingsGroups() {
    Text(stringResource(R.string.rules_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        stringResource(R.string.rules_settings_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SGroup.entries.forEach { g ->
        val cats = Catalog.settings.filter { it.group == g }
        var open by rememberSaveable(g.name) { mutableStateOf(false) }
        val active = cats.count { it.id in Store.blockedSettings }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(g.title), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (active > 0) {
                    Text("$active", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (open) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp)) {
                    cats.forEach { c ->
                        SwitchRow(c.icon, stringResource(c.title), null, c.id in Store.blockedSettings) {
                            Store.setBlockedSetting(c.id, it)
                        }
                    }
                }
            }
        }
    }
}

/** Explains that matching depends on the manufacturer's page titles, and how to block by path. */
@Composable
fun HowToCard() {
    SectionCard(stringResource(R.string.howto_title)) {
        Text(stringResource(R.string.howto_body), style = MaterialTheme.typography.bodyMedium)
    }
}
