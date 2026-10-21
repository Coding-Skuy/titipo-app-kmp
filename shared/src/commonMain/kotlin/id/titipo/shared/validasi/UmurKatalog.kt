package id.titipo.shared.validasi

import id.titipo.shared.config.KonfigurasiBisnis
import kotlinx.datetime.Instant

/** Aturan kedaluwarsa snapshot katalog (> 24 jam = pesan terkunci hingga tarik ulang). */
object UmurKatalog {
    fun kedaluwarsa(diperbaruiIso: String, sekarangMs: Long, konfigurasi: KonfigurasiBisnis): Boolean {
        val diperbarui = runCatching { Instant.parse(diperbaruiIso).toEpochMilliseconds() }.getOrNull()
            ?: return true // stempel tak terbaca = anggap kedaluwarsa (aman)
        return sekarangMs - diperbarui > konfigurasi.umurMaksKatalogJam * 3_600_000L
    }
}
