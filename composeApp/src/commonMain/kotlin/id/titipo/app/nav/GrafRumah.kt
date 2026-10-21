package id.titipo.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import id.titipo.app.ui.LayarSementara

/** Graf navigasi rumah tangga (5 layar). Backstack sendiri, dipilih dari klaim peran `household`. */
@Composable
fun GrafRumah(onKeluar: () -> Unit) {
    val backStack = remember { mutableStateListOf<RuteRumah>(RuteRumah.DaftarVendor) }
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider {
            entry<RuteRumah.DaftarVendor> { LayarSementara(it.path, onKeluar) }
            entry<RuteRumah.Katalog> { LayarSementara(it.path, onKeluar) }
            entry<RuteRumah.Keranjang> { LayarSementara(it.path, onKeluar) }
            entry<RuteRumah.Bayar> { LayarSementara(it.path, onKeluar) }
            entry<RuteRumah.Lacak> { LayarSementara(it.path, onKeluar) }
        },
    )
}
