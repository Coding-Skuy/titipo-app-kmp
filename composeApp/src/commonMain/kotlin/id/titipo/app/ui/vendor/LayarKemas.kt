package id.titipo.app.ui.vendor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.titipo.app.ui.KerangkaLayar
import id.titipo.shared.antrean.AntreanRepository
import id.titipo.shared.format.Format
import id.titipo.shared.model.StatusPesanan
import id.titipo.shared.vendor.ChecklistKemas
import id.titipo.shared.vendor.PemantauKoneksi
import id.titipo.shared.vendor.RepositoriVendor

/**
 * Layar vendor 2: `vendor/kemas`. Checklist ambil di titik Lumbung (04.00–06.00);
 * "Siap serah" aktif hanya setelah SEMUA item dicentang.
 */
@Composable
fun LayarKemas(
    vendor: RepositoriVendor,
    antrean: AntreanRepository,
    pemantau: PemantauKoneksi,
    onSiapSerah: (kode: String) -> Unit,
    onKeluar: () -> Unit,
) {
    val daftar by vendor.masuk.collectAsState(initial = emptyList())
    val disiapkan = daftar.filter { it.status == StatusPesanan.DISIAPKAN }
    val centang = remember { mutableStateMapOf<String, ChecklistKemas>() }

    KerangkaLayar("Kemas", "layar_vendor_kemas", onKeluar, spanduk = { BilahLuring(pemantau, antrean) }) {
        Text("Ambil di titik Lumbung 04.00–06.00", modifier = Modifier.testTag("kemas_jendela"))
        if (disiapkan.isEmpty()) Text("Belum ada pesanan untuk dikemas.", modifier = Modifier.testTag("kemas_kosong"))
        disiapkan.forEach { p ->
            val cek = centang[p.kode] ?: ChecklistKemas(p.item)
            Text(p.kode, style = MaterialTheme.typography.titleMedium)
            p.item.forEachIndexed { i, item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = i in cek.dicentang,
                        onCheckedChange = { centang[p.kode] = cek.ubah(i, it) },
                        modifier = Modifier.testTag("cek_${p.kode}_$i"),
                    )
                    Text("${item.nama} ${Format.berat(item.jumlahGram)}")
                }
            }
            Button(
                onClick = { onSiapSerah(p.kode) }, enabled = cek.siapSerah,
                modifier = Modifier.fillMaxWidth().testTag("tombol_siap_serah_${p.kode}"),
            ) { Text("Siap serah") }
        }
    }
}
