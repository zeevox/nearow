package net.zeevox.nearow.input

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import net.zeevox.nearow.MainActivity
import net.zeevox.nearow.R


class DataCollectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private var background = false
    private val notificationActivityRequestCode = 0
    private val notificationId = 1
    private val notificationStopRequestCode = 2

    companion object {
        const val KEY_BACKGROUND = "background"
        const val KEY_NOTIFICATION_ID = "notificationId"
        const val KEY_NOTIFICATION_STOP_ACTION = "net.zeevox.nearow.NOTIFICATION_STOP"

        const val SAMPLE_BUFFER_SIZE = 32
        const val COMPONENTS_PER_SAMPLE = 64
    }

    /** https://developer.android.com/guide/components/bound-services#Binder **/
    private val binder = LocalBinder()

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): DataCollectionService = this@DataCollectionService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    interface DataUpdateListener {
        fun onNewAccelerometerReadings(readings: FloatArray, samplingRate: Float)
    }

    private var listener: DataUpdateListener? = null

    fun setListener(listener: DataUpdateListener) {
        this.listener = listener
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(AppCompatActivity.SENSOR_SERVICE) as SensorManager

        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }

        val notification = createNotification()
        startForeground(notificationId, notification)
    }

    private var startTime = System.nanoTime()
    private var count = 0

    private fun calculateSensorFrequency(): Float {
        return count++ / ((System.nanoTime() - startTime) / 1000000000f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { background = it.getBooleanExtra(KEY_BACKGROUND, false) }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> System.arraycopy(
                event.values,
                0,
                accelerometerReading,
                0,
                accelerometerReading.size
            )
        }

        if (background) {
            val notification = createNotification()
            startForeground(notificationId, notification)
        } else stopForeground(true)

        this.listener?.onNewAccelerometerReadings(accelerometerReading, calculateSensorFrequency())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // TODO accuracy handling?
    }

    private fun createNotification(): Notification {

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Notifications channel required for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                application.packageName,
                getString(R.string.notification_channel_tracking_service),
                NotificationManager.IMPORTANCE_DEFAULT
            )

            // Configure the notification channel.
            notificationChannel.enableLights(false)
            notificationChannel.setSound(null, null)
            notificationChannel.enableVibration(false)
            notificationChannel.vibrationPattern = longArrayOf(0L)
            notificationChannel.setShowBadge(false)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notificationBuilder = NotificationCompat.Builder(baseContext, application.packageName)
        // Open activity intent
        val contentIntent = PendingIntent.getActivity(
            this,
            notificationActivityRequestCode,
            Intent(this, MainActivity::class.java),
            // https://stackoverflow.com/a/67046334
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop notification intent
        val stopNotificationIntent = Intent(this, WifiP2pManager.ActionListener::class.java)
        stopNotificationIntent.action = KEY_NOTIFICATION_STOP_ACTION
        stopNotificationIntent.putExtra(KEY_NOTIFICATION_ID, notificationId)
        val pendingStopNotificationIntent =
            PendingIntent.getBroadcast(
                this,
                notificationStopRequestCode,
                stopNotificationIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else PendingIntent.FLAG_UPDATE_CURRENT
            )

        notificationBuilder.setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText(getString(R.string.notification_background_service_running))
            .setWhen(System.currentTimeMillis())
            .setDefaults(0)
            .setVibrate(longArrayOf(0L))
            .setSound(null)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(contentIntent)
            .addAction(
                R.mipmap.ic_launcher_round,
                getString(R.string.stop_notifications),
                pendingStopNotificationIntent
            )


        return notificationBuilder.build()
    }

    class ActionListener : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent == null || intent.action == null) return

            if (intent.action.equals(KEY_NOTIFICATION_STOP_ACTION)) {
                context?.let {
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val dataServiceIntent = Intent(context, DataCollectionService::class.java)
                    context.stopService(dataServiceIntent)
                    val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1)
                    if (notificationId != -1) notificationManager.cancel(notificationId)
                }
            }

        }
    }
}