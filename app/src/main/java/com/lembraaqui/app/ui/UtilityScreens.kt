@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lembraaqui.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.lembraaqui.app.PermissionStatus
import com.lembraaqui.app.domain.LocationTransition

@Composable
fun HistoryScreen(vm: MainViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    var clearConfirm by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") } },
                actions = { if (history.isNotEmpty()) TextButton(onClick = { clearConfirm = true }) { Text("Limpar") } }
            )
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, inner.calculateTopPadding() + 8.dp, 16.dp, contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (history.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, null)
                            Text("Ainda não há eventos registrados.", modifier = Modifier.padding(top = 8.dp))
                            Text("Entradas, saídas e disparos aparecerão aqui.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(history, key = { it.id }) { event ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(formatHistoryTime(event.createdAt), style = MaterialTheme.typography.labelMedium)
                            Text(event.placeName, style = MaterialTheme.typography.titleSmall)
                            Text(event.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("Limpar histórico?") },
            text = { Text("Os lugares e lembretes não serão alterados.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); clearConfirm = false }) { Text("Limpar") } },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun PermissionScreen(
    status: PermissionStatus,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onReady: () -> Unit
) {
    PermissionLaunchers(status, onRefresh, onReady) { requestForeground, requestBackground, requestNotifications, openLocationSettings, openAppSettings ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Permissões e funcionamento") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") } }
                )
            }
        ) { inner ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, inner.calculateTopPadding() + 8.dp, 16.dp, contentPadding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "O LembraAqui não envia suas coordenadas para servidor. A localização é usada pelo Android para detectar entrada, permanência e saída mesmo com a tela do app fechada.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    PermissionRow(
                        title = "Localização precisa",
                        detail = if (status.fineLocation) "Permitida" else "Necessária para criar e monitorar áreas.",
                        ok = status.fineLocation,
                        action = if (!status.fineLocation) "Permitir" else null,
                        onClick = requestForeground
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    item {
                        PermissionRow(
                            title = "Localização em segundo plano",
                            detail = if (status.backgroundLocation) "Permitida"
                            else if (!status.fineLocation) "Primeiro permita a localização precisa acima."
                            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                "Abra as configurações e escolha acesso à localização o tempo todo. Sem isso, lembretes com o app fechado podem falhar."
                            else "Necessária para os lembretes com o app fechado.",
                            ok = status.backgroundLocation,
                            action = if (!status.backgroundLocation && status.fineLocation) "Configurar" else null,
                            onClick = requestBackground
                        )
                    }
                }
                item {
                    PermissionRow(
                        title = "Localização do aparelho",
                        detail = if (status.locationServices) "Ativada" else "O GPS/localização do sistema está desligado.",
                        ok = status.locationServices,
                        action = if (!status.locationServices) "Ativar" else null,
                        onClick = openLocationSettings
                    )
                }
                item {
                    PermissionRow(
                        title = "Notificações",
                        detail = if (status.notifications) "Ativadas" else "Ative para ver os lembretes quando dispararem.",
                        ok = status.notifications,
                        action = if (!status.notifications) "Permitir" else null,
                        onClick = requestNotifications
                    )
                }
                item {
                    OutlinedButton(onClick = openAppSettings, modifier = Modifier.fillMaxWidth()) { Text("Abrir configurações do aplicativo") }
                }
                item {
                    Button(
                        onClick = { onRefresh(); onReady() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = status.geofencingReady
                    ) { Text("Atualizar monitoramento") }
                }
                item {
                    Text(
                        "O Android pode atrasar eventos por economia de bateria, Doze, qualidade do sinal ou políticas do fabricante. O aplicativo não promete precisão de segundos.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, ok: Boolean, action: String?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Error, null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            if (action != null) TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
fun DebugToolsScreen(vm: MainViewModel, placeId: String, contentPadding: PaddingValues, onBack: () -> Unit) {
    val place by vm.place(placeId).collectAsStateWithLifecycle(initialValue = null)
    var feedback by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ferramentas de teste") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") } }
            )
        }
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Science, null)
                    Text("Somente build de desenvolvimento", style = MaterialTheme.typography.titleMedium)
                    Text("Estes botões simulam eventos para testar regras, histórico e notificações. Eles não alteram o funcionamento da versão release.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(place?.name ?: "Lugar", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = { vm.simulate(placeId, LocationTransition.ENTER) { feedback = "Entrada simulada." } },
                modifier = Modifier.fillMaxWidth(), enabled = BuildConfig.DEBUG
            ) { Icon(Icons.Default.MyLocation, null); Text("Simular CHEGANDO", Modifier.padding(start = 8.dp)) }
            Button(
                onClick = { vm.simulate(placeId, LocationTransition.DWELL) { feedback = "Permanência simulada; lembretes elegíveis foram processados." } },
                modifier = Modifier.fillMaxWidth(), enabled = BuildConfig.DEBUG
            ) { Icon(Icons.Default.Notifications, null); Text("Simular NO LOCAL", Modifier.padding(start = 8.dp)) }
            Button(
                onClick = { vm.simulate(placeId, LocationTransition.EXIT) { feedback = "Saída simulada." } },
                modifier = Modifier.fillMaxWidth(), enabled = BuildConfig.DEBUG
            ) { Icon(Icons.Default.LocationOff, null); Text("Simular SAINDO", Modifier.padding(start = 8.dp)) }
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
