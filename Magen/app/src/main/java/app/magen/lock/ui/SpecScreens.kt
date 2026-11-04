package app.magen.lock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val gap = 12.dp

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
    Column(Modifier.fillMaxWidth()) {
        Text("חסימות", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("אפליקציות", "הגדרות מערכת", "כללים מותאמים").forEachIndexed { i, label ->
                FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(label) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (selected) { 0 -> AppsTab(); 1 -> ProtectionTab(onOpenHome = {}); else -> CustomRulesScreen() }
        }
    }
}
@Composable
fun TimesHub() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
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
            Text("מכסה יומית: $minutes דקות")
            Slider(value = minutes.toFloat(), onValueChange = { minutes = it.toInt() }, valueRange = 0f..360f, steps = 35)
            SpecToggle("התראה 5 דקות לפני סיום", initial = true)
            SpecToggle("מכסה שונה לפי יום בשבוע")
        }
        SpecCard("קבוצות אפליקציות", "כל אפליקציה יכולה להשתייך לקבוצה אחת לכל היותר. אפשר להפעיל מכסה אישית וקבוצתית יחד.") {
            SpecField("שם הקבוצה", "רשתות חברתיות")
            Text("מכסה קבוצתית: $groupMinutes דקות")
            Slider(value = groupMinutes.toFloat(), onValueChange = { groupMinutes = it.toInt() }, valueRange = 0f..360f, steps = 35)
            SpecToggle("השהיית מכסה לקבוצה")
        }
        SpecCard("אפליקציות בקבוצה") { listOf("דפדפן", "YouTube", "Telegram", "משחקים").forEach { SpecToggle(it) } }
        SpecCard("הפסקות") {
            SpecToggle("הפסקה מתוזמנת משהה את המכסה")
            SpecField("שעת התחלה", "13:00")
            SpecField("שעת סיום", "14:00")
        }
    }
}
@Composable
private fun SchedulesScreen() {
    SpecPage("לוחות זמנים", "לוח נפרד לכל אפליקציה. חלונות יכולים לחצות חצות, וניתן להגדיר חריגים לתאריכים מסוימים.") {
        SpecCard("בחירת אפליקציה") { SpecField("חיפוש אפליקציה"); listOf("דפדפן", "YouTube", "Telegram").forEach { SpecToggle(it) } }
        SpecCard("ימים ושעות") {
            listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת").forEach { SpecToggle(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { SpecField("משעה", "08:00") }
                Column(Modifier.weight(1f)) { SpecField("עד שעה", "20:00") }
            }
            SpecToggle("חלון זה חוצה חצות")
            SpecToggle("כל היום")
        }
        SpecCard("הפסקות וחריגים") {
            SpecToggle("הוספת הפסקה קבועה")
            SpecField("תאריך חריג")
            SpecToggle("ביום זה לחסום")
            SpecToggle("ביום זה לאפשר")
        }
    }
}
@Composable
private fun ShabbatScreen() {
    var candles by rememberSaveable { mutableIntStateOf(18) }
    var angle by rememberSaveable { mutableFloatStateOf(8.5f) }
    SpecPage("שבת וחגים", "חסימה כללית של האפליקציות המנוהלות. ההגדרה גוברת על מכסות ולוחות זמנים רגילים.") {
        SpecCard("מיקום ולוח שנה") {
            SpecField("עיר או מיקום", "בחירת מיקום מהמכשיר")
            SpecToggle("שימוש במיקום המכשיר")
            SpecToggle("יום טוב שני בישראל", initial = false)
            Text("חול המועד אינו נחסם.")
        }
        SpecCard("כניסת שבת וחג") {
            Text("הדלקת נרות: $candles דקות לפני השקיעה")
            Slider(value = candles.toFloat(), onValueChange = { candles = it.toInt() }, valueRange = 0f..60f, steps = 59)
            SpecToggle("התראה 30 דקות לפני תחילת החסימה", initial = true)
        }
        SpecCard("יציאת שבת וחג") {
            Text("זווית צאת הכוכבים: ${"%.1f".format(angle)} מעלות")
            Slider(value = angle, onValueChange = { angle = it }, valueRange = 4f..12f)
            SpecToggle("חישוב לפי צאת הכוכבים")
        }
        SpecCard("בזמן שבת וחג") {
            SpecToggle("הצגת מגן במצב קריאה בלבד", initial = true)
            Text("שירותי מערכת חיוניים נשארים זמינים. אתחול במהלך שבת או חג אמור להחיל את החסימה מחדש.")
        }
    }
}
@Composable
private fun CustomRulesScreen() {
    SpecPage("כללים מותאמים אישית", "כללים נוספים מעבר לחסימת אפליקציות ולהגדרות המערכת.") {
        SpecCard("מילות מפתח וכותרות") {
            SpecField("מילת מפתח או כותרת מסך")
            Button(onClick = {}) { Text("הוספת כלל (תצוגה בלבד)") }
            Text("כלל מותאם צריך להיות ממוקד כדי שלא לחסום מסכים שאינם קשורים.")
        }
        SpecCard("VideoStop", "זיהוי מסכי וידאו ונגנים באפליקציות ובדפדפן.") {
            SpecToggle("הפעלת VideoStop")
            SpecField("אפליקציות להחרגה")
            Text("זיהוי וידאו אינו מוחלט ודורש בדיקות תאימות וביצועים.")
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
        SpecCard("אירועים") {
            Text("אין עדיין אירועים שמורים בממשק החדש.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("אירוע יכלול חומרה, זמן, מקור, מצב טיפול ופרטי אימות.")
        }
        SpecCard("פעולות") {
            Button(onClick = {}) { Text("ייצוא דוח (טרם חובר)") }
            Button(onClick = {}) { Text("בדיקת הגנה ותיקון בטוח (טרם חובר)") }
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
    SpecPage("הגדרות", "התאמת התצוגה, השפה והתנהגות האפליקציה.") {
        SpecCard("שפה") {
            SpecToggle("עברית (ימין לשמאל)", initial = true)
            SpecToggle("English (left to right)")
            Text("החלפת השפה אמורה להתבצע מיד.")
        }
        SpecCard("מראה") {
            SpecToggle("ערכת נושא בהירה")
            SpecToggle("ערכת נושא כהה")
            SpecToggle("לפי הגדרות המכשיר", initial = true)
            SpecToggle("כיבוי אנימציות")
        }
        SpecCard("ניווט") {
            var nav by rememberSaveable { mutableIntStateOf(0) }
            listOf("תפריט צד", "סרגל תחתון", "סרגל עליון").forEachIndexed { i, label -> FilterChip(selected = nav == i, onClick = { nav = i }, label = { Text(label) }) }
            Text("בחירת מיקום הניווט תתחבר בהמשך.")
        }
        SpecCard("פרטיות ואבחון") {
            SpecToggle("הצגת מידע על שימוש במשאבים")
            SpecToggle("בקשת אישור לפני שיתוף דוח אבחון")
            Text("מגן פועלת מקומית. לא נדרש חשבון ענן או שירות בתשלום.")
        }
    }
}
