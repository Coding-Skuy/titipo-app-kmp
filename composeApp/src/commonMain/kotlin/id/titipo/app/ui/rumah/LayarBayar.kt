package id.titipo.app.ui.rumah

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.titipo.app.ui.KerangkaLayar
import id.titipo.shared.format.Format
import id.titipo.shared.rumah.HasilBayar
import id.titipo.shared.rumah.HasilCheckout
import id.titipo.shared.rumah.SesiBelanja
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Gagal bayar diulang maksimal 3 kali, lalu pengguna diminta membuat QR baru (UX 4.2). */
const val MAKS_ULANG_BAYAR = 3

/** Layar 4: `rumah/bayar`. QRIS + timer 15:00 mundur; habis -> layar kedaluwarsa + buat ulang (ID baru). */
@Composable
fun LayarBayar(
    sesi: SesiBelanja,
    onSudahBayar: (kode: String) -> Unit,
    onKeluar: () -> Unit,
) {
    val aktif by sesi.pesananAktif.collectAsState()
    var sisa by remember { mutableStateOf(sesi.sisaDetikBayar()) }
    var galat by remember { mutableStateOf<String?>(null) }
    var gagalBerturut by remember { mutableStateOf(0) }
    var sibuk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(aktif?.kode) {
        while (true) {
            sisa = sesi.sisaDetikBayar()
            delay(1000)
        }
    }

    KerangkaLayar("Bayar QRIS", "layar_rumah_bayar", onKeluar) {
        val p = aktif
        if (p == null) {
            Text("Tidak ada pesanan yang menunggu pembayaran.", modifier = Modifier.testTag("bayar_kosong"))
            return@KerangkaLayar
        }
        val habis = (sisa ?: 0L) <= 0L
        if (habis) {
            Text("Waktu pembayaran habis.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("bayar_kedaluwarsa"))
            Button(
                enabled = !sibuk,
                onClick = {
                    sibuk = true
                    scope.launch {
                        when (val h = sesi.buatUlang()) {
                            is HasilCheckout.Berhasil -> { galat = null; gagalBerturut = 0 }
                            is HasilCheckout.Ditolak -> galat = h.pesan
                            is HasilCheckout.GagalJaringan -> galat = h.pesan
                        }
                        sibuk = false
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("tombol_buat_ulang"),
            ) { Text("Buat ulang pesanan") }
        } else {
            // ASUMSI: kontrak belum memuat payload QRIS/gateway; ruang QR ini placeholder.
            Box(
                Modifier.fillMaxWidth().height(180.dp).border(1.dp, MaterialTheme.colorScheme.outline).testTag("qris"),
                contentAlignment = Alignment.Center,
            ) { Text("QRIS ${p.kode}") }
            val rincian = p.ringkasan.baris.joinToString(" + ") {
                "${it.item.nama} ${Format.berat(it.jumlahGram)} (${Format.rupiah(HitungSubtotal(it))})"
            }
            Text(
                "Pindai QRIS ${Format.rupiah(p.totalBayar)} — " +
                    (if (rincian.isEmpty()) "" else "$rincian + ") + "Ongkir ${Format.rupiah(p.ringkasan.ongkir)}.",
                modifier = Modifier.testTag("bayar_rincian"),
            )
            Text("Berlaku ${Format.mmss(sisa ?: 0)}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp).testTag("timer_bayar"))
            galat?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("bayar_galat")) }
            if (gagalBerturut >= MAKS_ULANG_BAYAR) {
                Text("Pembayaran gagal berulang. Buat QR baru.", modifier = Modifier.testTag("bayar_qr_baru"))
                Button(
                    onClick = {
                        scope.launch {
                            if (sesi.buatUlang() is HasilCheckout.Berhasil) { gagalBerturut = 0; galat = null }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("tombol_buat_ulang"),
                ) { Text("Buat QR baru") }
            } else {
                Button(
                    enabled = !sibuk,
                    onClick = {
                        sibuk = true
                        scope.launch {
                            when (val h = sesi.sudahBayar()) {
                                is HasilBayar.Berhasil -> onSudahBayar(p.kode)
                                is HasilBayar.Ditolak -> { gagalBerturut++; galat = "Pembayaran gagal (kode ${h.kode}). Coba lagi." }
                                is HasilBayar.GagalJaringan -> { gagalBerturut++; galat = "Pembayaran gagal (jaringan). Coba lagi." }
                            }
                            sibuk = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("tombol_sudah_bayar"),
                ) { Text("Saya sudah bayar") }
            }
        }
    }
}

private fun HitungSubtotal(b: id.titipo.shared.validasi.BarisKeranjang): Long =
    id.titipo.shared.validasi.HitungKeranjang.subtotal(b.item.hargaPerKg, b.jumlahGram)
