package app.magen.lock.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.app.admin.DevicePolicyManager
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.magen.lock.GuardService
import app.magen.lock.Policy
import app.magen.lock.R
import app.magen.lock.Store

// ---- real protection state (Stage 2) ---------------------------------------------------------
// Everything here is read from Android or from the running service at the moment of the check.
// A saved preference is never reported as "working" on its own.

enum class Sev { OK, INFO, WARN, BAD, UNKNOWN }

/** Where the person can go to fix a problem. Never changes a setting by itself. */
enum class Fix { ACCESSIBILITY, ADMIN, BATTERY, NOTIFICATIONS, STRONG_GUIDE, MORE_TAB }

class Check(
    val key: String,
    val sev: Sev,
    @StringRes val title: Int,
    @StringRes val detail: Int,
    val fix: Fix? = null,
    val args: List<Any> = emptyList(),
)

class Health(val overall: Sev, val checks: List<Check>) {
    /** Warnings and problems (not info or "cannot verify"). */
    val issues: Int get() = checks.count { it.sev == Sev.WARN || it.sev == Sev.BAD }
}

/** True when at least one app, block or Settings rule is selected, i.e. there is something to enforce. */
fun hasAnyRule(): Boolean =
    Store.locked.isNotEmpty() || Store.blockPlay || Store.blockApk ||
        Store.blockedSettings.isNotEmpty() || Store.customKeywords.isNotEmpty()

/**
 * Waits for Android to show the expected Device Owner state. It is checked at once and once more after a
 * moment, because Android may take a short time to update. [done] gets the real answer; it runs on the main thread.
 */
fun verifyOwnership(ctx: Context, expectOwner: Boolean, done: (Boolean) -> Unit) {
    fun ok() = Policy.isOwner(ctx) == expectOwner
    if (ok()) {
        done(true)
    } else {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ done(ok()) }, 1500)
    }
}

const val TAB_APPS = 1
const val TAB_MORE = 4

fun computeHealth(ctx: Context): Health {
    val p = readPerms(ctx)
    // The service object exists only while Android has really started the accessibility service.
    val running = GuardService.instance != null
    val list = ArrayList<Check>()

    list += when {
        running -> Check("acc", Sev.OK, R.string.chk_acc, R.string.chk_acc_ok)
        p.acc -> Check("acc", Sev.BAD, R.string.chk_acc, R.string.chk_acc_stalled, Fix.ACCESSIBILITY)
        else -> Check("acc", Sev.BAD, R.string.chk_acc, R.string.chk_acc_off, Fix.ACCESSIBILITY)
    }

    list += if (p.admin) {
        Check("admin", Sev.OK, R.string.chk_admin, R.string.chk_admin_ok)
    } else {
        Check("admin", Sev.WARN, R.string.chk_admin, R.string.chk_admin_off, Fix.ADMIN)
    }

    // Device Owner comes from Android itself (DevicePolicyManager), not from the saved level.
    list += when {
        p.owner -> Check("owner", Sev.OK, R.string.chk_owner, R.string.chk_owner_ok)
        Store.level == "strong" -> Check("owner", Sev.WARN, R.string.chk_owner, R.string.chk_owner_missing, Fix.STRONG_GUIDE)
        else -> Check("owner", Sev.INFO, R.string.chk_owner, R.string.chk_owner_na)
    }

    val selected = Store.locked.keys
    val installed = selected.count { pkg ->
        try {
            ctx.packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    list += when {
        selected.isEmpty() -> Check("apps", Sev.INFO, R.string.chk_apps, R.string.chk_apps_none)
        !running || !Store.hasPin ->
            Check("apps", Sev.BAD, R.string.chk_apps, R.string.chk_apps_missing, Fix.ACCESSIBILITY, listOf(selected.size))
        else -> Check("apps", Sev.OK, R.string.chk_apps, R.string.chk_apps_ok, null, listOf(installed, selected.size))
    }

    list += if (p.battery) {
        Check("battery", Sev.OK, R.string.chk_battery, R.string.chk_battery_ok)
    } else {
        Check("battery", Sev.WARN, R.string.chk_battery, R.string.chk_battery_warn, Fix.BATTERY)
    }

    list += if (runCatching { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }.getOrDefault(true)) {
        Check("notif", Sev.OK, R.string.chk_notif, R.string.chk_notif_ok)
    } else {
        Check("notif", Sev.WARN, R.string.chk_notif, R.string.chk_notif_warn, Fix.NOTIFICATIONS)
    }

    if (Store.uninstallAllowed()) list += Check("pause", Sev.WARN, R.string.chk_pause, R.string.chk_pause_detail)
    if (Policy.staleUninstallBlock(ctx)) {
        list += Check("stale", Sev.WARN, R.string.chk_stale, R.string.chk_stale_detail, Fix.MORE_TAB)
    }

    // Honest about the limits: these cannot be tested from inside the app.
    list += Check("unknown", Sev.UNKNOWN, R.string.chk_unknown, R.string.chk_unknown_detail)

    val overall = when {
        list.any { it.sev == Sev.BAD } -> Sev.BAD
        list.any { it.sev == Sev.WARN } -> Sev.WARN
        else -> Sev.OK
    }
    return Health(overall, list)
}

// ---- UI --------------------------------------------------------------------------------------

@Composable
internal fun sevColor(sev: Sev): Color {
    val dark = isSystemInDarkTheme()
    return when (sev) {
        Sev.OK -> if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
        Sev.WARN -> if (dark) Color(0xFFFFB74D) else Color(0xFFE65100)
        Sev.BAD -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@StringRes
internal fun sevLabel(sev: Sev): Int = when (sev) {
    Sev.OK -> R.string.sev_ok
    Sev.WARN -> R.string.sev_warn
    Sev.BAD -> R.string.sev_bad
    Sev.INFO -> R.string.sev_info
    Sev.UNKNOWN -> R.string.sev_unknown
}

internal fun sevIcon(sev: Sev): ImageVector = when (sev) {
    Sev.OK -> Icons.Filled.CheckCircle
    Sev.WARN -> Icons.Filled.Warning
    Sev.BAD -> Icons.Filled.Error
    else -> Icons.Filled.Info
}

@Composable
private fun CheckRow(c: Check, showFix: Boolean, onFix: (Fix) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(sevIcon(c.sev), stringResource(sevLabel(c.sev)), tint = sevColor(c.sev), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(stringResource(c.title), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(c.detail, *c.args.toTypedArray()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showFix && c.fix != null && (c.sev == Sev.WARN || c.sev == Sev.BAD)) {
                TextButton(onClick = { onFix(c.fix) }) { Text(stringResource(fixLabel(c.fix))) }
            }
        }
    }
}

@StringRes
internal fun fixLabel(f: Fix): Int = when (f) {
    Fix.ACCESSIBILITY -> R.string.fix_accessibility
    Fix.ADMIN -> R.string.fix_admin
    Fix.BATTERY -> R.string.fix_battery
    Fix.NOTIFICATIONS -> R.string.fix_notifications
    Fix.STRONG_GUIDE -> R.string.fix_strong_guide
    Fix.MORE_TAB -> R.string.fix_more
}

/** Opens the screen where the person can fix the problem. Nothing is changed without their action there. */
internal fun openFix(ctx: Context, fix: Fix, onNavigate: (Int) -> Unit) {
    // Same launch as the buttons on the Home tab. The device-admin approval screen must be started from
    // an Activity WITHOUT FLAG_ACTIVITY_NEW_TASK (the Home button does exactly that); other Settings
    // screens are opened as before. A failure is reported instead of being swallowed.
    fun go(i: Intent, newTask: Boolean = true, @StringRes failed: Int = R.string.settings_open_failed) {
        val activity = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is android.app.Activity }
        Session.trip()
        try {
            if (activity != null && !newTask) {
                activity.startActivity(i)
            } else {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: Exception) {
            Session.cancelTrip()
            android.widget.Toast.makeText(ctx, ctx.getString(failed), android.widget.Toast.LENGTH_LONG).show()
        }
    }
    when (fix) {
        Fix.ACCESSIBILITY -> go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Fix.ADMIN -> go(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Policy.admin(ctx))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ctx.getString(R.string.admin_desc)),
            newTask = false,
            failed = R.string.admin_open_failed,
        )
        Fix.BATTERY -> go(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        Fix.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= 26) {
                go(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            } else {
                go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")))
            }
        Fix.STRONG_GUIDE -> Session.showGuide = true
        Fix.MORE_TAB -> onNavigate(TAB_MORE)
    }
}

@Composable
fun HomeTab(onNavigate: (Int) -> Unit) {
    val ctx = LocalContext.current
    val p = rememberPerms()
    // Re-read when the person comes back from Android settings, and again on every press of the check button.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    var checkedAt by remember { mutableStateOf<Long?>(null) }
    val locked = Store.locked
    val level = Store.level
    val health = remember(tick, p, locked, level) { computeHealth(ctx) }

    val idle = health.overall == Sev.OK && !hasAnyRule()
    TabColumn {
        HeroCard {
            Icon(
                if (idle) sevIcon(Sev.INFO) else sevIcon(health.overall),
                null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    when {
                        idle -> R.string.status_idle
                        else -> when (health.overall) {
                        Sev.OK -> R.string.status_full
                        Sev.WARN -> R.string.status_partial
                        else -> R.string.status_none
                        }
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                when {
                    health.issues > 0 -> stringResource(R.string.status_sub_issues, health.issues)
                    idle -> stringResource(R.string.status_sub_idle)
                    else -> stringResource(R.string.status_sub_ok)
                },
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        SectionCard(stringResource(R.string.glance_title)) {
            health.checks.filter { it.key == "acc" || it.key == "owner" || it.key == "apps" }
                .forEach { CheckRow(it, showFix = false, onFix = {}) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                val sev = if (health.issues == 0) Sev.OK else Sev.WARN
                Icon(sevIcon(sev), stringResource(sevLabel(sev)), tint = sevColor(sev), modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(stringResource(R.string.glance_issues_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (health.issues == 0) {
                            stringResource(R.string.glance_issues_none)
                        } else {
                            stringResource(R.string.glance_issues_some, health.issues)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = {
                    tick++
                    checkedAt = System.currentTimeMillis()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Icon(Icons.Filled.FactCheck, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.check_button))
            }
        }

        val at = checkedAt
        if (at != null) {
            SectionCard(
                stringResource(R.string.check_title),
                stringResource(R.string.check_time, java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(at))),
            ) {
                health.checks.forEach { CheckRow(it, showFix = true) { fix -> openFix(ctx, fix, onNavigate) } }
            }
        }

        PermissionCards(p, showOwner = true)
    }
}

// ---- Protection tab: per-option state (Stage 5) ----------------------------------------------

/** One line under an option: what its saved setting is, and whether a running mechanism enforces it. */
class Line(
    val sev: Sev,
    @StringRes val text: Int,
    val fix: Fix? = null,
    val args: List<Any> = emptyList(),
    @StringRes val title: Int? = null,
)

private fun androidRestricts(ctx: Context, key: String): Boolean =
    runCatching { ctx.getSystemService(android.os.UserManager::class.java).hasUserRestriction(key) }.getOrDefault(false)

fun playLine(running: Boolean): Line = when {
    !Store.blockPlay -> Line(Sev.INFO, R.string.ln_off)
    running -> Line(Sev.OK, R.string.ln_play_ok)
    else -> Line(Sev.BAD, R.string.ln_needs_service, Fix.ACCESSIBILITY)
}

fun apkLine(ctx: Context, p: Perms, running: Boolean): Line {
    if (!Store.blockApk) return Line(Sev.INFO, R.string.ln_off)
    // With Device Owner, Android keeps its own restriction; read it from Android, not from the saved setting.
    val androidOn = p.owner && androidRestricts(ctx, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
    return when {
        running && androidOn -> Line(Sev.OK, R.string.ln_apk_both)
        running -> Line(Sev.OK, R.string.ln_apk_service)
        androidOn -> Line(Sev.WARN, R.string.ln_apk_android_only, Fix.ACCESSIBILITY)
        else -> Line(Sev.BAD, R.string.ln_needs_service, Fix.ACCESSIBILITY)
    }
}

fun settingsLine(running: Boolean): Line {
    val groups = Store.blockedSettings.size
    val words = Store.customKeywords.size
    return when {
        groups + words == 0 -> Line(Sev.INFO, R.string.ln_settings_none)
        running -> Line(Sev.OK, R.string.ln_settings_ok, null, listOf(groups, words))
        else -> Line(Sev.BAD, R.string.ln_needs_service, Fix.ACCESSIBILITY)
    }
}

fun selfLine(p: Perms, running: Boolean): Line = when {
    !Store.protect -> Line(Sev.INFO, R.string.ln_off)
    Store.uninstallAllowed() -> Line(Sev.WARN, R.string.ln_self_paused)
    running && p.admin -> Line(Sev.OK, R.string.ln_self_ok)
    running -> Line(Sev.WARN, R.string.ln_self_no_admin, Fix.ADMIN)
    p.admin -> Line(Sev.WARN, R.string.ln_self_no_service, Fix.ACCESSIBILITY)
    else -> Line(Sev.BAD, R.string.ln_self_none, Fix.ACCESSIBILITY)
}

// Device Owner and the regular device admin are different things; each line names itself and says so.
fun ownerLine(p: Perms): Line = when {
    p.owner -> Line(Sev.OK, R.string.own_on, title = R.string.own_title)
    Store.level == "strong" -> Line(Sev.WARN, R.string.own_missing, Fix.STRONG_GUIDE, title = R.string.own_title)
    else -> Line(Sev.INFO, R.string.own_na, title = R.string.own_title)
}

fun adminLine(p: Perms): Line =
    if (p.admin) {
        Line(Sev.OK, R.string.adm_on, title = R.string.adm_title)
    } else {
        Line(Sev.WARN, R.string.adm_off, Fix.ADMIN, title = R.string.adm_title)
    }

@Composable
fun StatusLine(line: Line, onFix: (Fix) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(sevIcon(line.sev), stringResource(sevLabel(line.sev)), tint = sevColor(line.sev), modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            if (line.title != null) Text(stringResource(line.title), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(line.text, *line.args.toTypedArray()),
                style = MaterialTheme.typography.bodySmall,
                color = if (line.sev == Sev.BAD || line.sev == Sev.WARN) sevColor(line.sev) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val fix = line.fix
            if (fix != null && (line.sev == Sev.WARN || line.sev == Sev.BAD)) {
                TextButton(onClick = { onFix(fix) }) { Text(stringResource(fixLabel(fix))) }
            }
        }
    }
}
