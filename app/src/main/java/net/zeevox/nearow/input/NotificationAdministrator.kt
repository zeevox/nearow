package net.zeevox.nearow.input

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import net.zeevox.nearow.R
import net.zeevox.nearow.ui.MainActivity

class NotificationAdministrator(
    private val context: Context,
    private val application: Application
) {
    @RequiresApi(Build.VERSION_CODES.O)
    internal fun createServiceNotificationChannel() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationChannel = NotificationChannel(
            application.packageName,
            context.getString(R.string.notification_channel_tracking_service),
            NotificationManager.IMPORTANCE_MIN
        )

        // Configure the notification channel.
        notificationChannel.apply {
            enableLights(false)
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = longArrayOf(0L)
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(notificationChannel)
    }

    private fun getPendingStopNotificationIntent(): PendingIntent {
        // Stop notification intent
        val stopNotificationIntent =
            Intent(context, DataCollectionService.ActionListener::class.java)
        stopNotificationIntent.action = DataCollectionService.KEY_NOTIFICATION_STOP_ACTION
        stopNotificationIntent.putExtra(
            DataCollectionService.KEY_NOTIFICATION_ID,
            DataCollectionService.NOTIFICATION_ID
        )
        return PendingIntent.getBroadcast(
            context,
            DataCollectionService.NOTIFICATION_STOP_REQUEST_CODE,
            stopNotificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getPendingOpenActivityFromNotificationIntent(): PendingIntent {
        // Open activity intent
        return PendingIntent.getActivity(
            context,
            DataCollectionService.NOTIFICATION_ACTIVITY_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            // https://stackoverflow.com/a/67046334
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    internal fun getForegroundServiceNotification(): Notification {
        // Notifications channel required for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createServiceNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(context, application.packageName)

        notificationBuilder.setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentTitle(context.resources.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_background_service_running))
            .setWhen(System.currentTimeMillis())
            .setDefaults(0)
            .setVibrate(longArrayOf(0L))
            .setSound(null)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(getPendingOpenActivityFromNotificationIntent())
            .addAction(
                R.mipmap.ic_launcher_round,
                context.getString(R.string.stop_notifications),
                getPendingStopNotificationIntent()
            )

        return notificationBuilder.build()
    }


}