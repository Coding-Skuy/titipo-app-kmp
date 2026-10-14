# ADR-0001 — Fondasi teknis titipo-app-kmp

Status: Diterima | Tanggal: 2026-10-14

## Konteks

titipo-app-kmp adalah bagian divisi TitipO (Epic E2 #3, milestone v1.0.0). Spesifikasi v1.0.0 beku sejak 2026-10-07 di [TitipO-TownHall](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/platform/versions/v1.0.0/FSD/30-kontrak.md). Repo ini memerlukan fondasi teknis yang tetap agar pengembangan oleh AI dan manusia konsisten.

## Keputusan

Keputusan kunci CEO yang berlaku untuk repo ini:

1. **Stack mobile terkunci.** Kotlin 2.2.20 + Compose Multiplatform 1.8.2 + Navigation3 1.0.0 untuk Android 8+ dan iOS 16+. Framework UI lain dilarang.
2. **Satu biner dua graf.** Rumah tangga dan vendor berbagi satu aplikasi; graf navigasi dipilih dari klaim JWT `role` saat login. Ganti peran butuh logout dan login ulang.
3. **Offline-first untuk vendor.** Tulis ke SQLDelight dahulu (<200 ms, ID ULID 26 karakter), antrean FIFO jeda 1 detik, retry 30 detik maksimal 24 jam, batas 50 aksi per 24 jam, 20 foto maksimal 800 KB, 5 perubahan jadwal per minggu. Antrean tidak boleh kehilangan atau mengirim ganda secara diam-diam.
4. **JWT audiens titipo.** Klien mengirim Bearer JWT beraudien `titipo`; token disimpan terenkripsi (Keystore/Keychain). Service key titipo-lumbung tidak pernah ada di biner.
5. **Angka sah dari server.** Total, fee, dan stok yang sah dihitung server; klien hanya pratinjau.

Keputusan lintas repo: satu DB logis per service, JWT beraudien divisi, kunci idempoten ULID klien pada semua POST, uang integer rupiah, waktu timestamptz UTC.

## Konsekuensi

Pembaruan pin Kotlin/Compose/Navigation3 butuh PR dan ADR baru. Perubahan kontrak API mengikuti bump versi di `titipo-backend-service`. Rilis diblokir bila QA gate gagal.
ADR ini immutable; perubahan keputusan ditulis sebagai ADR baru yang menggantikan.
