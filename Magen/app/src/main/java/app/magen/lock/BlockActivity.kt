package app.magen.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.magen.lock.ui.BlockContent
import app.magen.lock.ui.MagenTheme

/** Small "Blocked by Magen" notice shown after [GuardService] closes a forbidden screen. */
class BlockActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_REASON = "reason"
    }

    private var reason by mutableIntStateOf(R.string.blocked_settings)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        reason = intent.getIntExtra(EXTRA_REASON, R.string.blocked_settings)
        setContent { MagenTheme { BlockContent(reason) { goHome() } } }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reason = intent.getIntExtra(EXTRA_REASON, R.string.blocked_settings)
    }

}
