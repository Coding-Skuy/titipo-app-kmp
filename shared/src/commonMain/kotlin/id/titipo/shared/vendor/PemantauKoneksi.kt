package id.titipo.shared.vendor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pemantau keterhubungan ke backend lewat `GET /healthz` (tanpa auth, ada di kontrak). Lintas-platform,
 * tanpa API Android/iOS. "Luring" = server tak terjangkau (bukan sekadar Wi-Fi mati), tepat untuk keputusan
 * mengantre aksi. [padaOnline] dipanggil saat berpindah luring -> online (mis. memicu pemroses antrean).
 */
class PemantauKoneksi(
    private val http: HttpClient,
    baseUrl: String,
    private val jedaMs: Long = 15_000,
    private val padaOnline: suspend () -> Unit = {},
) {
    private val alamat = baseUrl.trimEnd('/') + "/healthz"
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    suspend fun periksa(): Boolean {
        val ok = try {
            http.get(alamat).status.value in 200..299
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        val sebelumnya = _online.value
        _online.value = ok
        if (ok && !sebelumnya) padaOnline()
        return ok
    }

    /** Loop pemantauan; jalankan di scope aplikasi dan batalkan saat tidak perlu. */
    suspend fun jalankan() {
        while (true) {
            periksa()
            delay(jedaMs)
        }
    }
}
