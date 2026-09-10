package com.lembraaqui.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lembraaqui.app.data.PlaceEntity
import com.lembraaqui.app.data.ReminderEntity
import com.lembraaqui.app.domain.LocationTransition
import com.lembraaqui.app.domain.ReminderType
import com.lembraaqui.app.domain.RepeatMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.lembraaqui.app.concurrency.OperationRunner
import com.lembraaqui.app.background.PlaceEventLocks
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as LembraAquiApplication).container
    private val repository = container.repository

    private val operations = OperationRunner(viewModelScope)
    val busy = operations.busy
    val operationMessage = operations.message
    fun dismissMessage(id: Long) = operations.dismiss(id)

    private fun <T> mutatePlace(
        key: String,
        placeId: String,
        onDone: (T) -> Unit = {},
        onError: (String) -> Unit = {},
        work: suspend () -> T
    ) = operations.launch(key, onDone, onError = { message ->
        operations.notify(message)
        onError(message)
    }) {
        val result = PlaceEventLocks.withLock(placeId, work)
        // Never hold an event lock while waiting for Google Play services:
        // BroadcastReceivers must be able to finish their local work promptly.
        syncPlace(placeId)
        result
    }

    private suspend fun syncPlace(id: String) {
        val result = container.geofenceManager.syncPlace(id)
        if (result.isFailure) withContext(Dispatchers.Main.immediate) {
            operations.notify("Dados salvos, mas o monitoramento não foi atualizado. Tente atualizar nas permissões: ${result.exceptionOrNull()?.message.orEmpty()}")
        }
    }

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
        val id = existing?.id ?: UUID.randomUUID().toString()
        mutatePlace("place:${existing?.id ?: "new"}", id, onDone, onError) {
            val current = existing?.let { repository.getPlace(it.id) ?: error("Este lugar foi excluído.") }
            require(name.isNotBlank()) { "Informe um nome para o lugar." }
            require(latitude in -90.0..90.0) { "Latitude inválida." }
            require(longitude in -180.0..180.0) { "Longitude inválida." }
            require(radiusMeters in 25f..5000f) { "Use um raio entre 25 m e 5000 m." }
            val monitoringAreaChanged = current != null && (
                current.latitude != latitude ||
                    current.longitude != longitude ||
                    current.radiusMeters != radiusMeters ||
                    current.active != active
                )
            val preservePresence = current != null && !monitoringAreaChanged && active
            val place = PlaceEntity(
                id = id,
                name = name.trim(),
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                active = active,
                createdAt = current?.createdAt ?: System.currentTimeMillis(),
                inside = if (preservePresence) current.inside else false,
                cycleId = current?.cycleId ?: 0,
                lastTransitionAt = if (preservePresence) current.lastTransitionAt else null,
                enteredAt = if (preservePresence) current.enteredAt else null
            )
            repository.savePlace(place)
            container.dwellScheduler.scheduleForPlace(id)
            id
        }
    }

    fun deletePlace(place: PlaceEntity, onDone: () -> Unit) {
        mutatePlace("place:${place.id}", place.id, onDone = { onDone() }) {
            container.dwellScheduler.cancelForPlace(place.id)
            repository.deletePlace(place)
        }
    }

    fun setPlaceActive(id: String, active: Boolean) {
        mutatePlace("place:$id", id) {
            val place = repository.getPlace(id) ?: return@mutatePlace
            repository.setPlaceActive(id, active)
            // Reativar ou pausar começa sem presença ou trabalhos herdados.
            container.dwellScheduler.cancelForPlace(id)
            repository.updatePlaceState(id, false, place.cycleId, null, null)
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
        mutatePlace("reminder:${existing?.id ?: "new:$placeId"}", placeId, onDone = { onDone() }, onError = onError) {
            requireNotNull(repository.getPlace(placeId)) { "Este lugar foi excluído." }
            val current = existing?.let { repository.getReminder(it.id) ?: error("Este lembrete foi excluído.") }
            require(message.isNotBlank()) { "Escreva a mensagem do lembrete." }
            if (type == ReminderType.DWELL) require((dwellMinutes ?: 0) in 1..1440) { "Use um tempo entre 1 minuto e 24 horas." }
            require(daysMask != 0) { "Escolha pelo menos um dia da semana." }
            require((startMinute == null) == (endMinute == null)) { "Informe início e fim do horário." }
            val rearmingCompletedReminder = current?.oneShotCompleted == true && active
            val reminder = ReminderEntity(
                id = current?.id ?: UUID.randomUUID().toString(),
                placeId = placeId,
                type = type.name,
                message = message.trim(),
                dwellMinutes = if (type == ReminderType.DWELL) dwellMinutes else null,
                startMinute = startMinute,
                endMinute = endMinute,
                daysMask = daysMask,
                repeatMode = repeatMode.name,
                active = active,
                createdAt = current?.createdAt ?: System.currentTimeMillis(),
                oneShotCompleted = if (rearmingCompletedReminder) false else current?.oneShotCompleted ?: false,
                lastTriggeredAt = if (rearmingCompletedReminder) null else current?.lastTriggeredAt,
                lastTriggeredDayKey = if (rearmingCompletedReminder) null else current?.lastTriggeredDayKey,
                lastTriggeredCycleId = if (rearmingCompletedReminder) null else current?.lastTriggeredCycleId
            )
            repository.saveReminder(reminder)
            container.dwellScheduler.scheduleForPlace(placeId)
        }
    }

    fun deleteReminder(reminder: ReminderEntity, onDone: () -> Unit) {
        mutatePlace("reminder:${reminder.id}", reminder.placeId, onDone = { onDone() }) {
            repository.deleteReminder(reminder)
            container.dwellScheduler.scheduleForPlace(reminder.placeId)
        }
    }

    fun setReminderActive(reminder: ReminderEntity, active: Boolean) {
        mutatePlace("reminder:${reminder.id}", reminder.placeId) {
            val current = repository.getReminder(reminder.id) ?: return@mutatePlace
            if (active && current.oneShotCompleted) {
                repository.saveReminder(
                    current.copy(
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

            container.dwellScheduler.scheduleForPlace(reminder.placeId)
        }
    }

    fun clearHistory() = operations.launch("history") { repository.clearHistory() }

    fun syncMonitoring(onResult: (String) -> Unit = {}) {
        operations.launch("monitoring", onDone = onResult) {
            val result = container.geofenceManager.syncAll()
            repository.getActivePlaces().forEach { place ->
                PlaceEventLocks.withLock(place.id) { container.dwellScheduler.scheduleForPlace(place.id) }
            }
            if (result.isSuccess) "Monitoramento atualizado."
            else throw result.exceptionOrNull() ?: IllegalStateException("Não foi possível atualizar o monitoramento.")
        }
    }

    fun currentLocation(onResult: (Result<Pair<Double, Double>>) -> Unit) {
        operations.launch("location", onDone = onResult,
            onError = { onResult(Result.failure(IllegalStateException(it))) }) {
            container.locationProvider.currentCoordinates()
        }
    }

    fun simulate(placeId: String, transition: LocationTransition, onDone: () -> Unit = {}) {
        if (!BuildConfig.DEBUG) return
        operations.launch("simulate:$placeId", onDone = { onDone() }) {
            container.eventProcessor.handle(placeId, transition, "teste manual", bypassDebounce = true, debugForceDwell = transition == LocationTransition.DWELL)
        }
    }
}
