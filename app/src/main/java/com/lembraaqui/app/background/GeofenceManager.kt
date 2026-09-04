package com.lembraaqui.app.background

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.lembraaqui.app.data.AppRepository
import com.lembraaqui.app.data.PlaceEntity
import com.lembraaqui.app.data.ReminderEntity
import com.lembraaqui.app.domain.ReminderType
import kotlinx.coroutines.tasks.await

class GeofenceManager(
    private val context: Context,
    private val repository: AppRepository
) {
    private val client = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(context, 7341, intent, flags)
    }

    fun hasRequiredPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    suspend fun syncAll(): Result<Unit> = runCatching {
        if (!hasRequiredPermission()) return@runCatching
        client.removeGeofences(pendingIntent).await()
        val places = repository.getActivePlaces().take(100)
        if (places.isEmpty()) return@runCatching
        val geofences = places.map { place -> build(place, repository.getReminders(place.id)) }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        client.addGeofences(request, pendingIntent).await()
        if (repository.getActivePlaces().size > 100) {
            repository.addHistory(null, "Sistema", "GEOFENCE_LIMIT", "Há mais de 100 lugares ativos; o Android permite monitorar até 100 áreas por aplicativo.")
        }
    }

    suspend fun syncPlace(placeId: String): Result<Unit> = runCatching {
        client.removeGeofences(listOf(requestId(placeId))).await()
        val place = repository.getPlace(placeId) ?: return@runCatching
        if (!place.active || !hasRequiredPermission()) return@runCatching
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(build(place, repository.getReminders(place.id)))
            .build()
        client.addGeofences(request, pendingIntent).await()
    }

    suspend fun removePlace(placeId: String) {
        runCatching { client.removeGeofences(listOf(requestId(placeId))).await() }
    }

    private fun build(place: PlaceEntity, reminders: List<ReminderEntity>): Geofence {
        val dwell = reminders
            .filter { it.active && it.type == ReminderType.DWELL.name && it.dwellMinutes != null }
            .minOfOrNull { it.dwellMinutes!! }
        var transitions = Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
        val builder = Geofence.Builder()
            .setRequestId(requestId(place.id))
            .setCircularRegion(place.latitude, place.longitude, place.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
        if (dwell != null) {
            transitions = transitions or Geofence.GEOFENCE_TRANSITION_DWELL
            builder.setLoiteringDelay((dwell * 60_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        return builder.setTransitionTypes(transitions).build()
    }

    companion object {
        const val REQUEST_PREFIX = "place:"
        fun requestId(placeId: String) = "$REQUEST_PREFIX$placeId"
        fun placeIdFromRequestId(requestId: String) = requestId.removePrefix(REQUEST_PREFIX)
    }
}
