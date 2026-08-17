package com.familyguard.parent.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.familyguard.parent.MainActivity
import com.familyguard.parent.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the two local notification types this app sends (docs/roadmap.md notes this is the
 * interim mechanism until FCM push exists): "device went offline" and "sync issue". Calm,
 * informational phrasing — not alarmist — matching the family-safety tone requirement.
 */
@Singleton
class DeviceStatusNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Device status",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Lets you know when a child's device goes offline or has trouble syncing."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun notifyDeviceOffline(childName: String) {
        post(
            id = childName.hashCode(),
            title = "$childName's device is offline",
            text = "We haven't heard from this device in a while. We'll keep watching in the background.",
        )
    }

    fun notifySyncIssue(childName: String) {
        post(
            id = childName.hashCode() xor SYNC_ISSUE_ID_SALT,
            title = "Trouble syncing $childName's device",
            text = "The last background refresh didn't go through. Pull to refresh on the dashboard to retry now.",
        )
    }

    private fun post(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // permission not granted; silently skip rather than crash
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(MainActivity.pendingIntent(context))
            .build()
        androidx.core.app.NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val CHANNEL_ID = "device_status"
        const val SYNC_ISSUE_ID_SALT = 0x5A5A
    }
}
