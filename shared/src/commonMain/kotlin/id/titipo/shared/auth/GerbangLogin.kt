package id.titipo.shared.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface HasilLogin {
    data class Token(val jwt: String) : HasilLogin
    data object KredensialSalah : HasilLogin
    data class Gagal(val pesan: String) : HasilLogin
}

/** Login dilakukan di layanan auth Lumbung; TitipO tidak punya endpoint login. */
fun interface GerbangLogin {
    suspend fun masuk(email: String, sandi: String): HasilLogin
}

@Serializable
private data class LoginPermintaan(val email: String, val password: String, val audience: String)

@Serializable
private data class LoginRespons(@SerialName("access_token") val accessToken: String)

/**
 * ASUMSI KONTRAK (belum ada di repo manapun): kontrak login Lumbung tidak tercantum di openapi.yaml
 * TitipO. Diasumsikan `POST {baseUrlAuth}/auth/login` dengan `{email, password, audience:"titipo"}` dan
 * respons `{access_token}`. README app menyebut login "email/OTP"; alur OTP belum dikontrakkan.
 * Ganti implementasi ini (bukan UI/sesi) setelah kontrak Lumbung terbit.
 */
class LoginLumbungKtor(
    private val http: HttpClient,
    private val baseUrlAuth: String,
) : GerbangLogin {
    override suspend fun masuk(email: String, sandi: String): HasilLogin = try {
        val r = http.post("${baseUrlAuth.trimEnd('/')}/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginPermintaan(email, sandi, AUDIENS_TITIPO))
        }
        when (r.status.value) {
            in 200..299 -> HasilLogin.Token(r.body<LoginRespons>().accessToken)
            400, 401, 403 -> HasilLogin.KredensialSalah
            else -> HasilLogin.Gagal("HTTP ${r.status.value}: ${r.bodyAsText().take(120)}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        HasilLogin.Gagal(e.message ?: "jaringan")
    }
}

sealed interface HasilAlurLogin {
    /** Login sukses; [peran] menentukan graf navigasi. */
    data class Sukses(val sesi: Sesi) : HasilAlurLogin
    data object KredensialSalah : HasilAlurLogin
    /** Token bukan beraudiens `titipo`. */
    data object AudiensSalah : HasilAlurLogin
    /** Peran tidak didukung di mobile (mis. admin) atau klaim peran tak ada. */
    data object PeranDitolak : HasilAlurLogin
    data object TokenKedaluwarsa : HasilAlurLogin
    data class Gagal(val pesan: String) : HasilAlurLogin
}

/** Alur login: gerbang Lumbung -> validasi audiens/peran -> simpan terenkripsi. */
class AlurLogin(
    private val gerbang: GerbangLogin,
    private val pengelola: PengelolaSesi,
) {
    suspend fun jalankan(email: String, sandi: String): HasilAlurLogin {
        if (email.isBlank() || sandi.isBlank()) return HasilAlurLogin.KredensialSalah
        return when (val login = gerbang.masuk(email.trim(), sandi)) {
            HasilLogin.KredensialSalah -> HasilAlurLogin.KredensialSalah
            is HasilLogin.Gagal -> HasilAlurLogin.Gagal(login.pesan)
            is HasilLogin.Token -> when (val h = pengelola.masuk(login.jwt)) {
                is HasilMasuk.Berhasil -> HasilAlurLogin.Sukses(h.sesi)
                HasilMasuk.AudiensSalah -> HasilAlurLogin.AudiensSalah
                HasilMasuk.PeranDitolak -> HasilAlurLogin.PeranDitolak
                HasilMasuk.Kedaluwarsa -> HasilAlurLogin.TokenKedaluwarsa
                HasilMasuk.TokenRusak -> HasilAlurLogin.Gagal("token dari server tidak terbaca")
            }
        }
    }
}
