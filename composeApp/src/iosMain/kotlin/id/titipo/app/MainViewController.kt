package id.titipo.app

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import id.titipo.app.di.KonfigEndpoint
import id.titipo.shared.api.PembacaBerkas
import id.titipo.shared.auth.PenyimpanToken
import id.titipo.shared.db.TitipoDb
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIViewController
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
private fun bacaBerkas(path: String): ByteArray {
    val data = NSData.dataWithContentsOfFile(path) ?: error("berkas tidak ditemukan: $path")
    val hasil = ByteArray(data.length.toInt())
    if (hasil.isNotEmpty()) hasil.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return hasil
}

/** Titik masuk iOS; dipanggil dari host SwiftUI (`MainViewControllerKt.MainViewController()`). */
@OptIn(BetaInteropApi::class)
fun MainViewController(): UIViewController {
    // URL dari Info.plist (TitipoUrlApi / TitipoUrlAuth); bawaan lokal bila kosong.
    fun info(kunci: String, bawaan: String) = NSBundle.mainBundle.objectForInfoDictionaryKey(kunci) as? String ?: bawaan
    val platform = module {
        single<PenyimpanToken> { PenyimpanTokenIos() }
        single<SqlDriver> { NativeSqliteDriver(TitipoDb.Schema, "titipo.db") }
        single { PembacaBerkas { path -> bacaBerkas(path) } }
        single { KonfigEndpoint(info("TitipoUrlApi", "http://localhost:8080"), info("TitipoUrlAuth", "http://localhost:8081")) }
    }
    return ComposeUIViewController { App(platform) }
}
