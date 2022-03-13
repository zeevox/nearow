package net.zeevox.nearow.db

import androidx.room.Database
import androidx.room.RoomDatabase
import net.zeevox.nearow.db.model.TrackDao
import net.zeevox.nearow.db.model.TrackPoint

/**
 * [TrackDatabase] defines the database configuration and serves as the app's main access point to the persisted data.
 */
@Database(entities = [TrackPoint::class], version = 1)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}