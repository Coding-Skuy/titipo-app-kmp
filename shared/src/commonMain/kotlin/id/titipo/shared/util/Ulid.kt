package id.titipo.shared.util

import kotlin.random.Random

/** Pembuat ULID 26 karakter (Crockford base32): 10 karakter waktu + 16 karakter acak. */
object Ulid {
    private const val ALFABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val POLA = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")

    fun baru(jam: Jam = Jam.Sistem, acak: Random = Random.Default): String =
        buat(jam.sekarangMs(), acak)

    fun buat(waktuMs: Long, acak: Random): String {
        require(waktuMs >= 0) { "waktu negatif" }
        val sb = StringBuilder(26)
        var t = waktuMs
        val waktu = CharArray(10)
        for (i in 9 downTo 0) {
            waktu[i] = ALFABET[(t % 32).toInt()]
            t /= 32
        }
        sb.append(waktu)
        repeat(16) { sb.append(ALFABET[acak.nextInt(32)]) }
        return sb.toString()
    }

    fun valid(nilai: String): Boolean = POLA.matches(nilai)
}
