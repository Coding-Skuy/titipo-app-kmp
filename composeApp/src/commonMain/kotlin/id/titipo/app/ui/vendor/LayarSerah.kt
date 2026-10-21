package id.titipo.app.ui.vendor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import id.titipo.app.ui.KerangkaLayar
import id.titipo.shared.antrean.AntreanRepository
import id.titipo.shared.antrean.FotoSerahBaru
import id.titipo.shared.antrean.HasilEnqueue
import id.titipo.shared.util.Jam
import id.titipo.shared.vendor.PemantauKoneksi
import id.titipo.shared.vendor.RepositoriVendor
import kotlinx.coroutines.launch

/**
 * Layar vendor 3: `vendor/serah/{kode}`. Kamera (1280 px, kompres maksimal 800 KB), pratinjau,
 * "Selesai serah" mati tanpa foto (fee ditahan). Foto baru menggantikan foto lama.
 * Serah hanya 06.00–08.00 (toleransi 15 menit): penegakan jam ada di server.
 */
@Composable
fun LayarSerah(
    vendor: RepositoriVendor,
    antrean: AntreanRepository,
    pemantau: PemantauKoneksi,
    kode: String,
    onSelesai: () -> Unit,
    onKeluar: () -> Unit,
    jam: Jam = Jam.Sistem,
    padaAksi: () -> Unit = {},
) {
    val kamera = rememberPengambilFoto()
    var foto by remember { mutableStateOf<HasilFoto?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    KerangkaLayar("Serah $kode", "layar_vendor_serah_$kode", onKeluar, spanduk = { BilahLuring(pemantau, antrean) }) {
        Text("Serah 06.00–08.00. Foto wajib sebagai bukti.", modifier = Modifier.testTag("serah_jendela"))
        OutlinedButton(
            onClick = { scope.launch { kamera.ambil()?.let { foto = it; info = null } } },
            modifier = Modifier.fillMaxWidth().testTag("tombol_kamera"),
        ) { Text(if (foto == null) "Ambil foto" else "Ambil ulang foto") }
        foto?.let {
            AsyncImage(
                model = "file://${it.path}", contentDescription = "Pratinjau foto serah", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp).testTag("foto_pratinjau"),
            )
            Text("${it.lebar}×${it.tinggi} px, ${it.ukuranByte / 1024} KB", modifier = Modifier.testTag("foto_info"))
        }
        info?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("serah_info")) }
        Button(
            enabled = foto != null,
            onClick = {
                val f = foto ?: return@Button
                when (vendor.selesaiSerah(kode, FotoSerahBaru(kode, f.path, f.ukuranByte, jam.sekarangMs()))) {
                    is HasilEnqueue.Berhasil -> { padaAksi(); onSelesai() }
                    is HasilEnqueue.Ditolak -> info = "Antrean penuh atau foto melebihi batas; coba lagi nanti."
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("tombol_selesai_serah"),
        ) { Text("Selesai serah") }
    }
}
