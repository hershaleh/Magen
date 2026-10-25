package app.magen.lock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Re-apply system restrictions immediately if Magen is provisioned as Device Owner.
        Policy.apply()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    /**
     * Android calls this whenever Magen stops being a device administrator, whatever the reason (the person
     * switched it off, a transfer of ownership, a reinstall, a system clean-up). The time and whether Magen
     * itself expected it are kept for troubleshooting, and an unexpected loss raises an alert.
     */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        val expected = SystemClock.uptimeMillis() < Policy.adminLossExpectedUntil
        val sp = context.applicationContext.getSharedPreferences("magen", Context.MODE_PRIVATE)
        sp.edit()
            .putLong("adminLostAt", System.currentTimeMillis())
            .putBoolean("adminLostExpected", expected)
            .apply()
        if (expected || !sp.contains("hash") || !sp.getBoolean("protect", true)) return
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("magen_guard", context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH),
            )
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, "magen_guard")
                .setSmallIcon(R.drawable.ic_fg)
                .setContentTitle(context.getString(R.string.notif_admin_off_title))
                .setContentText(context.getString(R.string.notif_admin_off_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_admin_off_text)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(42, n)
            }
        }
    }
}
