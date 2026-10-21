package id.titipo.shared.rumah

import id.titipo.shared.api.HasilApi
import id.titipo.shared.api.TitipoApi
import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.db.TitipoDb
import id.titipo.shared.format.Format
import id.titipo.shared.model.DaftarVendor
import id.titipo.shared.model.ItemKatalog
import id.titipo.shared.model.ItemPesananBaru
import id.titipo.shared.model.Katalog
import id.titipo.shared.model.PesananBaru
import id.titipo.shared.model.PesananDibuat
import id.titipo.shared.model.StatusPesanan
import id.titipo.shared.util.Jam
import id.titipo.shared.util.Ulid
import id.titipo.shared.validasi.BarisKeranjang
import id.titipo.shared.validasi.HitungKeranjang
import id.titipo.shared.validasi.RingkasanKeranjang
import id.titipo.shared.validasi.UmurKatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant

/** Isi keranjang: satu pesanan satu vendor (jumlah dalam gram per SKU). */
data class Keranjang(
    val idVendor: String? = null,
    val jumlahGram: Map<String, Long> = emptyMap(),
)

sealed interface HasilUbah {
    data object Ok : HasilUbah
    /** Item dari vendor lain: keranjang lama harus dikosongkan dahulu (satu pesanan satu vendor). */
    data class PerluGantiVendor(val vendorLama: String) : HasilUbah
    data object KatalogKedaluwarsa : HasilUbah
}

/** Katalog yang sedang dimuat untuk satu vendor. */
data class KatalogVendor(val idVendor: String, val katalog: Katalog, val dimuatMs: Long)

sealed interface HasilMuat<out T> {
    data class Ok<T>(val data: T) : HasilMuat<T>
    /** Server 409 `KATALOG_KEDALUWARSA` / snapshot >24 jam: tombol pesan terkunci hingga tarik ulang. */
    data class Kedaluwarsa(val pesan: String) : HasilMuat<Nothing>
    data class Galat(val pesan: String) : HasilMuat<Nothing>
}

sealed interface HasilCheckout {
    data class Berhasil(val pesanan: PesananDibuat, val idPesanan: String) : HasilCheckout
    data class Ditolak(val kode: String, val pesan: String) : HasilCheckout
    data class GagalJaringan(val pesan: String) : HasilCheckout
}

sealed interface HasilBayar {
    data class Berhasil(val status: StatusPesanan) : HasilBayar
    data class Ditolak(val kode: String, val pesan: String) : HasilBayar
    data class GagalJaringan(val pesan: String) : HasilBayar
}

/** Terjemahan kode galat kontrak menjadi teks UI. Kode tak dikenal memakai pesan server. */
fun pesanDariKode(kode: String, pesanServer: String): String = when (kode) {
    "KATALOG_KEDALUWARSA" -> "Katalog kedaluwarsa, tarik ulang untuk memuat."
    "STOK_HABIS", "STOK_KEDALUWARSA" -> "Stok sudah berubah, periksa keranjang."
    "DI_LUAR_JAM_PESAN" -> "Pesan hanya dibuka pukul 05.00 sampai 20.00."
    "BERAT_MELEBIHI_BATAS" -> "Total berat melebihi batas, kurangi jumlah."
    "BARIS_MELEBIHI_BATAS" -> "Jumlah jenis barang melebihi batas."
    "SKU_TIDAK_ADA" -> "Ada barang yang tidak tersedia lagi."
    "ITEM_KOSONG" -> "Keranjang masih kosong."
    "SKU_DUPLIKAT" -> "Ada barang yang terdaftar dua kali."
    else -> pesanServer
}

/**
 * Orkestrasi sisi rumah tangga: keranjang, katalog, checkout (WAJIB online), bayar, rating.
 * Angka di sini hanya PRATINJAU; total sah datang dari server (`PesananDibuat.totalBayar`).
 * Checkout tidak pernah antre luring: gagal jaringan dikembalikan ke pengguna.
 */
class SesiBelanja(
    private val api: TitipoApi,
    private val db: TitipoDb,
    private val konfigurasi: KonfigurasiBisnis = KonfigurasiBisnis.BAWAAN,
    private val jam: Jam = Jam.Sistem,
) {
    private val _keranjang = MutableStateFlow(Keranjang())
    val keranjang: StateFlow<Keranjang> = _keranjang.asStateFlow()

    private val _katalog = MutableStateFlow<KatalogVendor?>(null)
    val katalog: StateFlow<KatalogVendor?> = _katalog.asStateFlow()

    private val _pesananAktif = MutableStateFlow<PesananAktif?>(null)
    val pesananAktif: StateFlow<PesananAktif?> = _pesananAktif.asStateFlow()

    suspend fun muatVendor(lat: Double, lng: Double, radiusKm: Int = 5): HasilMuat<DaftarVendor> =
        when (val h = api.vendorTerdekat(lat, lng, radiusKm)) {
            is HasilApi.Sukses -> HasilMuat.Ok(h.data)
            is HasilApi.GalatHttp -> HasilMuat.Galat(pesanDariKode(h.galat?.kode ?: "HTTP_${h.status}", h.galat?.pesan ?: "galat ${h.status}"))
            is HasilApi.GalatJaringan -> HasilMuat.Galat("Tidak ada koneksi: ${h.pesan}")
        }

    suspend fun muatKatalog(idVendor: String): HasilMuat<KatalogVendor> =
        when (val h = api.katalog(idVendor, konfigurasi.batasUnduhKatalogByte)) {
            is HasilApi.Sukses -> {
                val kv = KatalogVendor(idVendor, h.data, jam.sekarangMs())
                _katalog.value = kv
                if (UmurKatalog.kedaluwarsa(h.data.diperbarui, jam.sekarangMs(), konfigurasi)) {
                    HasilMuat.Kedaluwarsa("Katalog kedaluwarsa, tarik ulang untuk memuat.")
                } else {
                    HasilMuat.Ok(kv)
                }
            }
            is HasilApi.GalatHttp ->
                if (h.galat?.kode == "KATALOG_KEDALUWARSA") HasilMuat.Kedaluwarsa(pesanDariKode("KATALOG_KEDALUWARSA", ""))
                else HasilMuat.Galat(pesanDariKode(h.galat?.kode ?: "HTTP_${h.status}", h.galat?.pesan ?: "galat ${h.status}"))
            is HasilApi.GalatJaringan -> HasilMuat.Galat("Tidak ada koneksi: ${h.pesan}")
        }

    /** Katalog terkunci bila umur snapshot lebih dari 24 jam (hingga tarik ulang). */
    fun katalogTerkunci(): Boolean {
        val k = _katalog.value ?: return true
        return UmurKatalog.kedaluwarsa(k.katalog.diperbarui, jam.sekarangMs(), konfigurasi)
    }

    fun stempelKatalog(): String? =
        _katalog.value?.let { Format.jamWib(it.katalog.diperbarui, konfigurasi.offsetWibJam) }?.let { "Diperbarui $it" }

    /** Ubah jumlah (gram) satu item katalog; delta boleh negatif. Nilai di bawah 0 menjadi 0. */
    fun ubahJumlah(idVendor: String, item: ItemKatalog, deltaGram: Long): HasilUbah {
        if (deltaGram > 0 && katalogTerkunci()) return HasilUbah.KatalogKedaluwarsa
        val sekarang = _keranjang.value
        if (sekarang.idVendor != null && sekarang.idVendor != idVendor && sekarang.jumlahGram.isNotEmpty()) {
            return HasilUbah.PerluGantiVendor(sekarang.idVendor)
        }
        _keranjang.update { k ->
            val baru = ((k.jumlahGram[item.skuLumbung] ?: 0) + deltaGram).coerceIn(0, item.stokGram)
            val peta = if (baru == 0L) k.jumlahGram - item.skuLumbung else k.jumlahGram + (item.skuLumbung to baru)
            Keranjang(if (peta.isEmpty()) null else idVendor, peta)
        }
        return HasilUbah.Ok
    }

    fun kosongkan() {
        _keranjang.value = Keranjang()
    }

    fun ringkasan(): RingkasanKeranjang {
        val k = _keranjang.value
        val katalog = _katalog.value?.takeIf { it.idVendor == k.idVendor }?.katalog
        val baris = katalog?.data.orEmpty().mapNotNull { item ->
            k.jumlahGram[item.skuLumbung]?.let { BarisKeranjang(item, it) }
        }
        return HitungKeranjang.hitung(baris, konfigurasi)
    }

    /** Pesan dibuka 05.00 sampai 20.00 WIB (diperiksa lagi oleh server; ini hanya penjaga awal). */
    fun dalamJamPesan(): Boolean {
        val jamWib = Format.jamDindingWib(jam.sekarangMs(), konfigurasi.offsetWibJam)
        return jamWib >= konfigurasi.jamPesanMulai && jamWib < konfigurasi.jamPesanSelesai
    }

    /** Checkout online dengan `Idempotency-Key` = ULID pesanan. Gagal jaringan: tidak diantrekan. */
    suspend fun checkout(idPesanan: String = Ulid.baru(jam)): HasilCheckout {
        val k = _keranjang.value
        val vendor = k.idVendor ?: return HasilCheckout.Ditolak("ITEM_KOSONG", pesanDariKode("ITEM_KOSONG", ""))
        val ringkas = ringkasan()
        if (!ringkas.valid) return HasilCheckout.Ditolak("KERANJANG_TIDAK_VALID", "Periksa keranjang sebelum bayar.")
        if (katalogTerkunci()) return HasilCheckout.Ditolak("KATALOG_KEDALUWARSA", pesanDariKode("KATALOG_KEDALUWARSA", ""))
        if (!dalamJamPesan()) return HasilCheckout.Ditolak("DI_LUAR_JAM_PESAN", pesanDariKode("DI_LUAR_JAM_PESAN", ""))

        val badan = PesananBaru(idPesanan, vendor, ringkas.baris.map { ItemPesananBaru(it.item.skuLumbung, it.jumlahGram) })
        return when (val h = api.buatPesanan(idPesanan, badan)) {
            is HasilApi.Sukses -> {
                simpanCermin(idPesanan, vendor, ringkas, h.data)
                _pesananAktif.value = PesananAktif(h.data.kode, h.data.totalBayar, h.data.kedaluwarsaBayar, ringkas, StatusPesanan.MENUNGGU_PEMBAYARAN, null)
                HasilCheckout.Berhasil(h.data, idPesanan)
            }
            is HasilApi.GalatHttp -> {
                val kode = h.galat?.kode ?: "HTTP_${h.status}"
                HasilCheckout.Ditolak(kode, pesanDariKode(kode, h.galat?.pesan ?: "galat ${h.status}"))
            }
            is HasilApi.GalatJaringan -> HasilCheckout.GagalJaringan("Checkout perlu internet: ${h.pesan}")
        }
    }

    private fun simpanCermin(id: String, vendor: String, r: RingkasanKeranjang, dibuat: PesananDibuat) {
        db.transaction {
            db.skemaQueries.simpanPesanan(
                id, dibuat.kode, vendor, StatusPesanan.MENUNGGU_PEMBAYARAN.kawat,
                r.totalItem, r.ongkir, dibuat.totalBayar, dibuat.kedaluwarsaBayar, jam.sekarangMs(), 1,
            )
            r.baris.forEach {
                db.skemaQueries.simpanItem(id, it.item.skuLumbung, it.item.nama, it.item.hargaPerKg, it.jumlahGram, 1)
            }
        }
    }

    /**
     * "Saya sudah bayar". ASUMSI: kontrak tidak mengatakan dari mana `id_gateway` berasal (belum ada
     * integrasi gateway/QR di openapi). Sementara dikirim `QRIS-<kode>` agar alur dapat diuji end-to-end.
     */
    suspend fun sudahBayar(idGateway: String? = null): HasilBayar {
        val p = _pesananAktif.value ?: return HasilBayar.Ditolak("TIDAK_ADA_PESANAN", "Tidak ada pesanan aktif.")
        return when (val h = api.konfirmasiBayar(Ulid.baru(jam), p.kode, idGateway ?: "QRIS-${p.kode}")) {
            is HasilApi.Sukses -> {
                db.skemaQueries.ubahStatusPesanan(h.data.status.kawat, p.kode)
                _pesananAktif.value = p.copy(status = h.data.status)
                HasilBayar.Berhasil(h.data.status)
            }
            is HasilApi.GalatHttp -> {
                val kode = h.galat?.kode ?: "HTTP_${h.status}"
                HasilBayar.Ditolak(kode, pesanDariKode(kode, h.galat?.pesan ?: "Pembayaran gagal"))
            }
            is HasilApi.GalatJaringan -> HasilBayar.GagalJaringan(h.pesan)
        }
    }

    /** Buat ulang pesanan setelah timer habis: ULID baru, isi keranjang sama. */
    suspend fun buatUlang(): HasilCheckout = checkout(Ulid.baru(jam))

    /** Timer QRIS: sisa detik sampai `kedaluwarsa_bayar`. */
    fun sisaDetikBayar(): Long? {
        val p = _pesananAktif.value ?: return null
        return TimerBayar.sisaDetik(p.kedaluwarsaBayar, jam.sekarangMs())
    }

    suspend fun beriRating(bintang: Int, catatan: String?): Boolean {
        val p = _pesananAktif.value ?: return false
        require(bintang in 1..5) { "bintang 1..5" }
        return when (val h = api.beriRating(Ulid.baru(jam), p.kode, bintang, catatan)) {
            is HasilApi.Sukses -> {
                _pesananAktif.value = p.copy(status = h.data.status)
                true
            }
            else -> false
        }
    }

    fun tampilkanPesanan(kode: String) {
        val baris = db.skemaQueries.pesananByKode(kode).executeAsOneOrNull() ?: return
        val status = StatusPesanan.dariKawat(baris.status) ?: StatusPesanan.MENUNGGU_PEMBAYARAN
        _pesananAktif.value = (_pesananAktif.value?.takeIf { it.kode == kode } ?: PesananAktif(
            kode, baris.total_bayar, baris.kedaluwarsa_bayar ?: "", ringkasanKosong(baris.total_item, baris.ongkir, baris.total_bayar), status, null,
        )).copy(status = status)
    }

    private fun ringkasanKosong(totalItem: Long, ongkir: Long, totalBayar: Long) =
        RingkasanKeranjang(emptyList(), totalItem, ongkir, totalBayar, 0, emptyList())
}

/** Pesanan yang sedang dibayar/dilacak. [urlFoto] terisi bila server menyediakannya (lihat catatan kontrak). */
data class PesananAktif(
    val kode: String,
    val totalBayar: Long,
    val kedaluwarsaBayar: String,
    val ringkasan: RingkasanKeranjang,
    val status: StatusPesanan,
    val urlFoto: String?,
)

object TimerBayar {
    /** Sisa detik (tidak negatif); null bila stempel tak terbaca. */
    fun sisaDetik(kedaluwarsaIso: String, sekarangMs: Long): Long? {
        val habis = runCatching { Instant.parse(kedaluwarsaIso).toEpochMilliseconds() }.getOrNull() ?: return null
        return ((habis - sekarangMs) / 1000).coerceAtLeast(0)
    }
}

/** 8 langkah timeline pelacakan (PRD alur); urutan tampilan, dipetakan dari status kontrak. */
object Timeline {
    val langkah = listOf(
        "Dipesan" to listOf(StatusPesanan.MENUNGGU_PEMBAYARAN),
        "Dibayar" to listOf(StatusPesanan.MENUNGGU_KONFIRMASI),
        "Dikonfirmasi vendor" to listOf(StatusPesanan.DISIAPKAN),
        "Dikemas" to listOf(StatusPesanan.DISIAPKAN),
        "Diambil di Lumbung" to listOf(StatusPesanan.DISIAPKAN),
        "Diserahkan" to listOf(StatusPesanan.DISERAHKAN),
        "Diberi rating" to listOf(StatusPesanan.DISERAHKAN),
        "Selesai" to listOf(StatusPesanan.SELESAI),
    )

    /**
     * Indeks langkah (0..7) yang sedang aktif untuk [status]. ASUMSI: kontrak hanya punya 9 status
     * server, bukan 8 langkah; langkah antara (dikemas, diambil, rating) tidak punya status sendiri,
     * jadi status server dipetakan ke langkah terdekat tertinggi yang pasti terjadi.
     */
    fun indeksAktif(status: StatusPesanan): Int = when (status) {
        StatusPesanan.MENUNGGU_PEMBAYARAN -> 0
        StatusPesanan.MENUNGGU_KONFIRMASI -> 1
        StatusPesanan.DISIAPKAN -> 2
        StatusPesanan.DISERAHKAN -> 5
        StatusPesanan.SELESAI -> 7
        StatusPesanan.KEDALUWARSA, StatusPesanan.DIBATALKAN_SISTEM, StatusPesanan.DIBATALKAN_VENDOR -> -1
        StatusPesanan.DIALIHKAN -> 1
    }
}
