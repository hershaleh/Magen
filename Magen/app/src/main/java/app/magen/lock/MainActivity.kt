package app.magen.lock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.SystemClock
import app.magen.lock.ui.MagenTheme
import app.magen.lock.ui.Root
import app.magen.lock.ui.Session
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MagenTheme {
                // Saved across rotation and language changes, so the opening screen shows once per start only.
                var showLaunchScreen by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    if (showLaunchScreen) {
                        delay(1100)
                        showLaunchScreen = false
                    }
                }
                // Notification permission is asked once, and only after the code was entered.
                LaunchedEffect(Session.authed) {
                    if (Session.authed) askNotificationsOnce()
                }
                Box(Modifier.fillMaxSize()) {
                    Root()
                    if (showLaunchScreen) {
                        val transition = rememberInfiniteTransition(label = "launch-spinner")
                        val rotation by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(850, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "launch-rotation",
                        )
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color(0xFF4F46E5)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(22.dp),
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_fg),
                                    contentDescription = getString(R.string.app_name),
                                    modifier = Modifier.size(88.dp),
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(30.dp).rotate(rotation),
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** Needed for the "protection is off" alert on Android 13+. Later the Home check shows a warning with a button. */
    private fun askNotificationsOnce() {
        val prefs = getSharedPreferences("magen", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && Store.hasPin && !prefs.getBoolean("notifAsked", false) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean("notifAsked", true).apply()
            runCatching { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    override fun onStart() {
        super.onStart()
        // Coming back from a trip that Magen started itself (Settings, uninstall screen, Keeper): allowed
        // for a few minutes. Any other return demands the code again (see onStop).
        if (Session.tripPending) {
            Session.tripPending = false
            if (SystemClock.elapsedRealtime() > Session.tripUntil) Session.authed = false
        }
        // The exception is used up by this return, so it cannot cover a later departure.
        Session.tripUntil = 0L
    }

    override fun onPause() {
        super.onPause()
        if (Session.uninstallPending) Session.uninstallLeft = true
    }

    override fun onResume() {
        super.onResume()
        // Back from the uninstall screen with Magen still installed: the person cancelled, so the approval
        // window (which pauses protection) is closed again.
        if (Session.uninstallPending && Session.uninstallLeft) {
            Session.uninstallPending = false
            Session.uninstallLeft = false
            Store.allowUninstall(0)
            // Tell the person what state Android is really in (shown once the code is confirmed).
            Session.uninstallNotice = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            val now = SystemClock.elapsedRealtime()
            Session.leftAt = now
            // Leaving the app locks it at once; the process often stays alive (the accessibility service
            // keeps it running), so the old "still unlocked" flag must not survive until the next visit.
            if (now <= Session.tripUntil) Session.tripPending = true else Session.authed = false
        }
    }
}
