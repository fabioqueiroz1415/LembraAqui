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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lembraaqui.app.MainViewModel
import com.lembraaqui.app.PermissionStatus
import com.lembraaqui.app.data.PlaceSummaryRow

@Composable
fun HomeScreen(
    vm: MainViewModel,
    permissionStatus: PermissionStatus,
    contentPadding: PaddingValues,
    onAddPlace: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onHistory: () -> Unit,
    onPermissions: () -> Unit
) {
    val places by vm.places.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meus lugares") },
                actions = {
                    IconButton(onClick = onHistory) { Icon(Icons.Default.History, contentDescription = "Histórico") }
                    IconButton(onClick = onPermissions) { Icon(Icons.Default.Security, contentDescription = "Permissões") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPlace,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Adicionar lugar") }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + contentPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!permissionStatus.fullyReady) {
                item {
                    PermissionBanner(permissionStatus, onPermissions)
                }
            }
            if (places.isEmpty()) {
                item {
                    EmptyPlacesCard(onAddPlace)
                }
            } else {
                items(places, key = { it.id }) { place ->
                    PlaceCard(place, enabled = "place:${place.id}" !in busy, onOpenPlace = { onOpenPlace(place.id) }, onActiveChanged = { vm.setPlaceActive(place.id, it) })
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(status: PermissionStatus, onClick: () -> Unit) {
    val text = when {
        !status.fineLocation -> "A localização precisa ser permitida para monitorar seus lugares."
        !status.backgroundLocation -> "Permita localização o tempo todo para receber lembretes com o app fechado."
        !status.locationServices -> "A localização do aparelho está desligada."
        !status.notifications -> "As notificações estão desativadas; os eventos podem ser detectados sem aparecer na tela."
        else -> "Revise as permissões do aplicativo."
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null)
            Column(Modifier.padding(start = 12.dp)) {
                Text("Ação necessária", style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EmptyPlacesCard(onAddPlace: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onAddPlace)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(Modifier.height(12.dp))
            Text("Nenhum lugar cadastrado", style = MaterialTheme.typography.titleMedium)
            Text("Adicione casa, trabalho, academia ou qualquer lugar que queira monitorar.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PlaceCard(place: PlaceSummaryRow, enabled: Boolean, onOpenPlace: () -> Unit, onActiveChanged: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpenPlace)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                val parts = buildList {
                    if (place.arrivingCount > 0) add("${place.arrivingCount} chegando")
                    if (place.dwellCount > 0) add("${place.dwellCount} no local")
                    if (place.leavingCount > 0) add("${place.leavingCount} saindo")
                }
                Text(
                    if (parts.isEmpty()) "Sem lembretes ativos" else parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (place.active) "Monitoramento ativo" else "Monitoramento pausado",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (place.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Switch(enabled = enabled, checked = place.active, onCheckedChange = onActiveChanged)
        }
    }
}
