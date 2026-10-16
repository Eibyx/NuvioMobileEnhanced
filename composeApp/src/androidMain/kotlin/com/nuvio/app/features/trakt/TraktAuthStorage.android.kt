package com.nuvio.app.features.trakt

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nuvio.app.core.storage.ProfileScopedKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Trakt tokens and the pending PKCE verifier are stored encrypted with an AES-GCM key held in the
 * Android Keystore (same scheme as the AniList/MAL/MDBList/Simkl credential stores). Payloads
 * written by older builds in plain text are read once and immediately re-written encrypted.
 */
internal actual object TraktAuthStorage {
    private const val preferencesName = "nuvio_trakt_auth"
    private const val payloadKey = "trakt_auth_payload"
    private const val keyAlias = "com.nuvio.media.trakt.credentials.v1"
    private const val transformation = "AES/GCM/NoPadding"
    private const val gcmTagBits = 128

    private var preferences: SharedPreferences? = null
    private val lock = Any()

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(profileId: Int): String? = synchronized(lock) {
        val prefs = preferences ?: return@synchronized null
        val key = ProfileScopedKey.of(payloadKey, profileId)
        val stored = prefs.getString(key, null) ?: return@synchronized null
        if (stored.trimStart().startsWith("{")) {
            // Legacy plain-text payload: migrate it to encrypted storage.
            runCatching { prefs.edit().putString(key, encrypt(stored, key)).commit() }
            return@synchronized stored
        }
        runCatching { decrypt(stored, key) }
            .onFailure { prefs.edit().remove(key).commit() }
            .getOrNull()
    }

    actual fun savePayload(profileId: Int, payload: String) = synchronized(lock) {
        val prefs = preferences ?: return@synchronized
        val key = ProfileScopedKey.of(payloadKey, profileId)
        // Never fall back to plain text: if the Keystore is unusable the session is kept in memory only.
        val encrypted = runCatching { encrypt(payload, key) }.getOrNull() ?: return@synchronized
        prefs.edit().putString(key, encrypted).apply()
    }

    actual fun removeProfile(profileId: Int) = synchronized(lock) {
        preferences
            ?.edit()
            ?.remove(ProfileScopedKey.of(payloadKey, profileId))
            ?.apply()
        Unit
    }

    private fun encrypt(value: String, key: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD("$keyAlias:$key".toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return "${encode(cipher.iv)}.${encode(ciphertext)}"
    }

    private fun decrypt(value: String, key: String): String {
        val parts = value.split('.', limit = 2)
        require(parts.size == 2) { "Invalid encrypted Trakt credential" }
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(gcmTagBits, decode(parts[0])))
        cipher.updateAAD("$keyAlias:$key".toByteArray(Charsets.UTF_8))
        return cipher.doFinal(decode(parts[1])).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
