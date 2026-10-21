package id.titipo.shared

import id.titipo.shared.antrean.AksiAntrean
import id.titipo.shared.antrean.HasilKirim
import id.titipo.shared.antrean.JenisAksi
import id.titipo.shared.antrean.StatusAntrean
import id.titipo.shared.api.HasilApi
import id.titipo.shared.api.JsonTitipo
import id.titipo.shared.api.PengirimAksiKtor
import id.titipo.shared.api.TitipoApi
import id.titipo.shared.model.ItemPesananBaru
import id.titipo.shared.model.PesananBaru
import id.titipo.shared.model.StatusPesanan
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TitipoApiTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val diminta = mutableListOf<HttpRequestData>()

    private fun api(blok: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?): TitipoApi {
        val engine = MockEngine { req ->
            diminta += req
            blok(req) ?: respondError(HttpStatusCode.InternalServerError)
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(JsonTitipo) } }
        return TitipoApi(http, "https://api.contoh.test/v1/", { "jwt-uji" })
    }

    @Test
    fun checkoutKirimBearerDanIdempotencyKeyDanParseKontrak() = runTest {
        val a = api {
            respond(
                """{"kode":"T-2026-000112","total_bayar":52000,"kedaluwarsa_bayar":"2026-01-06T13:15:00Z"}""",
                HttpStatusCode.Created, json,
            )
        }
        val hasil = a.buatPesanan(
            "01JMB2Z9KQ0ABCD1234567890A",
            PesananBaru("01JMB2Z9KQ0ABCD1234567890A", "V-SARI-001", listOf(ItemPesananBaru("LMB-BAYAM-001", 2000))),
        )
        val ok = assertIs<HasilApi.Sukses<id.titipo.shared.model.PesananDibuat>>(hasil)
        assertEquals("T-2026-000112", ok.data.kode)
        assertEquals(52_000, ok.data.totalBayar)
        val req = diminta.single()
        assertEquals("https://api.contoh.test/v1/pesanan", req.url.toString())
        assertEquals("Bearer jwt-uji", req.headers[HttpHeaders.Authorization])
        assertEquals("01JMB2Z9KQ0ABCD1234567890A", req.headers["Idempotency-Key"])
        assertEquals(HttpMethod.Post, req.method)
    }

    @Test
    fun galatBakuDipetakan() = runTest {
        val a = api {
            respond(
                """{"kode":"KATALOG_KEDALUWARSA","pesan":"Snapshot katalog lebih dari 24 jam","detail":{}}""",
                HttpStatusCode.Conflict, json,
            )
        }
        val h = assertIs<HasilApi.GalatHttp>(a.katalog("V-SARI-001"))
        assertEquals(409, h.status)
        assertEquals("KATALOG_KEDALUWARSA", h.galat?.kode)
    }

    @Test
    fun katalogDanVendorTerdekatParseContohKontrak() = runTest {
        val a = api {
            when {
                it.url.encodedPath.endsWith("/vendor-terdekat") -> respond(
                    """{"data":[{"id":"V-SARI-001","nama":"Warung Sari","jarak_km":0.8,"rating":4.9,"sisa_kuota":12,"prioritas":true}]}""",
                    HttpStatusCode.OK, json,
                )
                else -> respond(
                    """{"diperbarui":"2026-01-07T04:30:00Z","data":[{"sku_lumbung":"LMB-BAYAM-001","nama":"Bayam","harga_per_kg":9000,"stok_gram":50000}]}""",
                    HttpStatusCode.OK, json,
                )
            }
        }
        val v = assertIs<HasilApi.Sukses<id.titipo.shared.model.DaftarVendor>>(a.vendorTerdekat(-6.9175, 107.6191)).data
        assertEquals(12, v.data.single().sisaKuota)
        assertEquals("radius_km=5", diminta.first().url.encodedQuery.split('&').single { it.startsWith("radius") })
        val k = assertIs<HasilApi.Sukses<id.titipo.shared.model.Katalog>>(a.katalog("V-SARI-001")).data
        assertEquals(9_000, k.data.single().hargaPerKg)
    }

    @Test
    fun statusPesananTakDikenalDitolakSebagaiResponsTakSesuai() = runTest {
        val a = api { respond("""{"status":"aneh"}""", HttpStatusCode.OK, json) }
        assertIs<HasilApi.GalatHttp>(a.konfirmasiBayar("01JMB2Z9KQ0ABCD1234567890A", "T-2026-000112", "QR-1"))
    }

    @Test
    fun jaringanPutusDanPemetaanPengirimAntrean() = runTest {
        val putus = TitipoApi(HttpClient(MockEngine { throw RuntimeException("tak ada sinyal") }) {
            install(ContentNegotiation) { json(JsonTitipo) }
        }, "https://x.test", { null })
        val aksi = AksiAntrean("01JMB2Z9KQ0ABCD1234567890A", JenisAksi.KONFIRMASI, "T-2026-000112", "{}", StatusAntrean.MENUNGGU, 0, 0, null)
        assertIs<HasilKirim.GagalSementara>(PengirimAksiKtor(putus).kirim(aksi, null))

        val konflik = api { respond("""{"kode":"TRANSISI_ILEGAL","pesan":"x"}""", HttpStatusCode.UnprocessableEntity, json) }
        assertEquals(HasilKirim.DitolakServer("TRANSISI_ILEGAL", "x"), PengirimAksiKtor(konflik).kirim(aksi, null))

        val tak401 = api { respond("""{"kode":"TOKEN_KEDALUWARSA","pesan":"x"}""", HttpStatusCode.Unauthorized, json) }
        assertIs<HasilKirim.GagalSementara>(PengirimAksiKtor(tak401).kirim(aksi, null))

        val ok = api { respond("""{"status":"disiapkan","dikonfirmasi":"2026-01-06T12:55:00Z"}""", HttpStatusCode.OK, json) }
        assertEquals(HasilKirim.Sukses, PengirimAksiKtor(ok).kirim(aksi, null))
        assertEquals(StatusPesanan.DISIAPKAN, StatusPesanan.dariKawat("disiapkan"))

        val tolak = aksi.copy(jenis = JenisAksi.TOLAK, muatanJson = """{"alasan":"Stok habis"}""")
        PengirimAksiKtor(ok).kirim(tolak, null)
        assertEquals(true, diminta.last().body.toString().isNotEmpty())
    }

    @Test
    fun tutupJadwalBelumAdaEndpointDitandaiJelas() = runTest {
        val aksi = AksiAntrean("01JMB2Z9KQ0ABCD1234567890A", JenisAksi.TUTUP_JADWAL, null, "{}", StatusAntrean.MENUNGGU, 0, 0, null)
        val h = PengirimAksiKtor(api { null }).kirim(aksi, null)
        assertEquals("ENDPOINT_BELUM_ADA", assertIs<HasilKirim.DitolakServer>(h).kode)
    }
}
