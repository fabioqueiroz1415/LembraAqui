package com.lembraaqui.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.google.android.gms.tasks.CancellationTokenSource
import com.lembraaqui.app.concurrency.suspendResult

class LocationProvider(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    suspend fun currentCoordinates(): Result<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        suspendResult {
            check(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                "Permita o acesso à localização primeiro."
            }
            val token = CancellationTokenSource()
            try {
                withTimeoutOrNull(20_000L) {
                    val location = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
                        ?: client.lastLocation.await()
                        ?: error("Não foi possível obter a localização atual. Ative a localização do aparelho e tente novamente.")
                    location.latitude to location.longitude
                } ?: error("A localização demorou demais. Tente novamente ou informe as coordenadas.")
            } finally {
                token.cancel()
            }
        }
    }
}
