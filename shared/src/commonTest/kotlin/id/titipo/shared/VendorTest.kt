package id.titipo.shared

import id.titipo.shared.antrean.AntreanRepository
import id.titipo.shared.antrean.AlasanTolak
import id.titipo.shared.antrean.FotoSerahBaru
import id.titipo.shared.antrean.HasilEnqueue
import id.titipo.shared.antrean.JenisAksi
import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.model.StatusPesanan
import id.titipo.shared.util.Jam
import id.titipo.shared.vendor.AlasanTolakVendor
import id.titipo.shared.vendor.ChecklistKemas
import id.titipo.shared.vendor.ItemMasuk
import id.titipo.shared.vendor.KompresFoto
import id.titipo.shared.vendor.PemantauKoneksi
import id.titipo.shared.vendor.RepositoriVendor
import id.titipo.shared.vendor.WaktuKonfirmasi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VendorTest {
    private var t = 1_800_000_000_000L
    private val jam = Jam { t }
    private val db = buatDbUji()
    private val antrean = AntreanRepository(db, jam, KonfigurasiBisnis.BAWAAN, Random(3))
    private val vendor = RepositoriVendor(db, antrean, jam)
    private val kode = "T-2026-000112"

    private fun masukkan() = vendor.simpanPesananMasuk(
        kode, "V-SARI-001", 52_000, t,
        listOf(ItemMasuk("Bayam", 2_000), ItemMasuk("Telur", 1_000)),
    )

    @Test
    fun pesananMasukTampilDenganKodeTotalItem() = runTest {
        masukkan()
        val kartu = vendor.masuk.first().single()
        assertEquals(kode, kartu.kode)
        assertEquals(52_000, kartu.totalBayar)
        assertEquals(2, kartu.item.size)
        assertEquals(StatusPesanan.MENUNGGU_KONFIRMASI, kartu.status)
    }

    @Test
    fun terimaLuringMasukAntreanDanStatusLokalSegera() = runTest {
        masukkan()
        val h = assertIs<HasilEnqueue.Berhasil>(vendor.terima(kode))
        assertEquals(JenisAksi.KONFIRMASI, h.aksi.jenis)
        assertEquals(kode, h.aksi.kodePesanan)
        assertEquals(1, antrean.jumlahMenunggu())
        val kartu = vendor.masuk.first().single()
        assertEquals(StatusPesanan.DISIAPKAN, kartu.status)
        assertFalse(kartu.sinkron) // belum dikonfirmasi server
    }

    @Test
    fun tolakMembawaAlasan() = runTest {
        masukkan()
        val h = assertIs<HasilEnqueue.Berhasil>(vendor.tolak(kode, AlasanTolakVendor.STOK_HABIS))
        assertEquals(JenisAksi.TOLAK, h.aksi.jenis)
        assertTrue(h.aksi.muatanJson.contains("Stok habis"))
        assertTrue(h.aksi.muatanJson.contains("\"terima\":false"))
        assertTrue(vendor.masuk.first().isEmpty()) // tidak lagi aktif
    }

    @Test
    fun batasAksiDitegakkanTanpaMengubahStatus() = runTest {
        val kecil = AntreanRepository(db, jam, KonfigurasiBisnis(batasAksiPer24Jam = 0), Random(3))
        val v = RepositoriVendor(db, kecil, jam)
        masukkan()
        assertEquals(HasilEnqueue.Ditolak(AlasanTolak.BATAS_AKSI_24_JAM), v.terima(kode))
        assertEquals(StatusPesanan.MENUNGGU_KONFIRMASI, v.masuk.first().single().status)
    }

    @Test
    fun serahWajibFotoDanFotoTerakhirMenang() = runTest {
        masukkan()
        assertFailsWith<IllegalArgumentException> { vendor.selesaiSerah(kode, null) }
        vendor.selesaiSerah(kode, FotoSerahBaru(kode, "/a.jpg", 500_000, 1))
        vendor.selesaiSerah(kode, FotoSerahBaru(kode, "/b.jpg", 400_000, 2))
        assertEquals("/b.jpg", db.skemaQueries.fotoAktif(kode).executeAsOne().path)
        assertEquals(2, antrean.jumlahMenunggu())
    }

    @Test
    fun sisaWaktuKonfirmasi30Menit() {
        val k = KonfigurasiBisnis.BAWAAN
        assertEquals(1800, WaktuKonfirmasi.sisaDetik(1_000, 1_000, k))
        assertEquals(12, WaktuKonfirmasi.sisaMenit(0, 18 * 60_000L, k))
        assertEquals(0, WaktuKonfirmasi.sisaDetik(0, 31 * 60_000L, k))
    }

    @Test
    fun checklistSiapSerahHanyaBilaSemuaDicentang() {
        var c = ChecklistKemas(listOf(ItemMasuk("Bayam", 2000), ItemMasuk("Telur", 1000)))
        assertFalse(c.siapSerah)
        c = c.ubah(0, true)
        assertFalse(c.siapSerah)
        c = c.ubah(1, true)
        assertTrue(c.siapSerah)
        assertFalse(c.ubah(1, false).siapSerah)
        assertFalse(ChecklistKemas(emptyList()).siapSerah)
    }

    @Test
    fun kompresSkalaDanKualitas() {
        assertEquals(1280 to 960, KompresFoto.skala(4000, 3000))
        assertEquals(960 to 1280, KompresFoto.skala(3000, 4000))
        assertEquals(800 to 600, KompresFoto.skala(800, 600)) // tidak diperbesar
        // ukuran sintetis naik seiring kualitas: 10 KB per poin kualitas
        val q = KompresFoto.cariKualitas(800 * 1024) { it * 10_000 }
        assertEquals(81, q) // 81 * 10_000 = 810_000 <= 819_200; 82 -> 820_000 > 819_200
        assertNull(KompresFoto.cariKualitas(1_000) { 5_000 }) // tak pernah muat
        assertNotNull(KompresFoto.cariKualitas(800 * 1024) { 1 })
    }

    @Test
    fun pemantauKoneksiLuringLaluOnlineMemicuCallback() = runTest {
        var hidup = false
        var dipicu = 0
        val engine = MockEngine { if (hidup) respond("{}", HttpStatusCode.OK) else throw RuntimeException("putus") }
        val p = PemantauKoneksi(HttpClient(engine), "https://x.test/", padaOnline = { dipicu++ })
        assertFalse(p.periksa())
        assertFalse(p.online.value)
        hidup = true
        assertTrue(p.periksa())
        assertEquals(1, dipicu)
        assertTrue(p.periksa())
        assertEquals(1, dipicu) // hanya saat transisi
    }
}
