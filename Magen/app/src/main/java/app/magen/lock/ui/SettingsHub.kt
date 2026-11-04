package app.magen.lock.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.magen.lock.Bio
import app.magen.lock.R
import app.magen.lock.Store

private data class SettingsCategory(
    val id: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
)

@Composable
fun SettingsHub(
    onChangePin: () -> Unit,
    onRecovery: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenProtection: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenLegacy: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val categories = remember {
        listOf(
            SettingsCategory("notifications", "התראות", "הרשאות, התראות הגנה והתראות מכסות", Icons.Filled.Notifications),
            SettingsCategory("appearance", "מראה ושפה", "ערכת נושא, שפה, כיוון כתיבה והנפשות", Icons.Filled.Palette),
            SettingsCategory("security", "אבטחה וכניסה", "קוד אישי, שחזור וזיהוי ביומטרי", Icons.Filled.Lock),
            SettingsCategory("permissions", "הרשאות והגנת מערכת", "הרשאות Android, שירותים ורמת הגנה", Icons.Filled.Security),
            SettingsCategory("privacy", "פרטיות", "מידע מקומי, שיתוף ונתוני אבחון", Icons.Filled.PrivacyTip),
            SettingsCategory("diagnostics", "אבחון ויומן אירועים", "בדיקות, תקלות, היסטוריה ודוחות", Icons.Filled.BugReport),
            SettingsCategory("backup", "גיבוי ושחזור", "שמירת הגדרות ושחזור בטוח", Icons.Filled.Backup),
            SettingsCategory("about", "מידע על מגן", "גרסה, עזרה, קרדיט ופרטי האפליקציה", Icons.Filled.Info),
            SettingsCategory("release", "שחרור והסרה", "פעולות מתקדמות לשחרור Device Owner ולהסרה", Icons.Filled.Settings),
        )
    }
    BackHandler(enabled = selected != null) { selected = null }

    val current = categories.firstOrNull { it.id == selected }
    if (current == null) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("הגדרות", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("כל ההגדרות מחולקות לפי נושא. בחר קטגוריה כדי להציג ולערוך את האפשרויות.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    placeholder = { Text("חיפוש בהגדרות") },
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            ) {
                val shown = categories.filter { query.isBlank() || it.title.contains(query, true) || it.summary.contains(query, true) }
                items(shown, key = { it.id }) { item ->
                    SettingsCategoryRow(item) { selected = item.id }
                }
                if (shown.isEmpty()) item { Text("לא נמצאו הגדרות מתאימות.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    } else {
        SettingsCategoryPage(
            category = current,
            onBack = { selected = null },
            onChangePin = onChangePin,
            onRecovery = onRecovery,
            onOpenHome = onOpenHome,
            onOpenProtection = onOpenProtection,
            onNavigate = onNavigate,
            onOpenLegacy = onOpenLegacy,
        )
    }
}

@Composable
private fun SettingsCategoryRow(item: SettingsCategory, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(item.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
}

@Composable
private fun SettingsCategoryPage(
    category: SettingsCategory,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onRecovery: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenProtection: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenLegacy: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹  הגדרות") }
            Spacer(Modifier.width(8.dp))
            Text(category.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                when (category.id) {
                    "notifications" -> NotificationsSettings()
                    "appearance" -> AppearanceSettings()
                    "security" -> SecuritySettings(ctx, onChangePin, onRecovery)
                    "permissions" -> PermissionsSettings(onOpenHome, onOpenProtection)
                    "privacy" -> PrivacySettings()
                    "diagnostics" -> DiagnosticsSettings(onNavigate, onOpenHome)
                    "backup" -> BackupSettings(onNavigate)
                    "about" -> AboutSettings(ctx)
                    "release" -> ReleaseSettings(onOpenLegacy)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp, top = 3.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, summary: String? = null, initial: Boolean = false) {
    var checked by rememberSaveable(title) { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = { checked = it })
    }
}

@Composable
private fun SettingsAction(title: String, summary: String? = null, action: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotificationsSettings() {
    val context = LocalContext.current
    SettingsGroup("ניהול התראות") {
        SettingsSwitch("התראות מגן", "הודעות על מצב ההגנה ושינויים חשובים", true)
        HorizontalDivider()
        SettingsAction("הרשאת התראות ב־Android", "פתיחת הגדרות ההתראות של המכשיר") {
            runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
        }
        HorizontalDivider()
        SettingsSwitch("התראה קבועה על מצב ההגנה", "הצגת מצב השירות והגנה ברקע", true)
    }
    Spacer(Modifier.size(10.dp))
    SettingsGroup("אירועים ותקלות") {
        SettingsSwitch("התראות על אירועים קריטיים", initial = true)
        HorizontalDivider()
        SettingsSwitch("התראות על אזהרות", initial = true)
        HorizontalDivider()
        SettingsSwitch("התראות על מידע ושינויים רגילים", initial = false)
        HorizontalDivider()
        SettingsSwitch("השאר אירועים קריטיים שלא נקראו בולטים", initial = true)
    }
    Spacer(Modifier.size(10.dp))
    SettingsGroup("מכסות שימוש") {
        SettingsSwitch("התראה לפני סיום מכסה", "ברירת מחדל: 5 דקות לפני החסימה", true)
        SettingsAction("זמן ההתראה המוקדמת", "5 דקות (תצוגה מקדימה)") {}
        SettingsSwitch("צליל להתראות", initial = false)
        SettingsSwitch("רטט להתראות", initial = true)
    }
}

@Composable
private fun AppearanceSettings() {
    val context = LocalContext.current
    var theme by rememberSaveable { mutableIntStateOf(2) }
    SettingsGroup("ערכת נושא") {
        listOf("בהיר", "כהה", "לפי הגדרות המכשיר").forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().clickable { theme = i }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = theme == i, onClick = { theme = i })
                Text(s, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Spacer(Modifier.size(10.dp))
    SettingsGroup("שפה וכיוון") {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("שפת האפליקציה", fontWeight = FontWeight.Medium)
                Text("החלפה מיידית בין עברית לאנגלית", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LanguagePicker()
        }
    }
    Spacer(Modifier.size(10.dp))
    SettingsGroup("תנועה וניווט") {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("הפעלת אנימציות", fontWeight = FontWeight.Medium)
                Text("מכבד גם את הגדרות הפחתת התנועה של Android", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = SpecUiPreferences.animationsEnabled,
                onCheckedChange = { SpecUiPreferences.setAnimationsEnabled(context, it) },
            )
        }
        listOf("סרגל תחתון", "סרגל עליון", "תפריט צד").forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().clickable { SpecUiPreferences.setNavigationMode(context, if (i == 0) 1 else if (i == 1) 2 else 0) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = SpecUiPreferences.navigationMode == (if (i == 0) 1 else if (i == 1) 2 else 0), onClick = { SpecUiPreferences.setNavigationMode(context, if (i == 0) 1 else if (i == 1) 2 else 0) })
                Text(s)
            }
        }
    }
}

@Composable
private fun SecuritySettings(ctx: Context, onChangePin: () -> Unit, onRecovery: () -> Unit) {
    SettingsGroup("קוד כניסה") {
        SettingsAction("שינוי קוד PIN", "יידרש אימות של הקוד הנוכחי") { onChangePin() }
        HorizontalDivider()
        SettingsAction(if (Store.hasRecovery) "חידוש קוד שחזור" else "יצירת קוד שחזור", "קוד חד־פעמי למקרה ששוכחים את הקוד הראשי") { onRecovery() }
    }
    Spacer(Modifier.size(10.dp))
    SettingsGroup("כניסה ביומטרית") {
        if (Bio.available(ctx)) {
            var enabled by rememberSaveable { mutableStateOf(Store.biometric) }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("זיהוי ביומטרי", fontWeight = FontWeight.Medium)
                    Text("פתיחה באמצעות מנגנון הזיהוי של המכשיר", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { checked ->
                    if (!checked) { Store.setBiometric(false); enabled = false }
                    else {
                        val activity = Bio.activity(ctx)
                        if (activity != null) Bio.prompt(activity, ctx.getString(R.string.bio_prompt_title), ctx.getString(R.string.cancel)) {
                            Store.setBiometric(true); enabled = true
                        }
                    }
                })
            }
        } else {
            Text("זיהוי ביומטרי אינו זמין במכשיר זה.", Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.size(10.dp))
    SettingsGroup("הגנה מפני ניסיונות שגויים") {
        SettingsAction("השהיה לאחר קוד שגוי", "לאחר 5 ניסיונות שגויים: 30 שניות; ההשהיה מתארכת בהדרגה") {}
    }
}

@Composable
private fun PermissionsSettings(onOpenHome: () -> Unit, onOpenProtection: () -> Unit) {
    SettingsGroup("מצב הרשאות") {
        SettingsAction("בדיקת הרשאות והגנה", "הצגת מצב שנקרא בפועל מ־Android") { onOpenHome() }
        HorizontalDivider()
        SettingsAction("הרשאות ושירותי Android", "נגישות, מנהל מכשיר, סוללה והתראות") { onOpenHome() }
        HorizontalDivider()
        SettingsAction("הגנה מתקדמת", "מצב רגיל, מצב חזק ובדיקת Device Owner") { onOpenProtection() }
    }
    Spacer(Modifier.size(10.dp))
    SettingsGroup("מידע חשוב") {
        Text("הפעלת הרשאה אינה הוכחה שהשירות פועל. מגן תציג בנפרד הרשאה, שירות פעיל ומצב שניתן לאמת מול Android.", Modifier.padding(vertical = 9.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacySettings() {
    SettingsGroup("מידע ופרטיות") {
        SettingsAction("אחסון מקומי", "הגדרות ונתוני מגן נשמרים במכשיר") {}
        HorizontalDivider()
        SettingsSwitch("אישור לפני שיתוף דוח אבחון", initial = true)
        HorizontalDivider()
        SettingsAction("מה נכלל בדוח אבחון?", "אין לכלול קוד PIN, קוד שחזור או נתוני אימות סודיים") {}
    }
}

@Composable
private fun DiagnosticsSettings(onNavigate: (String) -> Unit, onOpenHome: () -> Unit) {
    SettingsGroup("בדיקות") {
        SettingsAction("בדיקת הגנה", "בדיקה מול מצב ההרשאות והשירותים בפועל") { onOpenHome() }
        HorizontalDivider()
        SettingsAction("יומן אירועים", "אירועים, חומרה, מצב טיפול והיסטוריה") { onNavigate("log") }
    }
    Spacer(Modifier.size(10.dp))
    SettingsGroup("ייצוא וניהול") {
        SettingsAction("ייצוא דוח אבחון", "קובץ קריא ללא סודות או קודי שחזור") { onNavigate("log") }
        SettingsAction("שמירת אירועים", "ברירת מחדל: ללא הגבלת זמן; ניתן לבחור תקופה") { onNavigate("log") }
    }
}

@Composable
private fun BackupSettings(onNavigate: (String) -> Unit) {
    SettingsGroup("גיבוי ושחזור") {
        SettingsAction("פתיחת גיבוי ושחזור", "יצירת גיבוי, בחירת תכולה ושחזור סלקטיבי") { onNavigate("backup") }
        HorizontalDivider()
        SettingsAction("מה לא נכלל בגיבוי?", "קודי PIN ושחזור, חלונות שחרור ומגבלות Android") { onNavigate("backup") }
    }
}

@Composable
private fun AboutSettings(ctx: Context) {
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "—" }.getOrDefault("—")
    }
    SettingsGroup("פרטי האפליקציה") {
        SettingsAction("מגן", "גרסה $version") {}
        HorizontalDivider()
        SettingsAction("עזרה ומדריכים", "הסברים על מצב רגיל, מצב חזק והרשאות") { Session.showGuide = true }
        HorizontalDivider()
        SettingsAction("הערשלה", "פרופיל המפתח במתמחים טופ") {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mitmachim.top/user/%D7%94%D7%A2%D7%A8%D7%A9%D7%9C%D7%94"))) }
        }
    }
}

@Composable
private fun ReleaseSettings(onOpenLegacy: () -> Unit) {
    SettingsGroup("פעולות רגישות") {
        Text("שחרור Device Owner והסרת מגן הן פעולות נפרדות ורגישות. הן נשארות במסך הייעודי הקיים, ללא שינוי במנגנון.", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsAction("פתיחת מסך שחרור והסרה", "כולל שחרור רגיל, העברה ל־Magen Keeper ואפשרות Root") { onOpenLegacy() }
    }
}
