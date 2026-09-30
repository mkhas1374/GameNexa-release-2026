package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoManager {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "GameNetSecureStorageKey"
    private const val AES_GCM = "AES/GCM/NoPadding"
    

    init {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEY_STORE
                )
                keyGenerator.init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // Silently fallback if hardware KeyStore is restricted or on custom testing runtimes
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val secretKey = getSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(AES_GCM)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                
                val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
                val encStr = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
                "$ivStr:$encStr"
            } else {
                throw IllegalStateException("Android Keystore unavailable")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Secure storage encryption failed", e)
        }
    }

    fun decrypt(encryptedText: String): String {
        if (encryptedText.isBlank()) return ""
        return try {
            if (encryptedText.contains(":")) {
                val parts = encryptedText.split(":")
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                
                val secretKey = getSecretKey()
                if (secretKey != null) {
                    val cipher = Cipher.getInstance(AES_GCM)
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val decryptedBytes = cipher.doFinal(cipherBytes)
                    String(decryptedBytes, Charsets.UTF_8)
                } else {
                    throw IllegalStateException("Secret key unavailable")
                }
            } else {
                throw IllegalStateException("Invalid secure storage payload")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Secure storage decryption failed", e)
        }
    }
}
