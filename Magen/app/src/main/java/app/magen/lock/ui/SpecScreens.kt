package app.magen.lock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val gap = 12.dp

/** Presentation preference; enforcement settings are deliberately kept elsewhere. */
object SpecUiPreferences {
    var navigationMode by mutableIntStateOf(1) // 0 = side rail, 1 = bottom, 2 = top

    fun load(context: Context) {
        navigationMode = context.getSharedPreferences("magen_ui", Context.MODE_PRIVATE).getInt("navigationMode", 1).coerceIn(0, 2)
    }

    fun setNavigationMode(context: Context, mode: Int) {
        navigationMode = mode.coerceIn(0, 2)
        context.getSharedPreferences("magen_ui", Context.MODE_PRIVATE).edit().putInt("navigationMode", navigationMode).apply()
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
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text("חסימות", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("אפליקציות", "הגדרות מערכת", "כללים מותאמים").forEachIndexed { i, label ->
                FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(label) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (selected) { 0 -> AppsTab(); 1 -> SystemComponentsScreen(); else -> CustomRulesScreen() }
        }
    }
}
@Composable
fun TimesHub() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text("זמנים", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("מכסות", "לוחות זמנים", "שבת וחגים", "נעילה").forEachIndexed { i, label ->
                FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(label) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (selected) { 0 -> QuotasScreen(); 1 -> SchedulesScreen(); 2 -> ShabbatScreen(); else -> TimesTab() }
        }
    }
}
@Composable
private fun QuotasScreen() {
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var groupMinutes by rememberSaveable { mutableIntStateOf(120) }
    SpecPage("מכסות שימוש", "הגדרת זמן שימוש יומי לאפליקציה בודדת או לקבוצת אפליקציות.") {
        SpecCard("מכסה אישית לאפליקציה", "הזמן נספר רק כשהאפליקציה בחזית. 0 דקות פירושו חסימה במשך כל היום.") {
            SpecField("בחירת אפליקציה", "YouTube")
            Text("מכסה יומית: $minutes דקות")
            Slider(value = minutes.toFloat(), onValueChange = { minutes = it.toInt() }, valueRange = 0f..360f, steps = 35)
            SpecToggle("התראה 5 דקות לפני סיום", initial = true)
            SpecToggle("השהיית המכסה האישית")
            SpecToggle("מכסה שונה לפי יום בשבוע")
        }
        SpecCard("קבוצות אפליקציות", "כל אפליקציה יכולה להשתייך לקבוצה אחת לכל היותר. אפשר להפעיל מכסה אישית וקבוצתית יחד.") {
            SpecField("שם הקבוצה", "רשתות חברתיות")
            Text("מכסה קבוצתית: $groupMinutes דקות")
            Slider(value = groupMinutes.toFloat(), onValueChange = { groupMinutes = it.toInt() }, valueRange = 0f..360f, steps = 35)
            SpecToggle("השהיית מכסה לקבוצה")
            Text("אם המכסה האישית או הקבוצתית מסתיימת — האפליקציה נחסמת. מעבר קבוצה אינו מאפס שימוש שכבר נצבר.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("אפליקציות בקבוצה") { listOf("דפדפן", "YouTube", "Telegram", "משחקים").forEach { SpecToggle(it) } }
        SpecCard("הפסקות") {
            SpecToggle("הפסקה מתוזמנת משהה את המכסה")
            SpecField("שעת התחלה", "13:00")
            SpecField("שעת סיום", "14:00")
            Text("הפסקה משהה את ספירת המכסה בלבד; היא אינה מבטלת חסימות אחרות.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun SchedulesScreen() {
    SpecPage("לוחות זמנים", "לוח נפרד לכל אפליקציה. חלונות יכולים לחצות חצות, וניתן להגדיר חריגים לתאריכים מסוימים.") {
        SpecCard("בחירת אפליקציה") {
            SpecField("חיפוש אפליקציה")
            var selectedApp by rememberSaveable { mutableIntStateOf(0) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("דפדפן", "YouTube", "Telegram").forEachIndexed { i, label ->
                    FilterChip(selected = selectedApp == i, onClick = { selectedApp = i }, label = { Text(label) })
                }
            }
        }
        SpecCard("ימים ושעות") {
            listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת").forEach { SpecToggle(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { SpecField("משעה", "08:00") }
                Column(Modifier.weight(1f)) { SpecField("עד שעה", "20:00") }
            }
            SpecToggle("חלון זה חוצה חצות")
            SpecToggle("כל היום")
            Text("אפשר להגדיר כמה חלונות לכל יום, כולל חלונות שחוצים חצות.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("הפסקות וחריגים") {
            SpecToggle("הוספת הפסקה קבועה")
            SpecField("תחילת הפסקה", "13:00")
            SpecField("סיום הפסקה", "14:00")
            SpecField("תאריך חריג")
            var exceptionMode by rememberSaveable { mutableIntStateOf(0) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = exceptionMode == 0, onClick = { exceptionMode = 0 }, label = { Text("ללא חריג") })
                FilterChip(selected = exceptionMode == 1, onClick = { exceptionMode = 1 }, label = { Text("לחסום") })
                FilterChip(selected = exceptionMode == 2, onClick = { exceptionMode = 2 }, label = { Text("לאפשר") })
            }
            Text("כאשר חריג חוסם וחריג מתיר חופפים — החסימה גוברת.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun ShabbatScreen() {
    var candles by rememberSaveable { mutableIntStateOf(18) }
    var angle by rememberSaveable { mutableFloatStateOf(8.5f) }
    SpecPage("שבת וחגים", "חסימה כללית של האפליקציות המנוהלות. ההגדרה גוברת על מכסות ולוחות זמנים רגילים.") {
        SpecCard("מיקום ולוח שנה") {
            SpecField("מדינה", "ישראל")
            SpecField("עיר או מיקום", "בחירת מיקום מהמכשיר")
            SpecToggle("שימוש במיקום המכשיר")
            SpecToggle("יום טוב שני בישראל", initial = false)
            Text("חול המועד אינו נחסם.")
        }
        SpecCard("כניסת שבת וחג") {
            Text("הדלקת נרות: $candles דקות לפני השקיעה")
            Slider(value = candles.toFloat(), onValueChange = { candles = it.toInt() }, valueRange = 0f..60f, steps = 59)
            SpecToggle("התראה 30 דקות לפני תחילת החסימה", initial = true)
            SpecToggle("בדיקת תקינות 30 דקות לפני הכניסה", initial = true)
            Text("אין התראת סיום. חול המועד אינו נחסם; יום טוב שני בישראל כבוי כברירת מחדל.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SpecCard("יציאת שבת וחג") {
            Text("זווית צאת הכוכבים: ${"%.1f".format(angle)} מעלות")
            Slider(value = angle, onValueChange = { angle = it }, valueRange = 4f..12f)
            SpecToggle("חישוב לפי צאת הכוכבים")
        }
        SpecCard("בזמן שבת וחג") {
            SpecToggle("הצגת מגן במצב קריאה בלבד", initial = true)
            Text("שירותי מערכת חיוניים נשארים זמינים. אתחול במהלך שבת או חג אמור להחיל את החסימה מחדש. שינוי מיקום לא יבטל חסימה שכבר החלה.")
        }
    }
}
@Composable
private fun SystemComponentsScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    val groups = Catalog.settings.groupBy { it.group }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("חסימת רכיבי הגדרות Android", style = MaterialTheme.typography.titleLarge)
        Text("58 קטגוריות מתוך קטלוג ההגדרות הקיים. רמת האכיפה תלויה במכשיר ובגרסת Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("חיפוש בהגדרות") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        groups.forEach { (group, categories) ->
            SpecCard(stringResource(group.title)) {
                categories.forEach { cat ->
                    val label = stringResource(cat.title)
                    if (query.isBlank() || label.contains(query, ignoreCase = true) ||
                        cat.id.contains(query, ignoreCase = true) || cat.keywords.any { it.contains(query, ignoreCase = true) }
                    ) {
                        SwitchListRow(
                            title = label,
                            checked = cat.id in Store.blockedSettings,
                            onChange = { Store.setBlockedSetting(cat.id, it) },
                            supporting = if (cat.restriction != null) "קיימת גם מגבלת Android מערכתית במצב המתאים" else "מבוסס התאמת כותרת דרך שירות הנגישות",
                            icon = cat.icon,
                        )
                        RowDivider()
                    }
                }
            }
        }
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
        SpecCard("פרטיות ואבחון") {
            SpecToggle("הצגת מידע על שימוש במשאבים")
            SpecToggle("בקשת אישור לפני שיתוף דוח אבחון")
            Text("מגן פועלת מקומית. לא נדרש חשבון ענן או שירות בתשלום.")
        }
    }
}
