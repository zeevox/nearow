package net.zeevox.nearow.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import net.zeevox.nearow.R
import net.zeevox.nearow.databinding.ActivityMainBinding
import net.zeevox.nearow.input.DataCollectionService
import net.zeevox.nearow.ui.fragment.BaseFragment
import net.zeevox.nearow.ui.fragment.PerformanceMonitorFragment


class MainActivity : AppCompatActivity() {

    /** for accessing UI elements, bind this activity to the XML */
    private lateinit var binding: ActivityMainBinding

    /** for communicating with the service */
    private lateinit var mService: DataCollectionService

    /** whether there is an established link with the service */
    private var mBound: Boolean = false

    /** the UI fragment currently displayed in this activity */
    private lateinit var fragment: BaseFragment

    /**
     * Register the permissions callback, which handles the user's response to the
     * system permissions dialog.
     * https://developer.android.com/training/permissions/requesting
     */
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            // if permission has been granted return to where we left off and start the service
            if (isGranted) {
                if (!mBound) bindAndStartDataCollectionService()
            } else {
                // create an alert (dialog) to explain functionality loss since permission has been denied
                val permissionDeniedDialog = this.let {
                    val builder = AlertDialog.Builder(it)
                    builder.apply {
                        setTitle(getString(R.string.dialog_title_gps_permission_denied))
                        setMessage(getString(R.string.dialog_msg_gps_permission_denied))
                        setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                    }
                    // Create the AlertDialog
                    builder.create()
                }

                // show the dialog itself
                permissionDeniedDialog.show()
            }
        }


    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as DataCollectionService.LocalBinder
            mService = binder.getService()
            mBound = true

            // Listen to callbacks from the service
            mService.setDataUpdateListener(fragment)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Nearow)

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // do not let the screen dim or switch off
        binding.root.keepScreenOn = true

        if (savedInstanceState == null) {
            fragment = PerformanceMonitorFragment()
            supportFragmentManager
                .beginTransaction()
                .add(R.id.main_content, fragment)
                .commit()
        }
    }

    /**
     * Called when the app screen goes out of view.
     * For the application to not be killed, we display a notification
     * that lets the user know that we are still tracking their rowing.
     */
    override fun onPause() {
        super.onPause()
        if (mBound) mService.showNotification()
    }

    /**
     * Called when the app screen comes back into view.
     * Since the application is now visible, it is not necessary to
     * display a notification, so we can hide it.
     */
    override fun onResume() {
        super.onResume()
        if (mBound) mService.hideNotification()
    }

    /** Bind to LocalService. If it is not running, automatically start it up */
    private fun bindAndStartDataCollectionService() {
        val dataCollectionServiceIntent = Intent(this, DataCollectionService::class.java)

        dataCollectionServiceIntent.putExtra(DataCollectionService.KEY_SHOW_NOTIFICATION, false)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) dataCollectionServiceIntent.putExtra(DataCollectionService.KEY_ENABLE_GPS, false)
        else {
            // request GPS permission, callback will re-call this function to start the service
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        // bind and automatically create the service
        bindService(dataCollectionServiceIntent, connection, Context.BIND_AUTO_CREATE)

        // mark the service as started so that it is not killed
        // https://stackoverflow.com/a/43742797
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(dataCollectionServiceIntent)
        else startService(dataCollectionServiceIntent)
    }

    override fun onStart() {
        super.onStart()
        Log.d(javaClass.simpleName, "onStart called")
        bindAndStartDataCollectionService()
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }
}
