package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface KeyCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

private class DeviceKeyCipher : KeyCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    override fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size >= 28)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    companion object { private const val ALIAS = "lyrics.personal-gemini-key.v1" }
}

/** Commit encrypted data before deleting a legacy plaintext value; never log key contents. */
internal class PersonalApiKeyStore(context: Context, private val cipher: KeyCipher = DeviceKeyCipher()) {
    private val encrypted = context.applicationContext.getSharedPreferences("personal_api_key", Context.MODE_PRIVATE)
    private val legacy = context.applicationContext.getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE)
    fun get(): String? = synchronized(LOCK) {
        encrypted.getString("ciphertext", null)?.let {
            val value = cipher.decrypt(it)
            check(legacy.edit().remove("gemini_api_key").commit())
            return value.takeIf(String::isNotBlank)
        }
        val previous = legacy.getString("gemini_api_key", null)?.trim()?.takeIf(String::isNotBlank) ?: return null
        set(previous)
        return previous
    }
    fun set(value: String): Unit = synchronized(LOCK) {
        val key = value.trim()
        if (key.isBlank()) { delete(); return }
        val ciphertext = cipher.encrypt(key)
        check(cipher.decrypt(ciphertext) == key)
        check(encrypted.edit().putString("ciphertext", ciphertext).commit())
        check(legacy.edit().remove("gemini_api_key").commit())
    }
    fun delete(): Unit = synchronized(LOCK) {
        check(legacy.edit().remove("gemini_api_key").commit())
        check(encrypted.edit().clear().commit())
    }
    companion object { private val LOCK = Any() }
}
