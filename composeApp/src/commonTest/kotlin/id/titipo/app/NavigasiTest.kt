package id.titipo.app

import id.titipo.app.nav.RuteRumah
import id.titipo.app.nav.RuteVendor
import id.titipo.app.ui.pesanGalatLogin
import id.titipo.shared.auth.HasilAlurLogin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NavigasiTest {
    @Test
    fun rutePerPeranMengikutiUxDanTerpisah() {
        assertEquals("rumah/daftar-vendor", RuteRumah.DaftarVendor.path)
        assertEquals("rumah/katalog/V-SARI-001", RuteRumah.Katalog("V-SARI-001").path)
        assertEquals("rumah/keranjang", RuteRumah.Keranjang.path)
        assertEquals("rumah/bayar", RuteRumah.Bayar.path)
        assertEquals("rumah/lacak/T-2026-000112", RuteRumah.Lacak("T-2026-000112").path)
        assertEquals("vendor/masuk", RuteVendor.Masuk.path)
        assertEquals("vendor/kemas", RuteVendor.Kemas.path)
        assertEquals("vendor/serah/T-2026-000112", RuteVendor.Serah("T-2026-000112").path)
        // dua graf tidak berbagi rute
        assertTrue(RuteRumah::class != RuteVendor::class)
    }

    @Test
    fun pesanGalatLoginMemetakanSemuaHasil() {
        assertNotNull(pesanGalatLogin(HasilAlurLogin.KredensialSalah))
        assertNotNull(pesanGalatLogin(HasilAlurLogin.AudiensSalah))
        assertNotNull(pesanGalatLogin(HasilAlurLogin.PeranDitolak))
        assertNotNull(pesanGalatLogin(HasilAlurLogin.TokenKedaluwarsa))
        assertTrue(pesanGalatLogin(HasilAlurLogin.Gagal("putus"))!!.contains("putus"))
    }
}
