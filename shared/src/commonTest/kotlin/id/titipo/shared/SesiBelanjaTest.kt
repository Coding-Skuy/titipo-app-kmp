package id.titipo.shared

import id.titipo.shared.api.JsonTitipo
import id.titipo.shared.api.TitipoApi
import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.format.Format
import id.titipo.shared.model.ItemKatalog
import id.titipo.shared.model.StatusPesanan
import id.titipo.shared.rumah.HasilCheckout
import id.titipo.shared.rumah.HasilMuat
import id.titipo.shared.rumah.HasilUbah
import id.titipo.shared.rumah.SesiBelanja
import id.titipo.shared.rumah.TimerBayar
import id.titipo.shared.rumah.Timeline
import id.titipo.shared.util.Jam
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FormatTest {
    @Test
    fun formatRupiahBeratWaktu() {
        assertEquals("Rp52.000", Format.rupiah(52_000))
        assertEquals("Rp5.000", Format.rupiah(5_000))
        assertEquals("Rp0", Format.rupiah(0))
        assertEquals("Rp1.234.567", Format.rupiah(1_234_567))
        assertEquals("2 kg", Format.berat(2_000))
        assertEquals("1,5 kg", Format.berat(1_500))
        assertEquals("250 g", Format.berat(250))
        assertEquals("15:00", Format.mmss(900))
        assertEquals("00:00", Format.mmss(-5))
        assertEquals("11.30", Format.jamWib("2026-01-07T04:30:00Z")) // UTC+7
        assertEquals("04.30", Format.jamWib("2025-12-31T21:30:00Z"))
    }
}

class SesiBelanjaTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val diminta = mutableListOf<HttpRequestData>()
    private val sekarang = Instant.parse("2026-01-07T05:00:00Z").toEpochMilliseconds() // 12.00 WIB
    private var jamMs = sekarang
    private val jam = Jam { jamMs }

    private fun katalogJson(diperbarui: String) =
        """{"diperbarui":"$diperbarui","data":[
            {"sku_lumbung":"LMB-BAYAM-001","nama":"Bayam","harga_per_kg":9000,"stok_gram":50000},
            {"sku_lumbung":"LMB-TELUR-001","nama":"Telur","harga_per_kg":29000,"stok_gram":30000}]}"""

    private fun sesi(
        diperbarui: String = "2026-01-07T04:30:00Z",
        konfigurasi: KonfigurasiBisnis = KonfigurasiBisnis.BAWAAN,
        jaringanPutus: Boolean = false,
    ): SesiBelanja {
        val engine = MockEngine { req ->
            diminta += req
            if (jaringanPutus && req.url.encodedPath == "/pesanan") throw RuntimeException("tak ada sinyal")
            when {
                req.url.encodedPath.startsWith("/katalog/") -> respond(katalogJson(diperbarui), HttpStatusCode.OK, json)
                req.url.encodedPath == "/pesanan" -> respond(
                    """{"kode":"T-2026-000112","total_bayar":52000,"kedaluwarsa_bayar":"${Instant.fromEpochMilliseconds(jamMs + 900_000)}"}""",
                    HttpStatusCode.Created, json,
                )
                req.url.encodedPath.endsWith("/bayar") -> respond("""{"status":"menunggu_konfirmasi"}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(JsonTitipo) } }
        return SesiBelanja(TitipoApi(http, "https://x.test", { "jwt" }), buatDbUji(), konfigurasi, jam)
    }

    private val bayam = ItemKatalog("LMB-BAYAM-001", "Bayam", 9_000, 50_000)
    private val telur = ItemKatalog("LMB-TELUR-001", "Telur", 29_000, 30_000)

    private suspend fun SesiBelanja.isiContoh() {
        assertIs<HasilMuat.Ok<*>>(muatKatalog("V-SARI-001"))
        assertEquals(HasilUbah.Ok, ubahJumlah("V-SARI-001", bayam, 2_000))
        assertEquals(HasilUbah.Ok, ubahJumlah("V-SARI-001", telur, 1_000))
    }

    @Test
    fun contohRp52000DariKeranjangDanStempelKatalog() = runTest {
        val s = sesi()
        s.isiContoh()
        val r = s.ringkasan()
        assertEquals(47_000, r.totalItem)
        assertEquals(52_000, r.totalBayar)
        assertEquals("Diperbarui 11.30", s.stempelKatalog()) // 04:30Z = 11.30 WIB (lihat temuan dokumen)
        assertFalse(s.katalogTerkunci())
    }

    @Test
    fun satuPesananSatuVendorDanJumlahTidakNegatifDanTerbatasStok() = runTest {
        val s = sesi()
        s.isiContoh()
        assertEquals(HasilUbah.PerluGantiVendor("V-SARI-001"), s.ubahJumlah("V-LAIN-002", bayam, 1_000))
        s.ubahJumlah("V-SARI-001", bayam, -9_999_999)
        assertEquals(setOf("LMB-TELUR-001"), s.keranjang.value.jumlahGram.keys)
        s.ubahJumlah("V-SARI-001", telur, 9_999_999)
        assertEquals(30_000, s.keranjang.value.jumlahGram["LMB-TELUR-001"]) // dibatasi stok
        s.kosongkan()
        assertEquals(null, s.keranjang.value.idVendor)
    }

    @Test
    fun checkoutOnlineKirimIdempotencyKeyDanSimpanCerminDanTimer1500() = runTest {
        val s = sesi()
        s.isiContoh()
        val h = assertIs<HasilCheckout.Berhasil>(s.checkout("01JMB2Z9KQ0ABCD1234567890A"))
        assertEquals("T-2026-000112", h.pesanan.kode)
        assertEquals(52_000, h.pesanan.totalBayar)
        val req = diminta.last()
        assertEquals("01JMB2Z9KQ0ABCD1234567890A", req.headers["Idempotency-Key"])
        assertEquals(900, s.sisaDetikBayar()) // timer QRIS 15:00
        assertEquals("15:00", Format.mmss(s.sisaDetikBayar()!!))
        jamMs += 899_000
        assertEquals(1, s.sisaDetikBayar())
        jamMs += 5_000
        assertEquals(0, s.sisaDetikBayar())
        assertEquals(StatusPesanan.MENUNGGU_PEMBAYARAN, s.pesananAktif.value?.status)
    }

    @Test
    fun sudahBayarMengubahStatus() = runTest {
        val s = sesi()
        s.isiContoh()
        s.checkout("01JMB2Z9KQ0ABCD1234567890A")
        s.sudahBayar()
        assertEquals(StatusPesanan.MENUNGGU_KONFIRMASI, s.pesananAktif.value?.status)
    }

    @Test
    fun diLuarJamPesanDitolakTanpaPanggilanJaringan() = runTest {
        val s = sesi()
        s.isiContoh()
        val sebelum = diminta.size
        jamMs = Instant.parse("2026-01-07T14:00:00Z").toEpochMilliseconds() // 21.00 WIB
        val h = assertIs<HasilCheckout.Ditolak>(s.checkout())
        assertEquals("DI_LUAR_JAM_PESAN", h.kode)
        assertEquals(sebelum, diminta.size)
    }

    @Test
    fun katalogLebihDari24JamTerkunciDanTombolPesanMati() = runTest {
        val s = sesi(diperbarui = "2026-01-06T03:00:00Z") // 26 jam lalu
        assertIs<HasilMuat.Kedaluwarsa>(s.muatKatalog("V-SARI-001"))
        assertTrue(s.katalogTerkunci())
        assertEquals(HasilUbah.KatalogKedaluwarsa, s.ubahJumlah("V-SARI-001", bayam, 1_000))
    }

    @Test
    fun checkoutGagalJaringanTidakDiantrekan() = runTest {
        val s = sesi(jaringanPutus = true)
        s.isiContoh()
        assertIs<HasilCheckout.GagalJaringan>(s.checkout())
        assertEquals(null, s.pesananAktif.value) // tidak ada pesanan semu
    }

    @Test
    fun batasBeratDariKonfigurasiMenghalangiCheckout() = runTest {
        val s = sesi(konfigurasi = KonfigurasiBisnis(batasBeratGram = 2_500))
        s.isiContoh() // 3 kg
        assertEquals(false, s.ringkasan().valid)
        assertEquals("KERANJANG_TIDAK_VALID", assertIs<HasilCheckout.Ditolak>(s.checkout()).kode)
    }

    @Test
    fun timerDanTimeline() {
        assertEquals(900, TimerBayar.sisaDetik("2026-01-07T05:15:00Z", sekarang))
        assertEquals(0, TimerBayar.sisaDetik("2026-01-07T04:00:00Z", sekarang))
        assertEquals(null, TimerBayar.sisaDetik("bukan", sekarang))
        assertEquals(8, Timeline.langkah.size)
        assertEquals(1, Timeline.indeksAktif(StatusPesanan.MENUNGGU_KONFIRMASI))
        assertEquals(-1, Timeline.indeksAktif(StatusPesanan.KEDALUWARSA))
        assertNotNull(Timeline.langkah[Timeline.indeksAktif(StatusPesanan.SELESAI)])
    }
}
