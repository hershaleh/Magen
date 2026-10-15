package app.magen.lock

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.magen.lock.ui.MagenTheme
import app.magen.lock.ui.Root
import app.magen.lock.ui.Session

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MagenTheme { Root() } }
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
