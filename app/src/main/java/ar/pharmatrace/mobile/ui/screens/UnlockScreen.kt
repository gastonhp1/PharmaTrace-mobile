package ar.pharmatrace.mobile.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.pharmatrace.core.CargoStates
import ar.pharmatrace.core.UnlockResult
import ar.pharmatrace.mobile.ble.PharmaBoxBle
import ar.pharmatrace.mobile.ui.UnlockState
import ar.pharmatrace.mobile.ui.UnlockViewModel
import ar.pharmatrace.mobile.ui.blePermissions
import ar.pharmatrace.mobile.ui.hasAll
import ar.pharmatrace.mobile.ui.theme.StatusBad
import ar.pharmatrace.mobile.ui.theme.StatusOk

@Composable
fun UnlockScreen(
    vm: UnlockViewModel,
    ble: PharmaBoxBle,
    onScanAgain: () -> Unit,
    onCargo: (String) -> Unit,
    onSessionExpired: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var blocker by remember { mutableStateOf<String?>(null) }

    // Permissions, then Bluetooth on, then start. Each launcher continues the chain.
    lateinit var proceed: () -> Unit
    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (ble.isEnabled()) proceed() else blocker = "Hace falta el Bluetooth encendido para hablar con la caja."
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) proceed() else blocker = "Sin permiso de Bluetooth la app no puede encontrar la caja."
    }
    proceed = {
        blocker = null
        when {
            !context.hasAll(blePermissions()) -> permissions.launch(blePermissions())
            !ble.isEnabled() -> enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> vm.start()
        }
    }
    LaunchedEffect(Unit) { if (state == UnlockState.Ready) proceed() }

    ScreenScaffold(title = "Abrir ${vm.challenge?.deviceId ?: "caja"}", onBack = onBack) { padding ->
        ScreenColumn(padding) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepRow("Pedir autorización al servidor", stepStatus(state, 0))
                    StepRow("Buscar la caja por Bluetooth", stepStatus(state, 1))
                    StepRow("Conectar", stepStatus(state, 2))
                    StepRow("Enviar el token", stepStatus(state, 3))
                    StepRow("Respuesta de la caja", stepStatus(state, 4))
                }
            }

            blocker?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Button(onClick = proceed, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }

            when (val s = state) {
                is UnlockState.Done -> ResultCard(s, onScanAgain, onCargo, onRetry = proceed)
                is UnlockState.Denied -> Outcome(
                    icon = Icons.Filled.Block,
                    color = StatusBad,
                    title = "No autorizado",
                    body = s.reason + "\n\nNo se envió nada a la caja.",
                ) { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") } }
                is UnlockState.Failed -> Outcome(
                    icon = Icons.Filled.ErrorOutline,
                    color = StatusBad,
                    title = "No se pudo abrir",
                    body = s.message,
                ) {
                    when {
                        s.sessionExpired -> Button(onClick = onSessionExpired, modifier = Modifier.fillMaxWidth()) { Text("Ingresar de nuevo") }
                        else -> {
                            Button(onClick = proceed, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
                            OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) { Text("Escanear otro QR") }
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ResultCard(s: UnlockState.Done, onScanAgain: () -> Unit, onCargo: (String) -> Unit, onRetry: () -> Unit) {
    val ok = s.result == UnlockResult.Ok
    Outcome(
        icon = if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
        color = if (ok) StatusOk else StatusBad,
        title = if (ok) "Caja abierta" else "La caja rechazó el token",
        body = buildString {
            if (!ok) append(s.result.message)
            s.grant.cargoId?.let {
                if (isNotEmpty()) append("\n\n")
                append("Cargamento $it · ${CargoStates.label(s.grant.cargoState)}")
            }
            if (ok) append("\n\nLa apertura queda registrada en el servidor y en la próxima lectura firmada de la caja.")
        },
    ) {
        s.grant.cargoId?.let { id ->
            Button(onClick = { onCargo(id) }, modifier = Modifier.fillMaxWidth()) { Text("Ver cargamento") }
        }
        when {
            ok -> Unit
            s.result.retryWithNewQr -> OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) { Text("Escanear un QR nuevo") }
            else -> OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
        }
    }
}

@Composable
private fun Outcome(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    title: String,
    body: String,
    actions: @Composable () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

private enum class StepStatus { Pending, Active, Done, Failed }

/** Where [state] is in the five visible steps. */
private fun stepStatus(state: UnlockState, index: Int): StepStatus {
    val current = when (state) {
        UnlockState.Ready -> -1
        UnlockState.RequestingToken -> 0
        is UnlockState.Ble -> when (state.step) {
            PharmaBoxBle.Step.Scanning -> 1
            PharmaBoxBle.Step.Connecting -> 2
            PharmaBoxBle.Step.Sending -> 3
            PharmaBoxBle.Step.Waiting -> 4
        }
        is UnlockState.Done -> 5
        is UnlockState.Denied -> return if (index == 0) StepStatus.Failed else StepStatus.Pending
        is UnlockState.Failed -> return if (index == 0 && state.grant != null) StepStatus.Done else if (index == 0) StepStatus.Failed else StepStatus.Pending
    }
    return when {
        index < current -> StepStatus.Done
        index == current -> StepStatus.Active
        else -> StepStatus.Pending
    }
}

@Composable
private fun StepRow(label: String, status: StepStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (status) {
            StepStatus.Active -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            StepStatus.Done -> Icon(Icons.Filled.CheckCircle, contentDescription = "Listo", tint = StatusOk, modifier = Modifier.size(20.dp))
            StepStatus.Failed -> Icon(Icons.Filled.ErrorOutline, contentDescription = "Falló", tint = StatusBad, modifier = Modifier.size(20.dp))
            StepStatus.Pending -> Icon(
                Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (status == StepStatus.Pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
