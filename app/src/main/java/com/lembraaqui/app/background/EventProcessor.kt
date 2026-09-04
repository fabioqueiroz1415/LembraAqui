package com.lembraaqui.app.background

import com.lembraaqui.app.NotificationHelper
import com.lembraaqui.app.data.AppRepository
import com.lembraaqui.app.data.ReminderEntity
import com.lembraaqui.app.domain.LocationState
import com.lembraaqui.app.domain.LocationTransition
import com.lembraaqui.app.domain.ReminderPolicy
import com.lembraaqui.app.domain.ReminderType
import com.lembraaqui.app.domain.TransitionReducer
import java.time.ZonedDateTime

class EventProcessor(
    private val repository: AppRepository,
    private val notificationHelper: NotificationHelper,
    private val dwellScheduler: DwellScheduler
) {
    suspend fun handle(placeId: String, transition: LocationTransition, source: String = "detecção automática", bypassDebounce: Boolean = false, debugForceDwell: Boolean = false) {
        PlaceEventLocks.withLock(placeId) {
            handleLocked(placeId, transition, source, bypassDebounce, debugForceDwell)
        }
    }

    private suspend fun handleLocked(placeId: String, transition: LocationTransition, source: String, bypassDebounce: Boolean, debugForceDwell: Boolean) {
        val place = repository.getPlace(placeId) ?: return
        if (!place.active) return
        val nowMs = System.currentTimeMillis()
        val current = LocationState(place.inside, place.cycleId, if (bypassDebounce) null else place.lastTransitionAt, place.enteredAt)
        val decision = TransitionReducer.reduce(current, transition, nowMs)

        if (transition == LocationTransition.DWELL) {
            if (place.inside) {
                if (debugForceDwell) triggerForType(place.id, ReminderType.DWELL, place.cycleId)
                else dwellScheduler.scheduleForPlace(place.id)
            }
            return
        }
        if (!decision.accepted) return

        val next = decision.state
        repository.updatePlaceState(place.id, next.inside, next.cycleId, next.lastTransitionAt, next.enteredAt)
        when (transition) {
            LocationTransition.ENTER -> {
                repository.addHistory(place.id, place.name, "ENTER", "Entrou em ${place.name} ($source)", nowMs)
                triggerForType(place.id, ReminderType.ARRIVING, next.cycleId)
                dwellScheduler.scheduleForPlace(place.id)
            }
            LocationTransition.EXIT -> {
                dwellScheduler.cancelForPlace(place.id)
                repository.addHistory(place.id, place.name, "EXIT", "Saiu de ${place.name} ($source)", nowMs)
                triggerForType(place.id, ReminderType.LEAVING, next.cycleId)
            }
            LocationTransition.DWELL -> Unit
        }
    }

    private suspend fun triggerForType(placeId: String, type: ReminderType, cycleId: Long) {
        val place = repository.getPlace(placeId) ?: return
        val now = ZonedDateTime.now()
        repository.getReminders(placeId)
            .filter { it.type == type.name }
            .forEach { reminder ->
                if (ReminderPolicy.isAllowed(repository.run { reminder.policy() }, now, cycleId)) {
                    fire(place.id, place.name, type, reminder, cycleId, now)
                }
            }
    }

    private suspend fun fire(
        placeId: String,
        placeName: String,
        type: ReminderType,
        reminder: ReminderEntity,
        cycleId: Long,
        now: ZonedDateTime
    ) {
        val shown = notificationHelper.show(placeId, placeName, type, reminder.message, reminder.dwellMinutes)
        if (shown) {
            repository.markTriggered(reminder, System.currentTimeMillis(), ReminderPolicy.dayKey(now.toLocalDate()), cycleId)
            repository.addHistory(placeId, placeName, "REMINDER_${type.name}", "Lembrete disparado: ${reminder.message}")
        } else {
            repository.addHistory(placeId, placeName, "NOTIFICATION_BLOCKED", "Lembrete não exibido porque as notificações estão desativadas: ${reminder.message}")
        }
    }
}
