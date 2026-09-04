@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lembraaqui.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lembraaqui.app.MainViewModel
import com.lembraaqui.app.data.ReminderEntity
import com.lembraaqui.app.domain.ReminderType
import com.lembraaqui.app.domain.RepeatMode
import com.lembraaqui.app.domain.WeekdayMask
import java.time.DayOfWeek

@Composable
fun ReminderEditScreen(
    vm: MainViewModel,
    placeId: String,
    reminderId: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val existingFlow = if (reminderId == "new") {
        remember { kotlinx.coroutines.flow.flowOf<ReminderEntity?>(null) }
    } else {
        vm.reminder(reminderId)
    }
    val existing by existingFlow.collectAsStateWithLifecycle(initialValue = null)

    var loadedId by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf(ReminderType.ARRIVING) }
    var message by remember { mutableStateOf("") }
    var dwellMinutes by remember { mutableStateOf(30) }
    var customDwell by remember { mutableStateOf("30") }
    var restrictedTime by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf("17:00") }
    var endText by remember { mutableStateOf("22:00") }
    var daysMask by remember { mutableStateOf(WeekdayMask.ALL) }
    var repeatMode by remember { mutableStateOf(RepeatMode.EVERY_TIME) }
    var active by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(existing?.id) {
        if (existing != null && loadedId != existing?.id) {
            existing?.let {
                loadedId = it.id
                type = ReminderType.valueOf(it.type)
                message = it.message
                dwellMinutes = it.dwellMinutes ?: 30
                customDwell = (it.dwellMinutes ?: 30).toString()
                restrictedTime = it.startMinute != null && it.endMinute != null
                startText = it.startMinute?.let(::formatMinute) ?: "17:00"
                endText = it.endMinute?.let(::formatMinute) ?: "22:00"
                daysMask = it.daysMask
                repeatMode = RepeatMode.valueOf(it.repeatMode)
                active = it.active
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reminderId == "new") "Adicionar lembrete" else "Editar lembrete") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") } },
                actions = {
                    if (reminderId != "new") IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Excluir") }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, inner.calculateTopPadding() + 8.dp, 16.dp, contentPadding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text("Quando devo lembrar?", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderType.entries.forEach { option ->
                        Card(
                            Modifier.fillMaxWidth().clickable { type = option },
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = type == option, onClick = { type = option })
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(option.label.uppercase(), style = MaterialTheme.typography.titleSmall)
                                    Text(option.description(), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            if (type == ReminderType.DWELL) {
                item {
                    Text("Após quanto tempo?", style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 15, 30).forEach { option ->
                                FilterChip(
                                    selected = dwellMinutes == option,
                                    onClick = { dwellMinutes = option; customDwell = option.toString() },
                                    label = { Text("$option min") }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(60, 120).forEach { option ->
                                FilterChip(
                                    selected = dwellMinutes == option,
                                    onClick = { dwellMinutes = option; customDwell = option.toString() },
                                    label = { Text(if (option == 60) "1 hora" else "2 horas") }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customDwell,
                        onValueChange = {
                            customDwell = it.filter(Char::isDigit)
                            customDwell.toIntOrNull()?.let { v -> dwellMinutes = v }
                        },
                        label = { Text("Personalizado (minutos)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it; error = null },
                    label = { Text("Mensagem") },
                    placeholder = { Text("Ex.: Pegou a carteira?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Restrição de horário", style = MaterialTheme.typography.titleMedium)
                        Text(if (restrictedTime) "Somente dentro do intervalo abaixo." else "Qualquer horário.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = restrictedTime, onCheckedChange = { restrictedTime = it })
                }
                if (restrictedTime) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { startText = it },
                            label = { Text("Entre") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { endText = it },
                            label = { Text("e") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Text("Intervalos como 22:00 até 06:00 são aceitos.", style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                Text("Dias da semana", style = MaterialTheme.typography.titleMedium)
                val labels = mapOf(
                    DayOfWeek.MONDAY to "Seg", DayOfWeek.TUESDAY to "Ter", DayOfWeek.WEDNESDAY to "Qua",
                    DayOfWeek.THURSDAY to "Qui", DayOfWeek.FRIDAY to "Sex", DayOfWeek.SATURDAY to "Sáb", DayOfWeek.SUNDAY to "Dom"
                )
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.entries.take(4).forEach { (day, label) ->
                            FilterChip(
                                selected = WeekdayMask.contains(daysMask, day),
                                onClick = { daysMask = WeekdayMask.toggle(daysMask, day) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.entries.drop(4).forEach { (day, label) ->
                            FilterChip(
                                selected = WeekdayMask.contains(daysMask, day),
                                onClick = { daysMask = WeekdayMask.toggle(daysMask, day) },
                                label = { Text(label) }
                            )
                        }
                    }
                    TextButton(onClick = { daysMask = WeekdayMask.ALL }) { Text("Todos os dias") }
                }
            }

            item {
                Text("Repetição", style = MaterialTheme.typography.titleMedium)
                Column(Modifier.padding(top = 6.dp)) {
                    RepeatMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable { repeatMode = mode }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = repeatMode == mode, onClick = { repeatMode = mode })
                            Column {
                                Text(mode.label, style = MaterialTheme.typography.titleSmall)
                                Text(mode.description(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Lembrete ativo", style = MaterialTheme.typography.titleMedium)
                        Text("Você pode pausar sem excluir.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }

            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            item {
                Button(
                    onClick = {
                        val start = if (restrictedTime) parseMinute(startText) else null
                        val end = if (restrictedTime) parseMinute(endText) else null
                        if (restrictedTime && (start == null || end == null)) {
                            error = "Use horários no formato HH:mm."
                            return@Button
                        }
                        vm.saveReminder(
                            existing = existing,
                            placeId = placeId,
                            type = type,
                            message = message,
                            dwellMinutes = if (type == ReminderType.DWELL) dwellMinutes else null,
                            startMinute = start,
                            endMinute = end,
                            daysMask = daysMask,
                            repeatMode = repeatMode,
                            active = active,
                            onDone = onSaved,
                            onError = { error = it }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salvar lembrete") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir lembrete?") },
            text = { Text("Essa ação remove apenas este lembrete.") },
            confirmButton = {
                TextButton(onClick = {
                    existing?.let { vm.deleteReminder(it, onSaved) }
                    confirmDelete = false
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}
