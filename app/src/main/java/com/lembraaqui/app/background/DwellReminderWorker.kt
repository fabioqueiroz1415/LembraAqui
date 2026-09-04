package com.lembraaqui.app.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lembraaqui.app.LembraAquiApplication
import com.lembraaqui.app.domain.ReminderPolicy
import com.lembraaqui.app.domain.ReminderType
import com.lembraaqui.app.domain.RepeatMode
import java.time.ZonedDateTime

class DwellReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val placeId = inputData.getString(KEY_PLACE_ID) ?: return Result.failure()
        val reminderId = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val expectedCycle = inputData.getLong(KEY_CYCLE_ID, -1)
        return PlaceEventLocks.withLock(placeId) {
            processLocked(placeId, reminderId, expectedCycle)
        }
    }

    private suspend fun processLocked(placeId: String, reminderId: String, expectedCycle: Long): Result {
        val container = (applicationContext as LembraAquiApplication).container
        val repository = container.repository
        val place = repository.getPlace(placeId) ?: return Result.success()
        val reminder = repository.getReminder(reminderId) ?: return Result.success()
        if (!place.active || !place.inside || place.cycleId != expectedCycle) return Result.success()
        if (!reminder.active || reminder.type != ReminderType.DWELL.name) return Result.success()

        val now = ZonedDateTime.now()
        val allowed = ReminderPolicy.isAllowed(repository.run { reminder.policy() }, now, place.cycleId)
        if (!allowed) return Result.success()

        val shown = container.notificationHelper.show(
            place.id,
            place.name,
            ReminderType.DWELL,
            reminder.message,
            reminder.dwellMinutes
        )
        if (shown) {
            repository.markTriggered(
                reminder,
                System.currentTimeMillis(),
                ReminderPolicy.dayKey(now.toLocalDate()),
                place.cycleId
            )
            repository.addHistory(
                place.id,
                place.name,
                "REMINDER_DWELL",
                "Lembrete disparado: ${reminder.message}"
            )
        } else {
            repository.addHistory(
                place.id,
                place.name,
                "NOTIFICATION_BLOCKED",
                "Lembrete não exibido porque as notificações estão desativadas: ${reminder.message}"
            )
        }
        return Result.success()
    }

    companion object {
        const val KEY_PLACE_ID = "place_id"
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_CYCLE_ID = "cycle_id"
    }
}
