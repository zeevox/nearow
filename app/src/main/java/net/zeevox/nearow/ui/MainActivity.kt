package net.zeevox.nearow.ui

import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import net.zeevox.nearow.R
import net.zeevox.nearow.databinding.ActivityMainBinding
import net.zeevox.nearow.input.DataCollectionService
import net.zeevox.nearow.ui.fragment.PerformanceMonitorFragment

class MainActivity : AppCompatActivity() {

    /** for accessing UI elements, bind this activity to the XML */
    private lateinit var binding: ActivityMainBinding

    /** the UI fragment currently displayed in this activity */
    private lateinit var fragment: Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(
            VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build())

        setTheme(R.style.Theme_Nearow)

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            fragment = PerformanceMonitorFragment()
            supportFragmentManager.commit {
                // Android Dev best practices say to always setReorderingAllowed to true
                // https://developer.android.com/guide/fragments/create
                setReorderingAllowed(true)
                add<PerformanceMonitorFragment>(R.id.fragment_container_view)
            }
        }
    }

    /** Called when the activity has detected the user's press of the back key. */
    override fun onBackPressed() {
        stopService(Intent(this@MainActivity, DataCollectionService::class.java))
        super.onBackPressed()
    }
}
