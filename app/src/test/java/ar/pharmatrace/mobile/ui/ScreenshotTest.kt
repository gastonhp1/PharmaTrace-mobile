package ar.pharmatrace.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import ar.pharmatrace.core.UnlockResult
import ar.pharmatrace.mobile.ble.PharmaBoxBle
import ar.pharmatrace.mobile.data.Cargo
import ar.pharmatrace.mobile.data.ColdChainPolicy
import ar.pharmatrace.mobile.data.ColdChainStatus
import ar.pharmatrace.mobile.data.DeviceEvent
import ar.pharmatrace.mobile.data.LastReading
import ar.pharmatrace.mobile.data.Session
import ar.pharmatrace.mobile.data.UnlockResponse
import ar.pharmatrace.mobile.data.User
import ar.pharmatrace.mobile.ui.screens.CargoContent
import ar.pharmatrace.mobile.ui.screens.HomeScreen
import ar.pharmatrace.mobile.ui.screens.LoginContent
import ar.pharmatrace.mobile.ui.screens.ScanContent
import ar.pharmatrace.mobile.ui.screens.UnlockContent
import ar.pharmatrace.mobile.ui.theme.PharmaTraceTheme
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Renders every screen with sample data, on the JVM (Paparazzi, no emulator).
 *   ./gradlew :app:recordPaparazziDebug   -> PNGs in app/src/test/snapshots/images
 * CI publishes them on the `screenshots` branch.
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 0.5)

    private fun shot(name: String, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { PharmaTraceTheme { content() } }

    private val session = Session("https://api.pharmatrace.ar/", User("dario", "DistMSP"), "t", Instant.parse("2026-10-30T08:00:00Z"))
    private val grant = UnlockResponse(
        deviceId = "VAL-001",
        token = "",
        issuedAt = "2026-10-29T23:00:00Z",
        expiresAt = "2026-10-29T23:02:00Z",
        cargoId = "CG-2026-0142",
        cargoState = "IN_TRANSIT",
        reason = "cargo IN_TRANSIT, user of the recipient",
    )
    private val noop = {}

    private fun unlock(state: UnlockState, blocker: String? = null) =
        UnlockContent("VAL-001", state, blocker, noop, noop, {}, noop, noop)

    @Test
    fun login() = shot("01_login") {
        LoginContent(LoginState(apiUrl = "https://api.pharmatrace.ar", username = "dario", password = "secreto123"), {}, noop)
    }

    @Test
    fun loginError() = shot("02_login_error") {
        LoginContent(
            LoginState(apiUrl = "https://api.pharmatrace.ar", username = "dario", password = "x", error = "Usuario o contraseña incorrectos"),
            {},
            noop,
        )
    }

    @Test
    fun home() = shot("03_home") { HomeScreen(session, noop, {}, noop) }

    @Test
    fun scan() = shot("04_scan") {
        ScanContent(granted = true, notABox = false, onRequestPermission = noop, onBack = noop) { CameraPlaceholder() }
    }

    @Test
    fun unlockInProgress() = shot("05_unlock_connecting") { unlock(UnlockState.Ble(PharmaBoxBle.Step.Connecting, grant)) }

    @Test
    fun unlockOk() = shot("06_unlock_ok") { unlock(UnlockState.Done(UnlockResult.Ok, grant)) }

    @Test
    fun unlockDenied() = shot("07_unlock_denied") {
        unlock(UnlockState.Denied("No tenés permiso para abrir esta caja (cargo IN_TRANSIT: only the recipient DistMSP can open it)"))
    }

    @Test
    fun unlockRejectedByBox() = shot("08_unlock_rejected_by_box") { unlock(UnlockState.Done(UnlockResult.BadNonce, grant)) }

    @Test
    fun unlockBleFailed() = shot("09_unlock_ble_failed") {
        unlock(UnlockState.Failed("No se encontró la caja VAL-001 cerca. Acercate y verificá que siga mostrando el QR.", grant))
    }

    @Test
    fun cargo() = shot("10_cargo_monitoring") { CargoContent("CG-2026-0142", CargoState.Loaded(sampleCargo, monitoring), noop, noop) }

    @Test
    fun cargoCompromised() = shot("11_cargo_compromised") {
        CargoContent(
            "CG-2026-0142",
            CargoState.Loaded(
                sampleCargo.copy(state = "DELIVERED", owner = "FarmaMSP", pendingTo = ""),
                monitoring.copy(
                    verdict = "COMPROMISED",
                    reason = "time out of range 2140 s > budget 1800 s",
                    excursionSeconds = 2140,
                    closed = true,
                    pendingReadings = 0,
                    lastReading = monitoring.lastReading!!.copy(tempC = 11.3, lidOpened = true),
                ),
            ),
            noop,
            noop,
        )
    }

    @Test
    fun homeDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        shot("12_home_dark") { HomeScreen(session, noop, {}, noop) }
    }

    @Test
    fun unlockOkDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT))
        shot("13_unlock_ok_dark") { unlock(UnlockState.Done(UnlockResult.Ok, grant)) }
    }

    private val sampleCargo = Cargo(
        id = "CG-2026-0142",
        owner = "LabMSP",
        pendingTo = "DistMSP",
        batchIds = listOf("INS-L2611-A", "INS-L2611-B", "VAC-L0942"),
        state = "IN_TRANSIT",
        updatedAt = "2026-10-29T19:40:00Z",
    )

    private val monitoring = ColdChainStatus(
        cargoId = "CG-2026-0142",
        verdict = "MONITORING",
        deviceId = "VAL-001",
        policy = ColdChainPolicy("insulina", 2.0, 8.0, 1800),
        startedAt = "2026-10-29T18:00:00Z",
        excursionSeconds = 420,
        anchoredReadings = 96,
        anchorCount = 8,
        pendingReadings = 4,
        events = listOf(
            DeviceEvent("LID_OPENED", 1_793_300_400),
            DeviceEvent("SHOCK", 1_793_304_000),
        ),
        lastReading = LastReading(
            seq = 100,
            timestamp = "2026-10-29T19:58:00Z",
            tempC = 5.42,
            humidityPct = 58.0,
            lat = -34.46,
            lon = -58.91,
        ),
    )
}

@Composable
private fun CameraPlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF39413F), Color(0xFF1C2120)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("vista de la cámara", color = Color.White.copy(alpha = 0.35f))
    }
}
