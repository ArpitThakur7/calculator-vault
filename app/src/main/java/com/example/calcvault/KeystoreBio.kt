package com.example.calcvault

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed key used ONLY to wrap/unwrap a copy of the master key for
 * fingerprint unlock. The key lives in the Android Keystore, can never be
 * extracted, and every use requires a successful strong-biometric auth
 * (enforced by BiometricPrompt via a CryptoObject).
 *
 * Requires API 30+. On older devices biometric unlock is simply not offered;
 * the secret code still works.
 */
object KeystoreBio {

    private const val ALIAS = "calcvault_bio_dek"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private fun keystore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun hasKey(): Boolean = try {
        keystore().containsAlias(ALIAS)
    } catch (e: Exception) {
        false
    }

    fun deleteKey() {
        try {
            keystore().deleteEntry(ALIAS)
        } catch (e: Exception) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun createKey(): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    private fun getKey(): SecretKey? =
        (keystore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    /** Cipher to authorize with biometrics, then use to encrypt the DEK. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun encryptCipher(): Cipher {
        deleteKey()
        val key = createKey()
        return Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** Cipher to authorize with biometrics, then use to decrypt the stored DEK. */
    fun decryptCipher(iv: ByteArray): Cipher {
        val key = getKey() ?: throw IllegalStateException("no biometric key")
        return Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
    }
}
