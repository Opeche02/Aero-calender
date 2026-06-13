package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val startTime: Long, // timestamp ms
    val endTime: Long,   // timestamp ms
    val isAllDay: Boolean = false,
    val categoryName: String = "Local",
    val location: String = "",
    val syncSource: String = "Local", // "Local", "Google", "Samsung", "iCloud"
    val lastModified: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val externalId: String = "" // For syncing/tracking external account matches
)

@Entity(tableName = "categories")
data class CalendarCategory(
    @PrimaryKey val name: String,
    val colorHex: String,
    val isEnabled: Boolean = true,
    val source: String = "Local" // "Local", "Google Calendar", "iCloud", "Exchange"
)

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val syncTime: Long = System.currentTimeMillis(),
    val source: String, // "Google", "iCloud", "Samsung"
    val status: String, // "SUCCESS", "CONFLICTS_RESOLVED", "ERROR"
    val details: String,
    val itemsSynced: Int = 0
)
