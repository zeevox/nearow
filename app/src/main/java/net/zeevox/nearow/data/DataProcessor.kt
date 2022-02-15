package net.zeevox.nearow.data

import android.location.Location
import android.os.Handler
import android.os.Looper
import io.objectbox.Box
import net.zeevox.nearow.data.rate.Autocorrelator
import net.zeevox.nearow.model.DataRecord
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random

class DataProcessor(private val dataBox: Box<DataRecord>) {

    companion object {
        // rough estimate for sample rate
        private const val SAMPLE_RATE = 50

        // roughly 50Hz sampling - max 10 second buffer -> 12spm min detection (Nyquist)
        // autocorrelation works best when the buffer size is a power of two
        private val ACCEL_BUFFER_SIZE = Utils.nextPowerOf2(SAMPLE_RATE * 10)

        // smooth jumpy stroke rate -- take moving average of this period
        private const val STROKE_RATE_MOV_AVG_PERIOD = 3

        // magic number determined empirically
        private const val FILTERING_FACTOR = 0.1
        private const val CONJUGATE_FILTERING_FACTOR = 1.0 - FILTERING_FACTOR
    }

    interface DataUpdateListener {
        fun onStrokeTaken(strokeEvent: StrokeEvent)
        fun onNewAccelerationReading(reading: Double)
        fun onStrokeRateUpdate(strokeRate: Double)
        fun onNewAutocorrelationTable(array: DoubleArray)
    }

    private var listener: DataUpdateListener? = null

    fun setListener(listener: DataUpdateListener) {
        this.listener = listener
    }

    private val strokeRateCalculator = Thread {
        // after a three-second stabilisation period,
        // recalculate stroke rate roughly once per second
        fixedRateTimer("strokeRateCalculator", false, 3L, 1000) {
            recalculateStrokeRate()
            // this alternate thread cannot alter UI elements so post the callback onto the main thread
            Handler(Looper.getMainLooper()).post { listener?.onStrokeRateUpdate(strokeRate) }
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

    val strokeRate : Double
        get() = recentStrokeRates.average()

    // store last acceleration reading for ramping
    private val lastAccelReading = DoubleArray(3)

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

        // let our listener know that a reading has come in
        listener?.onNewAccelerationReading(magnitude)

        // save current readings into memory for when next readings come in
        System.arraycopy(filtered, 0, lastAccelReading, 0, 3)
    }

    fun addGpsReading(location: Location) {

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


    private fun recalculateStrokeRate(): Double {
        val frequencyScores = Autocorrelator.getFrequencyScores(accelReadings)
        listener?.onNewAutocorrelationTable(frequencyScores)

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