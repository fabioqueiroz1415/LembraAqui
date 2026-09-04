package com.lembraaqui.app.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.lembraaqui.app.LembraAquiApplication
import com.lembraaqui.app.domain.LocationTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> LocationTransition.ENTER
            Geofence.GEOFENCE_TRANSITION_DWELL -> LocationTransition.DWELL
            Geofence.GEOFENCE_TRANSITION_EXIT -> LocationTransition.EXIT
            else -> return
        }
        val ids = event.triggeringGeofences.orEmpty().map { GeofenceManager.placeIdFromRequestId(it.requestId) }
        val pending = goAsync()
        val container = (context.applicationContext as LembraAquiApplication).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ids.forEach { container.eventProcessor.handle(it, transition) }
            } finally {
                pending.finish()
            }
        }
    }
}
