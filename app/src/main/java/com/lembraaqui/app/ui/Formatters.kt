package com.lembraaqui.app.ui

import com.lembraaqui.app.domain.ReminderType
import com.lembraaqui.app.domain.RepeatMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

fun parseMinute(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

fun formatHistoryTime(epoch: Long): String = Instant.ofEpochMilli(epoch)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"))

fun ReminderType.description(): String = when (this) {
    ReminderType.ARRIVING -> "Quando eu entrar neste lugar."
    ReminderType.DWELL -> "Depois que eu permanecer aqui por um período."
    ReminderType.LEAVING -> "Quando eu deixar este lugar."
}

fun RepeatMode.description(): String = when (this) {
    RepeatMode.ONCE -> "Depois de disparar, o lembrete é desativado."
    RepeatMode.EVERY_TIME -> "Pode disparar de novo em cada nova ocorrência válida."
    RepeatMode.ONCE_PER_DAY -> "No máximo uma vez por dia."
}
