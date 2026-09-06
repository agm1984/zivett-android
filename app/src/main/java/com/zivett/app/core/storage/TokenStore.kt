package com.zivett.app.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/// Where the bearer token lives between launches. The Android Keystore
/// (AES-GCM, device-bound) in the app, memory in tests and previews.
interface TokenStore {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

class InMemoryTokenStore(private var token: String? = null) : TokenStore {
    override fun read(): String? = token
    override fun write(token: String) { this.token = token }
    override fun clear() { token = null }
}

/// The token itself sits in SharedPreferences, but only as AES-GCM
/// ciphertext under a key that never leaves the Android Keystore — the
/// mobile-api contract's "Keystore + encrypted preferences". Backups are
/// off in the manifest, so a token never rides onto another device.
class KeystoreTokenStore(context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences("zivett.auth", Context.MODE_PRIVATE)

    override fun read(): String? {
        val stored = prefs.getString(KEY, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, IV_BYTES)
            val cipherText = raw.copyOfRange(IV_BYTES, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(cipherText).decodeToString()
        } catch (_: Exception) {
            // A key rotated out from under us (device reset, keystore
            // wipe): the token is unrecoverable, so it's gone.
            prefs.edit().remove(KEY).apply()
            null
        }
    }

    override fun write(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(token.encodeToByteArray())
        prefs.edit().putString(KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "zivett-api-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY = "api-token"
        const val IV_BYTES = 12
    }
}
