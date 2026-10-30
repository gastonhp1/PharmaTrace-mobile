package ar.pharmatrace.mobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Session(val apiUrl: String, val user: User, val token: String, val expiresAt: Instant) {
    fun isExpired(now: Instant = Instant.now()) = !now.isBefore(expiresAt)
}

/**
 * The server URL (plain) and the session. The session token is encrypted with an AES-GCM key that
 * lives in the Android Keystore and never leaves it; backups exclude these preferences
 * (res/xml/data_extraction_rules.xml).
 */
class SessionStore(context: Context, defaultApiUrl: String) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    var apiUrl: String = prefs.getString(KEY_URL, null) ?: defaultApiUrl
        private set

    fun save(apiUrl: String, login: LoginResponse) {
        val expiresAt = runCatching { Instant.parse(login.expiresAt) }.getOrElse { Instant.now().plusSeconds(3600) }
        prefs.edit()
            .putString(KEY_URL, apiUrl)
            .putString(KEY_USER, login.user.username)
            .putString(KEY_MSP, login.user.mspId)
            .putString(KEY_EXPIRES, expiresAt.toString())
            .putString(KEY_TOKEN, encrypt(login.token))
            .apply()
        this.apiUrl = apiUrl
        _session.value = Session(apiUrl, login.user, login.token, expiresAt)
    }

    /** Forgets the session; keeps the server URL. */
    fun clear() {
        prefs.edit().remove(KEY_USER).remove(KEY_MSP).remove(KEY_EXPIRES).remove(KEY_TOKEN).apply()
        _session.value = null
    }

    /** The token if the session is still valid, for [ApiClient]. */
    fun validToken(): String? = _session.value?.takeUnless { it.isExpired() }?.token

    private fun load(): Session? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val user = prefs.getString(KEY_USER, null) ?: return null
        val msp = prefs.getString(KEY_MSP, null) ?: return null
        val expires = prefs.getString(KEY_EXPIRES, null)?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val token = prefs.getString(KEY_TOKEN, null)?.let { decrypt(it) } ?: return null
        return Session(url, User(user, msp), token, expires).takeUnless { it.isExpired() }
    }

    // ------------------------------------------------------------------ Keystore

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    // Null when the data or the key are gone (app data restored elsewhere, key invalidated):
    // the user simply logs in again.
    private fun decrypt(stored: String): String? = runCatching {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_SIZE))
        String(cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pharmatrace.session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val KEY_URL = "apiUrl"
        const val KEY_USER = "username"
        const val KEY_MSP = "mspId"
        const val KEY_EXPIRES = "expiresAt"
        const val KEY_TOKEN = "token"
    }
}
