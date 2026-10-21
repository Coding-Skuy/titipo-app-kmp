package id.titipo.app.ui.vendor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.vendor.KompresFoto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
actual fun rememberPengambilFoto(): PengambilFoto {
    val context = LocalContext.current
    val tertunda = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val peluncur = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        tertunda[0]?.complete(ok)
    }
    val peluncurTerbaru = rememberUpdatedState(peluncur)
    return remember(context) {
        object : PengambilFoto {
            override suspend fun ambil(): HasilFoto? {
                val folder = File(context.cacheDir, "serah").apply { mkdirs() }
                val berkas = File(folder, "serah_${System.currentTimeMillis()}.jpg")
                val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", berkas)
                val menunggu = CompletableDeferred<Boolean>().also { tertunda[0] = it }
                peluncurTerbaru.value.launch(uri)
                if (!menunggu.await()) { berkas.delete(); return null }
                return withContext(Dispatchers.IO) { kompres(berkas, KonfigurasiBisnis.BAWAAN.batasUkuranFotoByte) }
            }
        }
    }
}

/** Skala sisi terpanjang 1280 px, putar sesuai EXIF, lalu cari kualitas JPEG tertinggi yang muat <= batas. */
private fun kompres(sumber: File, batasByte: Int): HasilFoto? {
    val tepi = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(sumber.path, tepi)
    if (tepi.outWidth <= 0 || tepi.outHeight <= 0) return null
    var sampel = 1
    while (maxOf(tepi.outWidth, tepi.outHeight) / (sampel * 2) >= KompresFoto.SISI_MAKS_PX) sampel *= 2
    val dasar = BitmapFactory.decodeFile(sumber.path, BitmapFactory.Options().apply { inSampleSize = sampel }) ?: return null
    val (l, t) = KompresFoto.skala(dasar.width, dasar.height)
    val skala = if (l == dasar.width) dasar else Bitmap.createScaledBitmap(dasar, l, t, true)
    val putaran = when (ExifInterface(sumber.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val akhir = if (putaran == 0f) skala else Bitmap.createBitmap(skala, 0, 0, skala.width, skala.height, Matrix().apply { postRotate(putaran) }, true)
    fun encode(kualitas: Int) = ByteArrayOutputStream().also { akhir.compress(Bitmap.CompressFormat.JPEG, kualitas, it) }.toByteArray()
    val kualitas = KompresFoto.cariKualitas(batasByte) { encode(it).size } ?: return null
    val hasil = encode(kualitas)
    val keluaran = File(sumber.parentFile, sumber.nameWithoutExtension + "_k.jpg")
    keluaran.writeBytes(hasil)
    sumber.delete()
    return HasilFoto(keluaran.path, hasil.size, akhir.width, akhir.height)
}
