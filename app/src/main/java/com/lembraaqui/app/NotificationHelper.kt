package com.lembraaqui.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import com.lembraaqui.app.domain.ReminderType

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "place_reminders"
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lembretes por localização",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos quando você chega, permanece ou sai dos lugares cadastrados."
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun show(placeId: String, placeName: String, type: ReminderType, message: String, dwellMinutes: Int? = null): Boolean {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_place_id", placeId)
        }
        val pending = PendingIntent.getActivity(
            context,
            placeId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationTitle = when (type) {
            ReminderType.ARRIVING -> "chegando em $placeName"
            ReminderType.DWELL -> "em $placeName"
            ReminderType.LEAVING -> "saindo de $placeName"
        }
        val notificationText = "lembrete: $message"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = NotificationManagerCompat.from(context)
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!runtimePermissionGranted || !manager.areNotificationsEnabled()) return false
        return runCatching {
            manager.notify((placeId + message + System.nanoTime()).hashCode(), notification)
            true
        }.getOrDefault(false)
    }
}
