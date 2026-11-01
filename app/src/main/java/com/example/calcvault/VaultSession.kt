package com.example.calcvault

import javax.crypto.SecretKey

/**
 * Holds the decrypted master key (DEK) only while the vault is unlocked and only
 * in memory. Locking the vault (or leaving it) clears it, so nothing can be
 * decrypted again without the code or fingerprint.
 */
object VaultSession {
    @Volatile
    var dek: SecretKey? = null
        private set

    val isUnlocked: Boolean get() = dek != null

    fun unlock(key: SecretKey) { dek = key }

    fun lock() { dek = null }
}
