package com.example.calcvault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class VaultCryptoTest {

    @Test
    fun wrapUnwrapRoundTrip() {
        val salt = VaultCrypto.randomBytes(16)
        val dek = VaultCrypto.newDek()
        val kek = VaultCrypto.deriveKek("1984".toCharArray(), salt)
        val wrapped = VaultCrypto.wrapDek(dek, kek)
        val unwrapped = VaultCrypto.unwrapDek(wrapped, kek)
        assertArrayEquals(dek.encoded, unwrapped.encoded)
    }

    @Test
    fun wrongCodeFailsToUnwrap() {
        val salt = VaultCrypto.randomBytes(16)
        val dek = VaultCrypto.newDek()
        val good = VaultCrypto.deriveKek("1984".toCharArray(), salt)
        val bad = VaultCrypto.deriveKek("0000".toCharArray(), salt)
        val wrapped = VaultCrypto.wrapDek(dek, good)
        try {
            VaultCrypto.unwrapDek(wrapped, bad)
            fail("wrong code should not unwrap")
        } catch (e: Exception) {
            // expected (AEADBadTagException)
        }
    }

    @Test
    fun fileRoundTripAcrossChunkBoundary() {
        val dek = VaultCrypto.newDek()
        // 200 KB crosses the 64 KB chunk boundary several times
        val original = VaultCrypto.randomBytes(200_000)
        val enc = ByteArrayOutputStream()
        VaultCrypto.encrypt(dek, "beach.jpg", "image/jpeg", ByteArrayInputStream(original), enc)

        val encBytes = enc.toByteArray()
        // Ciphertext must not contain the plaintext anywhere.
        assertNotEquals("ciphertext must differ from plaintext", original.size, encBytes.size)

        val dec = ByteArrayOutputStream()
        val meta = VaultCrypto.decrypt(dek, ByteArrayInputStream(encBytes), dec)
        assertEquals("beach.jpg", meta.name)
        assertEquals("image/jpeg", meta.mime)
        assertArrayEquals(original, dec.toByteArray())
    }

    @Test
    fun metaOnlyReadsNameWithoutWholeFile() {
        val dek = VaultCrypto.newDek()
        val original = VaultCrypto.randomBytes(500_000)
        val enc = ByteArrayOutputStream()
        VaultCrypto.encrypt(dek, "secret video.mp4", "video/mp4", ByteArrayInputStream(original), enc)

        val meta = VaultCrypto.decryptMeta(dek, ByteArrayInputStream(enc.toByteArray()))
        assertEquals("secret video.mp4", meta.name)
        assertEquals("video/mp4", meta.mime)
    }

    @Test
    fun wrongKeyFailsToDecryptFile() {
        val dek = VaultCrypto.newDek()
        val other = VaultCrypto.newDek()
        val enc = ByteArrayOutputStream()
        VaultCrypto.encrypt(dek, "a.jpg", "image/jpeg", ByteArrayInputStream(VaultCrypto.randomBytes(1000)), enc)
        try {
            VaultCrypto.decrypt(other, ByteArrayInputStream(enc.toByteArray()), ByteArrayOutputStream())
            fail("wrong key should not decrypt")
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun emptyFileRoundTrips() {
        val dek = VaultCrypto.newDek()
        val enc = ByteArrayOutputStream()
        VaultCrypto.encrypt(dek, "empty.bin", "application/octet-stream", ByteArrayInputStream(ByteArray(0)), enc)
        val dec = ByteArrayOutputStream()
        val meta = VaultCrypto.decrypt(dek, ByteArrayInputStream(enc.toByteArray()), dec)
        assertEquals("empty.bin", meta.name)
        assertEquals(0, dec.size())
    }
}
