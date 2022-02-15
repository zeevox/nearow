package net.zeevox.nearow.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
class DataRecord(
    // unit: ms
    var timestampMillis: Long,

    // altitude in meters ASL
    var altitude: Float?,

    // fractional cadence in cycles per minute
    var cadence: Float?,

    // heart rate in beats per minute
    var heartRate: Short?,

    // distance in meters
    var distance: Float?,

    // speed in meters per second
    var speed: Float?,

    // power in watts
    var power: Int?,

    // radial GPS accuracy in meters
    var gpsAccuracy: Short?,

    // enhanced speed in meters per second,
    var enhancedSpeed: Float?,

    @Id
    // set the ID to zero to signal to ObjectBox that we are creating a new reading
    // it will handle incrementing the ID automatically
    var id: Long = 0L,


    )