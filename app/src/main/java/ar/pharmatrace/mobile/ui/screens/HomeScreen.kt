package ar.pharmatrace.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ar.pharmatrace.mobile.data.Session

private val ORG_NAMES = mapOf(
    "LabMSP" to "Laboratorio",
    "DistMSP" to "Distribuidora",
    "FarmaMSP" to "Farmacia",
    "RegMSP" to "Regulador",
)

@Composable
fun HomeScreen(session: Session, onOpenBox: () -> Unit, onCargo: (String) -> Unit, onLogout: () -> Unit) {
    var cargoId by rememberSaveable { mutableStateOf("") }
    ScreenScaffold(
        title = "PharmaTrace",
        actions = { IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Salir") } },
    ) { padding ->
        ScreenColumn(padding) {
            Text(
                "${session.user.username} · ${ORG_NAMES[session.user.mspId] ?: session.user.mspId}",
                style = MaterialTheme.typography.titleMedium,
            )

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text("Abrir una PharmaBox", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Apretá el botón de la caja y escaneá el QR que aparece en su pantalla. " +
                            "El servidor decide si podés abrirla según el estado del cargamento.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onOpenBox, modifier = Modifier.fillMaxWidth()) { Text("Escanear QR de la caja") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text("Consultar un cargamento", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("Estado, lotes y cadena de frío.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = cargoId,
                        onValueChange = { cargoId = it.trim() },
                        label = { Text("ID del cargamento") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (cargoId.isNotBlank()) onCargo(cargoId) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { onCargo(cargoId) }, enabled = cargoId.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("Ver cargamento")
                    }
                }
            }
        }
    }
}
