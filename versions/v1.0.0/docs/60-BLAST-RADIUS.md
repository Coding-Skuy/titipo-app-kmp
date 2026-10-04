# 60-BLAST-RADIUS — titipo-app-kmp

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
