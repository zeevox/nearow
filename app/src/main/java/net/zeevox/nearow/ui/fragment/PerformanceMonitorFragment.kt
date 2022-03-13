package net.zeevox.nearow.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import hallianinc.opensource.timecounter.StopWatch
import net.zeevox.nearow.R
import net.zeevox.nearow.databinding.FragmentPerformanceTrackerBinding

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

                binding.startStopButton.text = getString(R.string.action_stop_tracking)
                // https://stackoverflow.com/a/29146895
                binding.startStopButton.icon =
                    ResourcesCompat.getDrawable(resources, android.R.drawable.ic_media_pause, null)
            } else {
                stopWatch.pause()

                binding.startStopButton.text = getString(R.string.action_start_tracking)
                binding.startStopButton.icon =
                    ResourcesCompat.getDrawable(resources, android.R.drawable.ic_media_play, null)
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
     * Convert a speed, measured in metres per second, into the number
     * of seconds that would be required to cover 500m at this speed.
     */
    fun metersPerSecondToSecondsPer500(speed: Float): Float {
        return 500 / speed
    }

    /**
     * Called when a new GPS fix is obtained
     * [speed] - speed in metres per second
     * [totalDistance] - new total distance travelled
     */
    override fun onLocationUpdate(speed: Float, totalDistance: Float) {
        Log.d(javaClass.simpleName, "Received new location update")
        binding.splitFrame.visibility = View.VISIBLE
        binding.distanceFrame.visibility = View.VISIBLE
        binding.split.text = metersPerSecondToSecondsPer500(speed).toString()
        binding.distance.text = String.format("%.0f", totalDistance)
    }
}