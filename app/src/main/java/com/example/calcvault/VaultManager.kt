package com.example.calcvault

import android.content.Context
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Orchestrates unlocking: creating a vault, opening it with the code, changing
 * the code, and the biometric wrap/unwrap of the master key.
 *
 * The key file (salt + code-wrapped DEK) lives in your folder via
 * [VaultRepository], so it survives uninstall. The biometric-wrapped copy lives
 * in the Android Keystore + app settings and is only a convenience.
 */
object VaultManager {

    fun vaultExists(repo: VaultRepository): Boolean = repo.keyFileExists()

    /** First-time: make a new master key and write the key file. */
    fun createVault(repo: VaultRepository, code: String): Boolean {
        return try {
            val salt = VaultCrypto.randomBytes(16)
            val dek = VaultCrypto.newDek()
            val kek = VaultCrypto.deriveKek(code.toCharArray(), salt)
            val wrapped = VaultCrypto.wrapDek(dek, kek)
            if (!repo.writeKeyFile(salt, wrapped)) return false
            repo.ensureStructure()
            VaultSession.unlock(dek)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Open an existing vault with the code. Returns false if the code is wrong. */
    fun attachWithCode(repo: VaultRepository, code: String): Boolean {
        val kf = repo.readKeyFile() ?: return false
        return try {
            val kek = VaultCrypto.deriveKek(code.toCharArray(), kf.salt)
            val dek = VaultCrypto.unwrapDek(kf.wrappedDek, kek) // throws if wrong code
            VaultSession.unlock(dek)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Re-wrap the current master key under a new code (vault must be unlocked). */
    fun changeCode(repo: VaultRepository, newCode: String): Boolean {
        val dek = VaultSession.dek ?: return false
        return try {
            val salt = VaultCrypto.randomBytes(16)
            val kek = VaultCrypto.deriveKek(newCode.toCharArray(), salt)
            repo.writeKeyFile(salt, VaultCrypto.wrapDek(dek, kek))
        } catch (e: Exception) {
            false
        }
    }

    /** Store a biometric-unlockable copy of the master key (vault must be unlocked). */
    fun storeBioWrap(ctx: Context, authedCipher: Cipher): Boolean {
        val dek = VaultSession.dek ?: return false
        return try {
            val ct = authedCipher.doFinal(dek.encoded)
            SecurityPrefs.setBioWrap(ctx, authedCipher.iv, ct)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Recover the master key from the biometric-wrapped copy. */
    fun bioUnlock(ctx: Context, authedCipher: Cipher): Boolean {
        val ct = SecurityPrefs.bioDek(ctx) ?: return false
        return try {
            val raw = authedCipher.doFinal(ct)
            VaultSession.unlock(SecretKeySpec(raw, "AES"))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun disableBio(ctx: Context) {
        KeystoreBio.deleteKey()
        SecurityPrefs.clearBio(ctx)
    }
}
