package com.lembraaqui.app.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lembraaqui.app.LembraAquiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val container = (context.applicationContext as LembraAquiApplication).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    // O estado "dentro" persistido pode ter ficado inválido enquanto o aparelho estava desligado.
                    container.repository.getActivePlaces().forEach { container.dwellScheduler.cancelForPlace(it.id) }
                    container.repository.resetAllLocationStates()
                }
                container.geofenceManager.syncAll()
                container.repository.getActivePlaces().filter { it.inside }.forEach {
                    container.dwellScheduler.scheduleForPlace(it.id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
