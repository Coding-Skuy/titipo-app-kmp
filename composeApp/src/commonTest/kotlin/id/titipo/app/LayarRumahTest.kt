package id.titipo.app

import id.titipo.app.ui.rumah.pesanGalatKeranjang
import id.titipo.app.ui.rumah.saringVendor
import id.titipo.shared.model.VendorRingkas
import id.titipo.shared.validasi.GalatKeranjang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayarRumahTest {
    private val sari = VendorRingkas("V-SARI-001", "Warung Sari", 0.8, 4.9, 12, true)
    private val budi = VendorRingkas("V-BUDI-002", "Warung Budi", 2.1, 4.5, 3, false)

    @Test
    fun saringCariDanPrioritas() {
        assertEquals(listOf(sari, budi), saringVendor(listOf(sari, budi), "", false))
        assertEquals(listOf(sari), saringVendor(listOf(sari, budi), "", true))
        assertEquals(listOf(budi), saringVendor(listOf(sari, budi), " budi ", false))
        assertEquals(emptyList(), saringVendor(listOf(sari, budi), "zzz", false))
    }

    @Test
    fun pesanKelebihanBeratMemakaiBatasDariKonfigurasi() {
        val teks = pesanGalatKeranjang(GalatKeranjang.BeratMelebihiBatas(2_000, 20_000))
        assertTrue(teks.contains("Kelebihan 2 kg"))
        assertTrue(teks.contains("20 kg")) // angka berasal dari argumen, bukan konstanta UI
        assertTrue(pesanGalatKeranjang(GalatKeranjang.BeratMelebihiBatas(1_000, 50_000)).contains("50 kg"))
    }
}
