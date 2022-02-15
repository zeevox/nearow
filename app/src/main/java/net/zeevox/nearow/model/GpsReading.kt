package net.zeevox.nearow.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
class GpsReading(
    // UTC time of this location fix, in milliseconds since epoch (January 1, 1970).
    var timestampMillis: Long,

    // latitude, in degrees.
    var latitude: Double,

    // longitude, in degrees.
    var longitude: Double,

    // estimated horizontal accuracy of this location, radial, in meters.
    var horizontalAccuracy: Float,

    // estimated vertical accuracy of this location, in meters.
    var verticalAccuracy: Float,

    // altitude if available, in meters above the WGS 84 reference ellipsoid.
    var altitude: Double,

    // bearing, in degrees
    var bearing: Float,

    // estimated bearing accuracy of this location, in degrees.
    var bearingAccuracy: Float,

    // speed if it is available, in meters/second over ground.
    var speed: Float,

    // estimated speed accuracy of this location, in meters per second.
    var speedAccuracy: Float,

    @Id
    // set the ID to zero to signal to ObjectBox that we are creating a new reading
    // it will handle incrementing the ID automatically
    var readingId: Long = 0L,
)