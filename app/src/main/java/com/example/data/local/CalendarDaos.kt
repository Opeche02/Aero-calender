package com.example.data.local

import androidx.room.*
import com.example.data.model.CalendarCategory
import com.example.data.model.Event
import com.example.data.model.SyncLog
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE isDeleted = 0 ORDER BY startTime ASC")
    fun getActiveEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    fun getEventById(id: Long): Flow<Event?>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getEventByIdSync(id: Long): Event?

    @Query("SELECT * FROM events WHERE externalId = :externalId LIMIT 1")
    suspend fun getEventByExternalIdSync(externalId: String): Event?

    @Query("SELECT * FROM events WHERE isDeleted = 0 AND startTime >= :now ORDER BY startTime ASC LIMIT :limit")
    suspend fun getUpcomingEventsSync(now: Long, limit: Int): List<Event>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<Event>)

    @Update
    suspend fun updateEvent(event: Event)

    @Delete
    suspend fun deleteEventDirectly(event: Event)

    @Query("UPDATE events SET isDeleted = 1, lastModified = :timestamp WHERE id = :id")
    suspend fun softDeleteEvent(id: Long, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<CalendarCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CalendarCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CalendarCategory>)
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY syncTime DESC LIMIT 30")
    fun getRecentSyncLogs(): Flow<List<SyncLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLog)
}
