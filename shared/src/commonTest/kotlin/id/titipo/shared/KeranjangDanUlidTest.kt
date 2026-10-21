package id.titipo.shared

import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.model.ItemKatalog
import id.titipo.shared.util.Ulid
import id.titipo.shared.validasi.BarisKeranjang
import id.titipo.shared.validasi.GalatKeranjang
import id.titipo.shared.validasi.HitungKeranjang
import id.titipo.shared.validasi.UmurKatalog
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeranjangDanUlidTest {
    private val bayam = ItemKatalog("LMB-BAYAM-001", "Bayam", 9_000, 50_000)
    private val telur = ItemKatalog("LMB-TELUR-001", "Telur", 29_000, 30_000)

    @Test
    fun contohMengikatRp52000() {
        val r = HitungKeranjang.hitung(
            listOf(BarisKeranjang(bayam, 2_000), BarisKeranjang(telur, 1_000)),
            KonfigurasiBisnis.BAWAAN,
        )
        assertEquals(18_000 + 29_000, r.totalItem)
        assertEquals(5_000, r.ongkir)
        assertEquals(52_000, r.totalBayar)
        assertTrue(r.valid)
    }

    @Test
    fun batasBeratMengikutiKonfigurasiBukanAngkaKeras() {
        val baris = listOf(BarisKeranjang(bayam, 22_000))
        val batas20 = KonfigurasiBisnis(batasBeratGram = 20_000)
        val g = HitungKeranjang.hitung(baris, batas20).galat
        assertEquals(listOf<GalatKeranjang>(GalatKeranjang.BeratMelebihiBatas(2_000, 20_000)), g)
        // Dengan bawaan backend (50 kg) berat yang sama sah.
        assertTrue(HitungKeranjang.hitung(baris, KonfigurasiBisnis.BAWAAN).valid)
    }

    @Test
    fun batasBarisDanStokDanKosong() {
        val banyak = (1..51).map { BarisKeranjang(ItemKatalog("S$it", "x", 1000, 5_000), 100) }
        assertTrue(HitungKeranjang.hitung(banyak, KonfigurasiBisnis.BAWAAN).galat.any { it is GalatKeranjang.BarisMelebihiBatas })
        val stok = HitungKeranjang.hitung(listOf(BarisKeranjang(bayam, 60_000)), KonfigurasiBisnis(batasBeratGram = 100_000))
        assertTrue(stok.galat.any { it is GalatKeranjang.StokTidakCukup })
        val kosong = HitungKeranjang.hitung(emptyList(), KonfigurasiBisnis.BAWAAN)
        assertEquals(listOf<GalatKeranjang>(GalatKeranjang.KeranjangKosong), kosong.galat)
        assertEquals(0, kosong.totalBayar)
    }

    @Test
    fun katalogKedaluwarsaSetelah24Jam() {
        val diperbarui = "2026-01-07T04:30:00Z"
        val dasar = 1_767_760_200_000L // 2026-01-07T04:30:00Z
        val k = KonfigurasiBisnis.BAWAAN
        assertFalse(UmurKatalog.kedaluwarsa(diperbarui, dasar + 23 * 3_600_000L, k))
        assertTrue(UmurKatalog.kedaluwarsa(diperbarui, dasar + 24 * 3_600_000L + 1, k))
        assertTrue(UmurKatalog.kedaluwarsa("bukan-tanggal", dasar, k))
    }

    @Test
    fun ulid26KarakterValidDanBerurutWaktu() {
        val a = Ulid.buat(1_000, Random(1))
        val b = Ulid.buat(2_000_000, Random(1))
        assertEquals(26, a.length)
        assertTrue(Ulid.valid(a))
        assertTrue(a < b)
        assertFalse(Ulid.valid("short"))
        assertFalse(Ulid.valid(a.lowercase()))
    }
}
