package net.zeevox.nearow.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
class AccelerationReading(
    var x: Float,
    var y: Float,
    var z: Float,
    var timestampMillis: Long = System.currentTimeMillis(),

    @Id
    // set the ID to zero to signal to ObjectBox that we are creating a new reading
    // it will handle incrementing the ID automatically
    var id: Long = 0L

)