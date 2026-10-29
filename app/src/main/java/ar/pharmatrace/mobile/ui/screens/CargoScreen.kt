package ar.pharmatrace.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.pharmatrace.core.CargoStates
import ar.pharmatrace.core.Verdict
import ar.pharmatrace.core.formatDuration
import ar.pharmatrace.mobile.data.Cargo
import ar.pharmatrace.mobile.data.ColdChainStatus
import ar.pharmatrace.mobile.ui.CargoState
import ar.pharmatrace.mobile.ui.CargoViewModel
import ar.pharmatrace.mobile.ui.theme.StatusBad
import ar.pharmatrace.mobile.ui.theme.StatusOk
import ar.pharmatrace.mobile.ui.theme.StatusWarn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

private fun formatTime(iso: String?): String = iso?.let { runCatching { TIME.format(Instant.parse(it)) }.getOrDefault(it) } ?: "—"

@Composable
fun CargoScreen(cargoId: String, vm: CargoViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    CargoContent(cargoId, state, onRefresh = vm::refresh, onBack = onBack)
}

@Composable
fun CargoContent(cargoId: String, state: CargoState, onRefresh: () -> Unit, onBack: () -> Unit) {
    ScreenScaffold(
        title = "Cargamento $cargoId",
        onBack = onBack,
        actions = { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") } },
    ) { padding ->
        ScreenColumn(padding) {
            when (val s = state) {
                CargoState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is CargoState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is CargoState.Loaded -> {
                    CargoCard(s.cargo)
                    if (s.coldChain != null) ColdChainCard(s.coldChain) else Text("Este cargamento no tiene monitoreo de cadena de frío.")
                }
            }
        }
    }
}

@Composable
private fun CargoCard(c: Cargo) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Custodia", style = MaterialTheme.typography.titleMedium)
            LabeledRow("Estado", CargoStates.label(c.state))
            LabeledRow("Dueño", c.owner)
            if (c.pendingTo.isNotEmpty()) LabeledRow("Destino", c.pendingTo)
            LabeledRow("Actualizado", formatTime(c.updatedAt))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Lotes (${c.batchIds.size})", style = MaterialTheme.typography.titleSmall)
            c.batchIds.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ColdChainCard(cc: ColdChainStatus) {
    val verdict = Verdict.from(cc.verdict)
    val color = when (verdict) {
        Verdict.Compliant -> StatusOk
        Verdict.Compromised -> StatusBad
        Verdict.Monitoring, Verdict.Pending -> StatusWarn
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Cadena de frío", style = MaterialTheme.typography.titleMedium)
                VerdictChip(verdict.label, color)
            }
            cc.reason?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            cc.policy?.let { p ->
                LabeledRow("Rango", "%.1f °C a %.1f °C".format(p.minTempC, p.maxTempC))
                val budget = p.maxExcursionSeconds.coerceAtLeast(1)
                LabeledRow("Fuera de rango", "${formatDuration(cc.excursionSeconds)} de ${formatDuration(p.maxExcursionSeconds)}")
                LinearProgressIndicator(
                    progress = { (cc.excursionSeconds.toFloat() / budget).coerceIn(0f, 1f) },
                    color = color,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            cc.deviceId?.let { LabeledRow("PharmaBox", it) }
            LabeledRow("Lecturas ancladas", "${cc.anchoredReadings} en ${cc.anchorCount} ventanas")
            if (cc.pendingReadings > 0) LabeledRow("Pendientes de anclar", cc.pendingReadings.toString())
            if (cc.closed) LabeledRow("Monitoreo", "Cerrado (veredicto final)")

            cc.lastReading?.let { r ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Última lectura", style = MaterialTheme.typography.titleSmall)
                LabeledRow("Hora", formatTime(r.timestamp))
                LabeledRow("Temperatura", "%.2f °C".format(r.tempC))
                LabeledRow("Humedad", "%.0f %%".format(r.humidityPct))
                val flags = listOfNotNull(
                    "tapa abierta".takeIf { r.lidOpened },
                    "golpe".takeIf { r.shock },
                    "sello roto".takeIf { r.sealBroken },
                )
                if (flags.isNotEmpty()) LabeledRow("Eventos", flags.joinToString(", "))
            }

            val events = cc.events.orEmpty()
            if (events.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Eventos en el ledger", style = MaterialTheme.typography.titleSmall)
                events.takeLast(10).reversed().forEach { e ->
                    LabeledRow(eventLabel(e.kind), formatTime(Instant.ofEpochSecond(e.timestamp).toString()))
                }
            }
        }
    }
}

private fun eventLabel(kind: String) = when (kind) {
    "LID_OPENED" -> "Tapa abierta"
    "SHOCK" -> "Golpe"
    "SEAL_BROKEN" -> "Sello roto"
    else -> kind
}

@Composable
private fun VerdictChip(label: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).padding(0.dp)) { Surface(color = color, shape = RoundedCornerShape(50), modifier = Modifier.size(8.dp)) {} }
            Text(" $label", color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}
