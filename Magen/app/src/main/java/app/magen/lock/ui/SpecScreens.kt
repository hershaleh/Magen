package app.magen.lock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("magen_ui", Context.MODE_PRIVATE)
        navigationMode = prefs.getInt("navigationMode", 1).coerceIn(0, 2)
        animationsEnabled = prefs.getBoolean("animationsEnabled", true)
    }

    fun setNavigationMode(context: Context, mode: Int) {
        navigationMode = mode.coerceIn(0, 2)
        context.getSharedPreferences("magen_ui", Context.MODE_PRIVATE).edit().putInt("navigationMode", navigationMode).apply()
    }

    fun setAnimationsEnabled(context: Context, enabled: Boolean) {
        animationsEnabled = enabled
        context.getSharedPreferences("magen_ui", Context.MODE_PRIVATE).edit().putBoolean("animationsEnabled", enabled).apply()
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
private fun SpecPage(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
        Text("תצוגה מקדימה: חלק מהאפשרויות עדיין אינן מחוברות למנגנון האכיפה.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
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
    var enabled by rememberSaveable { mutableStateOf(false) }
    var windowCount by rememberSaveable { mutableIntStateOf(1) }
    var days by rememberSaveable { mutableStateOf(setOf("ראשון", "שני", "שלישי", "רביעי", "חמישי")) }
    val allDays = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
    SpecPage("לוח זמנים לכל המכשיר", "כאן מוגדרים חלונות החסימה הכלליים בלבד. לוחות זמנים לאפליקציה או לכלל מסוים נמצאים בתוך הגדרות החסימה המתאימות.") {
        SpecCard("הפעלת לוח הזמנים") {
            SpecToggle("הפעלת חסימה לפי לוח זמנים", initial = enabled)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                allDays.forEach { day ->
                    FilterChip(
                        selected = day in days,
                        onClick = { days = if (day in days) days - day else days + day },
                        label = { Text(day) },
                    )
                }
            }
        }
        repeat(windowCount) { index ->
            SpecCard("חלון חסימה ${index + 1}") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { SpecField("משעה", if (index == 0) "22:00" else "08:00") }
                    Column(Modifier.weight(1f)) { SpecField("עד שעה", if (index == 0) "07:00" else "09:00") }
                }
                SpecToggle("החלון חוצה חצות")
            }
        }
        SpecCard("ניהול חלונות") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { windowCount = (windowCount + 1).coerceAtMost(5) }, enabled = windowCount < 5) { Text("הוספת חלון") }
                TextButton(onClick = { windowCount = (windowCount - 1).coerceAtLeast(1) }, enabled = windowCount > 1) { Text("הסרת חלון") }
            }
        }
    }
}

@Composable
private fun GroupsManagementScreen() {
    var groups by rememberSaveable { mutableStateOf(listOf("קבוצה חדשה")) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var newName by rememberSaveable { mutableStateOf("") }
    SpecPage("קבוצות", "קבוצות מאפשרות להגדיר לוח זמנים משותף לאפליקציות המשויכות אליהן. מכסות אישיות וקבוצתיות מוגדרות בתוך הגדרות האפליקציות.") {
        SpecCard("הקבוצות שלי") {
            groups.forEachIndexed { index, name ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = selected == index, onClick = { selected = index })
                    Text(name, Modifier.weight(1f))
                    TextButton(onClick = {
                        if (groups.size > 1) {
                            groups = groups.filterIndexed { i, _ -> i != index }
                            selected = 0
                        }
                    }, enabled = groups.size > 1) { Text("הסרה") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("שם קבוצה חדשה") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        groups = groups + newName.trim()
                        selected = groups.lastIndex
                        newName = ""
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("הוספה") }
            }
        }
        SpecCard("לוח הזמנים של ${groups.getOrElse(selected) { "הקבוצה" }}") {
            SpecToggle("הפעלת לוח זמנים לקבוצה")
            Text("בחירת ימים וחלונות זמן תוגדר כאן. אפליקציה יכולה להשתייך לקבוצה אחת בלבד; מעבר קבוצה אינו מוחק שימוש קודם.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { SpecField("משעה", "16:00") }
                Column(Modifier.weight(1f)) { SpecField("עד שעה", "20:00") }
            }
            SpecToggle("החלון חוצה חצות")
        }
    }
}

@Composable
private fun QuotasScreen() {
    var selectedApp by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var groupMinutes by rememberSaveable { mutableIntStateOf(120) }
    var warningMinutes by rememberSaveable { mutableIntStateOf(5) }
    var groupSelected by rememberSaveable { mutableIntStateOf(0) }
    val apps = listOf("דפדפן", "YouTube", "Telegram", "משחקים")
    val groups = listOf("רשתות חברתיות", "וידאו", "משחקים")
    SpecPage("מכסות שימוש", "מכסה אישית לכל אפליקציה ומכסה משותפת לקבוצה. השימוש נספר רק כשהאפליקציה בחזית ומתאפס בחצות המקומית.") {
        SpecCard("בחירת אפליקציה") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                apps.forEachIndexed { i, app -> FilterChip(selected = selectedApp == i, onClick = { selectedApp = i }, label = { Text(app) }) }
            }
            Text("שימוש היום: 42 דקות מתוך $minutes דקות", style = MaterialTheme.typography.bodyMedium)
            Text("נותרו: ${(minutes - 42).coerceAtLeast(0)} דקות", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("מכסה אישית — ${apps[selectedApp]}", "0 דקות פירושן חסימה לכל היום. שינוי מכסה אינו מאפס שימוש שכבר נצבר.") {
            Text(if (minutes == 0) "חסימה מלאה למשך היום" else "מכסה יומית: $minutes דקות")
            Slider(value = minutes.toFloat(), onValueChange = { minutes = it.toInt() }, valueRange = 0f..360f, steps = 35)
            SpecToggle("הפעלת מכסה אישית", initial = true)
            SpecToggle("השהיית המכסה האישית")
        }
        SpecCard("התראה לפני סיום") {
            Text("התראה $warningMinutes דקות לפני סיום המכסה")
            Slider(value = warningMinutes.toFloat(), onValueChange = { warningMinutes = it.toInt() }, valueRange = 1f..30f, steps = 28)
            Text("ברירת המחדל היא 5 דקות. כשהמכסה מסתיימת, החסימה אמורה להיכנס לתוקף מיד גם אם האפליקציה פתוחה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("קבוצות מכסה") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEachIndexed { i, group -> FilterChip(selected = groupSelected == i, onClick = { groupSelected = i }, label = { Text(group) }) }
            }
            SpecField("שם הקבוצה", groups[groupSelected])
            Text("מכסה משותפת: $groupMinutes דקות")
            Slider(value = groupMinutes.toFloat(), onValueChange = { groupMinutes = it.toInt() }, valueRange = 0f..360f, steps = 35)
            Text("שימוש קבוצתי היום: 86 דקות מתוך $groupMinutes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("כל אפליקציה יכולה להשתייך לקבוצה אחת לכל היותר. אפשר להפעיל גם מכסה אישית וגם קבוצתית; אם אחת מהן מסתיימת — האפליקציה נחסמת.", style = MaterialTheme.typography.bodySmall)
        }
        SpecCard("שיוך אפליקציות לקבוצה") {
            apps.forEach { app -> SpecToggle(app, "שיוך לקבוצה הנבחרת") }
            Text("מעבר קבוצה חל מיד: שימוש קודם נשאר בקבוצה הקודמת, ושימוש חדש נספר בקבוצה החדשה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("השהיות והפסקות") {
            SpecToggle("השהיית המכסה הקבוצתית")
            SpecToggle("הוספת הפסקה מתוזמנת")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { SpecField("התחלה", "13:00") }
                Column(Modifier.weight(1f)) { SpecField("סיום", "14:00") }
            }
            Text("השהיה של מכסה אישית אינה משהה מכסה קבוצתית, ולהפך. הפסקה אינה מבטלת חסימות אחרות.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun SchedulesScreen() {
    var selectedApp by rememberSaveable { mutableIntStateOf(0) }
    var selectedDays by rememberSaveable { mutableStateOf(setOf("ראשון", "שני", "שלישי", "רביעי", "חמישי")) }
    var windowCount by rememberSaveable { mutableIntStateOf(1) }
    var exceptionMode by rememberSaveable { mutableIntStateOf(0) }
    val apps = listOf("דפדפן", "YouTube", "Telegram", "משחקים")
    val days = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
    SpecPage("לוחות זמנים", "הגדר חלונות שבהם אפליקציה מותרת או חסומה. ניתן ליצור כמה חלונות ביום, כולל חלונות שחוצים חצות.") {
        SpecCard("בחירת אפליקציה") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                apps.forEachIndexed { i, app -> FilterChip(selected = selectedApp == i, onClick = { selectedApp = i }, label = { Text(app) }) }
            }
        }
        SpecCard("ימים פעילים") {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { day ->
                    FilterChip(selected = day in selectedDays, onClick = {
                        selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                    }, label = { Text(day) })
                }
            }
        }
        repeat(windowCount) { index ->
            SpecCard("חלון זמן ${index + 1}") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { SpecField("משעה", if (index == 0) "08:00" else "16:00") }
                    Column(Modifier.weight(1f)) { SpecField("עד שעה", if (index == 0) "13:00" else "20:00") }
                }
                SpecToggle("חלון זה חוצה חצות", initial = false)
                SpecToggle("חסימה בכל היום", initial = false)
            }
        }
        SpecCard("ניהול חלונות") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { windowCount = (windowCount + 1).coerceAtMost(5) }, enabled = windowCount < 5) { Text("הוספת חלון") }
                TextButton(onClick = { windowCount = (windowCount - 1).coerceAtLeast(1) }, enabled = windowCount > 1) { Text("הסרת חלון") }
            }
            Text("אפשר להגדיר חלונות נפרדים לאותו יום. חלונות חופפים אינם יוצרים היתר נוסף.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("הפסקות וחריגים לתאריכים") {
            SpecToggle("הוספת הפסקה קבועה")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { SpecField("תחילת הפסקה", "13:00") }
                Column(Modifier.weight(1f)) { SpecField("סיום הפסקה", "14:00") }
            }
            SpecField("תאריך חריג", "DD/MM/YYYY")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ללא חריג", "לחסום", "לאפשר").forEachIndexed { i, label ->
                    FilterChip(selected = exceptionMode == i, onClick = { exceptionMode = i }, label = { Text(label) })
                }
            }
            Text("כאשר לוחות זמנים, חריגים או מכסות מתנגשים — החסימה גוברת. שבת וחגים מנוהלים בנפרד.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun ShabbatScreen() {
    var enabled by rememberSaveable { mutableStateOf(true) }
    var candles by rememberSaveable { mutableIntStateOf(18) }
    var exitOffset by rememberSaveable { mutableIntStateOf(0) }
    var locationMode by rememberSaveable { mutableIntStateOf(0) }
    var secondDay by rememberSaveable { mutableIntStateOf(0) }
    SpecPage("חסימה בשבתות ובחגים יהודיים", "חסימה נפרדת של האפליקציות המנוהלות בשבת ובימים טובים שבהם אסורה מלאכה. חול המועד אינו נחסם.") {
        SpecCard("הפעלת החסימה") {
            SpecToggle("חסימה בשבתות ובחגים יהודיים", initial = enabled)
            Text("החסימה גוברת על מכסות, השהיות ולוחות זמנים רגילים. מגן נשארת זמינה במצב קריאה בלבד, ללא שינוי הגדרות.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("מיקום ומנהג") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = locationMode == 0, onClick = { locationMode = 0 }, label = { Text("מיקום המכשיר") })
                FilterChip(selected = locationMode == 1, onClick = { locationMode = 1 }, label = { Text("בחירה ידנית") })
            }
            SpecField("מדינה", "ישראל")
            SpecField("עיר", "בחירת עיר או מיקום")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = secondDay == 0, onClick = { secondDay = 0 }, label = { Text("יום טוב שני: כבוי") })
                FilterChip(selected = secondDay == 1, onClick = { secondDay = 1 }, label = { Text("יום טוב שני: פעיל") })
            }
            Text("לוח עברי לפי מנהג ישראל. חול המועד אינו נכלל בחסימה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("כניסת שבת וחג") {
            Text("הקדמת תחילת החסימה: $candles דקות לפני השקיעה")
            Slider(value = candles.toFloat(), onValueChange = { candles = it.toInt() }, valueRange = 0f..60f, steps = 59)
            SpecToggle("התראה 30 דקות לפני תחילת החסימה", initial = true)
            SpecField("תוספת ידנית לכניסה (דקות)", "0")
        }
        SpecCard("יציאת שבת וחג") {
            Text("תוספת ידנית ליציאה: $exitOffset דקות")
            Slider(value = exitOffset.toFloat(), onValueChange = { exitOffset = it.toInt() }, valueRange = -30f..90f, steps = 119)
            SpecToggle("חישוב יציאה לפי צאת הכוכבים", initial = true)
            SpecField("שיטת חישוב / זווית", "בחירת שיטה")
        }
        SpecCard("לוח שנה מקומי ועבודה ללא אינטרנט") {
            SpecToggle("שמירת לוח עברי מחושב מראש במכשיר", initial = true)
            Text("הלוח המקומי מאפשר אכיפה גם ללא חיבור לאינטרנט. יש להתריע מראש לפני שתוקף הכיסוי המקומי מסתיים.", style = MaterialTheme.typography.bodySmall)
            Text("כיסוי לוח שנה לדוגמה: עד סוף 2028", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("במהלך שבת וחג") {
            SpecToggle("מגן במצב קריאה בלבד", initial = true)
            Text("אתחול במהלך החסימה אמור להחיל אותה מחדש. שינוי מיקום אינו מבטל חסימה שכבר החלה. אם לא ניתן להפעיל חסימה, יש לזהות, לנסות שוב, להתריע ולתעד — ולא להציג מצב פעיל כוזב.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            SpecToggle("הפעלת VideoStop")
            SpecField("אפליקציות להחרגה")
            Text("VideoStop עדיין לא מחובר. זיהוי וידאו אינו מוחלט ודורש בדיקות תאימות וביצועים.")
        }
    }
}
@Composable
fun NotificationsCenterScreen() {
    SpecPage("התראות", "מרכז ההתראות והאירועים של מגן.") {
        SpecCard("מצב הגנה") {
            val running = app.magen.lock.GuardService.instance != null
            Text(if (running) "שירות הנגישות של מגן פועל" else "שירות הנגישות של מגן אינו פועל")
            Text("מצב השירות מוצג בנפרד מההרשאות ומהגדרות החסימה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("אירועים הדורשים טיפול") {
            Text("יומן אירועים מתמשך עדיין אינו מחובר. כרגע מוצגים רק מצבי הגנה שניתן לבדוק בזמן אמת.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("הגדרות התראות") {
            Text("העדפות סוגי ההתראות, חומרה, צליל והתנהגות קריאה יופיעו בהגדרות > התראות.")
        }
    }
}

@Composable
fun SpecRouteScreen(route: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) { Text("חזרה") }
        Box(Modifier.weight(1f)) {
            when (route) { "log" -> SecurityLogScreen(); "backup" -> BackupScreen(); else -> AppSettingsScreen() }
        }
    }
}
@Composable
private fun SecurityLogScreen() {
    SpecPage("יומן אבטחה", "תיעוד אירועי הגנה, שינויים, כשלים וניסיונות עקיפה.") {
        SpecCard("סינון וחיפוש") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("הכול", "קריטי", "אזהרה", "מידע").forEach { label -> FilterChip(selected = label == "הכול", onClick = {}, label = { Text(label) }) }
            }
            SpecField("חיפוש ביומן")
            SpecToggle("הצגת אירועים שלא נקראו בלבד")
        }
        SpecCard("מצבי טיפול") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("פעיל", "בטיפול", "נפתר", "לא נפתר").forEach { label ->
                    FilterChip(selected = false, onClick = {}, label = { Text(label) })
                }
            }
        }
        SpecCard("אירועים") {
            Text("אין עדיין אירועים שמורים בממשק החדש.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("אירוע יכלול חומרה, זמן, מקור, מצב טיפול ופרטי אימות. אירועים חוזרים יאוחדו; פתרון יסומן רק לאחר אימות.")
            Text("ברירת המחדל היא שימור ללא הגבלת זמן, עם אפשרות להגדיר תקופת שמירה.")
        }
        SpecCard("פעולות") {
            Button(onClick = {}) { Text("תצוגה מקדימה וייצוא דוח (טרם חובר)") }
            Button(onClick = {}) { Text("סמן הכול כנקרא (טרם חובר)") }
            Button(onClick = {}) { Text("בדיקת הגנה ותיקון בטוח (טרם חובר)") }
            Button(onClick = {}) { Text("מחיקת היומן (טרם חובר)") }
        }
    }
}
@Composable
private fun BackupScreen() {
    SpecPage("גיבוי ושחזור", "גיבוי מקומי של הגדרות מגן, ללא קודי סוד, מונים או חלונות זמן פעילים.") {
        SpecCard("גיבוי אוטומטי") {
            SpecToggle("יצירת גיבוי לפני עדכון או שינוי משמעותי")
            SpecToggle("שמירת כמה גרסאות גיבוי")
            Text("מיקום הגיבוי: אחסון מקומי במכשיר.")
        }
        SpecCard("גיבוי ידני") {
            Button(onClick = {}) { Text("יצירת גיבוי עכשיו (טרם חובר)") }
            Button(onClick = {}) { Text("ייצוא קובץ גיבוי (טרם חובר)") }
        }
        SpecCard("שחזור") {
            Button(onClick = {}) { Text("בחירת קובץ לשחזור (טרם חובר)") }
            Text("שחזור הגדרות לא יאפס את יומן האבטחה או את נתוני השימוש. תופיע אזהרה אם השחזור עלול להחליש את ההגנה.")
        }
    }
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
            var theme by rememberSaveable { mutableIntStateOf(2) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("בהיר", "כהה", "לפי המכשיר").forEachIndexed { i, label ->
                    FilterChip(selected = theme == i, onClick = { theme = i }, label = { Text(label) })
                }
            }
            SpecToggle("כיבוי אנימציות")
            Text("בחירת ערכת הנושא מוצגת כעת בממשק; החלתה על כל רכיבי האפליקציה תושלם בשלב הבא.")
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
