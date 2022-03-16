package net.zeevox.nearow.data

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.room.Room
import kotlinx.coroutines.*
import net.zeevox.nearow.data.rate.Autocorrelator
import net.zeevox.nearow.db.TrackDatabase
import net.zeevox.nearow.db.model.TrackDao
import net.zeevox.nearow.db.model.TrackPoint
import net.zeevox.nearow.input.DataCollectionService
import net.zeevox.nearow.output.FitFileExporter
import java.io.File
import kotlin.math.sqrt
import kotlin.random.Random

class DataProcessor(private val applicationContext: Context) {

    companion object {
        // rough estimate for sample rate
        private const val SAMPLE_RATE = 1000000 / DataCollectionService.ACCELEROMETER_SAMPLING_DELAY

        // roughly 50Hz sampling - max 10 second buffer -> 12spm min detection (Nyquist)
        // autocorrelation works best when the buffer size is a power of two
        private val ACCEL_BUFFER_SIZE = nextPowerOf2(SAMPLE_RATE * 10)

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

        private const val DATABASE_NAME = "nearow"

        /**
         * Return smallest power of two greater than or equal to n
         */
        private fun nextPowerOf2(n: Int): Int {
            // base case already power of 2
            if (n > 0 && n and n - 1 == 0) return n

            // increment through powers of two until find one larger than n
            var p = 1
            while (p < n) p = p shl 1
            return p
        }

        fun magnitude(triple: DoubleArray): Double =
            sqrt(triple[0] * triple[0] + triple[1] * triple[1] + triple[2] * triple[2])
    }

    interface DataUpdateListener {
        /**
         * Called when stroke rate is recalculated
         * [strokeRate] - estimated rate in strokes per minute
         */
        @UiThread
        fun onStrokeRateUpdate(strokeRate: Double)

        /**
         * Called when a new GPS fix is obtained
         * [location] - [Location] with all available GPS data
         * [totalDistance] - new total distance travelled
         */
        @UiThread
        fun onLocationUpdate(location: Location, totalDistance: Float)

        /**
         * Called once a session has been finished and successfully
         * exported to a file.
         */
        @UiThread
        fun onTrackExported(exportedFile: File)
    }

    private var listener: DataUpdateListener? = null

    /**
     * The application database
     */
    private val db: TrackDatabase = Room.databaseBuilder(
        applicationContext,
        TrackDatabase::class.java, DATABASE_NAME
    ).build()

    /**
     * Interface with the table where individual rate-location records are stored
     */
    private val track: TrackDao = db.trackDao()

    /**
     * Increment sessionId each time a new rowing session is started
     * Getting the session ID is expected to be a very quick function
     * call so we can afford to run it as a blocking function
     */
    private var currentSessionId: Int = -1

    /**
     * Check database for existing sessions. The new session ID is
     * auto-incremented from the last recorded session. In case this
     * is the first recorded session, the ID returned is 1.
     */
    private suspend fun getNewSessionId(): Int = coroutineScope {
        val sessionId = async(Dispatchers.IO) { (track.getLastSessionId() ?: 0) + 1 }
        sessionId.await()
    }

    /**
     * Perform CPU-intensive tasks on the `Default` coroutine scope
     */
    private val scope = CoroutineScope(Dispatchers.Default)

    fun setListener(listener: DataUpdateListener) {
        this.listener = listener
    }

    private val strokeRateCalculator: Job =
        scope.launch {
            // after a three-second stabilisation period
            delay(STROKE_RATE_INITIAL_DELAY)
            // recalculate stroke rate roughly once per second
            while (true) {
                // check that coroutine scope has not requested a shutdown
                ensureActive()

                getCurrentStrokeRate()

                // this alternate thread cannot alter UI elements so post the callback onto the main thread
                // https://stackoverflow.com/a/56852228
                Handler(Looper.getMainLooper()).post {
                    listener?.onStrokeRateUpdate(smoothedStrokeRate)
                }

                delay(STROKE_RATE_RECALCULATION_PERIOD)
            }
        }

    // somewhere to store acceleration readings
    private val accelReadings =
        CircularDoubleBuffer(ACCEL_BUFFER_SIZE) { Random.nextDouble() }

    // and another one for their corresponding timestamps
    // this is so that we can calculate the sampling frequency
    private val timestamps = CircularDoubleBuffer(ACCEL_BUFFER_SIZE)

    // store last few stroke rate values for smoothing
    private val recentStrokeRates = CircularDoubleBuffer(STROKE_RATE_MOV_AVG_PERIOD)

    private val smoothedStrokeRate: Double
        get() = recentStrokeRates.average()

    // store last acceleration reading for ramping
    private val lastAccelReading = DoubleArray(3)

    private lateinit var mLocation: Location

    private var totalDistance: Float = 0f

    private val startTimestamp = System.currentTimeMillis()

    init {
        // calculate the stroke rate in coroutine scope to not block UI
        strokeRateCalculator.start()
    }

    fun addAccelerometerReading(readings: FloatArray) {
        // ramp-speed filtering https://stackoverflow.com/a/1736623
        val filtered =
            DoubleArray(3) { readings[it] * FILTERING_FACTOR + lastAccelReading[it] * CONJUGATE_FILTERING_FACTOR }

        // calculate magnitude of the acceleration
        val magnitude = magnitude(filtered)

        // add the acceleration reading to the end of the buffer; displace an old value
        accelReadings.addLast(magnitude)

        // store the corresponding timestamp as well
        timestamps.addLast(((System.currentTimeMillis() - startTimestamp) / 1000L).toDouble())

        // save current readings into memory for when next readings come in
        System.arraycopy(filtered, 0, lastAccelReading, 0, 3)
    }

    /**
     * Called when a new GPS measurement comes in.
     * Informs any UI listener of this new measurement
     * and stores current location in memory
     */
    fun addGpsReading(location: Location) {
        // sum total distance travelled
        if (this::mLocation.isInitialized && mTracking) totalDistance += location.distanceTo(
            mLocation)

        // inform our listener of a new GPS location
        listener?.onLocationUpdate(location, totalDistance)

        // save this for calculating the distance travelled when next GPS measurement comes in
        mLocation = location
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
    private suspend fun getCurrentStrokeRate(): Double {
        // rudimentary but efficient stillness detection
        if (accelReadings.average() < 0.1 && magnitude(lastAccelReading) < 0.1) {
            recentStrokeRates.addLast(0.0)
            return 0.0
        }

        val frequencyScores = Autocorrelator.getFrequencyScores(accelReadings)

        val currentStrokeRate = samplesCountToFrequency(
            Autocorrelator.getBestFrequency(
                frequencyScores,
                accelerometerSamplingRate.toInt() // no more than 60 spm
            )
        )

        recentStrokeRates.addLast(currentStrokeRate)

        // save into the database
        if (mTracking) track.insert(
            TrackPoint(
                currentSessionId,
                System.currentTimeMillis(),
                smoothedStrokeRate,
                mLocation.latitude,
                mLocation.longitude,
                mLocation.speed
            )
        )

        return currentStrokeRate
    }

    private var mTracking = false

    /**
     * Whether the [DataProcessor] is persisting values to the database
     */
    val isRecording: Boolean
        get() = mTracking

    /**
     * Start recording a new rowing session. Once this method is called,
     * processed values such as stroke rate and GPS location are saved.
     *
     * @return whether recording was successfully started
     */
    fun startRecording(): Boolean {
        // if already recording, impossible
        if (mTracking) return false

        currentSessionId = runBlocking { getNewSessionId() }
        totalDistance = 0f
        mTracking = true
        return true
    }

    /**
     * Stop recording the current rowing session.
     *
     * @return whether recording was successfully stopped
     */
    fun stopRecording(): Boolean {
        // cannot stop if not yet started!
        if (!mTracking) return false

        mTracking = false
        return true
    }

    /**
     * Export the current rowing session into a FIT activity file
     */
    fun exportSession() {
        scope.launch {
            val file = FitFileExporter(applicationContext).exportTrackPoints(
                track.loadSession(currentSessionId)
            )
            listener?.onTrackExported(file)
        }
    }
}