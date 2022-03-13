package net.zeevox.nearow.input

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import net.zeevox.nearow.data.DataProcessor


// initially based on https://www.raywenderlich.com/10838302-sensors-tutorial-for-android-getting-started

class DataCollectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager

    private lateinit var mNotificationAdministrator: NotificationAdministrator

    // service config flags of sorts
    private var showNotification = false
    private var gpsEnabled = true

    private lateinit var dataProcessor: DataProcessor

    /**
     * Boolean used to determine whether there is a change in device configuration
     * (e.g. orientation change) that has caused the associated activity to be restarted.
     */
    private var mChangingConfiguration = false
    private var mRequestingLocationUpdates = false

    /**
     * Contains parameters used by [FusedLocationProviderClient].
     */
    private lateinit var mLocationRequest: LocationRequest

    /**
     * Provides access to the Fused Location Provider API.
     */
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    /**
     * Callback for changes in location.
     */
    private lateinit var mLocationCallback: LocationCallback

    companion object {
        const val KEY_SHOW_NOTIFICATION = "background"
        const val KEY_ENABLE_GPS = "enable_gps"
        const val KEY_NOTIFICATION_ID = "notificationId"
        const val NOTIFICATION_ID = 7652863
        const val KEY_NOTIFICATION_STOP_ACTION = "net.zeevox.nearow.NOTIFICATION_STOP"
        const val NOTIFICATION_ACTIVITY_REQUEST_CODE = 0
        const val NOTIFICATION_STOP_REQUEST_CODE = 2

        // 20,000 us => ~50Hz sampling
        const val ACCELEROMETER_SAMPLING_DELAY = 20000

        /**
         * The desired interval for location updates. Inexact. Updates may be more or less frequent.
         */
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

        /**
         * The fastest rate for active location updates. Updates will never be more frequent
         * than this value.
         */
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            showNotification = it.getBooleanExtra(KEY_SHOW_NOTIFICATION, false)
            gpsEnabled = it.getBooleanExtra(KEY_ENABLE_GPS, true)
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(javaClass.simpleName, "Starting Nero data collection service...")

        mNotificationAdministrator = NotificationAdministrator(this, application)

        // start the data processor before registering sensor and GPS
        // listeners so that it is ready to receive values as soon as
        // they start coming in.
        dataProcessor = DataProcessor().also {
            // physical sensor data is not permission-protected so no need to check
            registerSensorListener()

            // measuring GPS is neither always needed (e.g. erg) nor permitted by user
            // check that access has been granted to the user's geolocation before starting gps collection
            if (gpsEnabled && isGpsPermissionGranted()) requestLocationUpdates()
        }

        startService(Intent(applicationContext, DataCollectionService::class.java))
    }

    /**
     * Called when a client comes to the foreground and binds with this service.
     * The service should cease to be a foreground service when that happens.
     */
    override fun onBind(intent: Intent?): IBinder {
        stopForeground(true)
        mChangingConfiguration = false
        return binder
    }

    /**
     * Called when a client comes to the foreground and binds with this service.
     * The service should cease to be a foreground service when that happens.
     */
    override fun onRebind(intent: Intent?) {
        stopForeground(true)
        mChangingConfiguration = false
        super.onRebind(intent)
    }

    /**
     * Called when the last client unbinds from this service. If this method is
     * called due to a configuration change in the associated activity,  do
     * nothing. Otherwise, we make this service a foreground service.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(javaClass.simpleName, "Last client unbound from service")

        if (!mChangingConfiguration && mRequestingLocationUpdates)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(
                    Intent(
                        this,
                        DataCollectionService::class.java
                    )
                ) else startForeground(
                NOTIFICATION_ID,
                mNotificationAdministrator.getForegroundServiceNotification()
            )
        return true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mChangingConfiguration = true
    }

    fun setDataUpdateListener(listener: DataProcessor.DataUpdateListener) =
        dataProcessor.setListener(listener)

    private fun registerSensorListener() {
        sensorManager = getSystemService(AppCompatActivity.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                ACCELEROMETER_SAMPLING_DELAY
            )
        }
    }

    private fun isGpsPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }


    /**
     * https://github.com/android/location-samples/blob/main/LocationUpdatesForegroundService/app/src/main/java/com/google/android/gms/location/sample/locationupdatesforegroundservice/LocationUpdatesService.java
     */
    private fun requestLocationUpdates() {
        Log.d(javaClass.simpleName, "Requesting GPS location updates")

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                dataProcessor.addGpsReading(locationResult.lastLocation)
            }
        }

        createLocationRequest()

        mRequestingLocationUpdates = true

        try {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback,
                Looper.getMainLooper()
            )
        } catch (unlikely: SecurityException) {
            mRequestingLocationUpdates = false
            Log.e(
                javaClass.simpleName,
                "Lost location permission. Could not request updates. $unlikely"
            )
        }
    }

    /**
     * Sets the location request parameters.
     */
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest.create()
        mLocationRequest.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> dataProcessor.addAccelerometerReading(event.values)
        }

        if (showNotification) startForeground(
            NOTIFICATION_ID,
            mNotificationAdministrator.getForegroundServiceNotification()
        ) else stopForeground(true)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // TODO accuracy handling?
    }

    class ActionListener : BroadcastReceiver() {
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

    fun showNotification() {
        showNotification = true
    }

    fun hideNotification() {
        showNotification = false
    }

}