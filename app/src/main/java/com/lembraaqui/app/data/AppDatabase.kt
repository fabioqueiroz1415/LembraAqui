package com.lembraaqui.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaceEntity::class, ReminderEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun reminderDao(): ReminderDao
    abstract fun historyDao(): HistoryDao
}
