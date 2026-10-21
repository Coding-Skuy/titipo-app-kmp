package id.titipo.app.nav

/**
 * Rute Navigation3 per peran. Path mengikuti `produk/10-ux-pesan-titip.md` (rumah/..., vendor/...).
 * Dua graf terpisah (GrafRumah, GrafVendor); backstack masing-masing berdiri sendiri dan
 * dipilih dari klaim peran JWT saat login. Ganti peran = logout + login ulang.
 */
sealed interface RuteRumah {
    val path: String

    data object DaftarVendor : RuteRumah { override val path = "rumah/daftar-vendor" }
    data class Katalog(val idVendor: String) : RuteRumah { override val path = "rumah/katalog/$idVendor" }
    data object Keranjang : RuteRumah { override val path = "rumah/keranjang" }
    data object Bayar : RuteRumah { override val path = "rumah/bayar" }
    data class Lacak(val kode: String) : RuteRumah { override val path = "rumah/lacak/$kode" }
}

sealed interface RuteVendor {
    val path: String

    data object Masuk : RuteVendor { override val path = "vendor/masuk" }
    data object Kemas : RuteVendor { override val path = "vendor/kemas" }
    data class Serah(val kode: String) : RuteVendor { override val path = "vendor/serah/$kode" }
}
