package id.titipo.shared.auth

import id.titipo.shared.api.PenyediaToken
import id.titipo.shared.model.Peran
import id.titipo.shared.util.Jam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val AUDIENS_TITIPO = "titipo"

/** Klaim JWT yang dibaca klien (tanpa verifikasi tanda tangan: itu tugas backend via JWKS). */
data class KlaimToken(
    val sub: String,
    val audiens: List<String>,
    /** Nilai mentah klaim peran: `role` (kontrak backend) atau `peran` (teks tiket). */
    val peranMentah: String?,
    val expDetik: Long?,
)

object JwtParser {
    @OptIn(ExperimentalEncodingApi::class)
    fun klaim(token: String): KlaimToken? {
        val bagian = token.split('.')
        if (bagian.size != 3) return null
        val obj = try {
            val json = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(bagian[1]).decodeToString()
            Json.parseToJsonElement(json) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        val aud = when (val a = obj["aud"]) {
            is JsonPrimitive -> listOfNotNull(a.contentOrNull)
            is JsonArray -> a.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            else -> emptyList()
        }
        return KlaimToken(
            sub = obj.teks("sub") ?: return null,
            audiens = aud,
            peranMentah = obj.teks("role") ?: obj.teks("peran"),
            expDetik = (obj["exp"] as? JsonPrimitive)?.longOrNull,
        )
    }

    private fun JsonObject.teks(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
}

/** Pemetaan klaim peran ke graf. `admin` hanya web: tidak ada layar admin di mobile. */
fun petakanPeran(mentah: String?): Peran? = when (mentah?.lowercase()) {
    "household", "rumah" -> Peran.RUMAH
    "vendor" -> Peran.VENDOR
    else -> null
}

/** Sesi aktif hasil login. */
data class Sesi(val token: String, val sub: String, val peran: Peran, val expDetik: Long?)

sealed interface HasilMasuk {
    data class Berhasil(val sesi: Sesi) : HasilMasuk
    /** Token bukan beraudiens `titipo`. */
    data object AudiensSalah : HasilMasuk
    /** Peran tidak dikenal/tidak didukung di mobile (mis. `admin`). */
    data object PeranDitolak : HasilMasuk
    data object Kedaluwarsa : HasilMasuk
    data object TokenRusak : HasilMasuk
}

/** Penyimpanan token terenkripsi (Android Keystore / iOS Keychain), diimplementasikan di platform. */
interface PenyimpanToken {
    suspend fun simpan(token: String)
    suspend fun baca(): String?
    suspend fun hapus()
}

/**
 * Pengelola sesi: memvalidasi audiens `titipo`, memilih peran, menyimpan terenkripsi.
 * Ganti peran = logout lalu login ulang (tidak ada toggle).
 * Aplikasi tidak pernah menyimpan service key titipo→lumbung; hanya JWT pengguna.
 */
class PengelolaSesi(
    private val penyimpan: PenyimpanToken,
    private val jam: Jam = Jam.Sistem,
) : PenyediaToken {
    private val _sesi = MutableStateFlow<Sesi?>(null)
    val sesi: StateFlow<Sesi?> = _sesi.asStateFlow()

    override suspend fun token(): String? = _sesi.value?.token

    fun periksa(token: String): HasilMasuk {
        val k = JwtParser.klaim(token) ?: return HasilMasuk.TokenRusak
        if (AUDIENS_TITIPO !in k.audiens) return HasilMasuk.AudiensSalah
        val peran = petakanPeran(k.peranMentah) ?: return HasilMasuk.PeranDitolak
        if (k.expDetik != null && k.expDetik * 1000 <= jam.sekarangMs()) return HasilMasuk.Kedaluwarsa
        return HasilMasuk.Berhasil(Sesi(token, k.sub, peran, k.expDetik))
    }

    /** Dipanggil setelah login sukses; token yang gagal validasi TIDAK disimpan. */
    suspend fun masuk(token: String): HasilMasuk {
        val hasil = periksa(token)
        if (hasil is HasilMasuk.Berhasil) {
            penyimpan.simpan(token)
            _sesi.value = hasil.sesi
        }
        return hasil
    }

    /** Pulihkan sesi saat buka dingin; token tersimpan yang tak lagi valid dihapus. */
    suspend fun pulihkan(): Sesi? {
        val tersimpan = penyimpan.baca() ?: return null
        val hasil = periksa(tersimpan)
        return if (hasil is HasilMasuk.Berhasil) {
            _sesi.value = hasil.sesi
            hasil.sesi
        } else {
            penyimpan.hapus()
            null
        }
    }

    suspend fun keluar() {
        penyimpan.hapus()
        _sesi.value = null
    }
}
