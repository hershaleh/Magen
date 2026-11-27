package app.magen.lock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.magen.lock.Catalog
import app.magen.lock.Store
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
private val gap = 12.dp

/** Presentation preference; enforcement settings are deliberately kept elsewhere. */
object SpecUiPreferences {
    var navigationMode by mutableIntStateOf(1) // 0 = overlay drawer, 1 = bottom, 2 = top
    var animationsEnabled by mutableStateOf(true)
    var themeMode by mutableIntStateOf(2) // 0 = light, 1 = dark, 2 = follow system

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("magen_ui", Context.MODE_PRIVATE)
        navigationMode = prefs.getInt("navigationMode", 1).coerceIn(0, 2)
        animationsEnabled = prefs.getBoolean("animationsEnabled", true)
        themeMode = prefs.getInt("themeMode", 2).coerceIn(0, 2)
    }

    fun setNavigationMode(context: Context, mode: Int) {
        navigationMode = mode.coerceIn(0, 2)
        context.getSharedPreferences("magen_ui", Context.MODE_PRIVATE).edit().putInt("navigationMode", navigationMode).apply()
    }

    fun setAnimationsEnabled(context: Context, enabled: Boolean) {
        animationsEnabled = enabled
        context.getSharedPreferences("magen_ui", Context.MODE_PRIVATE).edit().putBoolean("animationsEnabled", enabled).apply()
    }

    fun setThemeMode(context: Context, mode: Int) {
        themeMode = mode.coerceIn(0, 2)
        context.getSharedPreferences("magen_ui", Context.MODE_PRIVATE).edit().putInt("themeMode", themeMode).apply()
    }

    fun shouldAnimate(context: Context): Boolean {
        if (!animationsEnabled) return false
        return runCatching {
            val resolver = context.contentResolver
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f &&
                Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}

@Composable
private fun SpecPage(title: String, subtitle: String? = null, preview: Boolean = true, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
        if (preview) Text("תצוגה מקדימה: חלק מהאפשרויות עדיין אינן מחוברות למנגנון האכיפה.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
    }
}
@Composable
private fun SpecCard(title: String, description: String? = null, content: @Composable () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
@Composable
private fun SpecToggle(title: String, detail: String? = null, initial: Boolean = false) {
    var checked by rememberSaveable(title) { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = { checked = it })
    }
}
@Composable
private fun SpecField(label: String, initial: String = "") {
    var value by rememberSaveable(label) { mutableStateOf(initial) }
    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
fun BlockingsHub() {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val labels = listOf("חסימת אפליקציות", "חסימת חלקי הגדרות", "חסימות נוספות")
    val icons = listOf(Icons.Filled.Apps, Icons.Filled.Settings, Icons.Filled.Security)
    BackHandler(enabled = selected >= 0) { selected = -1 }
    if (selected >= 0) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { selected = -1 }, modifier = Modifier.padding(start = 8.dp, top = 4.dp).testTag("blocking_back")) {
                Text("‹  חסימות")
            }
            Box(Modifier.weight(1f)) {
                when (selected) {
                    0 -> AppsTab()
                    1 -> SystemComponentsScreen()
                    else -> CustomRulesScreen()
                }
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("חסימות", style = MaterialTheme.typography.headlineSmall)
            labels.forEachIndexed { i, label ->
                Card(
                    modifier = Modifier.fillMaxWidth().height(88.dp).clickable { selected = i }
                        .testTag("blocking_tab_$i"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(icons[i], contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Text("‹", style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun TimesHub() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("כל המכשיר", "קבוצות", "שבת וחגים")
    Column(Modifier.fillMaxSize()) {
        Text("זמנים", Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineSmall)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            labels.forEachIndexed { i, label ->
                Button(
                    onClick = { selected = i },
                    modifier = Modifier.weight(1f).testTag("times_tab_$i"),
                ) {
                    Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (selected) {
                0 -> WholeDeviceScheduleScreen()
                1 -> GroupsManagementScreen()
                else -> ShabbatScreen()
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun WholeDeviceScheduleScreen() {
    SpecPage("לוח זמנים לכל המכשיר", "זמני חסימה שחלים על כל האפליקציות המנוהלות, בנוסף ללוח הזמנים של כל אפליקציה. אפליקציות חיוניות לא נחסמות.", preview = false) { DeviceScheduleEditor() }
}

@Composable
private fun GroupsManagementScreen() {
    SpecPage("מכסות משותפות", "קבוצת אפליקציות שחולקת מכסה יומית אחת.", preview = false) { GroupsRules() }
}

@Composable
private fun QuotasScreen() {
    SpecPage("מכסות שימוש", "בחר אפליקציה כדי להגדיר מכסה יומית. השימוש נספר רק כשהאפליקציה בחזית ומתאפס בחצות המקומית.", preview = false) { AppsRules() }
}
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun SchedulesScreen() {
    SpecPage("לוחות זמנים", "בחר אפליקציה כדי להגדיר זמני חסימה שבועיים, מעבר חצות וחריגים לפי תאריך.", preview = false) { AppsRules() }
}
@Composable
private fun ShabbatScreen() {
    SpecPage("חסימה בשבתות ובחגים יהודיים", "חסימת האפליקציות המנוהלות בשבת ובימים טובים. חול המועד אינו נחסם.", preview = false) { ShabbatRules() }
}
@Composable
private fun SystemComponentsScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = Catalog.settings.groupBy { it.group }
    val selected = Catalog.settings.firstOrNull { it.id == selectedId }
    val nestedIds = when (selected?.id) {
        "network" -> setOf("wifi", "mobile", "hotspot", "datausage", "privatedns", "bluetooth", "nfc", "cast", "print", "usb", "vpn")
        else -> emptySet()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("חסימת חלקי הגדרות", style = MaterialTheme.typography.titleLarge)
        Text("בחר קטגוריה כדי לפתוח את תתי־ההגדרות שלה. הפעלת חסימה בקטגוריה תחסום רק את המסך המרכזי שלה.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SpecCard("איך למצוא חלק מסוים בהגדרות?") {
            Text("פתח את אפליקציית ההגדרות במכשיר, נווט למסך המבוקש וחזור למגן. אפשר גם לחפש לפי שם ההגדרה או מילת מפתח.")
            Text("במכשירים נתמכים אפשר להפעיל את כלי הבחירה מתוך שירות הנגישות כדי לזהות את המסך הפתוח.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("חיפוש בהגדרות") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        groups.forEach { (group, categories) ->
            SpecCard(stringResource(group.title)) {
                categories.forEach { cat ->
                    val label = stringResource(cat.title)
                    if (query.isBlank() || label.contains(query, true) || cat.id.contains(query, true) ||
                        cat.keywords.any { it.contains(query, true) }) {
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedId = cat.id }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(cat.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.Medium)
                            Text(if (cat.id == "network") "תתי־הגדרות: VPN, נקודה חמה ועוד" else "פתיחת אפשרויות החסימה",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (cat.id in Store.blockedSettings) "חסום" else "›",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RowDivider()
                    }
                }
            }
        }
    }
    if (selected != null) {
        val label = stringResource(selected.title)
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text(label) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SwitchListRow(
                        title = "חסימת $label",
                        checked = selected.id in Store.blockedSettings,
                        onChange = { Store.setBlockedSetting(selected.id, it) },
                        supporting = if (selected.restriction != null) "חסימה מרכזית; אינה כוללת תתי־הגדרות" else "חסימת המסך המרכזי בלבד",
                        icon = selected.icon,
                    )
                    if (nestedIds.isNotEmpty()) {
                        Text("תתי־הגדרות", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        nestedIds.mapNotNull { id -> Catalog.settings.firstOrNull { it.id == id } }.forEach { child ->
                            SwitchListRow(
                                title = stringResource(child.title),
                                checked = child.id in Store.blockedSettings,
                                onChange = { Store.setBlockedSetting(child.id, it) },
                                supporting = "חסימה נפרדת של תת־ההגדרה",
                                icon = child.icon,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedId = null }) { Text("סיום") } },
        )
    }
}

@Composable
private fun CustomRulesScreen() {
    var keyword by rememberSaveable { mutableStateOf("") }
    SpecPage("כללים מותאמים אישית", "כללים נוספים מעבר לחסימת אפליקציות ולהגדרות המערכת.") {
        SpecCard("מילות מפתח וכותרות") {
            OutlinedTextField(value = keyword, onValueChange = { keyword = it }, label = { Text("מילת מפתח או כותרת מסך") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { Store.addKeyword(keyword); keyword = "" }, enabled = keyword.isNotBlank()) { Text("הוספת כלל") }
            Store.customKeywords.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item, Modifier.weight(1f))
                    TextButton(onClick = { Store.removeKeyword(item) }) { Text("הסרה") }
                }
            }
            Text("כלל מותאם צריך להיות ממוקד כדי שלא לחסום מסכים שאינם קשורים.")
        }
        SpecCard("VideoStop", "זיהוי מסכי וידאו ונגנים באפליקציות ובדפדפן.") {
            VideoStopEditor()
        }
    }
}
@Composable
fun NotificationsCenterScreen() {
    EventLogPanel(
        title = "התראות",
        subtitle = "מרכז ההתראות והאירועים של מגן. מצב השירות מוצג בנפרד מהעדפות החסימה.",
        showProtectionStatus = true,
    )
}

@Composable
fun SpecRouteScreen(route: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) { Text("חזרה") }
        Box(Modifier.weight(1f)) {
            when (route) { "log" -> SecurityLogScreen(); "backup" -> BackupScreen(); "rules" -> RulesScreen(onClose = onBack); else -> AppSettingsScreen() }
        }
    }
}
@Composable
private fun SecurityLogScreen() {
    EventLogPanel(
        title = "יומן אבטחה",
        subtitle = "תיעוד אירועי הגנה, שינויים, כשלים וניסיונות עקיפה.",
        showProtectionStatus = false,
    )
}
@Composable
private fun BackupScreen() {
    SpecPage("גיבוי ושחזור", "גיבוי מקומי של הגדרות מגן, ללא קודי סוד, מונים או חלונות זמן פעילים.", preview = false) { BackupPage() }
}
@Composable
private fun AppSettingsScreen() {
    val context = LocalContext.current
    SpecPage("הגדרות", "התאמת התצוגה, השפה והתנהגות האפליקציה.") {
        SpecCard("שפה") {
            var language by rememberSaveable { mutableStateOf(Lang.current()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language.startsWith("he"), onClick = { language = "he"; Lang.set("he") }, label = { Text("עברית") })
                FilterChip(selected = language.startsWith("en"), onClick = { language = "en"; Lang.set("en") }, label = { Text("English") })
                FilterChip(selected = language.isBlank(), onClick = { language = ""; Lang.set("") }, label = { Text("שפת המכשיר") })
            }
            Text("החלפת השפה משנה את שפת הממשק ואת כיוון הכתיבה.")
        }
        SpecCard("מראה") {
            val theme = SpecUiPreferences.themeMode
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("בהיר", "כהה", "לפי המכשיר").forEachIndexed { i, label ->
                    FilterChip(selected = theme == i, onClick = { SpecUiPreferences.setThemeMode(context, i) }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("הפעלת אנימציות", Modifier.weight(1f))
                Switch(
                    checked = SpecUiPreferences.animationsEnabled,
                    onCheckedChange = { SpecUiPreferences.setAnimationsEnabled(context, it) },
                )
            }
            Text("בחירת ערכת הנושא מוחלת מיד ונשמרת גם לאחר הפעלה מחדש.")
        }
        SpecCard("ניווט") {
            listOf("תפריט צד", "סרגל תחתון", "סרגל עליון").forEachIndexed { i, label ->
                FilterChip(selected = SpecUiPreferences.navigationMode == i, onClick = { SpecUiPreferences.setNavigationMode(context, i) }, label = { Text(label) })
            }
            Text("בחירת מיקום הניווט משנה מיד את מבנה הניווט.")
        }
        SpecCard("אבחון משאבים") {
            val runtime = Runtime.getRuntime()
            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val totalMb = runtime.totalMemory() / (1024 * 1024)
            Text("זיכרון JVM בשימוש: $usedMb MB מתוך $totalMb MB")
            Text("Android ${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}")
            Text("אלה מדדי תהליך בסיסיים בלבד; ניטור CPU, סוללה ושירותים יתחבר בהמשך.")
        }
        SpecCard("פרטיות") {
            SpecToggle("בקשת אישור לפני שיתוף דוח אבחון")
            Text("מגן פועלת מקומית. לא נדרש חשבון ענן או שירות בתשלום.")
        }
    }
}
