package net.zeevox.nearow.data

import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import net.zeevox.nearow.BuildConfig
import net.zeevox.nearow.data.rate.Autocorrelator
import net.zeevox.nearow.input.DataCollectionService
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random

class DataProcessor {

    companion object {
        // rough estimate for sample rate
        private const val SAMPLE_RATE = 1000000 / DataCollectionService.ACCELEROMETER_SAMPLING_DELAY

        // roughly 50Hz sampling - max 10 second buffer -> 12spm min detection (Nyquist)
        // autocorrelation works best when the buffer size is a power of two
        private val ACCEL_BUFFER_SIZE = Utils.nextPowerOf2(SAMPLE_RATE * 10)

        // smooth jumpy stroke rate -- take moving average of this period
        private const val STROKE_RATE_MOV_AVG_PERIOD = 3

        // milliseconds between stroke rate recalculations
        private const val STROKE_RATE_RECALCULATION_PERIOD = 1000L

        // seconds to wait before starting stroke rate calculations
        private const val STROKE_RATE_INITIAL_DELAY = 3000L

        // magic number determined empirically
        // https://stackoverflow.com/a/1736623
        private const val FILTERING_FACTOR = 0.1
        private const val CONJUGATE_FILTERING_FACTOR = 1.0 - FILTERING_FACTOR
    }

    interface DataUpdateListener {
        @UiThread
        fun onNewAccelerationReading(reading: Double) {
        }

        @UiThread
        fun onNewAutocorrelationTable(array: DoubleArray) {
        }

        /**
         * Called when stroke rate is recalculated
         * [strokeRate] - estimated rate in strokes per minute
         */
        @UiThread
        fun onStrokeRateUpdate(strokeRate: Double)

        /**
         * Called when a new GPS fix is obtained
         * [speed] - speed in metres per second
         * [totalDistance] - new total distance travelled
         */
        @UiThread
        fun onLocationUpdate(speed: Float, totalDistance: Float)
    }

    private var listener: DataUpdateListener? = null

    fun setListener(listener: DataUpdateListener) {
        this.listener = listener
    }

    private val strokeRateCalculator = Thread {
        // after a three-second stabilisation period,
        // recalculate stroke rate roughly once per second
        fixedRateTimer(
            "strokeRateCalculator",
            false,
            STROKE_RATE_INITIAL_DELAY,
            STROKE_RATE_RECALCULATION_PERIOD
        ) {
            recalculateStrokeRate()
            // this alternate thread cannot alter UI elements so post the callback onto the main thread
            // https://stackoverflow.com/a/56852228
            Handler(Looper.getMainLooper()).post {
                listener?.onStrokeRateUpdate(strokeRate)
            }
        }
    }

    // somewhere to store acceleration readings
    private val accelReadings =
        CircularDoubleBuffer(ACCEL_BUFFER_SIZE) { Random.nextDouble() * (if (Random.nextBoolean()) 1 else -1) }

    // and another one for their corresponding timestamps
    // this is so that we can calculate the sampling frequency
    private val timestamps = CircularDoubleBuffer(ACCEL_BUFFER_SIZE)

    // store last few stroke rate values for smoothing
    private val recentStrokeRates = CircularDoubleBuffer(STROKE_RATE_MOV_AVG_PERIOD)

    private val strokeRate: Double
        get() = recentStrokeRates.average()

    // store last acceleration reading for ramping
    private val lastAccelReading = DoubleArray(3)

    private lateinit var lastLocation: Location

    private var totalDistance: Float = 0f

    private val startTimestamp = System.currentTimeMillis()

    init {
        // calculate the stroke rate on another thread to not slow down the GUI
        strokeRateCalculator.start()
    }

    fun addAccelerometerReading(readings: FloatArray) {
        // ramp-speed filtering https://stackoverflow.com/a/1736623
        val filtered =
            DoubleArray(3) { readings[it] * FILTERING_FACTOR + lastAccelReading[it] * CONJUGATE_FILTERING_FACTOR }

        // calculate magnitude of the acceleration
        val magnitude = Utils.magnitude(filtered)

        // add the acceleration reading to the end of the buffer; displace an old value
        accelReadings.addLast(magnitude)

        // store the corresponding timestamp as well
        timestamps.addLast(((System.currentTimeMillis() - startTimestamp) / 1000L).toDouble())

        // let our debug listener know that a reading has come in
        if (BuildConfig.DEBUG)
            listener?.onNewAccelerationReading(magnitude)

        // save current readings into memory for when next readings come in
        System.arraycopy(filtered, 0, lastAccelReading, 0, 3)
    }

    /**
     * Called when a new GPS measurement comes in.
     * This subroutine stores this measurement in the database
     * and informs any UI listener of this new measurement
     */
    fun addGpsReading(location: Location) {
        // sum total distance travelled
        if (this::lastLocation.isInitialized) totalDistance += location.distanceTo(lastLocation)

        // inform our listener of a new GPS location
        listener?.onLocationUpdate(location.speed, totalDistance)

        // save this for calculating the distance travelled when next GPS measurement comes in
        lastLocation = location
    }

    /**
     * Calculate the sampling frequency of the accelerometer in Hertz
     */
    private val accelerometerSamplingRate: Double
        get() = timestamps.size / (timestamps.head - timestamps.tail)

    /**
     * Convert an integer number of samples into a frequency in SPM
     */
    private fun samplesCountToFrequency(samplesPerStroke: Int): Double =
        if (samplesPerStroke <= 0) 0.0
        else 60.0 / samplesPerStroke * accelerometerSamplingRate


    @WorkerThread
    private fun recalculateStrokeRate(): Double {
        val frequencyScores = Autocorrelator.getFrequencyScores(accelReadings)

        // for debug purposes - post the table with scores for each frequency
        // this is to visualise which stroke rates are being selected as most probable
        if (BuildConfig.DEBUG) Handler(Looper.getMainLooper()).post {
            listener?.onNewAutocorrelationTable(frequencyScores)
        }

        val strokeRate = samplesCountToFrequency(
            Autocorrelator.getBestFrequency(
                frequencyScores,
                accelerometerSamplingRate.toInt() // no more than 60 spm
            )
        )

        recentStrokeRates.addLast(strokeRate)
        return strokeRate
    }
}