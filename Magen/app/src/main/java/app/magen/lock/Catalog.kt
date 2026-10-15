package app.magen.lock

import android.os.UserManager
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.ui.graphics.vector.ImageVector

enum class SGroup(@StringRes val title: Int) {
    CONNECT(R.string.grp_connect),
    PRIVACY(R.string.grp_privacy),
    APPS(R.string.grp_apps),
    DEVICE(R.string.grp_device),
    SYSTEM(R.string.grp_system),
}

/** A Settings area the user can restrict. [keywords] are page titles (English + Hebrew + common OEM names). */
class SettingsCat(
    val id: String,
    @StringRes val title: Int,
    val icon: ImageVector,
    val group: SGroup,
    val keywords: List<String>,
    /** Optional system-level user restriction applied when running as Device Owner. */
    val restriction: String?,
)

object Catalog {
    const val PLAY = "com.android.vending"

    val SETTINGS = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.google.android.apps.wellbeing",
    )

    val INSTALLERS = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
    )

    /** Windows that appear on top of apps and must not count as "leaving" the app. */
    val TRANSIENT = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.intentresolver",
    )

    /** Names under which Magen shows up in Settings and in installer dialogs. */
    val ownNames = listOf("magen", "מגן")

    // NOTE: these two must be declared before [settings], which reads them during initialisation.
    val adminPages = setOf(
        "device admin apps", "device admin app", "device admin",
        "אפליקציות לניהול המכשיר", "אפליקציות ניהול מכשיר", "אפליקציות מנהל מכשיר", "אפליקציית ניהול מכשיר",
    )

    val unknownSources = setOf(
        "install unknown apps", "special app access",
        "התקנת אפליקציות לא מוכרות", "התקנת אפליקציות לא ידועות", "גישה מיוחדת לאפליקציות", "אפליקציות לא מוכרות",
    )

    /** Button labels that mean "install a new app" in the Play Store. "Update" is deliberately absent. */
    val installWords = setOf("install", "התקנה", "התקן")

    private fun c(
        id: String,
        @StringRes title: Int,
        icon: ImageVector,
        group: SGroup,
        restriction: String?,
        vararg keywords: String,
    ) = SettingsCat(id, title, icon, group, keywords.toList(), restriction)

    val settings: List<SettingsCat> = listOf(
        // ---- connectivity ----
        c("network", R.string.cat_network, Icons.Filled.Public, SGroup.CONNECT, UserManager.DISALLOW_CONFIG_WIFI,
            "Network & internet", "Connections", "רשת ואינטרנט", "חיבורים"),
        c("wifi", R.string.cat_wifi, Icons.Filled.Wifi, SGroup.CONNECT, UserManager.DISALLOW_CONFIG_WIFI,
            "Wi-Fi", "Internet", "אינטרנט"),
        c("mobile", R.string.cat_mobile, Icons.Filled.SignalCellularAlt, SGroup.CONNECT, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            "Mobile network", "SIMs", "SIM cards", "רשת סלולרית", "רשת ניידת", "כרטיסי SIM"),
        c("hotspot", R.string.cat_hotspot, Icons.Filled.WifiTethering, SGroup.CONNECT, UserManager.DISALLOW_CONFIG_TETHERING,
            "Hotspot & tethering", "Hotspot", "Tethering", "נקודה חמה", "שיתוף אינטרנט", "נקודת אחיזה ושיתוף אינטרנט"),
        c("datausage", R.string.cat_datausage, Icons.Filled.DataUsage, SGroup.CONNECT, null,
            "Data usage", "Data Saver", "צריכת נתונים", "חיסכון בנתונים"),
        c("privatedns", R.string.cat_privatedns, Icons.Filled.Dns, SGroup.CONNECT, UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
            "Private DNS", "DNS פרטי"),
        c("bluetooth", R.string.cat_bluetooth, Icons.Filled.Bluetooth, SGroup.CONNECT, UserManager.DISALLOW_CONFIG_BLUETOOTH,
            "Bluetooth", "Connected devices", "Connection preferences", "מכשירים מחוברים", "העדפות חיבור", "בלוטות׳"),
        c("nfc", R.string.cat_nfc, Icons.Filled.Nfc, SGroup.CONNECT, null,
            "NFC", "NFC and contactless payments"),
        c("cast", R.string.cat_cast, Icons.Filled.Cast, SGroup.CONNECT, null,
            "Cast", "Smart View", "שידור", "העברה"),
        c("print", R.string.cat_print, Icons.Filled.Print, SGroup.CONNECT, UserManager.DISALLOW_PRINTING,
            "Printing", "Print", "הדפסה"),
        c("usb", R.string.cat_usb, Icons.Filled.Usb, SGroup.CONNECT, UserManager.DISALLOW_USB_FILE_TRANSFER,
            "USB", "USB preferences", "העדפות USB"),
        c("vpn", R.string.cat_vpn, Icons.Filled.VpnKey, SGroup.CONNECT, UserManager.DISALLOW_CONFIG_VPN,
            "VPN"),

        // ---- security & privacy ----
        c("security", R.string.cat_security, Icons.Filled.Security, SGroup.PRIVACY, null,
            "Security", "Security & privacy", "Privacy", "Passwords & security", "אבטחה", "אבטחה ופרטיות", "פרטיות", "סיסמאות ואבטחה"),
        c("screenlock", R.string.cat_screenlock, Icons.Filled.ScreenLockPortrait, SGroup.PRIVACY, null,
            "Screen lock", "Choose screen lock", "Lock screen", "נעילת מסך", "מסך נעילה", "בחירת נעילת מסך"),
        c("biometrics", R.string.cat_biometrics, Icons.Filled.Fingerprint, SGroup.PRIVACY, null,
            "Fingerprint", "Face unlock", "Biometrics and security", "טביעת אצבע", "זיהוי פנים", "ביומטריה ואבטחה"),
        c("credentials", R.string.cat_credentials, Icons.Filled.EnhancedEncryption, SGroup.PRIVACY, UserManager.DISALLOW_CONFIG_CREDENTIALS,
            "Encryption & credentials", "Credential storage", "הצפנה ופרטי כניסה", "אחסון פרטי כניסה"),
        c("finddevice", R.string.cat_finddevice, Icons.Filled.LocationSearching, SGroup.PRIVACY, null,
            "Find My Device", "Find My Mobile", "Find Hub", "איתור המכשיר שלי"),
        c("admin", R.string.cat_admin, Icons.Filled.AdminPanelSettings, SGroup.PRIVACY, null,
            *adminPages.toTypedArray()),
        c("playprotect", R.string.cat_playprotect, Icons.Filled.VerifiedUser, SGroup.PRIVACY, null,
            "Google Play Protect", "Play Protect"),
        c("permissions", R.string.cat_permissions, Icons.Filled.Key, SGroup.PRIVACY, null,
            "Permission manager", "Permissions", "App permissions", "מנהל ההרשאות", "הרשאות", "הרשאות אפליקציה"),
        c("privacydash", R.string.cat_privacydash, Icons.Filled.PrivacyTip, SGroup.PRIVACY, null,
            "Privacy dashboard", "Privacy controls", "לוח בקרה לפרטיות", "פקדי פרטיות"),
        c("location", R.string.cat_location, Icons.Filled.LocationOn, SGroup.PRIVACY, UserManager.DISALLOW_CONFIG_LOCATION,
            "Location", "מיקום"),
        c("accounts", R.string.cat_accounts, Icons.Filled.AccountCircle, SGroup.PRIVACY, UserManager.DISALLOW_MODIFY_ACCOUNTS,
            "Accounts", "Passwords & accounts", "Passwords, passkeys & accounts", "Users & accounts", "Accounts and backup",
            "Add an account", "חשבונות", "סיסמאות וחשבונות", "חשבונות וגיבוי", "הוספת חשבון"),
        c("pinning", R.string.cat_pinning, Icons.Filled.PushPin, SGroup.PRIVACY, null,
            "App pinning", "Screen pinning", "הצמדת אפליקציה", "הצמדת מסך"),
        c("reset", R.string.cat_reset, Icons.Filled.RestartAlt, SGroup.PRIVACY, UserManager.DISALLOW_FACTORY_RESET,
            "Reset options", "Reset", "Factory reset", "Erase all data (factory reset)",
            "אפשרויות איפוס", "איפוס", "איפוס להגדרות היצרן"),

        // ---- apps ----
        c("apps", R.string.cat_apps, Icons.Filled.Apps, SGroup.APPS, UserManager.DISALLOW_APPS_CONTROL,
            "Apps", "App info", "All apps", "See all apps", "אפליקציות", "פרטי האפליקציה", "מידע על האפליקציה"),
        c("defaultapps", R.string.cat_defaultapps, Icons.Filled.Widgets, SGroup.APPS, null,
            "Default apps", "אפליקציות ברירת מחדל"),
        c("specialaccess", R.string.cat_specialaccess, Icons.Filled.Extension, SGroup.APPS, null,
            "Special app access", "גישה מיוחדת לאפליקציות"),
        c("autofill", R.string.cat_autofill, Icons.Filled.Key, SGroup.PRIVACY, null,
            "Autofill service", "Autofill", "שירות מילוי אוטומטי", "מילוי אוטומטי"),
        c("notificationaccess", R.string.cat_notificationaccess, Icons.Filled.Notifications, SGroup.APPS, null,
            "Device & app notifications", "Notification access", "גישה להתראות", "גישה להודעות"),
        c("usageaccess", R.string.cat_usageaccess, Icons.Filled.PrivacyTip, SGroup.APPS, null,
            "Usage access", "Usage data access", "גישה לנתוני שימוש", "גישה לשימוש באפליקציות"),
        c("displayover", R.string.cat_displayover, Icons.Filled.Apps, SGroup.APPS, null,
            "Display over other apps", "Appear on top", "הצגה מעל אפליקציות אחרות", "הופעה מעל"),
        c("pip", R.string.cat_pip, Icons.Filled.PhoneAndroid, SGroup.APPS, null,
            "Picture-in-picture", "תמונה בתוך תמונה"),
        c("screenrecord", R.string.cat_screenrecord, Icons.Filled.PhoneAndroid, SGroup.SYSTEM, null,
            "Screen record", "Screen recording", "הקלטת מסך", "מקליט מסך"),
        c("emergency", R.string.cat_emergency, Icons.Filled.Security, SGroup.PRIVACY, null,
            "Emergency SOS", "Emergency", "SOS לשעת חירום", "מצב חירום"),
        c("installapps", R.string.cat_installapps, Icons.Filled.GetApp, SGroup.APPS, null,
            "Install apps", "App installation", "התקנת אפליקציות", "התקן אפליקציות"),
        c("unknownapps", R.string.cat_unknownapps, Icons.Filled.GetApp, SGroup.APPS, null,
            *unknownSources.toTypedArray()),
        c("notifications", R.string.cat_notifications, Icons.Filled.Notifications, SGroup.APPS, null,
            "Notifications", "התראות"),
        c("battery", R.string.cat_battery, Icons.Filled.BatteryFull, SGroup.APPS, null,
            "Battery", "Battery and device care", "Battery Saver", "סוללה", "סוללה וטיפול במכשיר"),
        c("storage", R.string.cat_storage, Icons.Filled.Storage, SGroup.APPS, null,
            "Storage", "אחסון"),
        c("wellbeing", R.string.cat_wellbeing, Icons.Filled.ChildCare, SGroup.APPS, null,
            "Digital Wellbeing & parental controls", "Digital Wellbeing", "רווחה דיגיטלית ובקרת הורים", "רווחה דיגיטלית"),

        // ---- device ----
        c("display", R.string.cat_display, Icons.Filled.Brightness6, SGroup.DEVICE, UserManager.DISALLOW_CONFIG_BRIGHTNESS,
            "Display", "תצוגה"),
        c("screentimeout", R.string.cat_screentimeout, Icons.Filled.Timer, SGroup.DEVICE, UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT,
            "Screen timeout", "Sleep", "זמן קצוב לתפוגת המסך", "שינה"),
        c("sound", R.string.cat_sound, Icons.Filled.VolumeUp, SGroup.DEVICE, null,
            "Sound & vibration", "Sound", "Sounds and vibration", "צליל ורטט", "צלילים ורטט", "צליל"),
        c("dnd", R.string.cat_dnd, Icons.Filled.DoNotDisturbOn, SGroup.DEVICE, null,
            "Do Not Disturb", "נא לא להפריע"),
        c("wallpaper", R.string.cat_wallpaper, Icons.Filled.Wallpaper, SGroup.DEVICE, UserManager.DISALLOW_SET_WALLPAPER,
            "Wallpaper & style", "Wallpaper", "טפט וסגנון", "טפט"),
        c("about", R.string.cat_about, Icons.Filled.Info, SGroup.DEVICE, null,
            "About phone", "About device", "מידע על הטלפון", "אודות הטלפון"),

        // ---- system ----
        c("accessibility", R.string.cat_accessibility, Icons.Filled.Accessibility, SGroup.SYSTEM, null,
            "Accessibility", "נגישות"),
        c("developer", R.string.cat_developer, Icons.Filled.DeveloperMode, SGroup.SYSTEM, UserManager.DISALLOW_DEBUGGING_FEATURES,
            "Developer options", "אפשרויות למפתחים", "אפשרויות מפתח"),
        c("datetime", R.string.cat_datetime, Icons.Filled.AccessTime, SGroup.SYSTEM, UserManager.DISALLOW_CONFIG_DATE_TIME,
            "Date & time", "תאריך ושעה"),
        c("language", R.string.cat_language, Icons.Filled.Translate, SGroup.SYSTEM, UserManager.DISALLOW_CONFIG_LOCALE,
            "Languages & input", "Language & input", "Languages", "שפות וקלט", "שפה והקלדה", "שפות"),
        c("keyboard", R.string.cat_keyboard, Icons.Filled.Keyboard, SGroup.SYSTEM, null,
            "On-screen keyboard", "Keyboard", "Physical keyboard", "מקלדת וירטואלית", "מקלדת"),
        c("gestures", R.string.cat_gestures, Icons.Filled.Gesture, SGroup.SYSTEM, null,
            "Gestures", "System navigation", "תנועות", "ניווט במערכת"),
        c("backup", R.string.cat_backup, Icons.Filled.Backup, SGroup.SYSTEM, null,
            "Backup", "Backup & restore", "גיבוי", "גיבוי ושחזור"),
        c("update", R.string.cat_update, Icons.Filled.SystemUpdate, SGroup.SYSTEM, null,
            "System update", "Software update", "עדכון מערכת", "עדכון תוכנה"),
        c("users", R.string.cat_users, Icons.Filled.Group, SGroup.SYSTEM, UserManager.DISALLOW_ADD_USER,
            "Users", "Multiple users", "משתמשים", "מספר משתמשים"),
        c("google", R.string.cat_google, Icons.Filled.Cloud, SGroup.SYSTEM, null,
            "Google", "Google services & preferences", "שירותים והעדפות של Google"),
        c("system", R.string.cat_system, Icons.Filled.PhoneAndroid, SGroup.SYSTEM, null,
            "System", "General management", "Additional settings", "מערכת", "ניהול כללי", "הגדרות נוספות"),
    )

    val byId = settings.associateBy { it.id }

    /** Lower-cases and strips bidi marks / odd hyphens so labels compare reliably. */
    fun norm(s: String): String =
        s.lowercase()
            .replace(Regex("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069\\u00a0]"), " ")
            .replace('‑', '-')
            .replace('‐', '-')
            .replace(Regex("\\s+"), " ")
            .trim()
}
