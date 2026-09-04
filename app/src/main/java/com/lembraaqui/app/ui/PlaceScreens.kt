@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lembraaqui.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.lembraaqui.app.BuildConfig
import com.lembraaqui.app.MainViewModel
import com.lembraaqui.app.data.PlaceEntity
import com.lembraaqui.app.data.ReminderEntity
import com.lembraaqui.app.domain.CoordinateParseResult
import com.lembraaqui.app.domain.CoordinateParser
import com.lembraaqui.app.domain.ReminderType
import java.util.Locale

@Composable
fun PlaceDetailScreen(
    vm: MainViewModel,
    placeId: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddReminder: () -> Unit,
    onEditReminder: (String) -> Unit,
    onDebug: () -> Unit
) {
    val place by vm.place(placeId).collectAsStateWithLifecycle(initialValue = null)
    val reminders by vm.reminders(placeId).collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(place?.name ?: "Lugar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") } },
                actions = {
                    IconButton(onClick = onEdit, enabled = place != null) { Icon(Icons.Default.Edit, "Editar lugar") }
                    IconButton(onClick = { confirmDelete = true }, enabled = place != null) { Icon(Icons.Default.Delete, "Excluir lugar") }
                }
            )
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onAddReminder,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Adicionar lembrete") }
            )
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                16.dp,
                inner.calculateTopPadding() + 8.dp,
                16.dp,
                inner.calculateBottomPadding() + contentPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            place?.let { p ->
                item { PlaceInfoCard(p) { vm.setPlaceActive(p.id, it) } }
                item { Text("Lembretes", style = MaterialTheme.typography.titleLarge) }
                if (reminders.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth().clickable(onClick = onAddReminder)) {
                            Column(Modifier.padding(20.dp)) {
                                Text("Este lugar ainda não tem lembretes.")
                                Text("Toque para criar o primeiro.", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderCard(reminder, onClick = { onEditReminder(reminder.id) }, onToggle = { vm.setReminderActive(reminder, it) })
                    }
                }
                if (BuildConfig.DEBUG) {
                    item {
                        OutlinedButton(onClick = onDebug, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Science, null)
                            Text("Ferramentas de teste", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir lugar?") },
            text = { Text("O lugar e todos os seus lembretes serão removidos. O histórico já registrado permanece no aparelho.") },
            confirmButton = {
                TextButton(onClick = {
                    place?.let { vm.deletePlace(it) { onBack() } }
                    confirmDelete = false
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PlaceInfoCard(place: PlaceEntity, onActiveChanged: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${formatCoord(place.latitude)}, ${formatCoord(place.longitude)}", style = MaterialTheme.typography.bodyMedium)
            Text("Raio: ${place.radiusMeters.toInt()} m")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Monitoramento", style = MaterialTheme.typography.titleSmall)
                    Text(if (place.active) "Ativo" else "Pausado", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = place.active, onCheckedChange = onActiveChanged)
            }
            if (place.inside) Text("Estado atual: dentro da área", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ReminderCard(reminder: ReminderEntity, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val type = ReminderType.valueOf(reminder.type)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (type) {
                        ReminderType.DWELL -> "NO LOCAL · após ${reminder.dwellMinutes} minutos"
                        else -> type.label.uppercase(Locale.getDefault())
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(reminder.message, style = MaterialTheme.typography.titleMedium)
                if (reminder.startMinute != null && reminder.endMinute != null) {
                    Text("${formatMinute(reminder.startMinute)} às ${formatMinute(reminder.endMinute)}", style = MaterialTheme.typography.bodySmall)
                }
                if (reminder.oneShotCompleted) Text("Concluído", style = MaterialTheme.typography.labelSmall)
            }
            Switch(checked = reminder.active, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun PlaceEditScreen(
    vm: MainViewModel,
    placeId: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onNeedPermission: () -> Unit
) {
    val existingFlow = if (placeId == "new") {
        remember { kotlinx.coroutines.flow.flowOf<PlaceEntity?>(null) }
    } else {
        vm.place(placeId)
    }
    val existing by existingFlow.collectAsStateWithLifecycle(initialValue = null)

    var loadedId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var coordinatesText by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var radius by remember { mutableStateOf(100f) }
    var customRadius by remember { mutableStateOf("100") }
    var active by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existing?.id, placeId) {
        if (placeId != "new" && existing != null && loadedId != existing?.id) {
            existing?.let {
                loadedId = it.id
                name = it.name
                latitude = it.latitude
                longitude = it.longitude
                coordinatesText = "${formatCoord(it.latitude)}, ${formatCoord(it.longitude)}"
                radius = it.radiusMeters
                customRadius = it.radiusMeters.toInt().toString()
                active = it.active
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (placeId == "new") "Adicionar lugar" else "Editar lugar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") } }
            )
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, inner.calculateTopPadding() + 8.dp, 16.dp, contentPadding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nome do lugar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text("Localização", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        vm.currentLocation { result ->
                            result.onSuccess { (lat, lon) ->
                                latitude = lat; longitude = lon
                                coordinatesText = "${formatCoord(lat)}, ${formatCoord(lon)}"
                                error = null
                            }.onFailure {
                                error = it.message
                                if (it.message?.contains("Permita") == true) onNeedPermission()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LocationOn, null)
                    Text("Usar minha localização atual", Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = coordinatesText,
                    onValueChange = { text ->
                        coordinatesText = text
                        when (val result = CoordinateParser.parse(text)) {
                            is CoordinateParseResult.Success -> {
                                latitude = result.coordinates.latitude
                                longitude = result.coordinates.longitude
                                error = null
                            }
                            is CoordinateParseResult.Error -> {
                                latitude = null; longitude = null
                            }
                        }
                    },
                    label = { Text("Colar coordenadas") },
                    supportingText = { Text("Ex.: -12,1231234, -47,9238498234") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (latitude != null && longitude != null) {
                    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Latitude: ${formatCoord(latitude!!)}")
                            Text("Longitude: ${formatCoord(longitude!!)}")
                            Text("Raio: ${radius.toInt()} m")
                        }
                    }
                }
            }
            item {
                Text("Raio", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(50f, 100f, 200f).forEach { option ->
                        FilterChip(
                            selected = radius == option,
                            onClick = { radius = option; customRadius = option.toInt().toString() },
                            label = { Text("${option.toInt()} m") }
                        )
                    }
                }
                OutlinedTextField(
                    value = customRadius,
                    onValueChange = {
                        customRadius = it.filter(Char::isDigit)
                        customRadius.toFloatOrNull()?.let { v -> radius = v }
                    },
                    label = { Text("Personalizado (metros)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Monitoramento ativo", style = MaterialTheme.typography.titleMedium)
                        Text("Pausar o lugar impede todos os lembretes sem apagá-los.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(
                    onClick = {
                        val parsed = CoordinateParser.parse(coordinatesText)
                        if (parsed is CoordinateParseResult.Error) {
                            error = parsed.message
                            return@Button
                        }
                        val coords = (parsed as CoordinateParseResult.Success).coordinates
                        vm.savePlace(existing, name, coords.latitude, coords.longitude, radius, active, onSaved) { error = it }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salvar lugar") }
            }
        }
    }
}

private fun formatCoord(value: Double): String = String.format(Locale.US, "%.7f", value).replace('.', ',')
