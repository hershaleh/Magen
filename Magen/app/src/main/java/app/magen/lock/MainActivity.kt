package app.magen.lock

import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MagenTheme {
                var showLaunchScreen by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(1100)
                    showLaunchScreen = false
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

    override fun onStart() {
        super.onStart()
        // Short grace period so a trip to Settings (to enable a permission) does not demand the code again.
        if (Session.authed && SystemClock.elapsedRealtime() - Session.leftAt > 30_000) Session.authed = false
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) Session.leftAt = SystemClock.elapsedRealtime()
    }
}
