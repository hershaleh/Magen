package app.magen.lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/**
 * The enforcement engine. It watches which window is on screen and
 *  - shows the lock screen over locked apps,
 *  - closes the Play Store when it offers a new install (updates stay allowed),
 *  - closes the package installer for APK installs,
 *  - closes restricted Settings pages and protects Magen itself from removal.
 */
class GuardService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: GuardService? = null
    }

    private var currentApp = ""
    private val tempUnlocked = HashSet<String>()
    private var lastLaunch = 0L
    private var lastScan = 0L
    private var lastBlock = 0L
    private var lastMaint = 0L
    private var imePkgs: Set<String> = emptySet()
    private var imeAt = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            currentApp = ""
            if (Store.relockOnScreenOff) {
                tempUnlocked.clear()
                Store.clearMainUnlocked()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    fun markUnlocked(pkg: String) {
        tempUnlocked.add(pkg)
    }

    // ---- events ------------------------------------------------------------------------------

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        val state = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (state) onForeground(pkg)
        maintain()

        if (Store.hasPin && Store.locked.containsKey(pkg) && (state || activeIs(pkg))) enforceLock(pkg)

        val now = SystemClock.uptimeMillis()
        if (!state && now - lastScan < 250) return
        when {
            pkg == Catalog.PLAY -> if (Store.blockPlay) {
                lastScan = now
                scanPlay()
            }
            pkg in Catalog.SETTINGS -> {
                lastScan = now
                scanSettings()
            }
            pkg in Catalog.INSTALLERS -> {
                lastScan = now
                scanInstaller()
            }
        }
    }

    private fun maintain() {
        val n = SystemClock.uptimeMillis()
        if (n - lastMaint > 15_000) {
            lastMaint = n
            Policy.apply()
        }
    }

    // ---- app lock ----------------------------------------------------------------------------

    private fun onForeground(pkg: String) {
        if (pkg == packageName || isTransient(pkg)) return
        if (pkg != currentApp) {
            tempUnlocked.remove(currentApp)
            currentApp = pkg
        }
    }

    private fun isTransient(pkg: String) = pkg in Catalog.TRANSIENT || pkg in ime()

    private fun ime(): Set<String> {
        val n = SystemClock.uptimeMillis()
        if (n - imeAt > 60_000) {
            imeAt = n
            imePkgs = runCatching {
                getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
            }.getOrDefault(imePkgs)
        }
        return imePkgs
    }

    private fun activeIs(pkg: String) = rootInActiveWindow?.packageName?.toString() == pkg

    private fun enforceLock(pkg: String) {
        val mode = Store.locked[pkg] ?: return
        if (pkg in tempUnlocked) return
        if (mode == LockMode.MAIN && pkg in Store.mainUnlocked) return
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunch < 600) return
        lastLaunch = now
        startActivity(
            Intent(this, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(LockActivity.EXTRA_PKG, pkg),
        )
    }

    // ---- Google Play -------------------------------------------------------------------------

    private fun scanPlay() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != Catalog.PLAY) return
        if (collect(root, false).any { Catalog.norm(it) in Catalog.installWords }) block(R.string.blocked_play)
    }

    // ---- Settings ----------------------------------------------------------------------------

    private fun scanSettings() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in Catalog.SETTINGS) return
        val top = collect(root, true).map { Catalog.norm(it) }
        if (top.isEmpty()) return

        val selfProtected = Store.protect && !Store.uninstallAllowed() && Policy.isAdmin(this)
        if (selfProtected && (top.any { t -> Catalog.ownNames.any { t.contains(it) } } || top.any { it in Catalog.adminPages })) {
            block(R.string.blocked_self)
            return
        }
        if (Store.blockApk && top.any { it in Catalog.unknownSources }) {
            block(R.string.blocked_apk)
            return
        }
        val hit = Store.blockedSettings.mapNotNull { Catalog.byId[it] }
            .any { c -> c.keywords.any { Catalog.norm(it) in top } }
        val custom = Store.customKeywords.any { Catalog.norm(it) in top }
        if (hit || custom) block(R.string.blocked_settings)
    }

    // ---- package installer -------------------------------------------------------------------

    private fun scanInstaller() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in Catalog.INSTALLERS) return
        val texts = collect(root, false).map { Catalog.norm(it) }
        val mentionsUs = texts.any { t -> Catalog.ownNames.any { t.contains(it) } }
        if (Store.protect && !Store.uninstallAllowed() && mentionsUs) {
            block(R.string.blocked_self)
            return
        }
        val isUninstall = texts.any { it.contains("uninstall") || it.contains("הסר") }
        if (Store.blockApk && !isUninstall) block(R.string.blocked_apk)
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun block(@StringRes reason: Int) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val n = SystemClock.uptimeMillis()
        if (n - lastBlock < 1500) return
        lastBlock = n
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(BlockActivity.EXTRA_REASON, reason),
        )
    }

    /** Gathers visible text. With [topOnly], only nodes in the top 30% of the screen (page titles). */
    private fun collect(root: AccessibilityNodeInfo, topOnly: Boolean): List<String> {
        val out = ArrayList<String>()
        val limit = (resources.displayMetrics.heightPixels * 0.30).toInt()
        val r = Rect()
        var count = 0

        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (count++ > 700 || depth > 25) return
            val a = n.text?.toString()
            val b = n.contentDescription?.toString()
            if (!a.isNullOrBlank() || !b.isNullOrBlank()) {
                var visible = true
                if (topOnly) {
                    n.getBoundsInScreen(r)
                    visible = r.bottom in 1..limit
                }
                if (visible) {
                    if (!a.isNullOrBlank()) out.add(a)
                    if (!b.isNullOrBlank()) out.add(b)
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }

        walk(root, 0)
        return out
    }
}
