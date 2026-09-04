package com.lembraaqui.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("""
        SELECT p.*,
          COALESCE(SUM(CASE WHEN r.active = 1 AND r.type = 'ARRIVING' THEN 1 ELSE 0 END), 0) AS arrivingCount,
          COALESCE(SUM(CASE WHEN r.active = 1 AND r.type = 'DWELL' THEN 1 ELSE 0 END), 0) AS dwellCount,
          COALESCE(SUM(CASE WHEN r.active = 1 AND r.type = 'LEAVING' THEN 1 ELSE 0 END), 0) AS leavingCount
        FROM places p
        LEFT JOIN reminders r ON r.placeId = p.id
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE
    """)
    fun observeSummaries(): Flow<List<PlaceSummaryRow>>

    @Query("SELECT * FROM places WHERE id = :id")
    fun observeById(id: String): Flow<PlaceEntity?>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: String): PlaceEntity?

    @Query("SELECT * FROM places WHERE active = 1 ORDER BY createdAt")
    suspend fun getActive(): List<PlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: PlaceEntity)

    @Delete
    suspend fun delete(place: PlaceEntity)

    @Query("UPDATE places SET active = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("""
        UPDATE places SET inside = :inside, cycleId = :cycleId,
        lastTransitionAt = :lastTransitionAt, enteredAt = :enteredAt
        WHERE id = :id
    """)
    suspend fun updateState(id: String, inside: Boolean, cycleId: Long, lastTransitionAt: Long?, enteredAt: Long?)

    @Query("UPDATE places SET inside = 0, lastTransitionAt = NULL, enteredAt = NULL")
    suspend fun resetAllLocationStates()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE placeId = :placeId ORDER BY createdAt")
    fun observeForPlace(placeId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE placeId = :placeId ORDER BY createdAt")
    suspend fun getForPlace(placeId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observeById(id: String): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("UPDATE reminders SET active = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("""
        UPDATE reminders SET
          lastTriggeredAt = :triggeredAt,
          lastTriggeredDayKey = :dayKey,
          lastTriggeredCycleId = :cycleId,
          oneShotCompleted = CASE WHEN :completeOneShot THEN 1 ELSE oneShotCompleted END,
          active = CASE WHEN :completeOneShot THEN 0 ELSE active END
        WHERE id = :id
    """)
    suspend fun markTriggered(
        id: String,
        triggeredAt: Long,
        dayKey: String,
        cycleId: Long,
        completeOneShot: Boolean
    )
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT 500")
    fun observeRecent(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(event: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()
}
