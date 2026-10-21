package id.titipo.shared.api

import id.titipo.shared.model.BayarPermintaan
import id.titipo.shared.model.DaftarVendor
import id.titipo.shared.model.GalatApi
import id.titipo.shared.model.Katalog
import id.titipo.shared.model.KonfirmasiPermintaan
import id.titipo.shared.model.KonfirmasiRespons
import id.titipo.shared.model.PesananBaru
import id.titipo.shared.model.PesananDibuat
import id.titipo.shared.model.RatingPermintaan
import id.titipo.shared.model.SerahRespons
import id.titipo.shared.model.StatusRespons
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Penyedia JWT beraudiens `titipo` (disimpan terenkripsi di platform). */
fun interface PenyediaToken {
    suspend fun token(): String?
}

/** Hasil pemanggilan API; galat jaringan dipisah dari galat HTTP agar antrean tahu kapan retry. */
sealed interface HasilApi<out T> {
    data class Sukses<T>(val data: T, val status: Int) : HasilApi<T>
    data class GalatHttp(val status: Int, val galat: GalatApi?) : HasilApi<Nothing>
    data class GalatJaringan(val pesan: String) : HasilApi<Nothing>
}

/** Pembaca isi foto dari path (platform). Menjaga `shared` bebas API Android/iOS. */
fun interface PembacaBerkas {
    suspend fun baca(path: String): ByteArray
}

val JsonTitipo = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

fun buatKlienHttp(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(JsonTitipo) }
    expectSuccess = false
}

/**
 * Klien HTTP ke titipo-backend-service. Hanya endpoint mobile dari openapi.yaml (BE-004).
 * Semua POST membawa `Idempotency-Key` (ULID klien).
 * Server menentukan angka sah; klien tidak menghitung total sah.
 */
class TitipoApi(
    private val http: HttpClient,
    private val baseUrl: String,
    private val token: PenyediaToken,
    private val pembacaBerkas: PembacaBerkas? = null,
) {
    private val dasar = baseUrl.trimEnd('/')

    suspend fun vendorTerdekat(lat: Double, lng: Double, radiusKm: Int = 5): HasilApi<DaftarVendor> =
        panggil {
            http.get("$dasar/vendor-terdekat") {
                otorisasi()
                parameter("lat", lat)
                parameter("lng", lng)
                parameter("radius_km", radiusKm)
            }
        }

    /** [batasByte]: tarik ulang katalog maksimal 5 MB (AC TTP-KMP-003); lebih dari itu ditolak klien. */
    suspend fun katalog(idVendor: String, batasByte: Long = 5L * 1024 * 1024): HasilApi<Katalog> =
        panggil(batasByte) { http.get("$dasar/katalog/$idVendor") { otorisasi() } }

    suspend fun buatPesanan(kunci: String, pesanan: PesananBaru): HasilApi<PesananDibuat> =
        panggil {
            http.post("$dasar/pesanan") {
                otorisasi(); header("Idempotency-Key", kunci)
                contentType(ContentType.Application.Json); setBody(pesanan)
            }
        }

    suspend fun konfirmasiBayar(kunci: String, kode: String, idGateway: String): HasilApi<StatusRespons> =
        panggil {
            http.post("$dasar/pesanan/$kode/bayar") {
                otorisasi(); header("Idempotency-Key", kunci)
                contentType(ContentType.Application.Json); setBody(BayarPermintaan(idGateway))
            }
        }

    suspend fun konfirmasiVendor(kunci: String, kode: String, terima: Boolean, alasan: String? = null): HasilApi<KonfirmasiRespons> =
        panggil {
            http.post("$dasar/vendor/pesanan/$kode/konfirmasi") {
                otorisasi(); header("Idempotency-Key", kunci)
                contentType(ContentType.Application.Json); setBody(KonfirmasiPermintaan(terima, if (terima) null else alasan))
            }
        }

    suspend fun serah(kunci: String, kode: String, pathFoto: String, waktuIso: String): HasilApi<SerahRespons> {
        val baca = pembacaBerkas ?: error("PembacaBerkas wajib untuk unggah foto serah")
        val bytes = try {
            baca.baca(pathFoto)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HasilApi.GalatJaringan("foto tidak terbaca: ${e.message}")
        }
        return panggil {
            http.submitFormWithBinaryData(
                url = "$dasar/vendor/pesanan/$kode/serah",
                formData = formData {
                    append("waktu", waktuIso)
                    append("foto", bytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"$kode.jpg\"")
                    })
                },
            ) { otorisasi(); header("Idempotency-Key", kunci) }
        }
    }

    suspend fun beriRating(kunci: String, kode: String, bintang: Int, catatan: String? = null): HasilApi<StatusRespons> =
        panggil {
            http.post("$dasar/pesanan/$kode/rating") {
                otorisasi(); header("Idempotency-Key", kunci)
                contentType(ContentType.Application.Json); setBody(RatingPermintaan(bintang, catatan?.take(200)))
            }
        }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.otorisasi() {
        token.token()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend inline fun <reified T> panggil(batasByte: Long? = null, blok: () -> HttpResponse): HasilApi<T> {
        val respons = try {
            blok()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HasilApi.GalatJaringan(e.message ?: e::class.simpleName ?: "jaringan")
        }
        val kode = respons.status.value
        val ukuran = respons.contentLength()
        if (batasByte != null && ukuran != null && ukuran > batasByte) {
            return HasilApi.GalatHttp(413, GalatApi("RESPONS_TERLALU_BESAR", "respons $ukuran byte melebihi batas $batasByte byte"))
        }
        return if (kode in 200..299) {
            try {
                HasilApi.Sukses(respons.body<T>(), kode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HasilApi.GalatHttp(kode, GalatApi("RESPONS_TAK_DIKENAL", e.message ?: "isi respons tak sesuai kontrak"))
            }
        } else {
            val galat = runCatching { JsonTitipo.decodeFromString(GalatApi.serializer(), respons.bodyAsText()) }.getOrNull()
            HasilApi.GalatHttp(kode, galat)
        }
    }
}
