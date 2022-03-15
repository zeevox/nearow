package net.zeevox.nearow.output

////////////////////////////////////////////////////////////////////////////////
// The following FIT Protocol software provided may be used with FIT protocol
// devices only and remains the copyrighted property of Garmin Canada Inc.
// The software is being provided on an "as-is" basis and as an accommodation,
// and therefore all warranties, representations, or guarantees of any kind
// (whether express, implied or statutory) including, without limitation,
// warranties of merchantability, non-infringement, or fitness for a particular
// purpose, are specifically disclaimed.
//
// Copyright 2021 Garmin International, Inc.
////////////////////////////////////////////////////////////////////////////////

import android.content.Context
import android.util.Log
import androidx.annotation.NonNull
import com.garmin.fit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zeevox.nearow.BuildConfig
import net.zeevox.nearow.db.model.TrackPoint
import net.zeevox.nearow.utils.UnitConverter
import java.text.SimpleDateFormat
import java.util.*
import java.util.TimeZone

class FitFileExporter(private val context: Context) {

    suspend fun exportTrackPoints(trackPoints: List<TrackPoint>): java.io.File {
        val activity = createActivityFromTrackPoints(trackPoints)

        // create directory if not exists
        val directory = java.io.File(context.filesDir.path + "/exports")
        if (!directory.exists()) directory.mkdir()

        val file =
            java.io.File(directory, getFilenameForTimestamp(trackPoints.first().timestamp))
        writeMessagesToFile(activity, file)
        return file
    }

    private fun createActivityFromTrackPoints(trackPoints: List<TrackPoint>): List<Mesg> {
        val messages: MutableList<Mesg> = mutableListOf()

        // The starting timestamp for the activity
        val activityStartTime = DateTime(trackPoints.first().timestamp)
        val activityEndTime = DateTime(trackPoints.last().timestamp)

        val elapsedTime = (activityEndTime.timestamp - activityStartTime.timestamp).toFloat()

        // Every FIT file MUST contain a File ID message
        messages.add(getFileMetadata(activityStartTime))

        // A Device Info message is a BEST PRACTICE for FIT ACTIVITY files
        messages.add(getDeviceInfo(activityStartTime))

        // record the developer ID message into the FIT file
        messages.add(getDeveloperMetadata())

        // Timer Events are a BEST PRACTICE for FIT ACTIVITY files
        val eventMesg: EventMesg = EventMesg().apply {
            timestamp = activityStartTime
            event = Event.TIMER
            eventType = EventType.START
        }
        messages.add(eventMesg)

        /** Create a [RecordMesg] for each [TrackPoint] and write to the output stream **/
        for (trackPoint in trackPoints)
            messages.add(RecordMesg().apply {
                timestamp = DateTime(trackPoint.timestamp)
                speed = trackPoint.speed
                power = UnitConverter.speedToWatts(speed).toInt()
                cadence256 = trackPoint.strokeRate.toFloat()
                trackPoint.latitude?.let { positionLat = decimalToGarmin(it) }
                trackPoint.longitude?.let { positionLong = decimalToGarmin(it) }
            })

        // mark the activity as ended
        messages.add(createEndEvent(activityEndTime))

        // Every FIT file MUST contain at least one Lap message
        messages.add(createLap(activityStartTime, elapsedTime))

        // Every FIT file MUST contain at least one Session message
        messages.add(createSession(activityStartTime, elapsedTime))

        // Every FIT ACTIVITY file MUST contain EXACTLY one Activity message
        val timeZone: TimeZone = TimeZone.getTimeZone("America/Denver")
        val timezoneOffset: Long = (timeZone.rawOffset + timeZone.dstSavings) / 1000L
        messages.add(ActivityMesg().apply {
            timestamp = activityStartTime
            numSessions = 1
            localTimestamp = activityStartTime.timestamp + timezoneOffset
            totalTimerTime =
                (activityStartTime.timestamp - activityStartTime.timestamp).toFloat()
        })

        return messages
    }

    private suspend fun writeMessagesToFile(messages: List<Mesg?>, file: java.io.File) {
        // Create the output stream
        val encoder: FileEncoder = try {
            FileEncoder(file, Fit.ProtocolVersion.V2_0)
        } catch (e: FitRuntimeException) {
            Log.e(javaClass.simpleName, "Error opening file ${file.name}")
            e.printStackTrace()
            return
        }

        withContext(Dispatchers.IO) { for (message in messages) encoder.write(message) }

        // Close the output stream
        try {
            encoder.close()
        } catch (e: FitRuntimeException) {
            Log.e(javaClass.simpleName, "Error closing encode.")
            e.printStackTrace()
            return
        }

        Log.d(javaClass.simpleName, "Encoded FIT Activity file ${file.name}")
    }


    companion object {

        // The combination of manufacturer id, product id, and serial number should be unique.
        // When available, a non-random serial number should be used.
        private const val TRACKING_PRODUCT_ID: Int = 1
        private const val TRACKING_PRODUCT_NAME: String = "Nero"
        private const val MANUFACTURER_ID: Int = Manufacturer.DEVELOPMENT
        private const val SOFTWARE_VERSION = BuildConfig.VERSION_CODE
        private val SERIAL_NUMBER: Long = Random().nextLong()

        /**
         * Garmin stores lat/long as integers.
         * Each decimal degree represents 2^32 / 360 = 11930465
         * https://gis.stackexchange.com/a/368905
         */
        fun decimalToGarmin(pos: Double): Int = (pos * 11930465).toInt()

        private fun getDeveloperMetadata(): DeveloperDataIdMesg {
            // Create the Developer Id message for the developer data fields.
            val developerIdMesg = DeveloperDataIdMesg()

            // It is a BEST PRACTICE to reuse the same Guid for all FIT files created by a single application
            // Randomly generated GUID of BADA3A1E-E9E2-44F3-920B-B38C88963ADF in byte array form
            val appId: Array<Int> = arrayOf(
                0xBA,
                0xDA,
                0x3A,
                0x1E,
                0xE9,
                0xE2,
                0x44,
                0xF3,
                0x92,
                0x0B,
                0xB3,
                0x8C,
                0x88,
                0x96,
                0x3A,
                0xDF
            )

            appId.forEachIndexed { index, value ->
                developerIdMesg.setApplicationId(
                    index,
                    value.toByte()
                )
            }

            developerIdMesg.apply {
                developerDataIndex = 0.toShort()
                applicationVersion = SOFTWARE_VERSION.toLong()
            }

            return developerIdMesg
        }

        private fun createEndEvent(end: DateTime): EventMesg =
            EventMesg().apply {
                timestamp = end
                event = Event.TIMER
                eventType = EventType.STOP_ALL
            }

        private fun createLap(lapStartTime: DateTime, @NonNull elapsedTime: Float): LapMesg =
            LapMesg().apply {
                messageIndex = 0
                startTime = lapStartTime
                timestamp = lapStartTime

                totalElapsedTime = elapsedTime
                totalTimerTime = elapsedTime
            }

        private fun getDeviceInfo(mesgTimestamp: DateTime): DeviceInfoMesg =
            DeviceInfoMesg().apply {
                deviceIndex = DeviceIndex.CREATOR
                manufacturer = MANUFACTURER_ID
                product = TRACKING_PRODUCT_ID
                productName = TRACKING_PRODUCT_NAME
                serialNumber = SERIAL_NUMBER
                softwareVersion = SOFTWARE_VERSION.toFloat()
                timestamp = mesgTimestamp
            }

        private fun createSession(
            activityStartTime: DateTime,
            @NonNull elapsedTime: Float,
        ): SessionMesg =
            SessionMesg().apply {
                messageIndex = 0
                firstLapIndex = 0
                numLaps = 0

                startTime = activityStartTime
                timestamp = activityStartTime

                totalElapsedTime = elapsedTime
                totalTimerTime = elapsedTime

                sport = Sport.ROWING
                subSport = SubSport.GENERIC
            }

        private fun getFileMetadata(startTime: DateTime): FileIdMesg =
            FileIdMesg().apply {
                type = File.ACTIVITY
                manufacturer = MANUFACTURER_ID
                product = TRACKING_PRODUCT_ID
                timeCreated = startTime
                serialNumber = SERIAL_NUMBER
            }

        private fun getFilenameForTimestamp(timestamp: Long): String {
            // Create a DateFormatter object for displaying date in specified format.
            val formatter = SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.UK)

            // Create a calendar object that will convert the date and time value in milliseconds to date.
            val calendar = Calendar.getInstance().apply {
                timeInMillis = timestamp
            }

            return "Nero-${formatter.format(calendar.time)}.fit"
        }
    }
}