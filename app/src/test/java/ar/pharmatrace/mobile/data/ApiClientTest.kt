package ar.pharmatrace.mobile.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer
    private var token: String? = "session-token"
    private lateinit var api: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = ApiClient(server.url("/").toString(), token = { token })
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("content-type", "application/json").setBody(body)

    private suspend fun expectError(status: Int, block: suspend () -> Unit): ApiException {
        try {
            block()
        } catch (e: ApiException) {
            assertEquals(status, e.status)
            return e
        }
        fail("expected ApiException $status")
        throw AssertionError()
    }

    @Test
    fun loginPostsCredentialsWithoutAuthorization() = runTest {
        server.enqueue(json(200, """{"token":"t","expiresAt":"2026-10-30T08:00:00.000Z","user":{"username":"dario","mspId":"DistMSP"}}"""))
        val res = api.login("dario", "secret-pass")
        assertEquals(User("dario", "DistMSP"), res.user)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/auth/login", req.path)
        assertNull(req.getHeader("Authorization"))
        assertEquals("""{"username":"dario","password":"secret-pass"}""", req.body.readUtf8())
    }

    @Test
    fun wrongCredentialsGetASpanishMessage() = runTest {
        server.enqueue(json(401, """{"error":"Invalid username or password"}"""))
        val e = expectError(401) { api.login("dario", "nope") }
        assertEquals("Usuario o contraseña incorrectos", e.message)
    }

    @Test
    fun unlockSendsTheQrWithTheSession() = runTest {
        server.enqueue(
            json(
                200,
                """{"deviceId":"VAL-001","token":"0101","issuedAt":"2026-10-29T23:00:00.000Z","expiresAt":"2026-10-29T23:02:00.000Z",
                   "cargoId":"C1","cargoState":"IN_TRANSIT","reason":"cargo IN_TRANSIT, user of the recipient","extra":"ignored"}""",
            ),
        )
        val res = api.requestUnlock("PB1:VAL-001:AAECAwQFBgcICQoLDA0ODw")
        assertEquals("C1", res.cargoId)
        val req = server.takeRequest()
        assertEquals("/api/box/unlock", req.path)
        assertEquals("Bearer session-token", req.getHeader("Authorization"))
        assertEquals("""{"qr":"PB1:VAL-001:AAECAwQFBgcICQoLDA0ODw"}""", req.body.readUtf8())
    }

    @Test
    fun deniedUnlockCarriesTheReason() = runTest {
        server.enqueue(json(403, """{"error":"cargo IN_TRANSIT: only the recipient DistMSP can open it"}"""))
        val e = expectError(403) { api.requestUnlock("PB1:VAL-001:AAECAwQFBgcICQoLDA0ODw") }
        assertTrue(e.message.contains("only the recipient DistMSP"))
    }

    @Test
    fun noSessionFailsBeforeCallingTheServer() = runTest {
        token = null
        val e = expectError(401) { api.requestUnlock("x") }
        assertTrue(e.isAuth)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun idsAreEncodedAsOnePathSegment() = runTest {
        server.enqueue(json(200, """{"id":"C/1 x","owner":"LabMSP","state":"OPEN","batchIds":["B1"]}"""))
        api.cargo("C/1 x")
        assertEquals("/api/cargo/C%2F1%20x", server.takeRequest().path)
    }

    @Test
    fun coldChainWithoutMonitoringIsNull() = runTest {
        server.enqueue(json(404, """{"error":"monitoring C9 not found"}"""))
        assertNull(api.coldChain("C9"))
    }

    @Test
    fun coldChainParsesTheGatewayStatus() = runTest {
        server.enqueue(
            json(
                200,
                """{"cargoId":"C1","verdict":"MONITORING","reason":"","deviceId":"VAL-001",
                   "policy":{"id":"insulin","minTempC":2,"maxTempC":8,"maxExcursionSeconds":1800},
                   "startedAt":"2026-10-29T20:00:00Z","closed":false,"excursionSeconds":120,"anchoredReadings":30,
                   "anchorCount":3,"events":null,"pendingReadings":2,
                   "lastReading":{"seq":31,"timestamp":"2026-10-29T20:31:00.000Z","tempC":4.5,"humidityPct":55,
                                  "lidOpened":false,"shock":false,"sealBroken":false,"lat":-34.6,"lon":-58.4}}""",
            ),
        )
        val cc = api.coldChain("C1")!!
        assertEquals("MONITORING", cc.verdict)
        assertEquals(1800L, cc.policy!!.maxExcursionSeconds)
        assertNull(cc.events)
        assertEquals(4.5, cc.lastReading!!.tempC, 0.0)
    }

    @Test
    fun unreachableServerIsStatusZero() = runTest {
        val closed = ApiClient("http://127.0.0.1:1")
        val e = expectError(0) { closed.cargo("C1") }
        assertTrue(e.message.startsWith("No se pudo conectar"))
    }

    @Test
    fun baseUrlIsNormalized() {
        assertEquals("https://api.example.com/", ApiClient.normalize("api.example.com")!!.toString())
        assertEquals("http://10.0.2.2:3001/", ApiClient.normalize(" http://10.0.2.2:3001/ ")!!.toString())
        assertEquals("https://x.example/pharma/", ApiClient.normalize("https://x.example/pharma")!!.toString())
        assertNull(ApiClient.normalize(""))
        val prefixed = ApiClient("https://x.example/pharma")
        assertEquals("https://x.example/pharma/api/cargo/C1", prefixed.url("api/cargo", "C1").toString())
    }
}
