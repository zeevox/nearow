package net.zeevox.nearow.input

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import io.objectbox.Box
import net.zeevox.nearow.MainActivity
import net.zeevox.nearow.MyObjectBox
import net.zeevox.nearow.R
import net.zeevox.nearow.data.DataProcessor
import net.zeevox.nearow.model.DataRecord

// initially based on https://www.raywenderlich.com/10838302-sensors-tutorial-for-android-getting-started

class DataCollectionService : Service(), SensorEventListener, LocationListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager

    private var background = false
    private val notificationActivityRequestCode = 0
    private val notificationId = 1
    private val notificationStopRequestCode = 2

    private lateinit var dataProcessor: DataProcessor

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

    override fun onBind(intent: Intent): IBinder = binder

    fun setDataUpdateListener(listener: DataProcessor.DataUpdateListener) =
        dataProcessor.setListener(listener)

    // ObjectBox was chosen because of the below
    // https://proandroiddev.com/android-databases-performance-crud-a963dd7bb0eb
    // ref https://github.com/objectbox/objectbox-java
    private lateinit var box: Box<DataRecord>

    override fun onCreate() {
        super.onCreate()

        initialiseDataProcessor()

        registerSensorListener()
        registerGpsListener()

        val notification = createNotification()
        startForeground(notificationId, notification)
    }

    private fun initialiseDataProcessor() {
        val boxStore = MyObjectBox.builder().androidContext(this@DataCollectionService).build()
        box = boxStore.boxFor(DataRecord::class.java)

        dataProcessor = DataProcessor(box)
    }

    private fun registerSensorListener() {
        sensorManager = getSystemService(AppCompatActivity.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }
    }

    private fun registerGpsListener() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0L,
            0f,
            this@DataCollectionService
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { background = it.getBooleanExtra(KEY_BACKGROUND, false) }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> dataProcessor.addAccelerometerReading(event.values)
        }


        if (background) {
            val notification = createNotification()
            startForeground(notificationId, notification)
        } else stopForeground(true)
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
        /**
         * This method is called when the BroadcastReceiver is receiving an Intent
         * broadcast.  During this time you can use the other methods on
         * BroadcastReceiver to view/modify the current result values.  This method
         * is always called within the main thread of its process, unless you
         * explicitly asked for it to be scheduled on a different thread using
         * [android.content.Context.registerReceiver].
         *
         * If you wish to interact with a service that is already running and previously
         * bound using [bindService()][android.content.Context.bindService], you can
         * use [peekService].
         *
         * [onReceive] implementations should respond only to known actions, ignoring
         * any unexpected [Intent] that they may receive.
         *
         * @param context The Context in which the receiver is running.
         * @param intent The Intent being received.
         */
        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent == null || intent.action == null) return

            if (intent.action.equals(KEY_NOTIFICATION_STOP_ACTION)) context?.let {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val dataServiceIntent = Intent(context, DataCollectionService::class.java)
                context.stopService(dataServiceIntent)
                val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1)
                if (notificationId != -1) notificationManager.cancel(notificationId)
            }

        }
    }

    /**
     * Called when the location has changed. A wakelock may be held on behalf on the listener for
     * some brief amount of time as this callback executes. If this callback performs long running
     * operations, it is the client's responsibility to obtain their own wakelock if necessary.
     *
     * @param location the updated location
     */
    override fun onLocationChanged(location: Location) = dataProcessor.addGpsReading(location)

}