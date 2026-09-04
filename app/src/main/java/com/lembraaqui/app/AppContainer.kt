package com.lembraaqui.app

import android.content.Context
import androidx.room.Room
import com.lembraaqui.app.background.DwellScheduler
import com.lembraaqui.app.background.EventProcessor
import com.lembraaqui.app.background.GeofenceManager
import com.lembraaqui.app.data.AppDatabase
import com.lembraaqui.app.data.AppRepository

class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "lembraaqui.db"
    ).build()

    val repository = AppRepository(database)
    val notificationHelper = NotificationHelper(context)
    val locationProvider = LocationProvider(context)
    val dwellScheduler = DwellScheduler(context, repository)
    val geofenceManager = GeofenceManager(context, repository)
    val eventProcessor = EventProcessor(repository, notificationHelper, dwellScheduler)
}
