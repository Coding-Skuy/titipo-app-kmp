package id.titipo.shared.vendor

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import id.titipo.shared.antrean.AntreanRepository
import id.titipo.shared.antrean.FotoSerahBaru
import id.titipo.shared.antrean.HasilEnqueue
import id.titipo.shared.antrean.JenisAksi
import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.db.TitipoDb
import id.titipo.shared.model.StatusPesanan
import id.titipo.shared.util.Jam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Alasan tolak yang disediakan UX (`Stok habis` / `Tutup`). */
enum class AlasanTolakVendor(val teks: String) { STOK_HABIS("Stok habis"), TUTUP("Tutup") }

data class ItemMasuk(val nama: String, val jumlahGram: Long)

/** Kartu pesanan masuk: kode, item ringkas, total, timer konfirmasi. */
data class PesananMasuk(
    val kode: String,
    val totalBayar: Long,
    val dibuatMs: Long,
    val status: StatusPesanan,
    val item: List<ItemMasuk>,
    val sinkron: Boolean,
)

/** Sisa waktu konfirmasi 30 menit (tidak negatif), dihitung dari saat pesanan masuk. */
object WaktuKonfirmasi {
    fun sisaDetik(dibuatMs: Long, sekarangMs: Long, konfigurasi: KonfigurasiBisnis): Long =
        ((dibuatMs + konfigurasi.batasKonfirmasiMenit * 60_000L - sekarangMs) / 1000).coerceAtLeast(0)

    fun sisaMenit(dibuatMs: Long, sekarangMs: Long, konfigurasi: KonfigurasiBisnis): Long =
        (sisaDetik(dibuatMs, sekarangMs, konfigurasi) + 59) / 60
}

/**
 * Sisi vendor. Sumber daftar = cermin lokal tabel `pesanan`, diisi dari push/sinkron lewat [simpanPesananMasuk]
 * (kontrak backend belum punya endpoint daftar pesanan vendor; lihat ADR-0005).
 * Aksi vendor (terima, tolak, foto serah) ditulis LOKAL dahulu lalu diantrekan: aman luring.
 */
class RepositoriVendor(
    private val db: TitipoDb,
    private val antrean: AntreanRepository,
    private val jam: Jam = Jam.Sistem,
) {
    private val q get() = db.skemaQueries

    val masuk: Flow<List<PesananMasuk>> =
        q.pesananMasukVendor().asFlow().mapToList(Dispatchers.Default).map { baris ->
            baris.map { b ->
                PesananMasuk(
                    kode = b.kode ?: b.id,
                    totalBayar = b.total_bayar,
                    dibuatMs = b.dibuat_ms,
                    status = StatusPesanan.dariKawat(b.status) ?: StatusPesanan.MENUNGGU_KONFIRMASI,
                    item = q.itemByPesanan(b.id).executeAsList().map { ItemMasuk(it.nama, it.jumlah_gram) },
                    sinkron = b.sinkron == 1L,
                )
            }
        }

    /** Dipanggil oleh penerima push/sinkron: kode, total, saat masuk, dan item pesanan. */
    fun simpanPesananMasuk(kode: String, idVendor: String, totalBayar: Long, dibuatMs: Long, item: List<ItemMasuk>) {
        db.transaction {
            q.simpanPesanan(kode, kode, idVendor, StatusPesanan.MENUNGGU_KONFIRMASI.kawat, totalBayar, 0, totalBayar, null, dibuatMs, 1)
            item.forEachIndexed { i, it -> q.simpanItem(kode, "item-$i", it.nama, 0, it.jumlahGram, 1) }
        }
    }

    /** Geser kanan: Terima (boleh luring). Status lokal langsung `disiapkan`, sinkron = 0 sampai server menjawab. */
    fun terima(kode: String): HasilEnqueue = aksi(kode, JenisAksi.KONFIRMASI, """{"terima":true}""", StatusPesanan.DISIAPKAN)

    /** Geser kiri: Tolak beralasan (boleh luring). */
    fun tolak(kode: String, alasan: AlasanTolakVendor): HasilEnqueue =
        aksi(kode, JenisAksi.TOLAK, buildJsonObject { put("terima", false); put("alasan", alasan.teks) }.toString(), StatusPesanan.DIBATALKAN_VENDOR)

    private fun aksi(kode: String, jenis: JenisAksi, muatan: String, statusLokal: StatusPesanan): HasilEnqueue {
        var hasil: HasilEnqueue? = null
        db.transaction {
            hasil = antrean.antre(jenis, kode, muatan)
            if (hasil is HasilEnqueue.Berhasil) q.ubahStatusById(statusLokal.kawat, 0, kode)
        }
        return hasil!!
    }

    /**
     * Selesai serah: wajib ada foto (UI mematikan tombol tanpa foto; penjaga ganda di sini).
     * Foto ganda: yang terakhir menang, yang lama diarsipkan (diatur [AntreanRepository]).
     */
    fun selesaiSerah(kode: String, foto: FotoSerahBaru?): HasilEnqueue {
        requireNotNull(foto) { "foto serah wajib; tanpa foto fee ditahan" }
        var hasil: HasilEnqueue? = null
        db.transaction {
            hasil = antrean.antre(JenisAksi.FOTO_SERAH, kode, "{}", foto)
            if (hasil is HasilEnqueue.Berhasil) q.ubahStatusById(StatusPesanan.DISERAHKAN.kawat, 0, kode)
        }
        return hasil!!
    }

    fun item(kode: String): List<ItemMasuk> =
        q.pesananByKode(kode).executeAsOneOrNull()?.let { b ->
            q.itemByPesanan(b.id).executeAsList().map { ItemMasuk(it.nama, it.jumlah_gram) }
        }.orEmpty()
}

/** Checklist kemas: tombol "Siap serah" aktif hanya bila SEMUA item dicentang. */
data class ChecklistKemas(val item: List<ItemMasuk>, val dicentang: Set<Int> = emptySet()) {
    val siapSerah: Boolean get() = item.isNotEmpty() && dicentang.size == item.size
    fun ubah(indeks: Int, centang: Boolean): ChecklistKemas {
        require(indeks in item.indices)
        return copy(dicentang = if (centang) dicentang + indeks else dicentang - indeks)
    }
}

/** Aturan foto serah: sisi terpanjang 1280 px, kompres JPEG sampai maksimal 800 KB. */
object KompresFoto {
    const val SISI_MAKS_PX = 1280

    /** Ukuran akhir (lebar, tinggi) dengan sisi terpanjang <= [maks]; tidak memperbesar. */
    fun skala(lebar: Int, tinggi: Int, maks: Int = SISI_MAKS_PX): Pair<Int, Int> {
        require(lebar > 0 && tinggi > 0)
        val terpanjang = maxOf(lebar, tinggi)
        if (terpanjang <= maks) return lebar to tinggi
        val f = maks.toDouble() / terpanjang
        return maxOf(1, (lebar * f).toInt()) to maxOf(1, (tinggi * f).toInt())
    }

    /**
     * Cari kualitas JPEG tertinggi (100 turun ke [kualitasMin]) yang menghasilkan ukuran <= [batasByte].
     * [ukuranUntuk] mengompres dan mengembalikan ukuran byte hasil. Null bila kualitas minimum tetap terlalu besar.
     */
    fun cariKualitas(batasByte: Int, kualitasMin: Int = 30, ukuranUntuk: (Int) -> Int): Int? {
        var rendah = kualitasMin
        var tinggi = 100
        var terbaik: Int? = null
        while (rendah <= tinggi) {
            val tengah = (rendah + tinggi) / 2
            if (ukuranUntuk(tengah) <= batasByte) { terbaik = tengah; rendah = tengah + 1 } else tinggi = tengah - 1
        }
        return terbaik
    }
}
