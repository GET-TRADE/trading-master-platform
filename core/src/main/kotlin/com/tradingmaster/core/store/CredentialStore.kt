package com.tradingmaster.core.store

import android.content.Context
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed AES-GCM credential storage.
 * Binance API secrets never touch the database, logs, or any server.
 * The Keystore key is non-exportable and hardware-backed where available.
 */
class CredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences("tm_secure_prefs", Context.MODE_PRIVATE)
    private val keyAlias = "tm_binance_master_key"
    private val ctx = context.applicationContext

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyPropertiesAlgorithm, "AndroidKeyStore")
        gen.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                keyAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return ByteArray(1 + iv.size + ct.size).also {
            it[0] = iv.size.toByte()
            iv.copyInto(it, 1)
            ct.copyInto(it, 1 + iv.size)
        }
    }

    private fun decrypt(blob: ByteArray): String {
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ct = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    fun saveBinanceCredentials(apiKey: String, apiSecret: String) {
        prefs.edit()
            .putString(K_API, encrypt(apiKey).joinToString(",") { it.toString() })
            .putString(K_SECRET, encrypt(apiSecret).joinToString(",") { it.toString() })
            .putLong(K_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun hasBinanceCredentials(): Boolean =
        prefs.contains(K_API) && prefs.contains(K_SECRET)

    fun binanceApiKey(): String? = load(K_API)
    fun binanceApiSecret(): String? = load(K_SECRET)

    fun clearBinanceCredentials() {
        prefs.edit().remove(K_API).remove(K_SECRET).remove(K_SAVED_AT).apply()
    }

    private fun load(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            decrypt(raw.split(",").map { it.toByte() }.toByteArray())
        } catch (e: Exception) {
            null // Keystore key may have been invalidated (device change/clear) — fail closed
        }
    }

    companion object {
        private const val K_API = "bk_api"
        private const val K_SECRET = "bk_secret"
        private const val K_SAVED_AT = "bk_saved_at"
        private const val KeyPropertiesAlgorithm = "AES"
    }
}
