package app.magen.lock

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
        private const val CHANNEL = "magen_guard"
        private const val NOTIF_OFF = 41
        private const val CAPTURE_WINDOW_MS = 10 * 60_000L
        private val GENERIC_TITLES = setOf(
            "settings", "search settings", "navigate up", "back", "more options", "search",
            "הגדרות", "חיפוש בהגדרות", "נווט למעלה", "חזרה", "אפשרויות נוספות", "חיפוש",
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private var captureButton: View? = null
    private var capturePanel: View? = null
    private var captureArmedAt = 0L
    private val hideCoverRunnable = Runnable { removeCover() }
    private var recheckPkg: String? = null
    private val recheck = Runnable {
        val p = recheckPkg ?: return@Runnable
        recheckPkg = null
        if (Store.hasPin && Store.locked.containsKey(p) && activeIs(p)) enforceLock(p)
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
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIF_OFF) }
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        instance = null
        removeCover()
        removeCapture()
        // Warn when protection is switched off (by hand, by the system or by a crash of the service).
        if (Store.hasPin && Store.protect && !Store.uninstallAllowed()) notifyServiceOff()
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    fun markUnlocked(pkg: String) {
        tempUnlocked.add(pkg)
        hideCoverSoon()
    }

    // ---- accessibility-off alert -------------------------------------------------------------

    private fun notifyServiceOff() {
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH),
            )
            val open = PendingIntent.getActivity(
                this, 0,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_fg)
                .setContentTitle(getString(R.string.notif_off_title))
                .setContentText(getString(R.string.notif_off_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notif_off_text)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                NotificationManagerCompat.from(this).notify(NOTIF_OFF, n)
            }
        }
    }

    // ---- instant cover over locked apps ------------------------------------------------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun overlayParams(w: Int, h: Int, gravity: Int, y: Int = 0): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).also {
            it.gravity = gravity
            it.y = y
        }

    private fun addOverlay(v: View, lp: WindowManager.LayoutParams): Boolean =
        runCatching { getSystemService(WindowManager::class.java).addView(v, lp) }.isSuccess

    private fun removeOverlay(v: View?) {
        if (v != null) runCatching { getSystemService(WindowManager::class.java).removeView(v) }
    }

    /** Solid screen shown the moment a locked app appears, so its content never flashes before the lock. */
    private fun showCover() {
        if (cover != null) {
            handler.removeCallbacks(hideCoverRunnable)
            handler.postDelayed(hideCoverRunnable, 1500)
            return
        }
        val v = FrameLayout(this).apply {
            setBackgroundColor(0xFF4F46E5.toInt())
            addView(
                ImageView(context).apply { setImageResource(R.drawable.ic_fg) },
                FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER),
            )
        }
        val full = WindowManager.LayoutParams.MATCH_PARENT
        if (addOverlay(v, overlayParams(full, full, Gravity.TOP))) {
            cover = v
            handler.postDelayed(hideCoverRunnable, 1500)
        }
    }

    private fun removeCover() {
        handler.removeCallbacks(hideCoverRunnable)
        removeOverlay(cover)
        cover = null
    }

    /** Called once the lock screen is on top: remove the cover a moment later so there is no gap. */
    fun hideCoverSoon() {
        if (cover == null) return
        handler.removeCallbacks(hideCoverRunnable)
        handler.postDelayed(hideCoverRunnable, 150)
    }

    // ---- pick a Settings page from the screen ------------------------------------------------

    /** Arms "pick from screen": a floating button appears in Settings; tapping it offers the page title to block. */
    fun armCapture() {
        captureArmedAt = SystemClock.uptimeMillis()
        removeCapture()
    }

    private fun captureArmed() =
        captureArmedAt != 0L && SystemClock.uptimeMillis() - captureArmedAt < CAPTURE_WINDOW_MS

    private fun updateCapture(pkg: String, state: Boolean) {
        if (!captureArmed()) {
            if (captureArmedAt != 0L) {
                captureArmedAt = 0L
                removeCapture()
            }
            return
        }
        if (pkg in Catalog.SETTINGS) {
            showCaptureButton()
        } else if (state && pkg != packageName && !isTransient(pkg)) {
            removeCapture()
        }
    }

    private fun showCaptureButton() {
        if (captureButton != null || capturePanel != null) return
        val v = TextView(this).apply {
            text = getString(R.string.capture_button)
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF4F46E5.toInt())
                cornerRadius = dp(28).toFloat()
            }
            setOnClickListener { showCapturePanel() }
        }
        if (addOverlay(v, overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, dp(110)))) {
            captureButton = v
        }
    }

    private fun showCapturePanel() {
        val root = rootInActiveWindow
        val titles = if (root != null && root.packageName?.toString() in Catalog.SETTINGS) pageTitles(root) else emptyList()
        if (titles.isEmpty()) {
            Toast.makeText(this, R.string.capture_none, Toast.LENGTH_LONG).show()
            return
        }
        removeOverlay(captureButton)
        captureButton = null
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
            }
            addView(
                TextView(context).apply {
                    text = getString(R.string.capture_pick_title)
                    setTextColor(0xFF1B1B2F.toInt())
                    textSize = 17f
                    setPadding(0, 0, 0, dp(8))
                },
            )
            titles.forEach { t ->
                addView(
                    Button(context).apply {
                        text = t
                        isAllCaps = false
                        setOnClickListener { captureDone(t) }
                    },
                )
            }
            addView(
                Button(context).apply {
                    text = getString(R.string.cancel)
                    isAllCaps = false
                    setOnClickListener {
                        captureArmedAt = 0L
                        removeCapture()
                    }
                },
            )
        }
        if (addOverlay(panel, overlayParams(dp(300), WindowManager.LayoutParams.WRAP_CONTENT, Gravity.CENTER))) {
            capturePanel = panel
        }
    }

    private fun captureDone(title: String) {
        Store.addKeyword(title)
        captureArmedAt = 0L
        removeCapture()
        Toast.makeText(this, getString(R.string.capture_done, title), Toast.LENGTH_LONG).show()
    }

    private fun removeCapture() {
        removeOverlay(captureButton)
        removeOverlay(capturePanel)
        captureButton = null
        capturePanel = null
    }

    /** Likely page titles on the current Settings screen: text in the top 30%, highest first. */
    private fun pageTitles(root: AccessibilityNodeInfo): List<String> {
        val limit = (resources.displayMetrics.heightPixels * 0.30).toInt()
        val found = ArrayList<Pair<Int, String>>()
        val r = Rect()
        var count = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (count++ > 700 || depth > 25) return
            n.getBoundsInScreen(r)
            if (r.bottom in 1..limit) {
                listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).forEach { raw ->
                    val t = raw.trim()
                    if (t.length in 2..50 && Catalog.norm(t) !in GENERIC_TITLES) found.add(r.top to t)
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return found.sortedBy { it.first }.map { it.second }.distinct().take(4)
    }

    // ---- events ------------------------------------------------------------------------------

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        val state = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (state) onForeground(pkg, e.className?.toString())
        maintain()

        if (Store.hasPin && Store.locked.containsKey(pkg) && (state || activeIs(pkg))) enforceLock(pkg)
        updateCapture(pkg, state)

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

    private fun onForeground(pkg: String, cls: String?) {
        if (isTransient(pkg)) return
        if (pkg == packageName) {
            // Magen's own lock/block screens appear over the app that is being guarded: not "leaving" it.
            // Magen's main window, on the other hand, means the person left the guarded app.
            if (cls != MainActivity::class.java.name) return
        }
        if (pkg != currentApp) {
            // Another app (or Magen) is now in front: every temporary unlock except the new one ends here.
            tempUnlocked.retainAll { it == pkg }
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
        if (now - lastLaunch < 600) {
            // The event arrived too soon after the previous lock screen. It may be the only one this app
            // produces, so look again shortly instead of leaving the app unprotected.
            recheckPkg = pkg
            handler.removeCallbacks(recheck)
            handler.postDelayed(recheck, 700)
            return
        }
        lastLaunch = now
        showCover()
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
        val visibleTexts = collect(root, false).map { Catalog.norm(it) }
        // Ignore generic home-feed/ad cards: their Install buttons are ads, not a user opening
        // a specific app page. Still block Install on search results and app-detail pages.
        val homeFeed = visibleTexts.any { it in setOf("for you", "recommended for you", "מומלץ בשבילך", "בשבילך") } &&
            visibleTexts.none { it in setOf("what's new", "about this app", "ratings and reviews", "מה חדש", "מידע על האפליקציה", "דירוגים וביקורות") }
        if (!homeFeed && hasInstallAction(root)) block(R.string.blocked_play)
    }

    /**
     * Only count a visible install label when it belongs to a clickable control. This avoids
     * matching review text, ad descriptions, and unrelated text that merely contains "install".
     */
    private fun hasInstallAction(root: AccessibilityNodeInfo): Boolean {
        var count = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int, clickableAncestor: Boolean): Boolean {
            if (count++ > 900 || depth > 28) return false
            val clickable = clickableAncestor || node.isClickable
            val labels = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .map { Catalog.norm(it) }
            if (clickable && labels.any { it in Catalog.installWords }) return true
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (walk(child, depth + 1, clickable)) return true
            }
            return false
        }
        return walk(root, 0, false)
    }

    // ---- Settings ----------------------------------------------------------------------------

    private fun scanSettings() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in Catalog.SETTINGS) return
        val top = collect(root, true).map { Catalog.norm(it) }
        if (top.isEmpty()) return

        val selfProtected = Store.protect && !Store.uninstallAllowed() && Policy.isAdmin(this)
        // Best-effort protection: close the Accessibility page while Magen is enabled.
        // Android/OEM shortcuts may still bypass this when the service is switched off.
        val accessibilityPage = Catalog.byId["accessibility"]?.keywords?.any { Catalog.norm(it) in top } == true
        if (Store.protect && Policy.isAdmin(this) && accessibilityPage) {
            block(R.string.blocked_settings)
            return
        }
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
