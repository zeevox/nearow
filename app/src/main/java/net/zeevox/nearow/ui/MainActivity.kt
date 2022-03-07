package net.zeevox.nearow.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import net.zeevox.nearow.R
import net.zeevox.nearow.databinding.ActivityMainBinding
import net.zeevox.nearow.input.DataCollectionService
import net.zeevox.nearow.ui.fragment.BaseFragment
import net.zeevox.nearow.ui.fragment.StrokeRateDebuggerFragment


class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding


    private lateinit var mService: DataCollectionService
    private var mBound: Boolean = false

    private lateinit var fragment: BaseFragment


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
        binding.root.keepScreenOn = true

        if (savedInstanceState == null) {
            fragment = StrokeRateDebuggerFragment()
            supportFragmentManager
                .beginTransaction()
                .add(R.id.main_content, fragment)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        startForegroundServiceForSensors(false)
    }

    private fun startForegroundServiceForSensors(background: Boolean) {
        val dataCollectionServiceIntent = Intent(this, DataCollectionService::class.java)
        dataCollectionServiceIntent.putExtra(DataCollectionService.KEY_BACKGROUND, background)
        ContextCompat.startForegroundService(this, dataCollectionServiceIntent)
    }

    override fun onPause() {
        super.onPause()
        startForegroundServiceForSensors(true)
    }

    override fun onStart() {
        super.onStart()
        // Bind to LocalService
        Intent(this, DataCollectionService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }
}
