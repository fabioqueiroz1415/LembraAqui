package com.lembraaqui.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
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

    fun show(reminderId: String, placeId: String, placeName: String, type: ReminderType, message: String, dwellMinutes: Int? = null): Boolean {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.Builder().scheme("lembraaqui").authority("reminder").appendPath(reminderId).build()
            putExtra("open_reminder_id", reminderId)
            putExtra("open_place_id", placeId)
        }
        val pending = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationTitle = when (type) {
            ReminderType.ARRIVING -> "Você chegou: $placeName"
            ReminderType.DWELL -> if (dwellMinutes != null && dwellMinutes > 0) {
                "Você está há pelo menos $dwellMinutes min em: $placeName"
            } else {
                "Você está no local: $placeName"
            }
            ReminderType.LEAVING -> "Você saiu: $placeName"
        }
        val notificationText = message

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
