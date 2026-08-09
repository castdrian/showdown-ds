package dev.adrian.showdown

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ShowdownCredentials(val username: String, val password: String)

class ShowdownCredentialsStore(context: Context) {
    private val preferences = context.getSharedPreferences("showdown_credentials", Context.MODE_PRIVATE)

    fun load(): ShowdownCredentials? {
        val username = preferences.getString("username", null) ?: return null
        val encrypted = preferences.getString("password", null) ?: return null
        return decrypt(encrypted)?.let { ShowdownCredentials(username, it) }
    }

    fun save(credentials: ShowdownCredentials) {
        preferences.edit().putString("username", credentials.username.trim()).putString("password", encrypt(credentials.password)).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray())
        val payload = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val payload = ByteBuffer.wrap(Base64.decode(value, Base64.NO_WRAP))
        val iv = ByteArray(payload.get().toInt())
        payload.get(iv)
        val encrypted = ByteArray(payload.remaining())
        payload.get(encrypted)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted))
    }.getOrNull()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "showdown_credentials_v1"
    }
}
