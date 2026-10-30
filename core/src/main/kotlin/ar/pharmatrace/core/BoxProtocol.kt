package ar.pharmatrace.core

import java.security.MessageDigest
import java.util.Base64

/**
 * The PharmaBox side of the authenticated opening, as defined in Pharmatrace-firmware
 * (docs/unlock-protocol.md, lib/pharma_proto/src/pharma_unlock.h).
 */
object BoxProtocol {
    const val SERVICE_UUID = "f30e1410-8085-4552-9d03-abe1d447ce2d"
    const val UNLOCK_CHAR_UUID = "e7cd3dce-1611-4770-beb3-8d88cb840550"
    const val STATUS_CHAR_UUID = "71b4af6b-3c8f-4880-96e3-df64ba0b4459"

    const val TOKEN_SIZE = 106
    const val NONCE_SIZE = 16
    const val MAX_LIFETIME_S = 300L

    private val DEVICE_ID = Regex("^[A-Za-z0-9_-]{1,24}$")
    private val NONCE_TEXT = Regex("^[A-Za-z0-9_-]{22}$")

    /** What the box shows on its e-paper: `PB1:<deviceId>:<nonce base64url>`. */
    class Challenge(val deviceId: String, val nonce: ByteArray, val raw: String) {
        override fun equals(other: Any?) =
            other is Challenge && other.deviceId == deviceId && other.nonce.contentEquals(nonce)

        override fun hashCode() = 31 * deviceId.hashCode() + nonce.contentHashCode()
        override fun toString() = "Challenge($deviceId)"
    }

    /** Returns the challenge, or null when the text is not a PharmaBox QR (any other code scanned). */
    fun parseQr(text: String): Challenge? {
        val parts = text.trim().split(":")
        if (parts.size != 3 || parts[0] != "PB1") return null
        val (_, deviceId, nonceText) = parts
        if (!DEVICE_ID.matches(deviceId) || !NONCE_TEXT.matches(nonceText)) return null
        val nonce = try {
            Base64.getUrlDecoder().decode(nonceText)
        } catch (e: IllegalArgumentException) {
            return null
        }
        // Reject non-canonical encodings (trailing bits set): the backend does the same.
        if (nonce.size != NONCE_SIZE || Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) != nonceText) return null
        return Challenge(deviceId, nonce, text.trim())
    }

    fun deviceTag(deviceId: String): ByteArray = sha256(deviceId.toByteArray(Charsets.UTF_8)).copyOf(8)

    fun actorTag(mspId: String, username: String): ByteArray =
        sha256("$mspId:$username".toByteArray(Charsets.UTF_8)).copyOf(8)

    /** Why a token is not worth sending to the box. Checked before writing it over BLE, because
     *  every rejected write costs one of the box's three attempts for that QR. */
    enum class TokenProblem { BadLength, BadHeader, BadLifetime, WrongBox, WrongNonce }

    /** Structural check of a token from the backend against the challenge it was asked for.
     *  The signature is the box's job: the app does not have (or need) the public key. */
    fun checkToken(token: ByteArray, challenge: Challenge): TokenProblem? {
        if (token.size != TOKEN_SIZE) return TokenProblem.BadLength
        if (token[0].toInt() != 1 || token[1].toInt() != 1) return TokenProblem.BadHeader
        val iat = readU32(token, 26)
        val exp = readU32(token, 30)
        if (exp <= iat || exp - iat > MAX_LIFETIME_S) return TokenProblem.BadLifetime
        if (!token.copyOfRange(2, 10).contentEquals(deviceTag(challenge.deviceId))) return TokenProblem.WrongBox
        if (!token.copyOfRange(10, 26).contentEquals(challenge.nonce)) return TokenProblem.WrongNonce
        return null
    }

    fun readU32(buf: ByteArray, off: Int): Long =
        ((buf[off].toLong() and 0xFF) shl 24) or ((buf[off + 1].toLong() and 0xFF) shl 16) or
            ((buf[off + 2].toLong() and 0xFF) shl 8) or (buf[off + 3].toLong() and 0xFF)

    private fun sha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
}

/** The byte the box notifies on the `status` characteristic (pharma::UnlockResult). */
enum class UnlockResult(val code: Int, val message: String, val retryWithNewQr: Boolean) {
    Ok(0, "Caja abierta", false),
    NoChallenge(1, "La caja no tiene un QR vigente: apretá el botón para generar uno nuevo", true),
    BadFormat(2, "La caja rechazó el formato del token", true),
    WrongBox(3, "El token es de otra caja", true),
    BadNonce(4, "El QR ya no es el que muestra la caja: escaneá el nuevo", true),
    Expired(5, "El token venció: pedí uno nuevo", true),
    BadSignature(6, "La caja no reconoce la firma del servidor (¿clave de apertura distinta?)", false),
    LockedOut(7, "Demasiados intentos: apretá el botón de la caja para un QR nuevo", true),
    ;

    companion object {
        fun fromCode(code: Int): UnlockResult? = entries.firstOrNull { it.code == code }
    }
}

object Hex {
    fun decode(text: String): ByteArray {
        val clean = text.removePrefix("0x")
        require(clean.length % 2 == 0 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "not hex" }
        return ByteArray(clean.length / 2) { i -> clean.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }

    fun encode(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
