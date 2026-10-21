package id.titipo.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import id.titipo.app.di.LokasiBawaan
import id.titipo.app.ui.rumah.LayarBayar
import id.titipo.app.ui.rumah.LayarDaftarVendor
import id.titipo.app.ui.rumah.LayarKatalog
import id.titipo.app.ui.rumah.LayarKeranjang
import id.titipo.app.ui.rumah.LayarLacak
import id.titipo.shared.rumah.SesiBelanja
import org.koin.compose.koinInject

/** Graf navigasi rumah tangga (5 layar). Backstack sendiri, dipilih dari klaim peran `household`. */
@Composable
fun GrafRumah(onKeluar: () -> Unit) {
    val sesi = koinInject<SesiBelanja>()
    val lokasi = remember { LokasiBawaan() }
    val backStack = remember { mutableStateListOf<RuteRumah>(RuteRumah.DaftarVendor) }
    fun kembaliKeAwal() { backStack.clear(); backStack.add(RuteRumah.DaftarVendor) }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider {
            entry<RuteRumah.DaftarVendor> {
                LayarDaftarVendor(
                    sesi, lokasi.lat, lokasi.lng,
                    onPilihVendor = { backStack.add(RuteRumah.Katalog(it)) },
                    onKeranjang = { backStack.add(RuteRumah.Keranjang) },
                    onKeluar = onKeluar,
                )
            }
            entry<RuteRumah.Katalog> {
                LayarKatalog(sesi, it.idVendor, onKeranjang = { backStack.add(RuteRumah.Keranjang) }, onKeluar = onKeluar)
            }
            entry<RuteRumah.Keranjang> {
                LayarKeranjang(sesi, onBayar = { backStack.add(RuteRumah.Bayar) }, onKeluar = onKeluar)
            }
            entry<RuteRumah.Bayar> {
                LayarBayar(sesi, onSudahBayar = { kode -> backStack.add(RuteRumah.Lacak(kode)) }, onKeluar = onKeluar)
            }
            entry<RuteRumah.Lacak> {
                LayarLacak(sesi, it.kode, onSelesai = ::kembaliKeAwal, onKeluar = onKeluar)
            }
        },
    )
}
