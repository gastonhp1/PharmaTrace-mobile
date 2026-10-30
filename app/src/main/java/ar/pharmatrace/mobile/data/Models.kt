package ar.pharmatrace.mobile.data

import kotlinx.serialization.Serializable

// The JSON of the Pharmatrace API (backend/ in gastonhp1/Pharmatrace). Unknown fields are ignored,
// so the backend can add fields without breaking the app.

@Serializable
data class User(val username: String, val mspId: String)

@Serializable
data class LoginResponse(val token: String, val expiresAt: String, val user: User)

@Serializable
data class UnlockResponse(
    val deviceId: String,
    val token: String,
    val issuedAt: String,
    val expiresAt: String,
    val cargoId: String? = null,
    val cargoState: String? = null,
    val reason: String? = null,
)

@Serializable
data class Cargo(
    val id: String,
    val owner: String,
    val pendingTo: String = "",
    val batchIds: List<String> = emptyList(),
    val state: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class Device(
    val id: String,
    val org: String,
    val active: Boolean = true,
    val activeCargo: String = "",
    val calibrationExpiry: Long = 0,
)

@Serializable
data class ColdChainPolicy(
    val id: String,
    val minTempC: Double,
    val maxTempC: Double,
    val maxExcursionSeconds: Long,
)

@Serializable
data class DeviceEvent(val kind: String, val timestamp: Long, val payloadHash: String = "")

@Serializable
data class LastReading(
    val seq: Long,
    val timestamp: String,
    val tempC: Double,
    val humidityPct: Double,
    val lidOpened: Boolean = false,
    val shock: Boolean = false,
    val sealBroken: Boolean = false,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

@Serializable
data class ColdChainStatus(
    val cargoId: String,
    val verdict: String? = null,
    val reason: String? = null,
    val deviceId: String? = null,
    val policy: ColdChainPolicy? = null,
    val startedAt: String? = null,
    val closed: Boolean = false,
    val excursionSeconds: Long = 0,
    val anchoredReadings: Long = 0,
    val anchorCount: Int = 0,
    val events: List<DeviceEvent>? = null,
    val pendingReadings: Long = 0,
    val lastReading: LastReading? = null,
)

@Serializable
internal data class ErrorBody(val error: String? = null)
