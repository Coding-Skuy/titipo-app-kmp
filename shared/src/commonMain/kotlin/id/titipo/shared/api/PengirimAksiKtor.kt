package id.titipo.shared.api

import id.titipo.shared.antrean.AksiAntrean
import id.titipo.shared.antrean.FotoSerahBaru
import id.titipo.shared.antrean.HasilKirim
import id.titipo.shared.antrean.JenisAksi
import id.titipo.shared.antrean.PengirimAksi
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Menerjemahkan aksi antrean menjadi panggilan [TitipoApi]. `Idempotency-Key` = id aksi (ULID),
 * identik di setiap retry sehingga server tidak pernah membuat rekaman ganda.
 */
class PengirimAksiKtor(private val api: TitipoApi) : PengirimAksi {
    override suspend fun kirim(aksi: AksiAntrean, foto: FotoSerahBaru?): HasilKirim {
        val kode = aksi.kodePesanan
        return when (aksi.jenis) {
            JenisAksi.KONFIRMASI -> {
                kode ?: return tanpaKode()
                api.konfirmasiVendor(aksi.id, kode, terima = true).ke()
            }
            JenisAksi.TOLAK -> {
                kode ?: return tanpaKode()
                val alasan = (Json.parseToJsonElement(aksi.muatanJson) as? JsonObject)
                    ?.get("alasan")?.jsonPrimitive?.contentOrNull
                api.konfirmasiVendor(aksi.id, kode, terima = false, alasan = alasan).ke()
            }
            JenisAksi.FOTO_SERAH -> {
                kode ?: return tanpaKode()
                foto ?: return HasilKirim.DitolakServer("FOTO_HILANG", "berkas foto serah tidak ditemukan di antrean")
                api.serah(aksi.id, kode, foto.path, Instant.fromEpochMilliseconds(foto.waktuMs).toString()).ke()
            }
            // TIDAK ADA endpoint tutup jadwal di openapi.yaml (BE-004). Jangan mengarang kontrak:
            // aksi ditandai gagal dengan alasan jelas hingga backend menerbitkan endpoint.
            JenisAksi.TUTUP_JADWAL -> HasilKirim.DitolakServer(
                "ENDPOINT_BELUM_ADA",
                "kontrak backend belum memiliki endpoint tutup jadwal",
            )
        }
    }

    private fun tanpaKode() = HasilKirim.DitolakServer("KODE_PESANAN_KOSONG", "aksi tanpa kode pesanan")

    private fun <T> HasilApi<T>.ke(): HasilKirim = when (this) {
        is HasilApi.Sukses -> HasilKirim.Sukses
        is HasilApi.GalatJaringan -> HasilKirim.GagalSementara(pesan)
        is HasilApi.GalatHttp -> when {
            // 401: token kedaluwarsa, tunggu login/refresh; 408/429/5xx: sementara.
            status == 401 || status == 408 || status == 429 || status >= 500 ->
                HasilKirim.GagalSementara("HTTP $status ${galat?.kode ?: ""}".trim())
            else -> HasilKirim.DitolakServer(galat?.kode ?: "HTTP_$status", galat?.pesan ?: "ditolak server")
        }
    }
}
