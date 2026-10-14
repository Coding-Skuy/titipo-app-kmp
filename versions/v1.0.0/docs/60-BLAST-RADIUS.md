# 60-BLAST-RADIUS — titipo-app-kmp

> Versi: v1.0.0 | Status: disetujui | Menggantikan: -
> Acuan: [TitipO-TownHall platform/versions/v1.0.0](https://github.com/Coding-Skuy/TitipO-TownHall/tree/main/platform/versions/v1.0.0) (BRD/PRD/FRD/FSD/SRS beku 2026-10-07).

## Konsumen

- Pengguna langsung: rumah tangga dan vendor TitipO (Android 8 ke atas, iOS 16 ke atas).
- Hulu: `titipo-backend-service` (API pesanan), JWKS Lumbung (verifikasi login), `titipo-design` (token visual).
- Hilir: tidak ada; aplikasi adalah ujung.

## Failure

- Backend down: mode baca cache + antre tulis 50 aksi per 24 jam; kepastian menunggu sinkron; pesan jelas di banner.
- JWKS Lumbung tidak terjangkau saat login: login baru gagal tertutup; sesi berjalan dipakai sampai kedaluwarsa.
- Antre penuh atau kedaluwarsa: tolak dengan pesan; tidak ada kehilangan diam-diam; entri kedaluwarsa bisa diulang manual.
- Jam perangkat salah: cutoff 20.00 dinilai ulang di backend; UI menampilkan peringatan zona waktu.

## Rollback

- Rilis toko bertahap 20 persen ke 100 persen dalam 3 hari; rollback berarti rilis versi sebelumnya ditambah sinkron antre versi lama yang kompatibel.
- Tidak ada migrasi DB sisi klien selain antre lokal; rollback tidak menghapus antre yang belum terkirim.
- Rollback tidak memutar secret apa pun; tidak pernah menyertakan service key di biner.

## Batasan

- Dampak maksimal satu rilis aplikasi; tidak menyentuh DB titipo, DB Lumbung, pipeline, atau model AI.
- Insiden auth massal dieskalasi ke pemilik JWKS Lumbung via TitipO-TownHall, bukan perbaikan token lokal.

## Boleh disentuh

UI Compose (5 layar rumah + 3 layar vendor), antrean offline lokal SQLDelight, cache katalog 50 MB, pratinjau hitungan, push dan kamera lewat `expect/actual`.

## Dilarang

Service key titipo-lumbung, akses DB titipo/Lumbung, hitungan uang sah, layar admin, framework UI selain Compose Multiplatform, target desktop.

## Kontrak yang dikonsumsi

API mobile `titipo-backend-service` (7 endpoint, FSD/30-kontrak.md); JWT beraudien `titipo` (JWKS Lumbung); token visual `titipo-design`.

## Kontrak yang diproduksi

Tidak ada kontrak keluar; aplikasi adalah ujung rantai. Modul `:shared:pantry-resep` (milik Pawonee) TIDAK dikonsumsi TitipO.

Sumber batas: [townhall-roadmap.md §2](https://github.com/Coding-Skuy/ChefGenie-TownHall/blob/main/townhall-roadmap.md) baris TitipO (pesan <=20.00, serah 06.00-08.00; sinyal `pedaree.stok.v1` anjuran saja; fee vendor 12%) dan [KONTEKS-LINTAS-DIVISI.md](https://github.com/Coding-Skuy/ChefGenie-TownHall/blob/main/KONTEKS-LINTAS-DIVISI.md) §3, §8, §9.
