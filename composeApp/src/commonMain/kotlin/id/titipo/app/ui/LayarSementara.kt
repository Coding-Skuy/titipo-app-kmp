package id.titipo.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Penampung layar yang diisi tiket TTP-KMP-003/004; memberi titik navigasi + testTag lebih awal. */
@Composable
fun LayarSementara(path: String, onKeluar: () -> Unit) {
    KerangkaLayar(judul = path, tag = "layar_" + path.replace('/', '_'), onKeluar = onKeluar) {
        Text("Layar $path menyusul (tiket 003/004).")
    }
}
