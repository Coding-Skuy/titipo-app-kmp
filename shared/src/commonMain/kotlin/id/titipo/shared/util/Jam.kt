package id.titipo.shared.util

import kotlinx.datetime.Clock

/** Sumber waktu yang bisa diganti saat uji. */
fun interface Jam {
    fun sekarangMs(): Long

    companion object {
        val Sistem = Jam { Clock.System.now().toEpochMilliseconds() }
    }
}
