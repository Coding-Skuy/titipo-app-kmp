package id.titipo.app.ui.vendor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.titipo.app.ui.KerangkaLayar
import id.titipo.shared.antrean.AntreanRepository
import id.titipo.shared.antrean.HasilEnqueue
import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.format.Format
import id.titipo.shared.model.StatusPesanan
import id.titipo.shared.util.Jam
import id.titipo.shared.vendor.AlasanTolakVendor
import id.titipo.shared.vendor.ItemMasuk
import id.titipo.shared.vendor.PemantauKoneksi
import id.titipo.shared.vendor.PesananMasuk
import id.titipo.shared.vendor.RepositoriVendor
import id.titipo.shared.vendor.WaktuKonfirmasi
import kotlinx.coroutines.delay

/** Ringkasan item untuk kartu: "Bayam 2 kg, Telur 1 kg". */
fun ringkasItem(item: List<ItemMasuk>): String = item.joinToString(", ") { "${it.nama} ${Format.berat(it.jumlahGram)}" }

/** Bilah luring: banner kuning + badge antrean di semua layar vendor saat offline (AC4). */
@Composable
fun BilahLuring(pemantau: PemantauKoneksi, antrean: AntreanRepository) {
    val online by pemantau.online.collectAsState()
    val jumlah by antrean.jumlahMenungguFlow.collectAsState(initial = 0L)
    if (!online || jumlah > 0) {
        Row(
            Modifier.fillMaxWidth().background(if (online) Color(0xFFE3F2FD) else Color(0xFFFFF59D)).padding(8.dp).testTag("bilah_luring"),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (online) "Mengirim aksi tertunda..." else "Luring — aksi akan dikirim otomatis",
                color = Color.Black, modifier = Modifier.testTag("banner_luring"),
            )
            if (jumlah > 0) Text("Antrean $jumlah", color = Color.Black, modifier = Modifier.testTag("badge_antrean"))
        }
    }
}

/**
 * Layar vendor 1: `vendor/masuk`. Geser kanan = Terima, geser kiri = Tolak + alasan.
 * Aksi ditulis lokal dahulu (boleh luring). Push (kode, total, sisa menit) menyuntik data lewat
 * `RepositoriVendor.simpanPesananMasuk`; penerima push platform belum ada (ADR-0005).
 */
@Composable
fun LayarMasuk(
    vendor: RepositoriVendor,
    antrean: AntreanRepository,
    pemantau: PemantauKoneksi,
    onKemas: () -> Unit,
    onKeluar: () -> Unit,
    jam: Jam = Jam.Sistem,
    konfigurasi: KonfigurasiBisnis = KonfigurasiBisnis.BAWAAN,
    padaAksi: () -> Unit = {},
) {
    val daftar by vendor.masuk.collectAsState(initial = emptyList())
    var sekarang by remember { mutableStateOf(jam.sekarangMs()) }
    var tolakKode by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { while (true) { sekarang = jam.sekarangMs(); delay(1000) } }

    KerangkaLayar("Masuk", "layar_vendor_masuk", onKeluar, spanduk = { BilahLuring(pemantau, antrean) }) {
        Button(onClick = onKemas, modifier = Modifier.testTag("tombol_ke_kemas")) { Text("Kemas pesanan") }
        info?.let { Text(it, modifier = Modifier.testTag("info_aksi")) }
        if (daftar.isEmpty()) Text("Belum ada titipan masuk.", modifier = Modifier.testTag("masuk_kosong"))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(daftar, key = { it.kode }) { p ->
                KartuMasuk(
                    p, WaktuKonfirmasi.sisaMenit(p.dibuatMs, sekarang, konfigurasi),
                    onTerima = {
                        info = when (vendor.terima(p.kode)) {
                            is HasilEnqueue.Berhasil -> "Tersimpan — terkirim otomatis saat online (antrean ${antrean.jumlahMenunggu()})."
                            is HasilEnqueue.Ditolak -> "Batas aksi tercapai."
                        }
                        padaAksi()
                    },
                    onTolak = { tolakKode = p.kode },
                )
            }
        }
    }

    tolakKode?.let { kode ->
        AlertDialog(
            onDismissRequest = { tolakKode = null },
            title = { Text("Tolak $kode?") },
            text = { Text("Pilih alasan penolakan.") },
            confirmButton = {
                Row {
                    AlasanTolakVendor.entries.forEach { a ->
                        TextButton(
                            onClick = {
                                info = when (vendor.tolak(kode, a)) {
                                    is HasilEnqueue.Berhasil -> "Tersimpan — terkirim otomatis saat online (antrean ${antrean.jumlahMenunggu()})."
                                    is HasilEnqueue.Ditolak -> "Batas aksi tercapai."
                                }
                                tolakKode = null
                                padaAksi()
                            },
                            modifier = Modifier.testTag("alasan_${a.name.lowercase()}"),
                        ) { Text(a.teks) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { tolakKode = null }) { Text("Batal") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KartuMasuk(p: PesananMasuk, sisaMenit: Long, onTerima: () -> Unit, onTolak: () -> Unit) {
    val menunggu = p.status == StatusPesanan.MENUNGGU_KONFIRMASI
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { nilai ->
            when (nilai) {
                SwipeToDismissBoxValue.StartToEnd -> onTerima()   // geser kanan = Terima
                SwipeToDismissBoxValue.EndToStart -> onTolak()    // geser kiri = Tolak
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // kartu kembali; daftar berubah mengikuti status lokal
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = menunggu,
        enableDismissFromEndToStart = menunggu,
        modifier = Modifier.testTag("kartu_${p.kode}"),
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(
                when (state.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFFC8E6C9)
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFFFCDD2)
                    else -> Color.Transparent
                },
            ).padding(16.dp), contentAlignment = Alignment.Center) {
                Text(if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) "Terima" else "Tolak")
            }
        },
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(p.kode, style = MaterialTheme.typography.titleMedium)
                Text(ringkasItem(p.item), modifier = Modifier.testTag("ringkas_${p.kode}"))
                Text(Format.rupiah(p.totalBayar))
                if (menunggu) {
                    Text("Konfirmasi dalam $sisaMenit menit", modifier = Modifier.testTag("timer_${p.kode}"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Alternatif ketukan untuk aksesibilitas (target >= 48 dp): ketukan 1 = pilih aksi, bukan geser.
                        Button(onClick = onTerima, modifier = Modifier.testTag("terima_${p.kode}")) { Text("Terima") }
                        TextButton(onClick = onTolak, modifier = Modifier.testTag("tolak_${p.kode}")) { Text("Tolak") }
                    }
                } else {
                    Text(if (p.sinkron) "Disiapkan" else "Disiapkan (menunggu terkirim)")
                }
            }
        }
    }
}
