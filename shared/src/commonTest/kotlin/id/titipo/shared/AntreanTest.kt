package id.titipo.shared

import id.titipo.shared.antrean.AksiAntrean
import id.titipo.shared.antrean.AlasanTolak
import id.titipo.shared.antrean.AntreanRepository
import id.titipo.shared.antrean.FotoSerahBaru
import id.titipo.shared.antrean.HasilEnqueue
import id.titipo.shared.antrean.HasilKirim
import id.titipo.shared.antrean.JenisAksi
import id.titipo.shared.antrean.PemrosesAntrean
import id.titipo.shared.antrean.PengirimAksi
import id.titipo.shared.antrean.StatusAntrean
import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.util.Jam
import id.titipo.shared.util.Ulid
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class JamUji(var t: Long = 1_800_000_000_000L) : Jam {
    override fun sekarangMs() = t
}

private class PengirimUji(private val skrip: MutableList<HasilKirim>) : PengirimAksi {
    val dikirim = mutableListOf<Pair<String, String?>>() // (Idempotency-Key, kode)
    override suspend fun kirim(aksi: AksiAntrean, foto: FotoSerahBaru?): HasilKirim {
        dikirim += aksi.id to aksi.kodePesanan
        return if (skrip.isEmpty()) HasilKirim.Sukses else skrip.removeAt(0)
    }
}

class AntreanTest {
    private val jam = JamUji()
    private val konfig = KonfigurasiBisnis.BAWAAN

    private fun repo(k: KonfigurasiBisnis = konfig) =
        AntreanRepository(buatDbUji(), jam, k, Random(7))

    private fun AntreanRepository.konfirmasi(kode: String) =
        antre(JenisAksi.KONFIRMASI, kode, """{"terima":true}""") as HasilEnqueue.Berhasil

    private fun foto(kode: String, ukuran: Int = 700 * 1024) = FotoSerahBaru(kode, "/tmp/$kode.jpg", ukuran, jam.t)

    @Test
    fun tulisLokalDuluDenganUlid26Karakter() {
        val r = repo()
        val aksi = r.konfirmasi("T-2026-000112").aksi
        assertEquals(26, aksi.id.length)
        assertTrue(Ulid.valid(aksi.id))
        assertEquals(StatusAntrean.MENUNGGU, aksi.status)
        assertEquals(1, r.jumlahMenunggu())
    }

    @Test
    fun fifoSatuPerSatuDenganJedaSatuDetik() = runTest {
        val r = repo()
        val a = r.konfirmasi("T-2026-000001").aksi
        val b = r.konfirmasi("T-2026-000002").aksi
        val kirim = PengirimUji(mutableListOf())
        val jedaTercatat = mutableListOf<Long>()
        PemrosesAntrean(r, kirim, jam, konfig, jeda = { jedaTercatat += it }).prosesSemua()
        assertEquals(listOf(a.id, b.id), kirim.dikirim.map { it.first })
        assertEquals(listOf(1_000L), jedaTercatat) // jeda hanya di antara dua aksi
        assertEquals(0, r.jumlahMenunggu())
        assertTrue(r.semua().all { it.status == StatusAntrean.SELESAI })
    }

    @Test
    fun retryJaringanPakaiIdempotencyKeySamaTiap30DetikTanpaGanda() = runTest {
        val r = repo()
        val aksi = r.konfirmasi("T-2026-000003").aksi
        val kirim = PengirimUji(mutableListOf(HasilKirim.GagalSementara("putus"), HasilKirim.GagalSementara("putus")))
        val jeda = mutableListOf<Long>()
        PemrosesAntrean(r, kirim, jam, konfig, jeda = { jeda += it }).prosesSemua()
        assertEquals(3, kirim.dikirim.size)
        assertEquals(setOf(aksi.id), kirim.dikirim.map { it.first }.toSet()) // kunci identik di tiap retry
        assertEquals(listOf(30_000L, 30_000L), jeda)
        val akhir = r.semua().single()
        assertEquals(StatusAntrean.SELESAI, akhir.status)
        assertEquals(2, akhir.percobaan)
    }

    @Test
    fun setelah24JamDitandaiGagalLalu_kirimUlangManual() = runTest {
        val r = repo()
        val aksi = r.konfirmasi("T-2026-000004").aksi
        jam.t += 24L * 3_600_000 + 1
        val kirim = PengirimUji(mutableListOf())
        PemrosesAntrean(r, kirim, jam, konfig, jeda = {}).prosesSemua()
        assertEquals(0, kirim.dikirim.size) // tidak dikirim lagi
        val gagal = r.semua().single()
        assertEquals(StatusAntrean.GAGAL, gagal.status) // tidak hilang diam-diam
        assertNotNull(gagal.galat)
        assertEquals(1, r.jumlahGagal())

        r.kirimUlangManual(aksi.id)
        PemrosesAntrean(r, kirim, jam, konfig, jeda = {}).prosesSemua()
        assertEquals(listOf(aksi.id), kirim.dikirim.map { it.first })
        assertEquals(StatusAntrean.SELESAI, r.semua().single().status)
    }

    @Test
    fun penolakanServer409TidakDiulang() = runTest {
        val r = repo()
        r.konfirmasi("T-2026-000005")
        val kirim = PengirimUji(mutableListOf(HasilKirim.DitolakServer("TRANSISI_ILEGAL", "sudah dikonfirmasi")))
        PemrosesAntrean(r, kirim, jam, konfig, jeda = {}).prosesSemua()
        assertEquals(1, kirim.dikirim.size)
        val a = r.semua().single()
        assertEquals(StatusAntrean.GAGAL, a.status)
        assertTrue(a.galat!!.startsWith("TRANSISI_ILEGAL"))
    }

    @Test
    fun batas50AksiPer24JamDitegakkan() {
        val r = repo()
        repeat(50) { assertIs<HasilEnqueue.Berhasil>(r.antre(JenisAksi.KONFIRMASI, "T-2026-" + it.toString().padStart(6, '0'), "{}")) }
        val ke51 = r.antre(JenisAksi.KONFIRMASI, "T-2026-000051", "{}")
        assertEquals(HasilEnqueue.Ditolak(AlasanTolak.BATAS_AKSI_24_JAM), ke51)
        jam.t += 24L * 3_600_000 + 1 // jendela bergulir lewat
        assertIs<HasilEnqueue.Berhasil>(r.antre(JenisAksi.KONFIRMASI, "T-2026-000052", "{}"))
    }

    @Test
    fun batasFotoDanUkuranDanFotoGandaTerakhirMenang() {
        val r = repo(KonfigurasiBisnis(batasAksiPer24Jam = 1000))
        assertEquals(
            HasilEnqueue.Ditolak(AlasanTolak.FOTO_TERLALU_BESAR),
            r.antre(JenisAksi.FOTO_SERAH, "T-2026-000001", "{}", foto("T-2026-000001", 800 * 1024 + 1)),
        )
        assertIs<HasilEnqueue.Berhasil>(r.antre(JenisAksi.FOTO_SERAH, "T-2026-000001", "{}", foto("T-2026-000001", 800 * 1024)))
        repeat(19) { assertIs<HasilEnqueue.Berhasil>(r.antre(JenisAksi.FOTO_SERAH, "T-2026-1" + it.toString().padStart(5, '0'), "{}", foto("T-2026-1" + it.toString().padStart(5, '0')))) }
        assertEquals(
            HasilEnqueue.Ditolak(AlasanTolak.BATAS_FOTO_ANTRE),
            r.antre(JenisAksi.FOTO_SERAH, "T-2026-999999", "{}", foto("T-2026-999999")),
        )
    }

    @Test
    fun fotoSerahTerbaruMenangDanLamaDiarsipkan() {
        val db = buatDbUji()
        val r = AntreanRepository(db, jam, KonfigurasiBisnis.BAWAAN, Random(1))
        r.antre(JenisAksi.FOTO_SERAH, "T-2026-000009", "{}", FotoSerahBaru("T-2026-000009", "/lama.jpg", 100, 1))
        r.antre(JenisAksi.FOTO_SERAH, "T-2026-000009", "{}", FotoSerahBaru("T-2026-000009", "/baru.jpg", 100, 2))
        assertEquals("/baru.jpg", db.skemaQueries.fotoAktif("T-2026-000009").executeAsOne().path)
        assertEquals(1, db.skemaQueries.fotoAktif("T-2026-000009").executeAsList().size)
    }

    @Test
    fun batasLimaPerubahanJadwalPerMinggu() {
        val r = repo()
        repeat(5) { assertIs<HasilEnqueue.Berhasil>(r.antre(JenisAksi.TUTUP_JADWAL, null, "{}")) }
        assertEquals(HasilEnqueue.Ditolak(AlasanTolak.BATAS_JADWAL_MINGGUAN), r.antre(JenisAksi.TUTUP_JADWAL, null, "{}"))
    }
}
