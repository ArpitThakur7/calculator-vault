package com.example.calcvault

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-JCE cryptography (no Android APIs) so it can be unit-tested on the JVM.
 *
 * Envelope encryption:
 *   - A random 256-bit Data Encryption Key (DEK) encrypts your files.
 *   - The DEK is itself wrapped (encrypted) by a Key-Encryption-Key (KEK)
 *     derived from your secret code via PBKDF2. The wrapped DEK is what we
 *     store in the folder, so it survives uninstall and is useless without
 *     your code.
 *
 * File format (per media file):
 *   MAGIC(4) | headerLen(4) | header[ iv(12) | GCM(name,mime) ] |
 *   mediaIvBase(12) | { chunkLen(4) | GCM(chunk) }*
 *   Each 64 KB chunk uses a nonce = base with a 32-bit counter in the last
 *   4 bytes, and the counter as AAD, so chunks can't be reordered or dropped.
 *   Streaming in chunks keeps memory bounded even for large videos.
 */
object VaultCrypto {

    private const val KDF_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val CHUNK = 64 * 1024
    private const val GCM_TAG_BITS = 128
    private val MAGIC = byteArrayOf('C'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte())
    private val rng = SecureRandom()

    data class Meta(val name: String, val mime: String)

    /* --------------------------- keys --------------------------- */

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    fun newDek(): SecretKey = SecretKeySpec(randomBytes(KEY_BITS / 8), "AES")

    fun deriveKek(code: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(code, salt, KDF_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    /** Wrap (encrypt) the DEK with the code-derived KEK. */
    fun wrapDek(dek: SecretKey, kek: SecretKey): ByteArray {
        val iv = randomBytes(12)
        return iv + gcm(Cipher.ENCRYPT_MODE, kek, iv, null, dek.encoded)
    }

    /** Unwrap the DEK. Throws if the code (hence KEK) is wrong. */
    fun unwrapDek(blob: ByteArray, kek: SecretKey): SecretKey {
        val iv = blob.copyOfRange(0, 12)
        val ct = blob.copyOfRange(12, blob.size)
        return SecretKeySpec(gcm(Cipher.DECRYPT_MODE, kek, iv, null, ct), "AES")
    }

    /* ------------------------- file streams ------------------------- */

    fun encrypt(dek: SecretKey, name: String, mime: String, input: InputStream, output: OutputStream) {
        val out = DataOutputStream(BufferedOutputStream(output))
        out.write(MAGIC)

        val metaPlain = ByteArrayOutputStream().also {
            DataOutputStream(it).apply { writeUTF(name); writeUTF(mime) }
        }.toByteArray()
        val metaIv = randomBytes(12)
        val header = metaIv + gcm(Cipher.ENCRYPT_MODE, dek, metaIv, null, metaPlain)
        out.writeInt(header.size)
        out.write(header)

        val base = randomBytes(12)
        out.write(base)

        val buf = ByteArray(CHUNK)
        var counter = 0
        while (true) {
            val n = fill(input, buf)
            if (n == 0) break
            val plain = if (n == buf.size) buf else buf.copyOf(n)
            val ct = gcm(Cipher.ENCRYPT_MODE, dek, nonce(base, counter), intBytes(counter), plain)
            out.writeInt(ct.size)
            out.write(ct)
            counter++
            if (n < CHUNK) break
        }
        out.flush()
        out.close()
    }

    /** Decrypts the media payload to [output] and returns the file metadata. */
    fun decrypt(dek: SecretKey, input: InputStream, output: OutputStream): Meta {
        val inp = DataInputStream(BufferedInputStream(input))
        val meta = readHeader(dek, inp)
        val base = ByteArray(12).also { inp.readFully(it) }
        var counter = 0
        while (true) {
            val len = try {
                inp.readInt()
            } catch (e: EOFException) {
                break
            }
            val ct = ByteArray(len).also { inp.readFully(it) }
            output.write(gcm(Cipher.DECRYPT_MODE, dek, nonce(base, counter), intBytes(counter), ct))
            counter++
        }
        output.flush()
        return meta
    }

    /** Reads only the (small) metadata header — name + mime — without the media. */
    fun decryptMeta(dek: SecretKey, input: InputStream): Meta {
        val inp = DataInputStream(BufferedInputStream(input))
        return readHeader(dek, inp)
    }

    private fun readHeader(dek: SecretKey, inp: DataInputStream): Meta {
        val magic = ByteArray(4).also { inp.readFully(it) }
        require(magic.contentEquals(MAGIC)) { "not a vault file" }
        val headerLen = inp.readInt()
        require(headerLen in 13..4096) { "bad header" }
        val header = ByteArray(headerLen).also { inp.readFully(it) }
        val metaIv = header.copyOfRange(0, 12)
        val metaCt = header.copyOfRange(12, header.size)
        val metaPlain = gcm(Cipher.DECRYPT_MODE, dek, metaIv, null, metaCt)
        val md = DataInputStream(ByteArrayInputStream(metaPlain))
        return Meta(md.readUTF(), md.readUTF())
    }

    /* --------------------------- helpers --------------------------- */

    private fun gcm(mode: Int, key: SecretKey, iv: ByteArray, aad: ByteArray?, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(data)
    }

    private fun nonce(base: ByteArray, counter: Int): ByteArray {
        val n = base.copyOf(12)
        n[8] = (counter ushr 24).toByte()
        n[9] = (counter ushr 16).toByte()
        n[10] = (counter ushr 8).toByte()
        n[11] = counter.toByte()
        return n
    }

    private fun intBytes(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun fill(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }
}
