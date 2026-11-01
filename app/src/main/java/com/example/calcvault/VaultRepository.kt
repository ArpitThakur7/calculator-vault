package com.example.calcvault

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.crypto.SecretKey

data class VaultItem(
    val uri: Uri,          // file:// uri of the encrypted blob on disk
    val name: String,      // real (decrypted) display name
    val isVideo: Boolean,
    val size: Long,
    val lastModified: Long
)

/** A photo/video on the device, shown in the in-app picker. */
data class DeviceMedia(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean
)

/**
 * File handling with encryption, using a hidden folder the app creates and
 * manages itself — NO system folder picker.
 *
 * Location: the app's own external folder
 * (Android/data/com.example.calcvault/files/vault), plus a .nomedia marker.
 * The Gallery and other apps never index or read it, and it needs NO storage
 * permission and NO folder picker. Every file is AES-256-GCM encrypted via
 * [VaultCrypto]; on disk it has a random name and scrambled bytes.
 *
 * Trade-off: this folder is removed if the app is uninstalled.
 */
class VaultRepository(private val ctx: Context) {

    data class KeyFile(val salt: ByteArray, val wrappedDek: ByteArray)

    private val cr get() = ctx.contentResolver

    private val vaultDir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "vault")
    private val trashDir = File(vaultDir, "trash")
    private val keyFile = File(vaultDir, ".vaultkey")
    private val reserved = setOf(".nomedia", ".vaultkey")

    val folderPath: String get() = vaultDir.absolutePath

    fun hasStorageAccess(): Boolean = true

    fun ensureStructure() {
        try {
            if (!vaultDir.exists()) vaultDir.mkdirs()
            val nomedia = File(vaultDir, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
            if (!trashDir.exists()) trashDir.mkdirs()
        } catch (_: Exception) {
        }
    }

    /* ----------------------------- key file ----------------------------- */

    fun keyFileExists(): Boolean = keyFile.exists()

    fun readKeyFile(): KeyFile? {
        if (!keyFile.exists()) return null
        return try {
            val text = keyFile.readText()
            var salt: ByteArray? = null
            var dek: ByteArray? = null
            for (line in text.lineSequence()) {
                val t = line.trim()
                when {
                    t.startsWith("salt=") -> salt = Base64.decode(t.removePrefix("salt="), Base64.NO_WRAP)
                    t.startsWith("dek=") -> dek = Base64.decode(t.removePrefix("dek="), Base64.NO_WRAP)
                }
            }
            if (salt != null && dek != null) KeyFile(salt, dek) else null
        } catch (e: Exception) {
            null
        }
    }

    fun writeKeyFile(salt: ByteArray, wrappedDek: ByteArray): Boolean {
        return try {
            ensureStructure()
            val text = buildString {
                append("v1\n")
                append("salt=").append(Base64.encodeToString(salt, Base64.NO_WRAP)).append('\n')
                append("dek=").append(Base64.encodeToString(wrappedDek, Base64.NO_WRAP)).append('\n')
            }
            keyFile.writeText(text)
            true
        } catch (e: Exception) {
            false
        }
    }

    /* ----------------------------- listing ----------------------------- */

    fun listVault(): List<VaultItem> = listDir(vaultDir, includeSubdirs = false)
    fun listTrash(): List<VaultItem> = listDir(trashDir, includeSubdirs = false)

    private fun listDir(dir: File, includeSubdirs: Boolean): List<VaultItem> {
        val dek = VaultSession.dek ?: return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && it.name !in reserved }
            .mapNotNull { f ->
                try {
                    val meta = FileInputStream(f).use { VaultCrypto.decryptMeta(dek, it) }
                    VaultItem(Uri.fromFile(f), meta.name, meta.mime.startsWith("video"), f.length(), f.lastModified())
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.lastModified }
    }

    private fun fileOf(item: VaultItem): File = File(item.uri.path!!)

    /* ----------------------------- import ----------------------------- */

    fun import(sources: List<Uri>): Int {
        val dek = VaultSession.dek ?: return 0
        ensureStructure()
        var count = 0
        for (src in sources) {
            try {
                val mime = cr.getType(src) ?: "application/octet-stream"
                val name = queryName(src) ?: "file_${System.currentTimeMillis()}"
                val dest = File(vaultDir, UUID.randomUUID().toString() + ".enc")
                cr.openInputStream(src)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        VaultCrypto.encrypt(dek, name, mime, input, output)
                    }
                }
                count++
            } catch (_: Exception) {
            }
        }
        return count
    }

    /* ----------------------- device gallery (for picker) ----------------------- */

    fun queryDeviceMedia(limit: Int = 2000): List<DeviceMedia> {
        val out = ArrayList<DeviceMedia>()
        val collection = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val order = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        try {
            cr.query(collection, proj, sel, args, order)?.use { c ->
                val idI = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameI = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val typeI = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(idI)
                    val isVid = c.getInt(typeI) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    out.add(
                        DeviceMedia(
                            ContentUris.withAppendedId(collection, id),
                            c.getString(nameI) ?: "item",
                            isVid
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun deviceThumb(uri: Uri, target: Int = 256): Bitmap? {
        // Prefer the fast system thumbnail, but fall back to decoding the file
        // ourselves — some HyperOS media entries make loadThumbnail fail.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return cr.loadThumbnail(uri, Size(target, target), null)
            } catch (_: Exception) {
            }
        }
        return try {
            val baos = ByteArrayOutputStream()
            cr.openInputStream(uri)?.use { it.copyTo(baos) }
            decodeSampled(baos.toByteArray(), target)
        } catch (e: Exception) {
            null
        }
    }

    /** Decrypts an item and writes a normal, visible copy back into the gallery. */
    fun exportToGallery(item: VaultItem): Boolean {
        val dek = VaultSession.dek ?: return false
        val file = fileOf(item)
        return try {
            val meta = FileInputStream(file).use { VaultCrypto.decryptMeta(dek, it) }
            val isVideo = meta.mime.startsWith("video")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, meta.name)
                put(MediaStore.MediaColumns.MIME_TYPE, meta.mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        if (isVideo) "Movies/Calculator" else "Pictures/Calculator"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val dest = cr.insert(collection, values) ?: return false
            cr.openOutputStream(dest)?.use { out ->
                FileInputStream(file).use { input -> VaultCrypto.decrypt(dek, input, out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                cr.update(dest, values, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun queryName(uri: Uri): String? {
        var name: String? = null
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) name = c.getString(idx)
            }
        } catch (_: Exception) {
        }
        return name
    }

    /* ----------------------------- trash ----------------------------- */

    fun moveToTrash(item: VaultItem): Boolean {
        ensureStructure()
        val src = fileOf(item)
        val dest = File(trashDir, src.name)
        return moveFile(src, dest)
    }

    fun restore(item: VaultItem): Boolean {
        ensureStructure()
        val src = fileOf(item)
        val dest = File(vaultDir, src.name)
        return moveFile(src, dest)
    }

    private fun moveFile(src: File, dest: File): Boolean {
        return try {
            if (src.renameTo(dest)) return true
            FileInputStream(src).use { i -> FileOutputStream(dest).use { o -> i.copyTo(o) } }
            src.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun deleteForever(item: VaultItem): Boolean =
        try {
            fileOf(item).delete()
        } catch (e: Exception) {
            false
        }

    fun emptyTrash() {
        trashDir.listFiles()?.forEach { try { it.delete() } catch (_: Exception) {} }
    }

    /* --------------------------- decrypt / view --------------------------- */

    fun decodeThumb(item: VaultItem, target: Int = 300): Bitmap? = decode(item, target)
    fun decodeFull(item: VaultItem): Bitmap? = decode(item, 1600)

    /** Decrypts an item to a temporary file in app cache (for video playback). */
    fun decryptToCache(item: VaultItem): File? {
        val dek = VaultSession.dek ?: return null
        val file = fileOf(item)
        return try {
            val meta = FileInputStream(file).use { VaultCrypto.decryptMeta(dek, it) }
            val ext = meta.mime.substringAfterLast('/', "dat").ifBlank { "dat" }
            val tmp = File(ctx.cacheDir, "play_${System.nanoTime()}.$ext")
            FileInputStream(file).use { input ->
                FileOutputStream(tmp).use { out -> VaultCrypto.decrypt(dek, input, out) }
            }
            tmp
        } catch (e: Exception) {
            null
        }
    }

    private fun decode(item: VaultItem, target: Int): Bitmap? {
        val dek = VaultSession.dek ?: return null
        val file = fileOf(item)
        return try {
            if (item.isVideo) {
                val tmp = File(ctx.cacheDir, "v_${System.nanoTime()}.tmp")
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(tmp).use { out -> VaultCrypto.decrypt(dek, input, out) }
                    }
                    videoFrame(tmp)
                } finally {
                    tmp.delete()
                }
            } else {
                val baos = ByteArrayOutputStream()
                FileInputStream(file).use { input -> VaultCrypto.decrypt(dek, input, baos) }
                decodeSampled(baos.toByteArray(), target)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampled(bytes: ByteArray, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calcSample(bounds.outWidth, bounds.outHeight, target)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun videoFrame(file: File): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.getFrameAtTime(0)
        } catch (e: Exception) {
            null
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun calcSample(w: Int, h: Int, target: Int): Int {
        if (w <= 0 || h <= 0) return 1
        var s = 1
        val max = maxOf(w, h)
        while (max / s > target) s *= 2
        return s
    }
}
