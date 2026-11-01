package com.example.calcvault

import android.content.Context
import android.net.Uri
import android.util.Base64

/**
 * Small local settings. NOTE: the secret code is never stored here (not even a
 * hash) — the code is verified by successfully decrypting the key file in your
 * folder. This only keeps the chosen folder and the biometric-wrapped copy of
 * the master key.
 */
object SecurityPrefs {
    private const val PREFS = "calc_prefs"
    private const val K_TREE = "tree_uri"
    private const val K_BIO = "bio_enabled"
    private const val K_BIO_IV = "bio_iv"
    private const val K_BIO_DEK = "bio_dek"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun treeUri(ctx: Context): Uri? =
        prefs(ctx).getString(K_TREE, null)?.let { Uri.parse(it) }

    fun setTreeUri(ctx: Context, uri: Uri) {
        prefs(ctx).edit().putString(K_TREE, uri.toString()).apply()
    }

    fun bioEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(K_BIO, false)

    fun setBioEnabled(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(K_BIO, v).apply()
    }

    fun setBioWrap(ctx: Context, iv: ByteArray, wrappedDek: ByteArray) {
        prefs(ctx).edit()
            .putString(K_BIO_IV, b64(iv))
            .putString(K_BIO_DEK, b64(wrappedDek))
            .putBoolean(K_BIO, true)
            .apply()
    }

    fun bioIv(ctx: Context): ByteArray? = prefs(ctx).getString(K_BIO_IV, null)?.let { unb64(it) }
    fun bioDek(ctx: Context): ByteArray? = prefs(ctx).getString(K_BIO_DEK, null)?.let { unb64(it) }

    fun clearBio(ctx: Context) {
        prefs(ctx).edit()
            .remove(K_BIO_IV)
            .remove(K_BIO_DEK)
            .putBoolean(K_BIO, false)
            .apply()
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
