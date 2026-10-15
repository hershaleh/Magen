package app.magen.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.magen.lock.ui.LockContent
import app.magen.lock.ui.MagenTheme

/** Full-screen lock shown by [GuardService] on top of a locked app. */
class LockActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PKG = "pkg"
    }

    private var pkg by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            MagenTheme {
                LockContent(
                    pkg = pkg,
                    onUnlocked = {
                        GuardService.instance?.markUnlocked(pkg)
                        finish()
                    },
                    onOpenMain = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onBack = { goHome() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
