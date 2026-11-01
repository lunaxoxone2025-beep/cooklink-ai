package com.cooklink.ai.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores AI API keys encrypted with an AES-256-GCM key that lives in the Android
 * Keystore (hardware-backed where the device supports it). The key material never
 * leaves the Keystore; only ciphertext is written to SharedPreferences, and the
 * prefs file is excluded from backups (allowBackup=false).
 */
class SecureKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences("secure_keys", Context.MODE_PRIVATE)
    private val _configured = MutableStateFlow(AiProvider.entries.associateWith { has(it) })

    /** Which providers currently have a key saved (never exposes the key itself). */
    val configured: StateFlow<Map<AiProvider, Boolean>> = _configured.asStateFlow()

    fun has(provider: AiProvider): Boolean = prefs.contains(provider.name)

    fun save(provider: AiProvider, apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        prefs.edit().putString(provider.name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        refresh()
    }

    /** Returns the decrypted key, or null if none is saved or it can't be decrypted. */
    fun get(provider: AiProvider): String? {
        val stored = prefs.getString(provider.name, null) ?: return null
        return runCatching {
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, IV_SIZE)
            val data = packed.copyOfRange(IV_SIZE, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clear(provider: AiProvider) {
        prefs.edit().remove(provider.name).apply()
        refresh()
    }

    private fun refresh() {
        _configured.value = AiProvider.entries.associateWith { has(it) }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
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
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "cooklink_api_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
