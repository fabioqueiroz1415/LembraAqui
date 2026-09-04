package com.lembraaqui.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val active: Boolean,
    val createdAt: Long,
    val inside: Boolean = false,
    val cycleId: Long = 0,
    val lastTransitionAt: Long? = null,
    val enteredAt: Long? = null
)

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = PlaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["placeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("placeId")]
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    val type: String,
    val message: String,
    val dwellMinutes: Int? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val daysMask: Int,
    val repeatMode: String,
    val active: Boolean,
    val createdAt: Long,
    val oneShotCompleted: Boolean = false,
    val lastTriggeredAt: Long? = null,
    val lastTriggeredDayKey: String? = null,
    val lastTriggeredCycleId: Long? = null
)

@Entity(tableName = "history", indices = [Index("createdAt"), Index("placeId")])
data class HistoryEntity(
    @PrimaryKey val id: String,
    val placeId: String?,
    val placeName: String,
    val kind: String,
    val message: String,
    val createdAt: Long
)

data class PlaceSummaryRow(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val active: Boolean,
    val createdAt: Long,
    val inside: Boolean,
    val cycleId: Long,
    val lastTransitionAt: Long?,
    val enteredAt: Long?,
    val arrivingCount: Int,
    val dwellCount: Int,
    val leavingCount: Int
)
