package net.zeevox.nearow.db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Each instance of [TrackPoint] represents a row in a `records` table in the app's database. */
@Entity(tableName = "records", indices = [Index(value = ["trackId"])])
data class TrackPoint(
    val trackId: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "stroke_rate") val strokeRate: Double,
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    /**
     * The speed of the boat at the given timestamp, in metres per second
     *
     * This is a separate field because the speed here may be more accurate than would be obtained
     * simply by calculating distance / time for sequential positions if, for example, the Doppler
     * measurements from GNSS satellites are taken into account.
     */
    @ColumnInfo(name = "speed") val speed: Float? = null,

    /**
     * Automatically incremented point ID. For simple sequential ordering. It is placed last in the
     * constructor so that it is not necessary to use named arguments when creating a new
     * trackpoint.
     */
    @PrimaryKey(autoGenerate = true) val pointId: Int = 0,
)
