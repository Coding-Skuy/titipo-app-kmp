package id.titipo.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import id.titipo.app.ui.vendor.LayarKemas
import id.titipo.app.ui.vendor.LayarMasuk
import id.titipo.app.ui.vendor.LayarSerah
import id.titipo.shared.antrean.AntreanRepository
import id.titipo.shared.antrean.PemrosesAntrean
import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.vendor.PemantauKoneksi
import id.titipo.shared.vendor.RepositoriVendor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Graf navigasi vendor (3 layar). Backstack sendiri, dipilih dari klaim peran `vendor`. */
@Composable
fun GrafVendor(onKeluar: () -> Unit) {
    val vendor = koinInject<RepositoriVendor>()
    val antrean = koinInject<AntreanRepository>()
    val pemroses = koinInject<PemrosesAntrean>()
    val pemantau = koinInject<PemantauKoneksi>()
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<RuteVendor>(RuteVendor.Masuk) }

    // Pemantau koneksi (/healthz) + retry antrean tiap 30 detik (jeda retry dari konfigurasi).
    LaunchedEffect(Unit) { pemantau.jalankan() }
    LaunchedEffect(Unit) {
        while (true) {
            if (pemantau.online.value) pemroses.prosesSemua(tungguRetry = false)
            delay(KonfigurasiBisnis.BAWAAN.jedaRetryMs)
        }
    }
    val kirimSekarang: () -> Unit = { scope.launch { if (pemantau.online.value) pemroses.prosesSemua(tungguRetry = false) } }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider {
            entry<RuteVendor.Masuk> {
                LayarMasuk(vendor, antrean, pemantau, onKemas = { backStack.add(RuteVendor.Kemas) }, onKeluar = onKeluar, padaAksi = kirimSekarang)
            }
            entry<RuteVendor.Kemas> {
                LayarKemas(vendor, antrean, pemantau, onSiapSerah = { backStack.add(RuteVendor.Serah(it)) }, onKeluar = onKeluar)
            }
            entry<RuteVendor.Serah> {
                LayarSerah(
                    vendor, antrean, pemantau, it.kode,
                    onSelesai = { backStack.clear(); backStack.add(RuteVendor.Masuk) },
                    onKeluar = onKeluar, padaAksi = kirimSekarang,
                )
            }
        },
    )
}
