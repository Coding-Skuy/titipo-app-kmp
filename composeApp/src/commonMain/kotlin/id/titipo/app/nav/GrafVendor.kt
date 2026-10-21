package id.titipo.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import id.titipo.app.ui.LayarSementara

/** Graf navigasi vendor (3 layar). Backstack sendiri, dipilih dari klaim peran `vendor`. */
@Composable
fun GrafVendor(onKeluar: () -> Unit) {
    val backStack = remember { mutableStateListOf<RuteVendor>(RuteVendor.Masuk) }
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider {
            entry<RuteVendor.Masuk> { LayarSementara(it.path, onKeluar) }
            entry<RuteVendor.Kemas> { LayarSementara(it.path, onKeluar) }
            entry<RuteVendor.Serah> { LayarSementara(it.path, onKeluar) }
        },
    )
}
