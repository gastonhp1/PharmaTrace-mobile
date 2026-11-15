package ar.pharmatrace.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.pharmatrace.core.CargoStates
import ar.pharmatrace.core.Verdict
import ar.pharmatrace.core.formatDuration
import ar.pharmatrace.core.verdictReason
import ar.pharmatrace.mobile.data.Cargo
import ar.pharmatrace.mobile.data.ColdChainStatus
import ar.pharmatrace.mobile.data.LastReading
import ar.pharmatrace.mobile.ui.CargoState
import ar.pharmatrace.mobile.ui.CargoViewModel
import ar.pharmatrace.mobile.ui.theme.statusBad
import ar.pharmatrace.mobile.ui.theme.statusOk
import ar.pharmatrace.mobile.ui.theme.statusWarn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

private fun formatTime(iso: String?): String = iso?.let { runCatching { TIME.format(Instant.parse(it)) }.getOrDefault(it) } ?: "—"

private fun formatEpoch(seconds: Long): String = TIME.format(Instant.ofEpochSecond(seconds))

@Composable
fun CargoScreen(cargoId: String, vm: CargoViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    CargoContent(cargoId, state, onRefresh = vm::refresh, onBack = onBack)
}

@Composable
fun CargoContent(cargoId: String, state: CargoState, onRefresh: () -> Unit, onBack: () -> Unit) {
    ScreenScaffold(
        title = "Cargamento",
        onBack = onBack,
        actions = { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") } },
    ) { padding ->
        ScreenColumn(padding) {
            when (val s = state) {
                CargoState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is CargoState.Failed -> {
                    Text(cargoId, style = MaterialTheme.typography.headlineSmall)
                    Notice(s.message, MaterialTheme.colorScheme.error)
                }
                is CargoState.Loaded -> {
                    CargoLabel(s.cargo)
                    Contents(s.cargo)
                    if (s.coldChain != null) {
                        ColdChain(s.coldChain)
                    } else {
                        Sheet {
                            SectionTitle("Cadena de frío")
                            Text("Este cargamento no tiene monitoreo de cadena de frío.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CargoLabel(c: Cargo) {
    val tone = when (c.state) {
        "IN_TRANSIT" -> MaterialTheme.colorScheme.primary
        "DELIVERED" -> statusOk()
        else -> MaterialTheme.colorScheme.outline
    }
    RecordHeader(
        title = c.id,
        tone = tone,
        chip = { StatusChip(CargoStates.label(c.state), tone) },
        facts = listOf(
            "En custodia de" to orgName(c.owner),
            "Destino" to destination(c),
            "Actualizado" to formatTime(c.updatedAt),
            "Lotes" to c.batchIds.size.toString(),
        ),
    )
}

/** Where the cargo is going, or why it is going nowhere. */
private fun destination(c: Cargo): String = when {
    c.pendingTo.isNotEmpty() -> orgName(c.pendingTo)
    c.state == "DELIVERED" -> "Ya entregado"
    c.state == "DISSOLVED" -> "Disuelto"
    else -> "Sin despachar"
}

@Composable
private fun Contents(c: Cargo) {
    Sheet {
        SectionTitle("Contenido", hint = "Los lotes de un cargamento se despachan y se reciben juntos.")
        Column {
            c.batchIds.forEachIndexed { i, id ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(id, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun ColdChain(cc: ColdChainStatus) {
    val verdict = Verdict.from(cc.verdict)
    val color = when (verdict) {
        Verdict.Compliant -> statusOk()
        Verdict.Compromised -> statusBad()
        Verdict.Monitoring, Verdict.Pending -> statusWarn()
    }
    Sheet {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionTitle("Cadena de frío")
            StatusChip(verdict.label, color, filled = verdict == Verdict.Compromised)
        }
        verdictReason(cc.reason, ::formatEpoch)?.let { Notice(it, color) }

        cc.lastReading?.let { r -> Reading(r, cc) }

        cc.policy?.let { p ->
            val budget = p.maxExcursionSeconds.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tiempo fuera de rango", style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(
                    progress = { (cc.excursionSeconds.toFloat() / budget).coerceIn(0f, 1f) },
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${formatDuration(cc.excursionSeconds)} de ${formatDuration(p.maxExcursionSeconds)} que tolera la política ${p.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column {
            cc.deviceId?.let { LabeledRow("PharmaBox", it, first = true) }
            LabeledRow("Lecturas ancladas", "${cc.anchoredReadings} en ${cc.anchorCount} ventanas", first = cc.deviceId == null)
            if (cc.pendingReadings > 0) LabeledRow("Pendientes de anclar", cc.pendingReadings.toString())
            if (cc.closed) LabeledRow("Monitoreo", "Cerrado (veredicto final)")
        }

        val events = cc.events.orEmpty()
        if (events.isNotEmpty()) {
            Text("Eventos en el ledger", style = MaterialTheme.typography.titleSmall)
            Column {
                events.takeLast(10).reversed().forEachIndexed { i, e ->
                    LabeledRow(eventLabel(e.kind), formatEpoch(e.timestamp), first = i == 0)
                }
            }
        }
    }
}

/** The last reading: the temperature as the headline, on a thermometer with the allowed range. */
@Composable
private fun Reading(r: LastReading, cc: ColdChainStatus) {
    val policy = cc.policy
    val inRange = policy == null || (r.tempC >= policy.minTempC && r.tempC <= policy.maxTempC)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Última lectura, ${formatTime(r.timestamp)}", style = MaterialTheme.typography.titleSmall)
        Text(
            "%.2f °C".format(r.tempC),
            style = MaterialTheme.typography.displaySmall,
            color = if (inRange) MaterialTheme.colorScheme.onSurface else statusBad(),
        )
        if (policy != null) {
            TempGauge(
                value = r.tempC,
                min = policy.minTempC,
                max = policy.maxTempC,
                okColor = statusOk(),
                badColor = statusBad(),
                description = "Última lectura %.2f °C; rango permitido de %.1f a %.1f °C".format(r.tempC, policy.minTempC, policy.maxTempC),
            )
            Text(
                "Rango permitido: %.1f a %.1f °C".format(policy.minTempC, policy.maxTempC),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            LabeledRow("Humedad", "%.0f %%".format(r.humidityPct), first = true)
            LabeledRow("Precinto", if (r.sealBroken) "Roto" else "Íntegro")
            LabeledRow("Tapa", if (r.lidOpened) "Abierta" else "Cerrada")
            LabeledRow("Golpes", if (r.shock) "Detectado" else "Sin golpes")
        }
    }
}

private fun eventLabel(kind: String) = when (kind) {
    "LID_OPENED" -> "Tapa abierta"
    "SHOCK" -> "Golpe"
    "SEAL_BROKEN" -> "Precinto roto"
    else -> kind
}
