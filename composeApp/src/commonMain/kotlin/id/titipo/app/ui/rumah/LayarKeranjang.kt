package id.titipo.app.ui.rumah

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.titipo.app.ui.KerangkaLayar
import id.titipo.shared.format.Format
import id.titipo.shared.rumah.HasilCheckout
import id.titipo.shared.rumah.SesiBelanja
import id.titipo.shared.validasi.GalatKeranjang
import kotlinx.coroutines.launch

/** Teks galat validasi inline (diuji). Batas diambil dari konfigurasi, bukan angka tetap. */
fun pesanGalatKeranjang(g: GalatKeranjang): String = when (g) {
    is GalatKeranjang.BeratMelebihiBatas ->
        "Kelebihan ${Format.berat(g.kelebihanGram)} (batas ${Format.berat(g.batasGram)}), kurangi"
    is GalatKeranjang.BarisMelebihiBatas -> "Maksimal ${g.batas} jenis barang per pesanan"
    is GalatKeranjang.StokTidakCukup -> "Stok ${g.sku} tinggal ${Format.berat(g.stokGram)}"
    GalatKeranjang.KeranjangKosong -> "Keranjang masih kosong"
}

/** Layar 3: `rumah/keranjang`. Jendela serah terkunci 06.00–08.00 (Varian 1) dan selalu tampil. */
@Composable
fun LayarKeranjang(
    sesi: SesiBelanja,
    onBayar: () -> Unit,
    onKeluar: () -> Unit,
) {
    val keranjang by sesi.keranjang.collectAsState()
    val ringkas = remember(keranjang) { sesi.ringkasan() }
    var galat by remember { mutableStateOf<String?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    KerangkaLayar("Keranjang", "layar_rumah_keranjang", onKeluar) {
        Text("Serah besok 06.00–08.00 (terkunci)", modifier = Modifier.testTag("keranjang_jendela"))
        ringkas.baris.forEach { b ->
            Row(
                Modifier.fillMaxWidth().testTag("baris_${b.item.skuLumbung}"),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(b.item.nama)
                    Text("${Format.berat(b.jumlahGram)} × ${Format.rupiah(b.item.hargaPerKg)}/kg")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { sesi.ubahJumlah(keranjang.idVendor ?: "", b.item, -LANGKAH_GRAM) },
                        modifier = Modifier.testTag("kurang_${b.item.skuLumbung}"),
                    ) { Text("−") }
                    OutlinedButton(
                        onClick = { sesi.ubahJumlah(keranjang.idVendor ?: "", b.item, LANGKAH_GRAM) },
                        modifier = Modifier.testTag("tambah_${b.item.skuLumbung}"),
                    ) { Text("+") }
                }
            }
        }
        ringkas.galat.forEach {
            Text(pesanGalatKeranjang(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("keranjang_galat"))
        }
        Text(
            "Total item ${Format.rupiah(ringkas.totalItem)} + Ongkir ${Format.rupiah(ringkas.ongkir)} = Total bayar ${Format.rupiah(ringkas.totalBayar)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp).testTag("keranjang_total_bayar"),
        )
        galat?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("checkout_galat")) }
        Button(
            enabled = ringkas.valid && !sibuk,
            onClick = {
                sibuk = true; galat = null
                scope.launch {
                    when (val h = sesi.checkout()) {
                        is HasilCheckout.Berhasil -> onBayar()
                        is HasilCheckout.Ditolak -> galat = h.pesan
                        is HasilCheckout.GagalJaringan -> galat = h.pesan
                    }
                    sibuk = false
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("tombol_bayar"),
        ) { Text(if (sibuk) "Memproses..." else "Bayar ${Format.rupiah(ringkas.totalBayar)}") }
    }
}
