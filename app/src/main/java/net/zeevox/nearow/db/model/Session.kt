package net.zeevox.nearow.db.model

import androidx.room.ColumnInfo
import java.text.SimpleDateFormat
import java.util.*

data class Session(
    val trackId: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
) {
    override fun toString(): String =
        SimpleDateFormat("EEE MMM d HH:mm ''yy", Locale.UK).format(Date(timestamp))
}