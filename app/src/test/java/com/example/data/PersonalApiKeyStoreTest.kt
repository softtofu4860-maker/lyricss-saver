package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64

/** Exercises migration/storage with real AES-GCM; hardware AndroidKeyStore needs a device. */
internal class TestAesCipher : KeyCipher {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()))
    }
    override fun decrypt(value: String): String {
        val bytes = Base64.getDecoder().decode(value)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class PersonalApiKeyStoreTest {
    @Test fun `legacy plaintext migrates to authenticated encryption then is deleted and user can remove key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = context.getSharedPreferences("screensaver_prefs", 0)
        val encrypted = context.getSharedPreferences("personal_api_key", 0)
        encrypted.edit().clear().commit()
        legacy.edit().putString("gemini_api_key", "private-key").commit()
        val cipher = TestAesCipher()
        val store = PersonalApiKeyStore(context, cipher)
        assertEquals("private-key", store.get())
        assertFalse(legacy.contains("gemini_api_key"))
        assertFalse(encrypted.getString("ciphertext", "")!!.contains("private-key"))
        assertEquals("private-key", PersonalApiKeyStore(context, cipher).get())
        store.set("new-key")
        assertEquals("new-key", store.get())
        store.delete()
        assertNull(store.get())
        assertTrue(encrypted.all.isEmpty())
        assertFalse(legacy.contains("gemini_api_key"))
    }
    @Test fun `failed encryption never deletes the only legacy copy or silently saves plaintext`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("personal_api_key", 0).edit().clear().commit()
        val legacy = context.getSharedPreferences("screensaver_prefs", 0)
        legacy.edit().putString("gemini_api_key", "old-key").commit()
        val store = PersonalApiKeyStore(context, object : KeyCipher {
            override fun encrypt(value: String): String = error("keystore unavailable")
            override fun decrypt(value: String): String = error("unavailable")
        })
        assertThrows(IllegalStateException::class.java) { store.get() }
        assertEquals("old-key", legacy.getString("gemini_api_key", null))
        assertTrue(context.getSharedPreferences("personal_api_key", 0).all.isEmpty())
        store.delete()
    }
}
