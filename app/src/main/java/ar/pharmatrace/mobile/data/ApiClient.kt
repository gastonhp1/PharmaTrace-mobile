package ar.pharmatrace.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** An error the user can act on. [status] is the HTTP status, or 0 when the server was not reached. */
class ApiException(val status: Int, override val message: String) : Exception(message) {
    val isAuth get() = status == 401
}

/**
 * Client of the Pharmatrace REST API. Reads are public; `/api/box` needs the session from
 * [login]. [token] is read on every call, so a new session applies right away.
 */
class ApiClient(
    baseUrl: String,
    private val token: () -> String? = { null },
    private val http: OkHttpClient = defaultHttp,
) {
    val baseUrl: HttpUrl = normalize(baseUrl) ?: throw ApiException(0, "URL del servidor inválida")

    suspend fun login(username: String, password: String): LoginResponse =
        post(url("api/auth/login"), buildJsonObject { put("username", username); put("password", password) }.toString(), LoginResponse.serializer(), auth = false)

    suspend fun me(): User = get(url("api/auth/me"), User.serializer(), auth = true)

    /** Asks for an unlock token for the QR the box shows. 403 carries the reason of the denial. */
    suspend fun requestUnlock(qr: String): UnlockResponse =
        post(url("api/box/unlock"), buildJsonObject { put("qr", qr) }.toString(), UnlockResponse.serializer(), auth = true)

    suspend fun cargo(cargoId: String): Cargo = get(url("api/cargo", cargoId), Cargo.serializer())

    suspend fun device(deviceId: String): Device = get(url("api/iot/devices", deviceId), Device.serializer())

    /** Cold chain of a cargo; null when the cargo has no monitoring (404). */
    suspend fun coldChain(cargoId: String): ColdChainStatus? = try {
        get(url("api/iot/cargo", cargoId), ColdChainStatus.serializer())
    } catch (e: ApiException) {
        if (e.status == 404) null else throw e
    }

    // ------------------------------------------------------------------ plumbing

    private suspend fun <T> get(url: HttpUrl, serializer: KSerializer<T>, auth: Boolean = false): T =
        call(Request.Builder().url(url).get(), serializer, auth)

    private suspend fun <T> post(url: HttpUrl, body: String, serializer: KSerializer<T>, auth: Boolean): T =
        call(Request.Builder().url(url).post(body.toRequestBody(JSON_TYPE)), serializer, auth)

    private suspend fun <T> call(builder: Request.Builder, serializer: KSerializer<T>, auth: Boolean): T = withContext(Dispatchers.IO) {
        if (auth) {
            val t = token() ?: throw ApiException(401, "Tu sesión venció: volvé a ingresar")
            builder.header("Authorization", "Bearer $t")
        }
        builder.header("Accept", "application/json")
        try {
            http.newCall(builder.build()).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ApiException(res.code, errorMessage(res.code, body))
                try {
                    json.decodeFromString(serializer, body)
                } catch (e: IllegalArgumentException) {
                    throw ApiException(res.code, "Respuesta inesperada del servidor")
                }
            }
        } catch (e: IOException) {
            throw ApiException(0, "No se pudo conectar con el servidor (${e.message ?: e.javaClass.simpleName})")
        }
    }

    /** [path] is fixed text ("api/cargo"); [id] is user or server data, encoded as one segment. */
    internal fun url(path: String, id: String? = null): HttpUrl =
        baseUrl.newBuilder().addPathSegments(path).apply { if (id != null) addPathSegment(id) }.build()

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        internal val json = Json { ignoreUnknownKeys = true }

        val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /** "pharmatrace.example.com" -> "https://pharmatrace.example.com/". Keeps an explicit scheme. */
        fun normalize(input: String): HttpUrl? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            return "$withScheme/".toHttpUrlOrNull()
        }

        /** The API answers {"error": "..."}; the messages that matter to a user get Spanish text. */
        internal fun errorMessage(status: Int, body: String): String {
            val server = try {
                json.decodeFromString(ErrorBody.serializer(), body).error
            } catch (e: IllegalArgumentException) {
                null
            }
            return when (status) {
                401 -> if (server?.contains("username or password") == true) "Usuario o contraseña incorrectos" else "Tu sesión venció: volvé a ingresar"
                403 -> "No tenés permiso para abrir esta caja" + (server?.let { " ($it)" } ?: "")
                404 -> server ?: "No encontrado"
                429 -> "Demasiados intentos fallidos: esperá unos minutos"
                503 -> "El servidor no está disponible" + (server?.let { " ($it)" } ?: "")
                else -> server ?: "Error del servidor ($status)"
            }
        }
    }
}
