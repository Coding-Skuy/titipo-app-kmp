package id.titipo.app.ui.rumah

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import id.titipo.shared.model.StatusPesanan
import id.titipo.shared.rumah.SesiBelanja
import id.titipo.shared.rumah.Timeline
import kotlinx.coroutines.launch

/**
 * Layar 5: `rumah/lacak/{kode}`. Timeline 8 langkah + foto serah + rating 1–5.
 * "Selesai" aktif hanya setelah rating dipilih. Status mengikuti respons terakhir yang diketahui klien:
 * kontrak belum punya `GET /pesanan/{kode}` untuk polling (temuan di ADR-0004).
 */
@Composable
fun LayarLacak(
    sesi: SesiBelanja,
    kode: String,
    onSelesai: () -> Unit,
    onKeluar: () -> Unit,
) {
    val aktif by sesi.pesananAktif.collectAsState()
    var rating by remember { mutableStateOf(0) }
    var catatan by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(kode) { sesi.tampilkanPesanan(kode) }

    KerangkaLayar("Lacak $kode", "layar_rumah_lacak_$kode", onKeluar) {
        val status = aktif?.takeIf { it.kode == kode }?.status ?: StatusPesanan.MENUNGGU_KONFIRMASI
        val indeks = Timeline.indeksAktif(status)
        if (indeks < 0) {
            Text("Pesanan dibatalkan atau kedaluwarsa (${status.kawat}).", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("lacak_batal"))
        }
        Timeline.langkah.forEachIndexed { i, (nama, _) ->
            val selesai = indeks >= 0 && i < indeks
            val sekarang = i == indeks
            Text(
                (if (selesai) "✓ " else if (sekarang) "● " else "○ ") + nama,
                style = if (sekarang) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("langkah_$i"),
            )
        }
        aktif?.takeIf { it.kode == kode }?.urlFoto?.let {
            AsyncImage(
                model = it, contentDescription = "Foto serah $kode", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp).testTag("foto_serah"),
            )
        } ?: Text("Foto serah tampil setelah pesanan diserahkan.", modifier = Modifier.testTag("foto_serah_kosong"))

        if (status == StatusPesanan.DISERAHKAN || status == StatusPesanan.SELESAI) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { n ->
                    FilterChip(selected = rating == n, onClick = { rating = n }, label = { Text("$n★") }, modifier = Modifier.testTag("rating_$n"))
                }
            }
            catatan?.let { Text(it, modifier = Modifier.testTag("rating_info")) }
            Button(
                enabled = rating in 1..5,
                onClick = {
                    scope.launch {
                        if (sesi.beriRating(rating, null)) { sesi.kosongkan(); onSelesai() } else catatan = "Rating gagal terkirim, coba lagi."
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("tombol_selesai"),
            ) { Text("Selesai") }
        }
    }
}
