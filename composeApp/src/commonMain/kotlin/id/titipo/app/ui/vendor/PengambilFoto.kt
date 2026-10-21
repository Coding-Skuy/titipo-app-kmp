package id.titipo.app.ui.vendor

import androidx.compose.runtime.Composable

/** Foto serah yang sudah dikecilkan (sisi terpanjang <= 1280 px) dan dikompres JPEG (<= 800 KB). */
data class HasilFoto(val path: String, val ukuranByte: Int, val lebar: Int, val tinggi: Int)

/** Kamera platform. `ambil()` mengembalikan null bila pengguna membatalkan. */
interface PengambilFoto {
    suspend fun ambil(): HasilFoto?
}

/** Pengambil foto per platform (Android: kamera sistem + FileProvider; iOS: UIImagePickerController). */
@Composable
expect fun rememberPengambilFoto(): PengambilFoto
