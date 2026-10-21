package id.titipo.shared

import id.titipo.shared.auth.HasilMasuk
import id.titipo.shared.auth.JwtParser
import id.titipo.shared.auth.PenyimpanToken
import id.titipo.shared.auth.PengelolaSesi
import id.titipo.shared.auth.petakanPeran
import id.titipo.shared.model.Peran
import id.titipo.shared.util.Jam
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private class PenyimpanMemori : PenyimpanToken {
    var isi: String? = null
    override suspend fun simpan(token: String) { isi = token }
    override suspend fun baca() = isi
    override suspend fun hapus() { isi = null }
}

@OptIn(ExperimentalEncodingApi::class)
private fun jwt(payload: String): String {
    val b = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    return b.encode("""{"alg":"RS256"}""".encodeToByteArray()) + "." +
        b.encode(payload.encodeToByteArray()) + ".tandatangan"
}

class SesiTest {
    private val sekarangMs = 1_800_000_000_000L
    private val jam = Jam { sekarangMs }
    private val exp = sekarangMs / 1000 + 900 // 15 menit
    private val penyimpan = PenyimpanMemori()
    private val sesi = PengelolaSesi(penyimpan, jam)

    @Test
    fun audiensTitipoDanPeranHouseholdMasukGrafRumah() = runTest {
        val h = sesi.masuk(jwt("""{"sub":"u1","aud":"titipo","role":"household","exp":$exp}"""))
        assertEquals(Peran.RUMAH, assertIs<HasilMasuk.Berhasil>(h).sesi.peran)
        assertEquals(Peran.RUMAH, sesi.sesi.value?.peran)
    }

    @Test
    fun peranVendorDanAudiensArrayDanKlaimPeranIndonesia() = runTest {
        val h = sesi.masuk(jwt("""{"sub":"u2","aud":["lumbung","titipo"],"peran":"vendor","exp":$exp}"""))
        assertEquals(Peran.VENDOR, assertIs<HasilMasuk.Berhasil>(h).sesi.peran)
    }

    @Test
    fun audienLainDitolakDanTidakDisimpan() = runTest {
        val h = sesi.masuk(jwt("""{"sub":"u3","aud":"lumbung","role":"vendor","exp":$exp}"""))
        assertEquals(HasilMasuk.AudiensSalah, h)
        assertNull(penyimpan.isi)
        assertNull(sesi.sesi.value)
    }

    @Test
    fun adminDanPeranTakDikenalDitolakDiMobile() = runTest {
        assertEquals(HasilMasuk.PeranDitolak, sesi.masuk(jwt("""{"sub":"a","aud":"titipo","role":"admin","exp":$exp}""")))
        assertEquals(HasilMasuk.PeranDitolak, sesi.masuk(jwt("""{"sub":"a","aud":"titipo","exp":$exp}""")))
        assertNull(penyimpan.isi)
    }

    @Test
    fun kedaluwarsaDanRusak() = runTest {
        assertEquals(HasilMasuk.Kedaluwarsa, sesi.masuk(jwt("""{"sub":"a","aud":"titipo","role":"vendor","exp":${sekarangMs / 1000 - 1}}""")))
        assertEquals(HasilMasuk.TokenRusak, sesi.masuk("bukan.jwt"))
        assertEquals(HasilMasuk.TokenRusak, sesi.masuk("a.b.c"))
    }

    @Test
    fun pulihkanSaatBukaDinginDanKeluarMenghapus() = runTest {
        val t = jwt("""{"sub":"u1","aud":"titipo","role":"vendor","exp":$exp}""")
        penyimpan.isi = t
        assertEquals(Peran.VENDOR, sesi.pulihkan()?.peran)
        assertEquals(t, sesi.token())
        sesi.keluar()
        assertNull(penyimpan.isi)
        assertNull(sesi.sesi.value)
        // token tersimpan yang tak valid dibersihkan
        penyimpan.isi = jwt("""{"sub":"u1","aud":"lain","role":"vendor","exp":$exp}""")
        assertNull(sesi.pulihkan())
        assertNull(penyimpan.isi)
    }

    @Test
    fun petakanPeranDanKlaim() {
        assertEquals(Peran.RUMAH, petakanPeran("rumah"))
        assertNull(petakanPeran("admin"))
        assertNull(petakanPeran(null))
        assertEquals("u1", JwtParser.klaim(jwt("""{"sub":"u1","aud":"titipo"}"""))?.sub)
        assertNull(JwtParser.klaim(jwt("""{"aud":"titipo"}"""))) // tanpa sub
    }
}

class AlurLoginTest {
    private val sekarangMs = 1_800_000_000_000L
    private val exp = sekarangMs / 1000 + 900
    private val penyimpan = PenyimpanMemori()
    private val pengelola = PengelolaSesi(penyimpan, Jam { sekarangMs })

    private fun alur(hasil: id.titipo.shared.auth.HasilLogin) =
        id.titipo.shared.auth.AlurLogin({ _, _ -> hasil }, pengelola)

    @Test
    fun suksesMemilihPeranDariKlaim() = runTest {
        val t = jwt("""{"sub":"u1","aud":"titipo","role":"vendor","exp":$exp}""")
        val h = alur(id.titipo.shared.auth.HasilLogin.Token(t)).jalankan("sari@x.id", "rahasia")
        assertEquals(Peran.VENDOR, assertIs<id.titipo.shared.auth.HasilAlurLogin.Sukses>(h).sesi.peran)
        assertEquals(t, penyimpan.isi)
    }

    @Test
    fun audiensSalahDitolakKredensialKosongTanpaPanggilanDanGagalJaringan() = runTest {
        val salah = jwt("""{"sub":"u1","aud":"lumbung","role":"vendor","exp":$exp}""")
        assertEquals(
            id.titipo.shared.auth.HasilAlurLogin.AudiensSalah,
            alur(id.titipo.shared.auth.HasilLogin.Token(salah)).jalankan("a@x.id", "p"),
        )
        assertNull(penyimpan.isi)
        var dipanggil = false
        val a = id.titipo.shared.auth.AlurLogin({ _, _ -> dipanggil = true; id.titipo.shared.auth.HasilLogin.KredensialSalah }, pengelola)
        assertEquals(id.titipo.shared.auth.HasilAlurLogin.KredensialSalah, a.jalankan(" ", "p"))
        assertEquals(false, dipanggil)
        assertIs<id.titipo.shared.auth.HasilAlurLogin.Gagal>(
            alur(id.titipo.shared.auth.HasilLogin.Gagal("putus")).jalankan("a@x.id", "p"),
        )
    }
}
