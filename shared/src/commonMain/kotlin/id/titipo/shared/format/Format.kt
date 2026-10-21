package id.titipo.shared.format

import kotlinx.datetime.Instant

/** Pemformat tampilan (bahasa Indonesia). Bebas API platform. */
object Format {
    /** `52000` -> `Rp52.000`. */
    fun rupiah(nilai: Long): String {
        val negatif = nilai < 0
        val digit = kotlin.math.abs(nilai).toString()
        val sb = StringBuilder()
        digit.forEachIndexed { i, c ->
            if (i > 0 && (digit.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return (if (negatif) "-Rp" else "Rp") + sb
    }

    /** `2000` -> `2 kg`, `1500` -> `1,5 kg`, `250` -> `250 g`. */
    fun berat(gram: Long): String = when {
        gram < 1000 -> "$gram g"
        gram % 1000 == 0L -> "${gram / 1000} kg"
        // satu desimal cukup untuk langkah stepper 100 g; sisa dibulatkan ke bawah
        else -> "${gram / 1000},${(gram % 1000) / 100} kg"
    }

    /** `MM:SS` dari detik sisa (tidak negatif). */
    fun mmss(detik: Long): String {
        val d = if (detik < 0) 0 else detik
        return "${(d / 60).toString().padStart(2, '0')}:${(d % 60).toString().padStart(2, '0')}"
    }

    /** Jam dinding WIB `HH.MM` dari stempel ISO-8601 UTC (UI menampilkan WIB; kontrak menyimpan UTC). */
    fun jamWib(isoUtc: String, offsetJam: Int = 7): String? {
        val ms = runCatching { Instant.parse(isoUtc).toEpochMilliseconds() }.getOrNull() ?: return null
        return jamMenit(ms, offsetJam)
    }

    fun jamMenit(epochMs: Long, offsetJam: Int = 7): String {
        val menitHari = (epochMs / 60_000 + offsetJam * 60L).mod(24L * 60).toInt()
        return "${(menitHari / 60).toString().padStart(2, '0')}.${(menitHari % 60).toString().padStart(2, '0')}"
    }

    /** Jam (0-23) dinding WIB dari epoch ms. */
    fun jamDindingWib(epochMs: Long, offsetJam: Int = 7): Int =
        (epochMs / 3_600_000 + offsetJam).mod(24L).toInt()
}
