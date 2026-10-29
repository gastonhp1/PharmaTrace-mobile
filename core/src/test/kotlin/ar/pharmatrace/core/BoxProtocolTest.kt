package ar.pharmatrace.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BoxProtocolTest {
    private val nonce = ByteArray(16) { it.toByte() } // 00 01 .. 0f
    private val qr = "PB1:VAL-001:AAECAwQFBgcICQoLDA0ODw"

    // Token signed by the Pharmatrace backend (box/token.js) for deviceId VAL-001, the nonce above,
    // actor DistMSP:dario, iat 1700000000, ttl 120. Same layout as pharma_unlock.h.
    private val backendToken = Hex.decode(
        "01014af079c9638f4107000102030405060708090a0b0c0d0e0f6553f1006553f178baf6dd771420266f" +
            "f4fb964458bab91206e266c9cb1b61ab8379c534d15e3277645f896a5727d146ea2e7b22d4dd1182e1ca6e933ac618cf6e2c93b2ec8f278e94dc427c324bf996",
    )

    @Test
    fun parsesTheQrOfTheBox() {
        val c = BoxProtocol.parseQr(qr)
        assertNotNull(c)
        assertEquals("VAL-001", c!!.deviceId)
        assertArrayEquals(nonce, c.nonce)
        assertEquals(c, BoxProtocol.parseQr("  $qr\n"))
    }

    @Test
    fun rejectsAnythingElse() {
        listOf(
            "",
            "https://example.com",
            "PB2:VAL-001:AAECAwQFBgcICQoLDA0ODw",
            "PB1:VAL 001:AAECAwQFBgcICQoLDA0ODw",
            "PB1:" + "x".repeat(25) + ":AAECAwQFBgcICQoLDA0ODw",
            "PB1:VAL-001:AAECAwQF",
            "PB1:VAL-001:AAECAwQFBgcICQoLDA0ODw:extra",
            "PB1:VAL-001:AAECAwQFBgcICQoLDA0ODx", // non-canonical last char
            "PB1:VAL-001:AAECAwQFBgcICQoLDA0OD+", // '+' is not base64url
        ).forEach { assertNull(it, BoxProtocol.parseQr(it)) }
    }

    @Test
    fun deviceAndActorTagsMatchTheBackend() {
        // First 8 bytes of SHA-256("VAL-001") and SHA-256("DistMSP:dario"), computed with Node.
        assertEquals("4af079c9638f4107", Hex.encode(BoxProtocol.deviceTag("VAL-001")))
        assertEquals("baf6dd771420266f", Hex.encode(BoxProtocol.actorTag("DistMSP", "dario")))
    }

    @Test
    fun acceptsATokenForThisChallenge() {
        assertNull(BoxProtocol.checkToken(backendToken, BoxProtocol.parseQr(qr)!!))
        assertEquals(1_700_000_000L, BoxProtocol.readU32(backendToken, 26))
        assertEquals(1_700_000_120L, BoxProtocol.readU32(backendToken, 30))
    }

    @Test
    fun catchesTokensTheBoxWouldReject() {
        val c = BoxProtocol.parseQr(qr)!!
        assertEquals(BoxProtocol.TokenProblem.BadLength, BoxProtocol.checkToken(backendToken.copyOf(105), c))
        assertEquals(BoxProtocol.TokenProblem.BadHeader, BoxProtocol.checkToken(backendToken.copyOf().also { it[0] = 2 }, c))
        assertEquals(BoxProtocol.TokenProblem.BadLifetime, BoxProtocol.checkToken(backendToken.copyOf().also { it[32] = 0xFF.toByte() }, c))
        val other = BoxProtocol.parseQr("PB1:VAL-002:AAECAwQFBgcICQoLDA0ODw")!!
        assertEquals(BoxProtocol.TokenProblem.WrongBox, BoxProtocol.checkToken(backendToken, other))
        val newQr = BoxProtocol.parseQr("PB1:VAL-001:AAAAAAAAAAAAAAAAAAAAAA")!!
        assertEquals(BoxProtocol.TokenProblem.WrongNonce, BoxProtocol.checkToken(backendToken, newQr))
    }

    @Test
    fun mapsTheStatusByte() {
        assertEquals(UnlockResult.Ok, UnlockResult.fromCode(0))
        assertEquals(UnlockResult.LockedOut, UnlockResult.fromCode(7))
        assertNull(UnlockResult.fromCode(8))
        assertEquals((0..7).toList(), UnlockResult.entries.map { it.code })
    }

    @Test
    fun hexRoundTrip() {
        assertEquals("00ff10", Hex.encode(Hex.decode("0x00FF10")))
    }

    @Test
    fun coldChainLabels() {
        assertEquals(Verdict.Compromised, Verdict.from("COMPROMISED"))
        assertEquals(Verdict.Monitoring, Verdict.from("MONITORING"))
        assertEquals(Verdict.Pending, Verdict.from(null))
        assertEquals("En tránsito", CargoStates.label("IN_TRANSIT"))
        assertEquals("1 h 05 min", formatDuration(3900))
        assertEquals("3 min 20 s", formatDuration(200))
        assertEquals("45 s", formatDuration(45))
    }
}
