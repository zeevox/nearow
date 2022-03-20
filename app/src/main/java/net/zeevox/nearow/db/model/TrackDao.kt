package net.zeevox.nearow.db.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * [TrackDao] provides the methods that the rest of the app uses to interact with data in the
 * `tracks` table.
 */
@Dao
interface TrackDao {
    @Query(
        "SELECT trackId, MIN(timestamp) AS timestamp FROM records GROUP BY trackId ORDER BY timestamp DESC")
    suspend fun getSessions(): List<Session>

    @Query("SELECT MAX(trackId) FROM RECORDS") suspend fun getLastSessionId(): Int?

    @Query("SELECT * FROM records WHERE trackId == :sessionId ORDER BY pointId ASC")
    suspend fun loadSession(sessionId: Int): List<TrackPoint>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(vararg records: TrackPoint)
}
