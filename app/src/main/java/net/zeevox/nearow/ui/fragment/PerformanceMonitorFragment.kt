package net.zeevox.nearow.ui.fragment

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import net.zeevox.nearow.R
import net.zeevox.nearow.data.DataProcessor
import net.zeevox.nearow.databinding.FragmentPerformanceTrackerBinding
import net.zeevox.nearow.input.DataCollectionService
import net.zeevox.nearow.utils.UnitConverter
import java.io.File

class PerformanceMonitorFragment : Fragment(), DataProcessor.DataUpdateListener {

    private var _binding: FragmentPerformanceTrackerBinding? = null
    private val binding get() = _binding!!

    /** for communicating with the service */
    lateinit var mService: DataCollectionService

    /** whether there is an established link with the service */
    private var mBound: Boolean = false

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as DataCollectionService.LocalBinder
            mService = binder.getService()
            mBound = true

            // Listen to callbacks from the service
            mService.setDataUpdateListener(this@PerformanceMonitorFragment)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

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
                    val builder = AlertDialog.Builder(requireContext())
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPerformanceTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }


    /**
     * Called immediately after [onCreateView]
     * has returned, but before any saved state has been restored in to the view.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // do not let the screen dim or switch off
        binding.root.keepScreenOn = true

        // TODO create alternative landscape layout file to support rotation
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        binding.startStopButton.setOnClickListener {
            if (!mService.dataProcessor.isRecording)
                startTracking()
            else Snackbar.make(
                binding.root,
                "Long press to stop recording",
                Snackbar.LENGTH_LONG
            ).show()
        }

        // long click used to stop recording to prevent water splashes from stopping session
        binding.startStopButton.setOnLongClickListener {
            if (!mService.dataProcessor.isRecording) startTracking()
            else stopTracking()

            true
        }
    }

    private fun startTracking() {
        // reset chronometer
        binding.timer.base = SystemClock.elapsedRealtime()
        // start counting up
        binding.timer.start()

        mService.dataProcessor.startRecording()

        binding.startStopButton.apply {
            text = getString(R.string.action_stop_tracking)
            backgroundTintList = ColorStateList.valueOf(
                ResourcesCompat.getColor(resources, R.color.end_red, null))
            // https://stackoverflow.com/a/29146895
            icon =
                ResourcesCompat.getDrawable(resources, R.drawable.ic_round_stop_24, null)
        }
    }

    private fun stopTracking() {
        binding.timer.stop()

        mService.dataProcessor.stopRecording()

        binding.startStopButton.apply {
            text = getString(R.string.action_start_tracking)
            backgroundTintList = ColorStateList.valueOf(
                ResourcesCompat.getColor(resources, R.color.start_green, null))
            icon =
                ResourcesCompat.getDrawable(resources, R.drawable.ic_round_play_arrow_24, null)
        }

        mService.dataProcessor.exportSession()
    }

    /** Bind to LocalService. If it is not running, automatically start it up */
    private fun bindAndStartDataCollectionService() {
        val dataCollectionServiceIntent =
            Intent(requireContext(), DataCollectionService::class.java)

        dataCollectionServiceIntent.putExtra(DataCollectionService.KEY_SHOW_NOTIFICATION, false)

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) dataCollectionServiceIntent.putExtra(DataCollectionService.KEY_ENABLE_GPS, false)
        else {
            // request GPS permission, callback will re-call this function to start the service
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        // bind and automatically create the service
        requireContext().bindService(dataCollectionServiceIntent,
            connection,
            Context.BIND_AUTO_CREATE)

        // mark the service as started so that it is not killed
        // https://stackoverflow.com/a/43742797
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            requireContext().startForegroundService(dataCollectionServiceIntent)
        else requireContext().startService(dataCollectionServiceIntent)
    }

    /**
     * Called when the view previously created by [onCreateView] has
     * been detached from the fragment.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Called when the Fragment is visible to the user.
     */
    override fun onStart() {
        super.onStart()
        bindAndStartDataCollectionService()
    }

    /**
     * Called when the Fragment is no longer started.
     */
    override fun onStop() {
        super.onStop()
        requireContext().unbindService(connection)
        mBound = false
    }

    /**
     * Called when stroke rate is recalculated
     * [strokeRate] - estimated rate in strokes per minute
     */
    override fun onStrokeRateUpdate(strokeRate: Double) {
        binding.strokeRate.text = String.format("%.1f", strokeRate)
    }


    /**
     * Called when a new GPS fix is obtained
     */
    override fun onLocationUpdate(location: Location, totalDistance: Float) {
        Log.d(javaClass.simpleName, "Received new location update")
        binding.splitFrame.visibility = View.VISIBLE
        binding.distanceFrame.visibility = View.VISIBLE
        binding.split.text = UnitConverter.speedToSplitFormatted(location.speed)
        binding.distance.text = String.format("%.0f", totalDistance)
    }

    /**
     * Called once a session has been finished and successfully
     * exported to a file.
     * https://developer.android.com/training/secure-file-sharing/share-file
     */
    override fun onTrackExported(exportedFile: File) {
        val fileUri: Uri = try {
            FileProvider.getUriForFile(
                requireContext(),
                "net.zeevox.nearow.fileprovider",
                exportedFile
            )
        } catch (e: IllegalArgumentException) {
            Log.e(
                "File Selector",
                "The selected file can't be shared: $exportedFile"
            )
            null
        } ?: return

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_TITLE, "Exported fit file")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Share the exported FIT file with another application"
                    )
                    setDataAndType(fileUri, "application/vnd.ant.fit")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }, null
            )
        )
    }
}