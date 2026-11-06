package com.tim.articlequotes.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys (Claude, ESV, optional Claude Admin) encrypted with a key held in the Android
 * Keystore. The ciphertext lives in its own preferences file, which is excluded from backup,
 * so keys never leave the phone and never appear in logs.
 */
class SecretStore(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    /** Saves (or removes) a key. Returns false if the phone's keystore refused, so Settings can say so. */
    fun put(name: String, value: String?): Boolean {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) { sp.edit().remove(name).apply(); return true }
        fun encrypt(): String {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val enc = c.doFinal(v.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
        }
        // A broken keystore entry (e.g. after a restore) gets replaced once; other saved keys then need re-entering.
        val blob = runCatching { encrypt() }.recoverCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
            encrypt()
        }.getOrNull() ?: return false
        sp.edit().putString(name, blob).apply()
        return true
    }

    fun get(name: String): String? {
        val blob = sp.getString(name, null) ?: return null
        return runCatching {
            val (iv, enc) = blob.split(":", limit = 2)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(enc, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun has(name: String) = sp.contains(name) && get(name) != null

    /** Shown in Settings so you can tell which key is saved without revealing it. */
    fun hint(name: String): String = get(name)?.let { if (it.length > 8) "…" + it.takeLast(4) else "saved" } ?: ""

    companion object {
        private const val ALIAS = "article-quotes-secrets"
        const val CLAUDE = "claudeKey"
        const val ESV = "esvKey"
        const val ADMIN = "claudeAdminKey"
    }
}
