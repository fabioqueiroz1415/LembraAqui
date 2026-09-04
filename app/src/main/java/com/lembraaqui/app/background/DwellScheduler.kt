package com.lembraaqui.app.background

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lembraaqui.app.data.AppRepository
import com.lembraaqui.app.domain.ReminderType
import java.util.concurrent.TimeUnit
import kotlin.math.max

class DwellScheduler(
    private val context: Context,
    private val repository: AppRepository
) {
    private val workManager get() = WorkManager.getInstance(context)

    suspend fun scheduleForPlace(placeId: String) {
        val place = repository.getPlace(placeId) ?: return
        cancelForPlace(placeId)
        if (!place.active || !place.inside || place.enteredAt == null) return
        val elapsed = System.currentTimeMillis() - place.enteredAt
        repository.getReminders(placeId)
            .filter { it.active && it.type == ReminderType.DWELL.name && !it.oneShotCompleted }
            .forEach { reminder ->
                val dwellMs = (reminder.dwellMinutes ?: return@forEach).toLong() * 60_000L
                val remainingMs = max(0L, dwellMs - elapsed)
                val data = Data.Builder()
                    .putString(DwellReminderWorker.KEY_PLACE_ID, placeId)
                    .putString(DwellReminderWorker.KEY_REMINDER_ID, reminder.id)
                    .putLong(DwellReminderWorker.KEY_CYCLE_ID, place.cycleId)
                    .build()
                val request = OneTimeWorkRequestBuilder<DwellReminderWorker>()
                    .setInitialDelay(remainingMs, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag(placeTag(placeId))
                    .build()
                workManager.enqueueUniqueWork(
                    workName(placeId, reminder.id, place.cycleId),
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
    }

    fun cancelForPlace(placeId: String) {
        workManager.cancelAllWorkByTag(placeTag(placeId))
    }

    private fun placeTag(placeId: String) = "dwell-place-$placeId"
    private fun workName(placeId: String, reminderId: String, cycleId: Long) = "dwell-$placeId-$reminderId-$cycleId"
}
