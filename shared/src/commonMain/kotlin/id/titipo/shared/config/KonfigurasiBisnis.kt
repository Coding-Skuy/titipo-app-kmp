package id.titipo.shared.config

/**
 * Angka bisnis yang boleh berubah/dikonfigurasi tanpa mengubah UI.
 *
 * Batas berat TIDAK dikeraskan di UI: backend memakai `BATAS_BERAT_GRAM` (bawaan 50.000 g,
 * mengikuti AGENTS/KONTEKS) sementara FSD menyebut 20 kg (lihat ADR-0002 backend). Nilai
 * yang dipakai klien diinjeksi lewat konfigurasi build/remote; [BAWAAN] hanya cermin bawaan backend.
 */
data class KonfigurasiBisnis(
    val batasBeratGram: Long = BATAS_BERAT_GRAM_BAWAAN,
    val batasBaris: Int = 50,
    val ongkirRupiah: Long = 5_000,
    val timerBayarDetik: Int = 15 * 60,
    val umurMaksKatalogJam: Int = 24,
    val batasKonfirmasiMenit: Int = 30,
    val jamPesanMulai: Int = 5,
    val jamPesanSelesai: Int = 20,
    val offsetWibJam: Int = 7,
    val batasUnduhKatalogByte: Long = 5L * 1024 * 1024,
    val jendelaSerahMulaiJam: Int = 6,
    val jendelaSerahSelesaiJam: Int = 8,
    val batasAksiPer24Jam: Int = 50,
    val batasFotoAntre: Int = 20,
    val batasUkuranFotoByte: Int = 800 * 1024,
    val batasPerubahanJadwalPerMinggu: Int = 5,
    val jedaAntarKirimMs: Long = 1_000,
    val jedaRetryMs: Long = 30_000,
    val batasRetryMs: Long = 24L * 60 * 60 * 1000,
) {
    companion object {
        /** Cermin bawaan `BATAS_BERAT_GRAM` backend (50.000 g). Bukan angka UI. */
        const val BATAS_BERAT_GRAM_BAWAAN: Long = 50_000
        val BAWAAN = KonfigurasiBisnis()
    }
}
