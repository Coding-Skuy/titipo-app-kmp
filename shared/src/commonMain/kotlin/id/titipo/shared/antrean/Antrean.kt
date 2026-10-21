package id.titipo.shared.antrean

import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.db.Antrean as BarisAntrean
import id.titipo.shared.db.TitipoDb
import id.titipo.shared.util.Jam
import id.titipo.shared.util.Ulid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Aksi vendor yang boleh antre luring (AGENTS.md butir g). Checkout dan bayar wajib online. */
enum class JenisAksi(val kawat: String) {
    KONFIRMASI("konfirmasi"),
    TOLAK("tolak"),
    TUTUP_JADWAL("tutup_jadwal"),
    FOTO_SERAH("foto_serah");

    companion object {
        fun dariKawat(nilai: String): JenisAksi =
            entries.first { it.kawat == nilai }
    }
}

enum class StatusAntrean(val kawat: String) { MENUNGGU("menunggu"), SELESAI("selesai"), GAGAL("gagal") }

/** Aksi di antrean. [id] = ULID 26 karakter = nilai header `Idempotency-Key`. */
data class AksiAntrean(
    val id: String,
    val jenis: JenisAksi,
    val kodePesanan: String?,
    val muatanJson: String,
    val status: StatusAntrean,
    val percobaan: Int,
    val dibuatMs: Long,
    val galat: String?,
)

/** Foto serah yang sudah dikompres platform; hanya metadata+path yang disimpan di DB. */
data class FotoSerahBaru(
    val kodePesanan: String,
    val path: String,
    val ukuranByte: Int,
    val waktuMs: Long,
)

sealed interface HasilEnqueue {
    data class Berhasil(val aksi: AksiAntrean) : HasilEnqueue
    data class Ditolak(val alasan: AlasanTolak) : HasilEnqueue
}

enum class AlasanTolak {
    BATAS_AKSI_24_JAM,
    BATAS_FOTO_ANTRE,
    FOTO_TERLALU_BESAR,
    BATAS_JADWAL_MINGGUAN,
}

/** Hasil satu upaya kirim ke server. */
sealed interface HasilKirim {
    /** 2xx — termasuk 200 pengiriman ulang idempoten. */
    data object Sukses : HasilKirim

    /** Jaringan putus / 5xx / 408 / 429 / 401 (menunggu token baru): coba lagi. */
    data class GagalSementara(val pesan: String) : HasilKirim

    /** 4xx lain (mis. 409/422): server menolak; mengirim ulang otomatis tidak akan membantu. */
    data class DitolakServer(val kode: String, val pesan: String) : HasilKirim
}

/** Pengirim satu aksi antrean; implementasi Ktor ada di `api/PengirimAksiKtor`. */
fun interface PengirimAksi {
    suspend fun kirim(aksi: AksiAntrean, foto: FotoSerahBaru?): HasilKirim
}

private const val HARI_MS = 24L * 60 * 60 * 1000
private const val MINGGU_MS = 7 * HARI_MS

/**
 * Antrean offline-first (SQLDelight). Prinsip:
 * - tulis lokal dahulu (satu transaksi, tanpa menunggu jaringan);
 * - FIFO, satu per satu, jeda [KonfigurasiBisnis.jedaAntarKirimMs];
 * - setiap aksi memakai `Idempotency-Key` = id ULID yang sama di setiap retry (tidak pernah ganda);
 * - gagal jaringan diulang tiap [KonfigurasiBisnis.jedaRetryMs] hingga [KonfigurasiBisnis.batasRetryMs] sejak dibuat,
 *   lalu ditandai gagal (terlihat, tidak hilang) dan bisa [kirimUlangManual].
 */
class AntreanRepository(
    private val db: TitipoDb,
    private val jam: Jam = Jam.Sistem,
    private val konfigurasi: KonfigurasiBisnis = KonfigurasiBisnis.BAWAAN,
    private val acak: Random = Random.Default,
) {
    private val q get() = db.skemaQueries

    fun antre(
        jenis: JenisAksi,
        kodePesanan: String?,
        muatanJson: String,
        foto: FotoSerahBaru? = null,
    ): HasilEnqueue {
        require((jenis == JenisAksi.FOTO_SERAH) == (foto != null)) { "foto wajib tepat untuk jenis foto_serah" }
        val sekarang = jam.sekarangMs()
        lateinit var hasil: HasilEnqueue
        db.transaction {
            val tolak = periksaBatas(jenis, foto, sekarang)
            if (tolak != null) {
                hasil = HasilEnqueue.Ditolak(tolak)
                return@transaction
            }
            val id = Ulid.baru(jam, acak)
            q.sisipkanAntrean(id, jenis.kawat, kodePesanan, muatanJson, sekarang)
            if (foto != null) {
                // Foto ganda: yang terakhir menang, yang lama diarsipkan.
                q.arsipkanFotoLama(foto.kodePesanan)
                q.simpanFoto(Ulid.baru(jam, acak), foto.kodePesanan, foto.path, foto.ukuranByte.toLong(), foto.waktuMs, id)
            }
            hasil = HasilEnqueue.Berhasil(q.antreanById(id).executeAsOne().domain())
        }
        return hasil
    }

    private fun periksaBatas(jenis: JenisAksi, foto: FotoSerahBaru?, sekarang: Long): AlasanTolak? {
        if (q.jumlahAksiSejak(sekarang - HARI_MS).executeAsOne() >= konfigurasi.batasAksiPer24Jam) {
            return AlasanTolak.BATAS_AKSI_24_JAM
        }
        if (foto != null) {
            if (foto.ukuranByte > konfigurasi.batasUkuranFotoByte) return AlasanTolak.FOTO_TERLALU_BESAR
            if (q.jumlahFotoBelumSelesai().executeAsOne() >= konfigurasi.batasFotoAntre) return AlasanTolak.BATAS_FOTO_ANTRE
        }
        // ASUMSI: "5 perubahan jadwal per minggu" = jendela bergulir 7 hari (kontrak tak menentukan awal pekan).
        if (jenis == JenisAksi.TUTUP_JADWAL &&
            q.jumlahJenisSejak(jenis.kawat, sekarang - MINGGU_MS).executeAsOne() >= konfigurasi.batasPerubahanJadwalPerMinggu
        ) {
            return AlasanTolak.BATAS_JADWAL_MINGGUAN
        }
        return null
    }

    fun semua(): List<AksiAntrean> = q.semuaAntrean().executeAsList().map { it.domain() }

    fun jumlahMenunggu(): Long = q.jumlahMenunggu().executeAsOne()

    fun jumlahGagal(): Long = q.jumlahGagal().executeAsOne()

    fun kirimUlangManual(id: String) {
        // Jam dimulai ulang agar batas 24 jam tidak langsung terlewat; aksi pindah ke ujung FIFO.
        q.kirimUlangManual(jam.sekarangMs(), id)
    }

    internal fun berikutnya(): AksiAntrean? = q.antreanBerikutnya().executeAsOneOrNull()?.domain()

    internal fun foto(id: String): FotoSerahBaru? =
        q.fotoByAntrean(id).executeAsOneOrNull()?.let {
            FotoSerahBaru(it.kode_pesanan, it.path, it.ukuran_byte.toInt(), it.waktu_ms)
        }

    internal fun catatPercobaan(id: String, galat: String?) =
        q.catatPercobaan(jam.sekarangMs(), galat, id)

    internal fun selesai(id: String) {
        db.transaction {
            q.tandaiSelesai(id)
            q.tandaiFotoSinkron(id)
        }
    }

    internal fun gagal(id: String, galat: String) = q.tandaiGagal(galat, id)

    private fun BarisAntrean.domain() = AksiAntrean(
        id = id,
        jenis = JenisAksi.dariKawat(jenis),
        kodePesanan = kode_pesanan,
        muatanJson = muatan_json,
        status = StatusAntrean.entries.first { it.kawat == status },
        percobaan = percobaan.toInt(),
        dibuatMs = dibuat_ms,
        galat = galat,
    )
}

/**
 * Pemroses antrean. Panggil [prosesSemua] dari satu coroutine (mis. saat online/foreground);
 * fungsi ini kembali saat antrean kosong, atau saat aksi terdepan menunggu retry bila [tungguRetry] = false.
 */
class PemrosesAntrean(
    private val repo: AntreanRepository,
    private val pengirim: PengirimAksi,
    private val jam: Jam = Jam.Sistem,
    private val konfigurasi: KonfigurasiBisnis = KonfigurasiBisnis.BAWAAN,
    private val jeda: suspend (Long) -> Unit = { delay(it) },
) {
    /**
     * Kirim satu per satu secara FIFO. Aksi terdepan yang gagal sementara DITUNGGU (retry tiap 30 detik)
     * sebelum aksi berikutnya diproses, agar urutan tetap terjaga.
     */
    suspend fun prosesSemua(tungguRetry: Boolean = true) {
        var adaSebelumnyaSelesai = false
        while (true) {
            val aksi = repo.berikutnya() ?: return
            // Jeda 1 detik hanya di antara dua aksi berbeda (bukan sebelum retry aksi yang sama).
            if (adaSebelumnyaSelesai) jeda(konfigurasi.jedaAntarKirimMs)
            val selesai = prosesSatu(aksi)
            adaSebelumnyaSelesai = selesai
            if (!selesai) {
                if (!tungguRetry) return
                jeda(konfigurasi.jedaRetryMs)
            }
        }
    }

    /** @return true bila aksi sudah final (selesai/gagal permanen), false bila perlu retry. */
    internal suspend fun prosesSatu(aksi: AksiAntrean): Boolean {
        if (jam.sekarangMs() - aksi.dibuatMs > konfigurasi.batasRetryMs) {
            repo.gagal(aksi.id, "BATAS_24_JAM_TERLEWAT: ${aksi.galat ?: "tak ada respons server"}")
            return true
        }
        val foto = if (aksi.jenis == JenisAksi.FOTO_SERAH) repo.foto(aksi.id) else null
        val hasil = try {
            pengirim.kirim(aksi, foto)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilKirim.GagalSementara(e.message ?: e::class.simpleName ?: "galat")
        }
        return when (hasil) {
            HasilKirim.Sukses -> { repo.selesai(aksi.id); true }
            is HasilKirim.GagalSementara -> { repo.catatPercobaan(aksi.id, hasil.pesan); false }
            is HasilKirim.DitolakServer -> {
                repo.catatPercobaan(aksi.id, "${hasil.kode}: ${hasil.pesan}")
                repo.gagal(aksi.id, "${hasil.kode}: ${hasil.pesan}")
                true
            }
        }
    }
}
