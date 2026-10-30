package ar.pharmatrace.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.pharmatrace.core.BoxProtocol
import ar.pharmatrace.core.Hex
import ar.pharmatrace.core.UnlockResult
import ar.pharmatrace.mobile.AppContainer
import ar.pharmatrace.mobile.ble.BleException
import ar.pharmatrace.mobile.ble.PharmaBoxBle
import ar.pharmatrace.mobile.data.ApiClient
import ar.pharmatrace.mobile.data.ApiException
import ar.pharmatrace.mobile.data.Cargo
import ar.pharmatrace.mobile.data.ColdChainStatus
import ar.pharmatrace.mobile.data.UnlockResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------- login

data class LoginState(
    val apiUrl: String,
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LoginState(apiUrl = container.sessions.apiUrl))
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun update(transform: (LoginState) -> LoginState) = _state.update { transform(it).copy(error = null) }

    fun login() {
        val s = _state.value
        if (s.username.isBlank() || s.password.isEmpty() || s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val api = container.api(s.apiUrl)
                val res = api.login(s.username.trim(), s.password)
                container.sessions.save(api.baseUrl.toString(), res)
                _state.update { it.copy(busy = false, password = "") }
            } catch (e: ApiException) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }
}

// ---------------------------------------------------------------------- unlock

sealed interface UnlockState {
    /** Waiting for the Bluetooth permissions / adapter before starting. */
    data object Ready : UnlockState
    data object RequestingToken : UnlockState
    data class Ble(val step: PharmaBoxBle.Step, val grant: UnlockResponse) : UnlockState
    data class Done(val result: UnlockResult, val grant: UnlockResponse) : UnlockState

    /** The server said no: nothing was sent to the box. */
    data class Denied(val reason: String) : UnlockState
    data class Failed(val message: String, val grant: UnlockResponse?, val sessionExpired: Boolean = false) : UnlockState
}

/**
 * QR → token from the backend → structural check → BLE write → result of the box.
 *
 * A token is single-use on the box (its nonce is consumed on success) and every rejected write costs
 * one of the box's three attempts, so a token that fails the local check is never sent, and a
 * retry after a BLE failure reuses the same token only while it has not expired.
 */
class UnlockViewModel(private val container: AppContainer, qr: String) : ViewModel() {
    val challenge: BoxProtocol.Challenge? = BoxProtocol.parseQr(qr)

    private val _state = MutableStateFlow<UnlockState>(UnlockState.Ready)
    val state: StateFlow<UnlockState> = _state.asStateFlow()
    private var job: Job? = null
    private var grant: UnlockResponse? = null

    fun start() {
        val c = challenge ?: run {
            _state.value = UnlockState.Failed("Ese código no es de una PharmaBox", null)
            return
        }
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                val g = grant?.takeUnless { expired(it) } ?: run {
                    _state.value = UnlockState.RequestingToken
                    container.api().requestUnlock(c.raw).also { grant = it }
                }
                val token = Hex.decode(g.token)
                BoxProtocol.checkToken(token, c)?.let {
                    grant = null
                    _state.value = UnlockState.Failed("El servidor devolvió un token que la caja rechazaría ($it). No se envió.", g)
                    return@launch
                }
                val result = container.ble.unlock(c.deviceId, token) { step -> _state.value = UnlockState.Ble(step, g) }
                // Whatever the box answered, this token is spent (used, or counted as an attempt).
                grant = null
                _state.value = UnlockState.Done(result, g)
            } catch (e: ApiException) {
                _state.value = when (e.status) {
                    403 -> UnlockState.Denied(e.message)
                    401 -> UnlockState.Failed(e.message, null, sessionExpired = true)
                    else -> UnlockState.Failed(e.message, null)
                }
            } catch (e: BleException) {
                _state.value = UnlockState.Failed(e.message ?: "Error de Bluetooth", grant)
            } catch (e: IllegalArgumentException) {
                _state.value = UnlockState.Failed("Respuesta inválida del servidor", null)
            } catch (e: SecurityException) {
                _state.value = UnlockState.Failed("Faltan los permisos de Bluetooth", grant)
            }
        }
    }

    private fun expired(g: UnlockResponse): Boolean =
        runCatching { !java.time.Instant.now().plusSeconds(5).isBefore(java.time.Instant.parse(g.expiresAt)) }.getOrDefault(true)
}

// ---------------------------------------------------------------------- cargo

sealed interface CargoState {
    data object Loading : CargoState
    data class Loaded(val cargo: Cargo, val coldChain: ColdChainStatus?) : CargoState
    data class Failed(val message: String) : CargoState
}

class CargoViewModel(private val api: ApiClient, private val cargoId: String) : ViewModel() {
    private val _state = MutableStateFlow<CargoState>(CargoState.Loading)
    val state: StateFlow<CargoState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = CargoState.Loading
        viewModelScope.launch {
            _state.value = try {
                // coroutineScope: a failed request fails here, not the whole viewModelScope.
                coroutineScope {
                    val cargo = async { api.cargo(cargoId) }
                    val cold = async { api.coldChain(cargoId) }
                    CargoState.Loaded(cargo.await(), cold.await())
                }
            } catch (e: ApiException) {
                CargoState.Failed(e.message)
            }
        }
    }
}
