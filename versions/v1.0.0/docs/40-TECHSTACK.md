# 40-TECHSTACK — titipo-app-kmp

> Versi: v1.0.0 | Status: disetujui | Menggantikan: -
> Acuan: [TitipO-TownHall platform/versions/v1.0.0](https://github.com/Coding-Skuy/TitipO-TownHall/tree/main/platform/versions/v1.0.0) (BRD/PRD/FRD/FSD/SRS beku 2026-10-07).

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

## 3. Matriks fitur per platform

| Fitur | Android 8+ | iOS 16+ | Desktop |
|---|---|---|---|
| Daftar vendor radius 5 km (lokasi) | ya | ya | tidak ada target |
| Katalog tersinkron (stempel 24 jam) | ya | ya | - |
| Keranjang dan checkout (wajib online) | ya | ya | - |
| Bayar QRIS timer 15:00 | ya | ya | - |
| Lacak pesanan + rating 1 sampai 5 | ya | ya | - |
| Vendor: konfirmasi 2 ketukan (bisa luring, antre) | ya | ya | - |
| Vendor: kamera foto serah (JPEG, antre luring) | ya | ya | - |
| Push konfirmasi 30 menit | FCM | APNs | - |
| Antrean offline SQLDelight | ya | ya | - |
| Deep-link `titipo://lacak/{kode}` | ya | ya | - |

Platform khusus lewat `expect/actual` di modul `platform/`: kamera, notifikasi, lokasi, penyimpanan. Rilis: Play Store, APK sideload, TestFlight. Unduh maksimal 35 MB, buka dingin maksimal 2,5 detik.

## 4. Navigation graph ringkas

```
login (JWT audiens titipo) --klaim role--> GrafRumah | GrafVendor

GrafRumah:  rumah/daftar-vendor -> rumah/katalog/{idVendor} -> rumah/keranjang
            -> rumah/bayar (QRIS 15:00) -> rumah/lacak/{kode}
GrafVendor: vendor/masuk (terima/tolak) -> vendor/kemas (checklist)
            -> vendor/serah/{kode} (kamera, foto wajib)
```

Sumber: produk/10-ux-pesan-titip.md dan FSD/10-alur.md. Penambahan layar di luar 5 rumah dan 3 vendor butuh versi baru di TownHall.
