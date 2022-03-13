package net.zeevox.nearow.db.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * [TrackDao] provides the methods that the rest of the app uses to interact with data in the `tracks` table.
 */
@Dao
interface TrackDao {
    @Query("SELECT DISTINCT trackId FROM records")
    suspend fun getSessions(): List<Int>

    @Query("SELECT MAX(trackId) FROM RECORDS")
    suspend fun getLastSessionId(): Int?

    @Query("SELECT * FROM records WHERE trackId == :sessionId ORDER BY pointId ASC")
    suspend fun loadSession(sessionId: Int): List<TrackPoint>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(vararg records: TrackPoint)
}