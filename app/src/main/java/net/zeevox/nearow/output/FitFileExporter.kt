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

import com.garmin.fit.*
import io.objectbox.Box
import net.zeevox.nearow.BuildConfig
import net.zeevox.nearow.model.AccelerationReading
import java.util.*
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin


class FitFileExporter {

    companion object {

        private fun getDeveloperMetadata(): DeveloperDataIdMesg {
            // Create the Developer Id message for the developer data fields.
            val developerIdMesg = DeveloperDataIdMesg()

            // It is a BEST PRACTICE to reuse the same Guid for all FIT files created by a single application
            // Randomly generated GUID of BADA3A1E-E9E2-44F3-920B-B38C88963ADF in byte array form
            val appId = arrayOf(
                0xBA.toByte(),
                0xDA.toByte(),
                0x3A,
                0x1E,
                0xE9.toByte(),
                0xE2.toByte(),
                0x44,
                0xF3.toByte(),
                0x92.toByte(),
                0x0B,
                0xB3.toByte(),
                0x8C.toByte(),
                0x88.toByte(),
                0x96.toByte(),
                0x3A,
                0xDF.toByte()
            )

            appId.forEachIndexed { index, value -> developerIdMesg.setApplicationId(index, value) }

            developerIdMesg.developerDataIndex = 0.toShort()
            developerIdMesg.applicationVersion = BuildConfig.VERSION_CODE.toLong()

            return developerIdMesg
        }

        fun createTimeBasedActivity(filename: String, accelBox: Box<AccelerationReading>) {
            val twoPI = Math.PI * 2.0
            val semiCirclesPerMeter = 107.173
            val messages: MutableList<Mesg> = ArrayList()

            val firstReading = accelBox[0]

            // The starting timestamp for the activity
            val startTime = DateTime(firstReading.timestampMillis)

            // Timer Events are a BEST PRACTICE for FIT ACTIVITY files
            val eventMesg = EventMesg()
            eventMesg.timestamp = startTime
            eventMesg.event = Event.TIMER
            eventMesg.eventType = EventType.START
            messages.add(eventMesg)

            // the developer metadata does not change but is reused for every measurement
            val developerMetadata = getDeveloperMetadata()

            // record the developer ID message into the FIT file
            messages.add(developerMetadata)

            // Create the Developer Data Field Descriptions
            val doughnutsFieldDescMesg = FieldDescriptionMesg()
            doughnutsFieldDescMesg.developerDataIndex = 0.toShort()
            doughnutsFieldDescMesg.fieldDefinitionNumber = 0.toShort()
            doughnutsFieldDescMesg.fitBaseTypeId = FitBaseType.FLOAT32
            doughnutsFieldDescMesg.setUnits(0, "doughnuts")
            doughnutsFieldDescMesg.nativeMesgNum = MesgNum.SESSION
            messages.add(doughnutsFieldDescMesg)
            val hrFieldDescMesg = FieldDescriptionMesg()
            hrFieldDescMesg.developerDataIndex = 0.toShort()
            hrFieldDescMesg.fieldDefinitionNumber = 1.toShort()
            hrFieldDescMesg.fitBaseTypeId = FitBaseType.UINT8
            hrFieldDescMesg.setFieldName(0, "Heart Rate")
            hrFieldDescMesg.setUnits(0, "bpm")
            hrFieldDescMesg.nativeFieldNum = RecordMesg.HeartRateFieldNum.toShort()
            hrFieldDescMesg.nativeMesgNum = MesgNum.RECORD
            messages.add(hrFieldDescMesg)

            // Every FIT ACTIVITY file MUST contain Record messages
            val timestamp = DateTime(startTime)

            // Create one hour (3600 seconds) of Record data
            for (i in 0..3600) {
                // Create a new Record message and set the timestamp
                val recordMesg = RecordMesg()
                recordMesg.timestamp = timestamp

                // Fake Record Data of Various Signal Patterns
                recordMesg.distance = i.toFloat()
                recordMesg.speed = 1.toFloat()
                recordMesg.heartRate =
                    ((sin(twoPI * (0.01 * i + 10)) + 1.0) * 127.0).toInt().toShort() // Sine
                recordMesg.cadence = (i % 255).toShort() // Sawtooth
                recordMesg.power = if ((i % 255).toShort() < 157) 150 else 250 //Square
                recordMesg.altitude = (abs(i.toDouble() % 255.0) - 127.0).toFloat() // Triangle
                recordMesg.positionLat = 0
                recordMesg.positionLong = (i * semiCirclesPerMeter).roundToInt()

                // Add a Developer Field to the Record Message
                val hrDevField = DeveloperField(hrFieldDescMesg, developerMetadata)
                recordMesg.addDeveloperField(hrDevField)
                hrDevField.value = (sin(twoPI * (.01 * i + 10)) + 1.0).toInt().toShort() * 127.0

                // Write the Record message to the output stream
                messages.add(recordMesg)

                // Increment the timestamp by one second
                timestamp.add(1)
            }

            // Timer Events are a BEST PRACTICE for FIT ACTIVITY files
            val eventMesgStop = EventMesg()
            eventMesgStop.timestamp = timestamp
            eventMesgStop.event = Event.TIMER
            eventMesgStop.eventType = EventType.STOP_ALL
            messages.add(eventMesgStop)

            // Every FIT ACTIVITY file MUST contain at least one Lap message
            val lapMesg = LapMesg()
            lapMesg.messageIndex = 0
            lapMesg.timestamp = timestamp
            lapMesg.startTime = startTime
            lapMesg.totalElapsedTime = (timestamp.timestamp - startTime.timestamp).toFloat()
            lapMesg.totalTimerTime = (timestamp.timestamp - startTime.timestamp).toFloat()
            messages.add(lapMesg)

            // Every FIT ACTIVITY file MUST contain at least one Session message
            val sessionMesg = SessionMesg()
            sessionMesg.messageIndex = 0
            sessionMesg.timestamp = timestamp
            sessionMesg.startTime = startTime
            sessionMesg.totalElapsedTime = (timestamp.timestamp - startTime.timestamp).toFloat()
            sessionMesg.totalTimerTime = (timestamp.timestamp - startTime.timestamp).toFloat()
            sessionMesg.sport = Sport.STAND_UP_PADDLEBOARDING
            sessionMesg.subSport = SubSport.GENERIC
            sessionMesg.firstLapIndex = 0
            sessionMesg.numLaps = 1
            messages.add(sessionMesg)

            // Add a Developer Field to the Session message
            val doughnutsEarnedDevField = DeveloperField(doughnutsFieldDescMesg, developerMetadata)
            doughnutsEarnedDevField.value = sessionMesg.totalElapsedTime / 1200.0f
            sessionMesg.addDeveloperField(doughnutsEarnedDevField)

            // Every FIT ACTIVITY file MUST contain EXACTLY one Activity message
            val activityMesg = ActivityMesg()
            activityMesg.timestamp = timestamp
            activityMesg.numSessions = 1
            val timeZone: TimeZone = TimeZone.getTimeZone("America/Denver")
            val timezoneOffset: Long = (timeZone.rawOffset + timeZone.dstSavings) / 1000L
            activityMesg.localTimestamp = timestamp.timestamp + timezoneOffset
            activityMesg.totalTimerTime = (timestamp.timestamp - startTime.timestamp).toFloat()
            messages.add(activityMesg)
            createActivityFile(messages, filename, startTime)
        }

        private fun createActivityFile(messages: List<Mesg?>, filename: String, startTime: DateTime?) {
            // The combination of file type, manufacturer id, product id, and serial number should be unique.
            // When available, a non-random serial number should be used.
            val fileType = File.ACTIVITY
            val manufacturerId = Manufacturer.DEVELOPMENT.toShort()
            val productId: Short = 0
            val softwareVersion = 1.0f
            val random = Random()
            val serialNumber = random.nextInt()

            // Every FIT file MUST contain a File ID message
            val fileIdMesg = FileIdMesg()
            fileIdMesg.type = fileType
            fileIdMesg.manufacturer = manufacturerId.toInt()
            fileIdMesg.product = productId.toInt()
            fileIdMesg.timeCreated = startTime
            fileIdMesg.serialNumber = serialNumber.toLong()

            // A Device Info message is a BEST PRACTICE for FIT ACTIVITY files
            val deviceInfoMesg = DeviceInfoMesg()
            deviceInfoMesg.deviceIndex = DeviceIndex.CREATOR
            deviceInfoMesg.manufacturer = Manufacturer.DEVELOPMENT
            deviceInfoMesg.product = productId.toInt()
            deviceInfoMesg.productName = "FIT Cookbook" // Max 20 Chars
            deviceInfoMesg.serialNumber = serialNumber.toLong()
            deviceInfoMesg.softwareVersion = softwareVersion
            deviceInfoMesg.timestamp = startTime

            // Create the output stream
            val encode: FileEncoder = try {
                FileEncoder(java.io.File(filename), Fit.ProtocolVersion.V2_0)
            } catch (e: FitRuntimeException) {
                System.err.println("Error opening file $filename")
                e.printStackTrace()
                return
            }
            encode.write(fileIdMesg)
            encode.write(deviceInfoMesg)
            for (message in messages) encode.write(message)

            // Close the output stream
            try {
                encode.close()
            } catch (e: FitRuntimeException) {
                System.err.println("Error closing encode.")
                e.printStackTrace()
                return
            }

            println("Encoded FIT Activity file $filename")
        }
    }
}