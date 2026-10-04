# 40-TECHSTACK — titipo-app-kmp

STATUS: defined pending (target dikunci, implementasi menunggu).

Mengacu: TitipO-TownHall v1.0.0

## 1. Target stack

| Lapisan | Teknologi + versi | Keterangan |
|---|---|---|
| Bahasa | Kotlin 2.2.20 | KMP shared + platform |
| UI | Compose Multiplatform 1.8.2 | UI bersama Android + iOS |
| Navigasi | Navigation3 1.0.0 | 2 graf: rumah dan vendor |
| HTTP | Ktor client 3.x | Komunikasi ke backend, retry idempoten |
| DB lokal | SQLDelight 2.x | Antrean offline + cache katalog |
| Serialisasi | kotlinx-serialization + kotlinx-datetime | DTO cermin kontrak TownHall |
| DI | Koin 4.x | Injeksi ringan |
| Gambar | Coil 3 | Muat foto serah |
| Push | FCM + APNs topik titipan-{idVendor} | Timer konfirmasi 30 menit |
| Auth simpan | EncryptedSettings / Keystore-Keychain | JWT beraudien titipo terenkripsi |

Rujukan pin: `gradle/libs.versions.toml`. Aturan bisnis yang dijaga klien: pesan maksimal 20.00, serah 06.00–08.00, satu pesanan satu vendor, maksimal 20 kg dan 50 baris.

## 2. Konfigurasi kunci

- Basis API TitipO via BuildConfig per flavor; timeout 15 detik; GET retry 2 kali; POST memakai `Idempotency-Key` ULID.
- Batas antrean 50 aksi per 24 jam; foto antre maksimal 20 file maksimal 800 KB per foto; cache katalog 50 MB dan stempel 24 jam.
- Deep-link `titipo://lacak/{kode}`; bahasa UI Indonesia saja v1.0.0.
- Klien tidak pernah menyimpan service key titipo ke lumbung; tidak mengakses DB titipo maupun DB Lumbung langsung.

## Batasan

- Hanya klien mobile; bukan backend Rust, bukan web Bun Svelte, bukan model AI, bukan pipeline.
- Hitungan uang di klien hanya pratinjau; angka sah dari server.
- Dilarang menaikkan atau menurunkan pin Kotlin 2.2.20, Compose 1.8.2, Navigation3 1.0.0 tanpa PR.
