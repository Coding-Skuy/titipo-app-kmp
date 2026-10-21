package id.titipo.app.di

import app.cash.sqldelight.db.SqlDriver
import id.titipo.shared.antrean.AntreanRepository
import id.titipo.shared.antrean.PemrosesAntrean
import id.titipo.shared.api.PembacaBerkas
import id.titipo.shared.api.PengirimAksiKtor
import id.titipo.shared.api.TitipoApi
import id.titipo.shared.api.buatKlienHttp
import id.titipo.shared.auth.AlurLogin
import id.titipo.shared.auth.GerbangLogin
import id.titipo.shared.auth.LoginLumbungKtor
import id.titipo.shared.auth.PengelolaSesi
import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.db.TitipoDb
import org.koin.core.module.Module
import org.koin.dsl.module

/** Alamat layanan; diisi platform dari konfigurasi build, bukan dikeraskan di UI. */
data class KonfigEndpoint(val urlApi: String, val urlAuth: String)

/**
 * Modul Koin aplikasi. [platform] wajib menyediakan: `PenyimpanToken` (Keystore/Keychain),
 * `SqlDriver`, `PembacaBerkas`, `KonfigEndpoint`, dan (opsional) `KonfigurasiBisnis`.
 */
fun modulApp(platform: Module): Module = module {
    includes(platform)
    single { buatKlienHttp() }
    single { PengelolaSesi(get()) }
    single { TitipoApi(get(), get<KonfigEndpoint>().urlApi, get<PengelolaSesi>(), get<PembacaBerkas>()) }
    single<GerbangLogin> { LoginLumbungKtor(get(), get<KonfigEndpoint>().urlAuth) }
    single { AlurLogin(get(), get()) }
    single { TitipoDb(get<SqlDriver>()) }
    single { AntreanRepository(get(), konfigurasi = getOrNull<KonfigurasiBisnis>() ?: KonfigurasiBisnis.BAWAAN) }
    single { PemrosesAntrean(get(), PengirimAksiKtor(get()), konfigurasi = getOrNull<KonfigurasiBisnis>() ?: KonfigurasiBisnis.BAWAAN) }
}
