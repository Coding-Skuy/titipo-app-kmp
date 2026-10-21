package id.titipo.shared.validasi

import id.titipo.shared.config.KonfigurasiBisnis
import id.titipo.shared.model.ItemKatalog

/** Satu baris keranjang rumah tangga. */
data class BarisKeranjang(
    val item: ItemKatalog,
    val jumlahGram: Long,
)

sealed interface GalatKeranjang {
    /** Kelebihan [kelebihanGram] dari batas — UX: "Kelebihan 2 kg, kurangi". */
    data class BeratMelebihiBatas(val kelebihanGram: Long, val batasGram: Long) : GalatKeranjang
    data class BarisMelebihiBatas(val batas: Int) : GalatKeranjang
    data class StokTidakCukup(val sku: String, val stokGram: Long) : GalatKeranjang
    data object KeranjangKosong : GalatKeranjang
}

/**
 * PRATINJAU hitungan keranjang. Angka sah (total, fee, stok) dihitung server;
 * hasil ini hanya untuk tampilan dan validasi inline.
 *
 * ASUMSI pembulatan: subtotal = jumlah_gram * harga_per_kg / 1000, dibulatkan ke rupiah terdekat
 * (setengah ke atas). Kontrak tidak menetapkan aturan pembulatan; server menentukan.
 */
data class RingkasanKeranjang(
    val baris: List<BarisKeranjang>,
    val totalItem: Long,
    val ongkir: Long,
    val totalBayar: Long,
    val totalBeratGram: Long,
    val galat: List<GalatKeranjang>,
) {
    val valid: Boolean get() = galat.isEmpty()
}

object HitungKeranjang {
    fun subtotal(hargaPerKg: Long, jumlahGram: Long): Long =
        (hargaPerKg * jumlahGram + 500) / 1000

    fun hitung(
        baris: List<BarisKeranjang>,
        konfigurasi: KonfigurasiBisnis,
    ): RingkasanKeranjang {
        val aktif = baris.filter { it.jumlahGram > 0 }
        val totalItem = aktif.sumOf { subtotal(it.item.hargaPerKg, it.jumlahGram) }
        val berat = aktif.sumOf { it.jumlahGram }
        val galat = buildList {
            if (aktif.isEmpty()) add(GalatKeranjang.KeranjangKosong)
            if (berat > konfigurasi.batasBeratGram) {
                add(GalatKeranjang.BeratMelebihiBatas(berat - konfigurasi.batasBeratGram, konfigurasi.batasBeratGram))
            }
            if (aktif.size > konfigurasi.batasBaris) add(GalatKeranjang.BarisMelebihiBatas(konfigurasi.batasBaris))
            aktif.filter { it.jumlahGram > it.item.stokGram }
                .forEach { add(GalatKeranjang.StokTidakCukup(it.item.skuLumbung, it.item.stokGram)) }
        }
        val ongkir = if (aktif.isEmpty()) 0 else konfigurasi.ongkirRupiah
        return RingkasanKeranjang(aktif, totalItem, ongkir, totalItem + ongkir, berat, galat)
    }
}
