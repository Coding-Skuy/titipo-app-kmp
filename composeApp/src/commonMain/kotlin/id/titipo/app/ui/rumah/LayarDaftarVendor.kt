package id.titipo.app.ui.rumah

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.titipo.app.ui.KerangkaLayar
import id.titipo.shared.model.VendorRingkas
import id.titipo.shared.rumah.HasilMuat
import id.titipo.shared.rumah.SesiBelanja

/** Penyaring murni (diuji): pencarian nama + filter Prioritas. */
fun saringVendor(daftar: List<VendorRingkas>, kata: String, hanyaPrioritas: Boolean): List<VendorRingkas> =
    daftar.filter { (!hanyaPrioritas || it.prioritas) && it.nama.contains(kata.trim(), ignoreCase = true) }

private sealed interface StatusDaftar {
    data object Memuat : StatusDaftar
    data class Data(val daftar: List<VendorRingkas>) : StatusDaftar
    data class Galat(val pesan: String) : StatusDaftar
}

/** Layar 1: `rumah/daftar-vendor`. Radius 5 km; kosong menawarkan radius 10 km (ongkir tetap Rp5.000). */
@Composable
fun LayarDaftarVendor(
    sesi: SesiBelanja,
    lat: Double,
    lng: Double,
    onPilihVendor: (String) -> Unit,
    onKeranjang: () -> Unit,
    onKeluar: () -> Unit,
) {
    var radius by remember { mutableStateOf(5) }
    var percobaan by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<StatusDaftar>(StatusDaftar.Memuat) }
    var kata by remember { mutableStateOf("") }
    var prioritas by remember { mutableStateOf(false) }

    LaunchedEffect(radius, percobaan) {
        status = StatusDaftar.Memuat
        status = when (val h = sesi.muatVendor(lat, lng, radius)) {
            is HasilMuat.Ok -> StatusDaftar.Data(h.data.data)
            is HasilMuat.Galat -> StatusDaftar.Galat(h.pesan)
            is HasilMuat.Kedaluwarsa -> StatusDaftar.Galat(h.pesan)
        }
    }

    KerangkaLayar("Titip hari ini", "layar_rumah_daftar-vendor", onKeluar) {
        OutlinedTextField(
            value = kata, onValueChange = { kata = it }, singleLine = true, label = { Text("Cari warung") },
            modifier = Modifier.fillMaxWidth().testTag("daftar_vendor_cari"),
        )
        FilterChip(
            selected = prioritas, onClick = { prioritas = !prioritas }, label = { Text("Prioritas") },
            modifier = Modifier.testTag("filter_prioritas"),
        )
        Button(onClick = onKeranjang, modifier = Modifier.testTag("tombol_ke_keranjang")) { Text("Keranjang") }
        when (val s = status) {
            StatusDaftar.Memuat -> repeat(3) { SkeletonKartu() }
            is StatusDaftar.Galat -> {
                Text(s.pesan, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("daftar_galat"))
                Button(onClick = { percobaan++ }, modifier = Modifier.testTag("tombol_coba_lagi")) { Text("Coba lagi") }
            }
            is StatusDaftar.Data -> {
                val tampil = saringVendor(s.daftar, kata, prioritas)
                if (tampil.isEmpty()) {
                    Text("Belum ada warung di radius $radius km.", modifier = Modifier.testTag("daftar_kosong"))
                    if (radius < 10) {
                        Button(onClick = { radius = 10 }, modifier = Modifier.testTag("tombol_radius_10")) {
                            Text("Coba radius 10 km")
                        }
                        Text("Ongkir tetap Rp5.000")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tampil, key = { it.id }) { v -> KartuVendor(v) { onPilihVendor(v.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun KartuVendor(v: VendorRingkas, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("kartu_vendor_${v.id}")) {
        Column(Modifier.padding(12.dp)) {
            Text(v.nama + if (v.prioritas) " (Prioritas)" else "", style = MaterialTheme.typography.titleMedium)
            Text("${v.jarakKm} km  •  ${v.rating}★  •  Sisa ${v.sisaKuota} titipan")
            Text("Besok 06.00–08.00")
        }
    }
}

@Composable
private fun SkeletonKartu() {
    Card(Modifier.fillMaxWidth().height(72.dp).testTag("skeleton_kartu")) { Box(Modifier.fillMaxWidth()) }
}
