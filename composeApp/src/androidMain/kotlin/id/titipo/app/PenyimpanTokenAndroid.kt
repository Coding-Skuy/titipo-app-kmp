package id.titipo.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import id.titipo.shared.auth.PenyimpanToken
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * JWT disimpan terenkripsi AES-256-GCM; kunci hidup di Android Keystore (tidak pernah keluar).
 * Isi tersimpan: base64(iv[12] + ciphertext). Tidak menyimpan service key apa pun.
 */
class PenyimpanTokenAndroid(context: Context) : PenyimpanToken {
    private val prefs = context.getSharedPreferences("titipo_sesi", Context.MODE_PRIVATE)

    private fun kunci(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override suspend fun simpan(token: String) {
        val c = Cipher.getInstance(TRANSFORMASI).apply { init(Cipher.ENCRYPT_MODE, kunci()) }
        val iv = c.iv
        val isi = c.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KUNCI_PREF, Base64.encodeToString(iv + isi, Base64.NO_WRAP)).apply()
    }

    override suspend fun baca(): String? {
        val tersimpan = prefs.getString(KUNCI_PREF, null) ?: return null
        return try {
            val semua = Base64.decode(tersimpan, Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORMASI)
            c.init(Cipher.DECRYPT_MODE, kunci(), GCMParameterSpec(128, semua, 0, 12))
            String(c.doFinal(semua, 12, semua.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            hapus() // rusak / kunci hilang: perlakukan sebagai belum login
            null
        }
    }

    override suspend fun hapus() {
        prefs.edit().remove(KUNCI_PREF).apply()
    }

    private companion object {
        const val ALIAS = "titipo_jwt_v1"
        const val KUNCI_PREF = "jwt_terenkripsi"
        const val TRANSFORMASI = "AES/GCM/NoPadding"
    }
}
