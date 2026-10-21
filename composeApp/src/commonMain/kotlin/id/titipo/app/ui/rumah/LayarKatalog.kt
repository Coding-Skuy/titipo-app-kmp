package id.titipo.app.ui.rumah

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.titipo.app.ui.KerangkaLayar
import id.titipo.shared.format.Format
import id.titipo.shared.model.ItemKatalog
import id.titipo.shared.rumah.HasilMuat
import id.titipo.shared.rumah.HasilUbah
import id.titipo.shared.rumah.SesiBelanja
import id.titipo.shared.validasi.HitungKeranjang

/** Langkah stepper (gram). ASUMSI: UX menyebut "stepper kg" tanpa langkah; dipakai 500 g. */
const val LANGKAH_GRAM = 500L

/** Layar 2: `rumah/katalog/{idVendor}`. */
@Composable
fun LayarKatalog(
    sesi: SesiBelanja,
    idVendor: String,
    onKeranjang: () -> Unit,
    onKeluar: () -> Unit,
) {
    val katalog by sesi.katalog.collectAsState()
    val keranjang by sesi.keranjang.collectAsState()
    var kedaluwarsa by remember { mutableStateOf(false) }
    var galat by remember { mutableStateOf<String?>(null) }
    var percobaan by remember { mutableStateOf(0) }
    var konfirmasiGanti by remember { mutableStateOf<Pair<ItemKatalog, String>?>(null) }

    LaunchedEffect(idVendor, percobaan) {
        galat = null
        when (val h = sesi.muatKatalog(idVendor)) {
            is HasilMuat.Ok -> kedaluwarsa = false
            is HasilMuat.Kedaluwarsa -> kedaluwarsa = true
            is HasilMuat.Galat -> galat = h.pesan
        }
    }

    val terkunci = kedaluwarsa || sesi.katalogTerkunci()
    KerangkaLayar(
        judul = "Katalog",
        tag = "layar_rumah_katalog_$idVendor",
        onKeluar = onKeluar,
        spanduk = {
            if (terkunci && galat == null) {
                Surface(color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().testTag("banner_kedaluwarsa")) {
                    Text(
                        "Katalog kedaluwarsa — tarik untuk muat ulang",
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        },
    ) {
        sesi.stempelKatalog()?.let { Text(it, modifier = Modifier.testTag("stempel_katalog")) }
        galat?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("katalog_galat")) }
        OutlinedButton(onClick = { percobaan++ }, modifier = Modifier.testTag("tombol_muat_ulang")) { Text("Muat ulang katalog") }
        val data = katalog?.takeIf { it.idVendor == idVendor }?.katalog?.data.orEmpty()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data, key = { it.skuLumbung }) { item ->
                val jumlah = keranjang.jumlahGram[item.skuLumbung] ?: 0L
                BarisKatalog(
                    item = item, jumlahGram = jumlah, terkunci = terkunci,
                    onUbah = { delta ->
                        when (val h = sesi.ubahJumlah(idVendor, item, delta)) {
                            is HasilUbah.PerluGantiVendor -> konfirmasiGanti = item to h.vendorLama
                            HasilUbah.KatalogKedaluwarsa -> kedaluwarsa = true
                            HasilUbah.Ok -> Unit
                        }
                    },
                )
            }
        }
        Button(
            onClick = onKeranjang, enabled = keranjang.jumlahGram.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("tombol_ke_keranjang"),
        ) { Text("Lihat keranjang") }
    }

    konfirmasiGanti?.let { (item, lama) ->
        AlertDialog(
            onDismissRequest = { konfirmasiGanti = null },
            title = { Text("Ganti warung?") },
            text = { Text("Satu pesanan hanya untuk satu warung. Keranjang dari $lama akan dikosongkan.") },
            confirmButton = {
                TextButton(
                    onClick = { sesi.kosongkan(); sesi.ubahJumlah(idVendor, item, LANGKAH_GRAM); konfirmasiGanti = null },
                    modifier = Modifier.testTag("tombol_ganti_vendor"),
                ) { Text("Kosongkan dan ganti") }
            },
            dismissButton = { TextButton(onClick = { konfirmasiGanti = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun BarisKatalog(item: ItemKatalog, jumlahGram: Long, terkunci: Boolean, onUbah: (Long) -> Unit) {
    val habis = item.stokGram <= 0
    Card(Modifier.fillMaxWidth().testTag("item_${item.skuLumbung}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.nama, style = MaterialTheme.typography.titleMedium)
            Text("${Format.rupiah(item.hargaPerKg)}/kg  •  1 kg = ${Format.rupiah(HitungKeranjang.subtotal(item.hargaPerKg, 1000))}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onUbah(-LANGKAH_GRAM) }, enabled = jumlahGram > 0,
                    modifier = Modifier.testTag("kurang_${item.skuLumbung}"),
                ) { Text("−") }
                Text(Format.berat(jumlahGram), modifier = Modifier.testTag("jumlah_${item.skuLumbung}"))
                Button(
                    onClick = { onUbah(LANGKAH_GRAM) }, enabled = !habis && !terkunci,
                    modifier = Modifier.testTag("tambah_${item.skuLumbung}"),
                ) { Text(if (habis) "Habis" else "+") }
            }
        }
    }
}
