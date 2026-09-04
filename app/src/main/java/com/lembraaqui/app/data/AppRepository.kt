package com.lembraaqui.app.data

import com.lembraaqui.app.domain.ReminderPolicyInput
import com.lembraaqui.app.domain.ReminderType
import com.lembraaqui.app.domain.RepeatMode
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AppRepository(private val db: AppDatabase) {
    val places = db.placeDao().observeSummaries()
    val history = db.historyDao().observeRecent()

    fun place(id: String): Flow<PlaceEntity?> = db.placeDao().observeById(id)
    fun reminders(placeId: String): Flow<List<ReminderEntity>> = db.reminderDao().observeForPlace(placeId)
    fun reminder(id: String): Flow<ReminderEntity?> = db.reminderDao().observeById(id)

    suspend fun getPlace(id: String) = db.placeDao().getById(id)
    suspend fun getActivePlaces() = db.placeDao().getActive()
    suspend fun getReminders(placeId: String) = db.reminderDao().getForPlace(placeId)
    suspend fun getReminder(id: String) = db.reminderDao().getById(id)

    suspend fun savePlace(place: PlaceEntity) = db.placeDao().upsert(place)
    suspend fun deletePlace(place: PlaceEntity) = db.placeDao().delete(place)
    suspend fun setPlaceActive(id: String, active: Boolean) = db.placeDao().setActive(id, active)
    suspend fun updatePlaceState(id: String, inside: Boolean, cycleId: Long, lastTransitionAt: Long?, enteredAt: Long?) =
        db.placeDao().updateState(id, inside, cycleId, lastTransitionAt, enteredAt)
    suspend fun resetAllLocationStates() = db.placeDao().resetAllLocationStates()

    suspend fun saveReminder(reminder: ReminderEntity) = db.reminderDao().upsert(reminder)
    suspend fun deleteReminder(reminder: ReminderEntity) = db.reminderDao().delete(reminder)
    suspend fun setReminderActive(id: String, active: Boolean) = db.reminderDao().setActive(id, active)

    suspend fun markTriggered(reminder: ReminderEntity, nowMs: Long, dayKey: String, cycleId: Long) {
        db.reminderDao().markTriggered(
            reminder.id,
            nowMs,
            dayKey,
            cycleId,
            reminder.repeatMode == RepeatMode.ONCE.name
        )
    }

    suspend fun addHistory(placeId: String?, placeName: String, kind: String, message: String, nowMs: Long = System.currentTimeMillis()) {
        db.historyDao().insert(
            HistoryEntity(UUID.randomUUID().toString(), placeId, placeName, kind, message, nowMs)
        )
    }

    suspend fun clearHistory() = db.historyDao().clear()

    fun ReminderEntity.policy(): ReminderPolicyInput = ReminderPolicyInput(
        active = active,
        oneShotCompleted = oneShotCompleted,
        daysMask = daysMask,
        startMinute = startMinute,
        endMinute = endMinute,
        repeatMode = RepeatMode.valueOf(repeatMode),
        lastTriggeredDayKey = lastTriggeredDayKey,
        lastTriggeredCycleId = lastTriggeredCycleId
    )

    fun ReminderEntity.reminderType(): ReminderType = ReminderType.valueOf(type)
}
