package app.magen.lock

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Saves a screenshot when a test fails. The CI script pulls the folder
 * /sdcard/Android/data/app.magen.lock/files/failure-screenshots off the emulator and uploads it as an artifact.
 */
class ScreenshotOnFailureRule : TestWatcher() {
    override fun failed(e: Throwable?, description: Description) {
        runCatching {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val ctx: Context = instrumentation.targetContext
            val dir = File(ctx.getExternalFilesDir(null), "failure-screenshots").apply { mkdirs() }
            val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
            val name = "${description.className.substringAfterLast('.')}.${description.methodName}.png"
            FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
