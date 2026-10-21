package id.titipo.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO diturunkan dari `docs/api/openapi.yaml` titipo-backend-service
 * (branch PR #10 BE-004, versi kontrak 1.0.0). Nama properti JSON dipertahankan snake_case
 * persis seperti kontrak. Uang = integer rupiah, berat = integer gram, waktu = ISO-8601 UTC (String).
 */

@Serializable
enum class StatusPesanan {
    @SerialName("menunggu_pembayaran") MENUNGGU_PEMBAYARAN,
    @SerialName("menunggu_konfirmasi") MENUNGGU_KONFIRMASI,
    @SerialName("disiapkan") DISIAPKAN,
    @SerialName("diserahkan") DISERAHKAN,
    @SerialName("selesai") SELESAI,
    @SerialName("kedaluwarsa") KEDALUWARSA,
    @SerialName("dialihkan") DIALIHKAN,
    @SerialName("dibatalkan_sistem") DIBATALKAN_SISTEM,
    @SerialName("dibatalkan_vendor") DIBATALKAN_VENDOR;

    /** Nilai kawat (wire) sesuai kontrak, dipakai untuk kolom `status` tabel lokal. */
    val kawat: String
        get() = when (this) {
            MENUNGGU_PEMBAYARAN -> "menunggu_pembayaran"
            MENUNGGU_KONFIRMASI -> "menunggu_konfirmasi"
            DISIAPKAN -> "disiapkan"
            DISERAHKAN -> "diserahkan"
            SELESAI -> "selesai"
            KEDALUWARSA -> "kedaluwarsa"
            DIALIHKAN -> "dialihkan"
            DIBATALKAN_SISTEM -> "dibatalkan_sistem"
            DIBATALKAN_VENDOR -> "dibatalkan_vendor"
        }

    companion object {
        fun dariKawat(nilai: String): StatusPesanan? = entries.firstOrNull { it.kawat == nilai }
    }
}

@Serializable
data class VendorRingkas(
    val id: String,
    val nama: String,
    @SerialName("jarak_km") val jarakKm: Double,
    val rating: Double,
    @SerialName("sisa_kuota") val sisaKuota: Int,
    val prioritas: Boolean,
)

@Serializable
data class DaftarVendor(val data: List<VendorRingkas>)

@Serializable
data class ItemKatalog(
    @SerialName("sku_lumbung") val skuLumbung: String,
    val nama: String,
    @SerialName("harga_per_kg") val hargaPerKg: Long,
    @SerialName("stok_gram") val stokGram: Long,
)

@Serializable
data class Katalog(
    /** Stempel snapshot (ISO-8601 UTC). Umur > 24 jam = kedaluwarsa. */
    val diperbarui: String,
    val data: List<ItemKatalog>,
)

@Serializable
data class ItemPesananBaru(
    @SerialName("sku_lumbung") val skuLumbung: String,
    @SerialName("jumlah_gram") val jumlahGram: Long,
)

@Serializable
data class PesananBaru(
    /** ULID pesanan dibuat klien (26 karakter). */
    val id: String,
    @SerialName("id_vendor") val idVendor: String,
    val item: List<ItemPesananBaru>,
)

@Serializable
data class PesananDibuat(
    val kode: String,
    @SerialName("total_bayar") val totalBayar: Long,
    @SerialName("kedaluwarsa_bayar") val kedaluwarsaBayar: String,
)

@Serializable
data class BayarPermintaan(@SerialName("id_gateway") val idGateway: String)

@Serializable
data class StatusRespons(val status: StatusPesanan)

@Serializable
data class KonfirmasiPermintaan(
    val terima: Boolean,
    val alasan: String? = null,
)

@Serializable
data class KonfirmasiRespons(
    val status: StatusPesanan,
    val dikonfirmasi: String? = null,
)

@Serializable
data class SerahRespons(
    val status: StatusPesanan,
    @SerialName("url_foto") val urlFoto: String,
)

@Serializable
data class RatingPermintaan(
    val bintang: Int,
    val catatan: String? = null,
)

/** Galat baku `AppError`: `{ "kode": "...", "pesan": "...", "detail": {} }`. */
@Serializable
data class GalatApi(
    val kode: String,
    val pesan: String,
)

/** Peran yang dikenali aplikasi mobile. `admin` hanya web, ditolak di mobile. */
enum class Peran { RUMAH, VENDOR }
