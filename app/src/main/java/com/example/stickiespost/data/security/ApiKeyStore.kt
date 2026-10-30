package com.example.stickiespost.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun get(): String? {
        val encoded = preferences.getString(KEY_VALUE, null) ?: return null
        val stored = Base64.decode(encoded, Base64.NO_WRAP)
        require(stored.size > GCM_IV_LENGTH_BYTES) { "Stored Gemini key is invalid. Please save it again." }
        val iv = stored.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encrypted = stored.copyOfRange(GCM_IV_LENGTH_BYTES, stored.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun save(apiKey: String) {
        require(apiKey.isNotBlank()) { "Enter a Gemini API key." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.iv + cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        val saved = preferences.edit()
            .putString(KEY_VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        if (!saved) throw IOException("Could not save the Gemini API key.")
    }

    fun clear() {
        val saved = preferences.edit().remove(KEY_VALUE).commit()
        if (!saved) throw IOException("Could not remove the Gemini API key.")
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_gemini_settings"
        const val KEY_VALUE = "encrypted_api_key"
        const val KEY_ALIAS = "stickiespost_gemini_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
