package net.zeevox.nearow.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
/**
 * Entity class for saving strokes into an ObjectBox database
 * All properties passed as arguments for performance
 */
class StrokeEventEntity(
    var strokeRate: Double,

    @Id
    // setting the ID to zero lets ObjectBox know that we are
    // creating a new object, it will autoincrement the ID
    var strokeEventId: Long = 0L
)