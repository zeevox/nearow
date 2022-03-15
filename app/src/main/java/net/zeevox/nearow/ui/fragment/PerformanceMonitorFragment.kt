package net.zeevox.nearow.ui.fragment

import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import hallianinc.opensource.timecounter.StopWatch
import net.zeevox.nearow.R
import net.zeevox.nearow.databinding.FragmentPerformanceTrackerBinding
import net.zeevox.nearow.ui.MainActivity
import net.zeevox.nearow.utils.UnitConverter
import java.io.File

class PerformanceMonitorFragment : BaseFragment() {

    private var _binding: FragmentPerformanceTrackerBinding? = null
    private val binding get() = _binding!!

    private lateinit var stopWatch: StopWatch
    private var stopWatchStarted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPerformanceTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stopWatch = StopWatch(binding.timer)

        binding.startStopButton.setOnClickListener {
            if (!stopWatchStarted) {

                stopWatch.resume()

                // TODO poor assumption: fragment depending on [MainActivity]
                (activity as MainActivity).mService.startTracking()

                binding.startStopButton.text = getString(R.string.action_stop_tracking)
                // https://stackoverflow.com/a/29146895
                binding.startStopButton.icon =
                    ResourcesCompat.getDrawable(resources, R.drawable.ic_round_stop_24, null)
            } else {
                stopWatch.pause()
                // TODO poor assumption: fragment depending on [MainActivity]
                (activity as MainActivity).mService.stopTracking()


                binding.startStopButton.text = getString(R.string.action_start_tracking)
                binding.startStopButton.icon =
                    ResourcesCompat.getDrawable(resources, R.drawable.ic_round_play_arrow_24, null)
            }

            stopWatchStarted = !stopWatchStarted
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
                requireActivity(),
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