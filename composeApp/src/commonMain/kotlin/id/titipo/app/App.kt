package id.titipo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import id.titipo.app.di.modulApp
import id.titipo.app.nav.GrafRumah
import id.titipo.app.nav.GrafVendor
import id.titipo.app.ui.LayarLogin
import id.titipo.shared.auth.AlurLogin
import id.titipo.shared.auth.PengelolaSesi
import id.titipo.shared.model.Peran
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.module.Module

/** Akar aplikasi. [platform] = modul Koin per platform (lihat `modulApp`). */
@Composable
fun App(platform: Module) {
    KoinApplication(application = { modules(modulApp(platform)) }) {
        MaterialTheme { AkarApp() }
    }
}

/**
 * Satu biner, dua graf: sesi kosong -> login; sesi ada -> graf sesuai klaim peran JWT.
 * Ganti peran butuh keluar + masuk ulang; `key(sub)` mereset backstack tiap sesi baru.
 */
@Composable
private fun AkarApp() {
    val pengelola = koinInject<PengelolaSesi>()
    val alur = koinInject<AlurLogin>()
    val sesi by pengelola.sesi.collectAsState()
    var siap by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        pengelola.pulihkan()
        siap = true
    }

    if (!siap) return
    val aktif = sesi
    if (aktif == null) {
        LayarLogin(alur)
    } else {
        val keluar: () -> Unit = { scope.launch { pengelola.keluar() } }
        key(aktif.sub) {
            when (aktif.peran) {
                Peran.RUMAH -> GrafRumah(keluar)
                Peran.VENDOR -> GrafVendor(keluar)
            }
        }
    }
}
