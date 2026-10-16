package com.nuvio.app.core.avix

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal actual object AvixSecretStore {
    private const val PREFERENCES_NAME = "avix_secrets"
    private const val KEY_ALIAS = "com.avix.nuvio.secrets.v1"
    private var preferences: SharedPreferences? = null
    private val lock = Any()

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    actual val isSupported: Boolean = true

    actual fun get(key: String): String? = synchronized(lock) {
        val prefs = preferences ?: return@synchronized null
        val stored = prefs.getString(key, null) ?: return@synchronized null
        runCatching {
            val parts = stored.split('.', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, decode(parts[0])))
            cipher.updateAAD("$KEY_ALIAS:$key".toByteArray(Charsets.UTF_8))
            cipher.doFinal(decode(parts[1])).toString(Charsets.UTF_8)
        }.getOrElse {
            // Unreadable (e.g. Keystore key lost after a restore): drop it; the user re-enters it.
            prefs.edit().remove(key).apply()
            null
        }
    }

    actual fun put(key: String, value: String?) = synchronized(lock) {
        val prefs = preferences ?: return@synchronized
        if (value.isNullOrEmpty()) {
            prefs.edit().remove(key).apply()
            return@synchronized
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD("$KEY_ALIAS:$key".toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, "${encode(cipher.iv)}.${encode(encrypted)}").apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
