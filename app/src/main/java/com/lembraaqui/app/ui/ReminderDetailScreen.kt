@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lembraaqui.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lembraaqui.app.MainViewModel
import com.lembraaqui.app.data.PlaceEntity
import com.lembraaqui.app.data.ReminderEntity
import com.lembraaqui.app.domain.ReminderType
import com.lembraaqui.app.domain.RepeatMode
import com.lembraaqui.app.domain.WeekdayMask
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ReminderDetailScreen(
    vm: MainViewModel,
    reminderId: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    // Distingue carregamento de um lembrete que já foi excluído.
    val reminderFlow = remember(vm, reminderId) { vm.reminder(reminderId).map { true to it } }
    val state by reminderFlow.collectAsStateWithLifecycle(initialValue = false to (null as ReminderEntity?))
    val reminder = state.second
    val placeFlow = remember(vm, reminder?.placeId) {
        reminder?.let { vm.place(it.placeId) } ?: flowOf<PlaceEntity?>(null)
    }
    val place by placeFlow.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Detalhes do lembrete") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") } }
        )
    }) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = maxOf(inner.calculateBottomPadding(), contentPadding.calculateBottomPadding()) + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                !state.first -> item { CircularProgressIndicator() }
                reminder == null -> item {
                    Text("Lembrete indisponível", style = MaterialTheme.typography.titleLarge)
                    Text("Este lembrete ou o lugar associado foi excluído.")
                    Button(onClick = onBack) { Text("Voltar") }
                }
                else -> {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Mensagem", style = MaterialTheme.typography.labelLarge)
                                Text(reminder.message, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                    item { ReminderInfo("Lugar", place?.name ?: "Carregando lugar…") }
                    item {
                        ReminderInfo("Situação", when {
                            reminder.oneShotCompleted -> "Concluído"
                            !reminder.active -> "Pausado"
                            else -> "Ativo"
                        })
                        if (place?.active == false) {
                            Text("O monitoramento deste lugar está pausado.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    item { ReminderInfo("Quando avisar", ReminderType.valueOf(reminder.type).description()) }
                    if (reminder.type == ReminderType.DWELL.name) {
                        item { ReminderInfo("Tempo no local", "${reminder.dwellMinutes ?: 0} minutos") }
                    }
                    item {
                        val start = reminder.startMinute
                        val end = reminder.endMinute
                        ReminderInfo("Horário", if (start == null || end == null) "Qualquer horário" else {
                            "${formatMinute(start)} até ${formatMinute(end)}" +
                                (if (end < start) " (dia seguinte)" else "")
                        })
                    }
                    item {
                        val days = if (reminder.daysMask == WeekdayMask.ALL) "Todos os dias" else {
                            DayOfWeek.values().filter { WeekdayMask.contains(reminder.daysMask, it) }
                                .joinToString(", ") { it.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR")) }
                        }
                        ReminderInfo("Dias da semana", days)
                    }
                    item { ReminderInfo("Repetição", RepeatMode.valueOf(reminder.repeatMode).label) }
                    item { ReminderInfo("Criado em", detailDateTime(reminder.createdAt)) }
                    item { ReminderInfo("Último aviso", reminder.lastTriggeredAt?.let(::detailDateTime) ?: "Ainda não disparou") }
                    item {
                        Button(onClick = { onEdit(reminder.placeId) }, enabled = place != null, modifier = Modifier.fillMaxWidth()) {
                            Text("Editar lembrete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderInfo(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun detailDateTime(epoch: Long): String = Instant.ofEpochMilli(epoch)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))
