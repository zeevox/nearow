package net.zeevox.nearow.input

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.zeevox.nearow.R

class NotificationUtils private constructor() {
    companion object {
        private const val CHANNEL_ID = "tracking_channel"

        @RequiresApi(Build.VERSION_CODES.O)
        internal fun createServiceNotificationChannel(context: Context) {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_tracking_service),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    enableLights(false)
                    setSound(null, null)
                    enableVibration(false)
                    vibrationPattern = longArrayOf(0L)
                    setShowBadge(false)
                }
            )
        }

        internal fun getForegroundServiceNotification(context: Context): Notification {

            val notificationBuilder =
                NotificationCompat.Builder(context, CHANNEL_ID).setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setContentTitle(context.resources.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.notification_background_service_running))
                    .setWhen(System.currentTimeMillis())
                    .setOngoing(true)
                    .setVibrate(longArrayOf(0L))
                    .setSound(null)
                    .setSmallIcon(R.mipmap.ic_launcher_round)

            // Notifications channel required for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createServiceNotificationChannel(context)
                notificationBuilder.setChannelId(CHANNEL_ID)
            }

            return notificationBuilder.build()
        }
    }

}