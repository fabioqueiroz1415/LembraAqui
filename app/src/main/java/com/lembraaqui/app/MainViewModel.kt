package com.lembraaqui.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lembraaqui.app.data.PlaceEntity
import com.lembraaqui.app.data.ReminderEntity
import com.lembraaqui.app.domain.LocationTransition
import com.lembraaqui.app.domain.ReminderType
import com.lembraaqui.app.domain.RepeatMode
import com.lembraaqui.app.domain.WeekdayMask
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LembraAquiApplication).container
    private val repository = container.repository

    val places = repository.places.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun place(id: String) = repository.place(id)
    fun reminders(placeId: String) = repository.reminders(placeId)
    fun reminder(id: String) = repository.reminder(id)

    fun savePlace(
        existing: PlaceEntity?,
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        active: Boolean,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                require(name.isNotBlank()) { "Informe um nome para o lugar." }
                require(latitude in -90.0..90.0) { "Latitude inválida." }
                require(longitude in -180.0..180.0) { "Longitude inválida." }
                require(radiusMeters in 25f..5000f) { "Use um raio entre 25 m e 5000 m." }
                val id = existing?.id ?: UUID.randomUUID().toString()
                val monitoringAreaChanged = existing != null && (
                    existing.latitude != latitude ||
                        existing.longitude != longitude ||
                        existing.radiusMeters != radiusMeters ||
                        existing.active != active
                    )
                val preservePresence = existing != null && !monitoringAreaChanged && active
                val place = PlaceEntity(
                    id = id,
                    name = name.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters,
                    active = active,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    inside = if (preservePresence) existing.inside else false,
                    cycleId = existing?.cycleId ?: 0,
                    lastTransitionAt = if (preservePresence) existing.lastTransitionAt else null,
                    enteredAt = if (preservePresence) existing.enteredAt else null
                )
                repository.savePlace(place)
                container.geofenceManager.syncPlace(id)
                if (place.inside) container.dwellScheduler.scheduleForPlace(id)
                id
            }.onSuccess(onDone).onFailure { onError(it.message ?: "Não foi possível salvar o lugar.") }
        }
    }

    fun deletePlace(place: PlaceEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            container.dwellScheduler.cancelForPlace(place.id)
            container.geofenceManager.removePlace(place.id)
            repository.deletePlace(place)
            onDone()
        }
    }

    fun setPlaceActive(id: String, active: Boolean) {
        viewModelScope.launch {
            val place = repository.getPlace(id) ?: return@launch
            repository.setPlaceActive(id, active)
            if (active) {
                // Reativar sempre começa sem um estado de presença herdado.
                repository.updatePlaceState(id, false, place.cycleId, null, null)
                container.geofenceManager.syncPlace(id)
            } else {
                container.geofenceManager.removePlace(id)
                container.dwellScheduler.cancelForPlace(id)
                repository.updatePlaceState(id, false, place.cycleId, null, null)
            }
        }
    }

    fun saveReminder(
        existing: ReminderEntity?,
        placeId: String,
        type: ReminderType,
        message: String,
        dwellMinutes: Int?,
        startMinute: Int?,
        endMinute: Int?,
        daysMask: Int,
        repeatMode: RepeatMode,
        active: Boolean,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                require(message.isNotBlank()) { "Escreva a mensagem do lembrete." }
                if (type == ReminderType.DWELL) require((dwellMinutes ?: 0) in 1..1440) { "Use um tempo entre 1 minuto e 24 horas." }
                require(daysMask != 0) { "Escolha pelo menos um dia da semana." }
                require((startMinute == null) == (endMinute == null)) { "Informe início e fim do horário." }
                val rearmingCompletedReminder = existing?.oneShotCompleted == true && active
                val reminder = ReminderEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    placeId = placeId,
                    type = type.name,
                    message = message.trim(),
                    dwellMinutes = if (type == ReminderType.DWELL) dwellMinutes else null,
                    startMinute = startMinute,
                    endMinute = endMinute,
                    daysMask = daysMask,
                    repeatMode = repeatMode.name,
                    active = active,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    oneShotCompleted = if (rearmingCompletedReminder) false else existing?.oneShotCompleted ?: false,
                    lastTriggeredAt = if (rearmingCompletedReminder) null else existing?.lastTriggeredAt,
                    lastTriggeredDayKey = if (rearmingCompletedReminder) null else existing?.lastTriggeredDayKey,
                    lastTriggeredCycleId = if (rearmingCompletedReminder) null else existing?.lastTriggeredCycleId
                )
                repository.saveReminder(reminder)
                container.geofenceManager.syncPlace(placeId)
                container.dwellScheduler.scheduleForPlace(placeId)
            }.onSuccess { onDone() }.onFailure { onError(it.message ?: "Não foi possível salvar o lembrete.") }
        }
    }

    fun deleteReminder(reminder: ReminderEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
            container.geofenceManager.syncPlace(reminder.placeId)
            container.dwellScheduler.scheduleForPlace(reminder.placeId)
            onDone()
        }
    }

    fun setReminderActive(reminder: ReminderEntity, active: Boolean) {
        viewModelScope.launch {
            if (active && reminder.oneShotCompleted) {
                repository.saveReminder(
                    reminder.copy(
                        active = true,
                        oneShotCompleted = false,
                        lastTriggeredAt = null,
                        lastTriggeredDayKey = null,
                        lastTriggeredCycleId = null
                    )
                )
            } else {
                repository.setReminderActive(reminder.id, active)
            }
            container.geofenceManager.syncPlace(reminder.placeId)
            container.dwellScheduler.scheduleForPlace(reminder.placeId)
        }
    }

    fun clearHistory() = viewModelScope.launch { repository.clearHistory() }

    fun syncMonitoring(onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = container.geofenceManager.syncAll()
            repository.getActivePlaces().filter { it.inside }.forEach { container.dwellScheduler.scheduleForPlace(it.id) }
            onResult(if (result.isSuccess) "Monitoramento atualizado." else "Não foi possível atualizar o monitoramento: ${result.exceptionOrNull()?.message.orEmpty()}")
        }
    }

    fun currentLocation(onResult: (Result<Pair<Double, Double>>) -> Unit) {
        viewModelScope.launch { onResult(container.locationProvider.currentCoordinates()) }
    }

    fun simulate(placeId: String, transition: LocationTransition, onDone: () -> Unit = {}) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            container.eventProcessor.handle(placeId, transition, "teste manual", bypassDebounce = true, debugForceDwell = transition == LocationTransition.DWELL)
            onDone()
        }
    }
}
