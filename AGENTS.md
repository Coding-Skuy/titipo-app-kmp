# AGENTS.md — titipo-app-kmp

Aplikasi mobile Android dan iOS TitipO (satu biner, dua peran: rumah tangga dan vendor), offline-first untuk sisi vendor.

Divisi TitipO (Epic E2 [#3](https://github.com/Coding-Skuy/ChefGenie-TownHall/issues/3), milestone v1.0.0). Bahasa semua artefak: Indonesia.

## Sumber kebenaran

Dokumen beku v1.0.0 ada di repo [TitipO-TownHall](https://github.com/Coding-Skuy/TitipO-TownHall). Repo ini hanya menjalankan, bukan mendefinisikan, requirement.

| Dokumen | Isi | Link |
|---|---|---|
| `platform/versions/v1.0.0/BRD/00-ikhtisar.md` | Ikhtisar bisnis, vendor kurasi, titip harian, fee | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/platform/versions/v1.0.0/BRD/00-ikhtisar.md) |
| `platform/versions/v1.0.0/PRD/20-alur.md` | Alur pesan sampai fee cair | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/platform/versions/v1.0.0/PRD/20-alur.md) |
| `platform/versions/v1.0.0/FRD/10-fungsional.md` | Kebutuhan fungsional FR-001..FR-304 | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/platform/versions/v1.0.0/FRD/10-fungsional.md) |
| `platform/versions/v1.0.0/FSD/10-alur.md` | Alur sistem, tulis lokal dahulu, sinkron | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/platform/versions/v1.0.0/FSD/10-alur.md) |
| `platform/versions/v1.0.0/FSD/20-model-data.md` | Model data, aturan angka | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/platform/versions/v1.0.0/FSD/20-model-data.md) |
| `platform/versions/v1.0.0/FSD/30-kontrak.md` | Kontrak API, event, galat | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/platform/versions/v1.0.0/FSD/30-kontrak.md) |
| `platform/versions/v1.0.0/SRS/30-non-functional.md` | Non-fungsional (kinerja, keamanan, rollback) | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/platform/versions/v1.0.0/SRS/30-non-functional.md) |
| `platform/versions/v1.0.0/SRS/40-acceptance-criteria.md` | Kriteria penerimaan QA gate | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/platform/versions/v1.0.0/SRS/40-acceptance-criteria.md) |
| `produk/10-ux-pesan-titip.md` | UX 5 layar rumah + 3 layar vendor (living) | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/produk/10-ux-pesan-titip.md) |
| `produk/20-kontrak-api-KMP-mobile.md` | Kontrak API mobile 7 endpoint (living) | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/produk/20-kontrak-api-KMP-mobile.md) |
| `produk/30-modul-KMP-bersama.md` | Struktur modul KMP bersama (living) | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/produk/30-modul-KMP-bersama.md) |
| `pesanan/10-alur-titip-harian.md` | Alur 8 langkah + sinyal pedaree.stok.v1 sisi konsumen (§6) (living) | [buka](https://github.com/Coding-Skuy/TitipO-TownHall/blob/main/pesanan/10-alur-titip-harian.md) |
| `KONTEKS-LINTAS-DIVISI.md` | Konteks lintas-divisi (event registry, ID, satuan, metrik) | [buka](https://github.com/Coding-Skuy/ChefGenie-TownHall/blob/main/KONTEKS-LINTAS-DIVISI.md) |
| `townhall-roadmap.md` | Epic E2 #3, milestone v1.0.0, Definisi Selesai §6 | [buka](https://github.com/Coding-Skuy/ChefGenie-TownHall/blob/main/townhall-roadmap.md) |

Aturan:
- Dilarang menduplikasi requirement di repo kode; cukup tautkan ke versi di atas.
- Requirement berubah = PR di TitipO-TownHall dahulu (versi baru), baru kode mengikuti.
- Bila dokumen divisi bertentangan dengan KONTEKS-LINTAS-DIVISI.md, laporkan; jangan memperbaiki diam-diam.

## Tech stack terkunci

- Kotlin 2.2.20, Compose Multiplatform 1.8.2, Navigation3 1.0.0 (pin di `gradle/libs.versions.toml`).
- Target: Android 8 ke atas dan iOS 16 ke atas. Tanpa target desktop/Windows (hanya lumbung gudang dan titeny analis yang memilikinya).
- Offline-first: SQLDelight 2.x (4 tabel cermin: pesanan, item pesanan, antrean, foto serah) + antrean + retry; Ktor client 3.x; kotlinx-serialization + kotlinx-datetime; Koin 4.x; Coil 3.
- Satu biner, dua graf Navigation3 (`GrafRumah`, `GrafVendor`) dipilih dari klaim JWT peran.
- Dilarang: framework UI selain Compose Multiplatform, hitungan uang sah di klien (hanya pratinjau), menyimpan service key titipo-lumbung, akses DB titipo/Lumbung langsung.

Angka bisnis TitipO yang berlaku di seluruh repo: fee vendor 12% (+0,5% Prioritas), ongkir flat Rp5.000 (Rp4.000 vendor + Rp1.000 kas), pesan maksimal 20.00, serah 06.00-08.00, pokok ke Lumbung tiap Senin, denda batal sepihak Rp15.000, maksimal 50 kg per transaksi (di atasnya ditandai grosir), ID pesanan `T-2026-NNNNNN`.

## Aturan kerja AI

a. Baca AGENTS.md ini dan seluruh ADR di `docs/decisions/` sebelum menulis kode.
b. TDD merah-hijau: tulis test yang gagal dahulu. Rilis diblokir bila QA gate (SRS/40-acceptance-criteria.md) gagal.
c. Kontrak API/event hanya diubah lewat bump versi + catatan di ADR baru.
d. Pesan commit berawalan `docs:`, `feat:`, atau `fix:`; wajib memperbarui `CHANGELOG.md` repo ini.
e. Keputusan arsitektur baru = tambah ADR bernomor di `docs/decisions/`; jangan mengedit ADR lama (immutable, sama seperti aturan versi TownHall).
f. Dilarang menyentuh DB divisi lain (DB per service; Titeny read-only).
g. Khusus repo ini:
- Aksi vendor yang boleh antre luring: konfirmasi, tolak, tutup jadwal, foto serah. Checkout dan bayar rumah tangga wajib online.
- `shared` murni Kotlin (tanpa Compose, tanpa API Android/iOS); pelanggaran ditolak di code review.

## Definisi selesai

- Dokumen versi lengkap (BRD/PRD/FRD/FSD/SRS) + `SNAPSHOT-ROADMAP.md`.
- QA gate berbasis SRS lulus (TDD merah-hijau).
- Kontrak API terbit; audit keamanan bersih (JWT audiens + DB per service).
- Bukti metrik lintas-divisi (KONTEKS §7) tercatat.
- CHANGELOG versi diperbarui; dokumen beku tidak diedit.
Sumber: [townhall-roadmap.md §6](https://github.com/Coding-Skuy/ChefGenie-TownHall/blob/main/townhall-roadmap.md)
