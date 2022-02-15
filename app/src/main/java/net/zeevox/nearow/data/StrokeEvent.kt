package net.zeevox.nearow.data

class StrokeEvent {
    var timestampStart: Long = System.currentTimeMillis()
    var timestampEnd: Long = System.currentTimeMillis()
    var strokeRate: Double = 0.0

    fun endStroke() {
        timestampEnd = System.currentTimeMillis()
        strokeRate = 60 * 1000 / (timestampEnd - timestampStart).toDouble()
    }

    fun toEntity(): StrokeEventEntity = StrokeEventEntity(strokeRate)
}