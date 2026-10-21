# ADR-0004 — 5 layar rumah tangga dan celah kontrak backend

Status: Diterima | Tanggal: 2026-10-21 | Tiket: TTP-KMP-003

## Konteks

TTP-KMP-003 membangun 5 layar rumah tangga (daftar vendor, katalog, keranjang, bayar QRIS, lacak). Kontrak yang dikonsumsi adalah `openapi.yaml` backend branch PR #10 (BE-004). Beberapa kebutuhan layar tidak tercakup kontrak tersebut; celahnya dicatat di sini dan tidak ditambal dengan kontrak karangan.

## Keputusan

1. **Hitungan hanya pratinjau.** `SesiBelanja` menghitung rincian untuk tampilan; total sah ditampilkan dari `PesananDibuat.totalBayar` (server). Batas berat/baris dibaca dari `KonfigurasiBisnis`, tidak dikeraskan di UI (pesan "Kelebihan X kg" memakai batas dari konfigurasi). Contoh mengikat Rp52.000 diuji.
2. **Checkout wajib online.** Gagal jaringan dikembalikan ke pengguna; tidak diantrekan dan tidak membuat pesanan semu. `Idempotency-Key` = ULID pesanan; "buat ulang" setelah timer habis memakai ULID baru.
3. **Penjaga klien** (diulang oleh server): jam pesan 05.00 sampai 20.00 WIB, katalog umur maksimal 24 jam (tombol tambah mati dan banner merah sampai tarik ulang), unduh katalog maksimal 5 MB (ditolak klien bila `Content-Length` lebih besar).
4. **Timer QRIS** dihitung dari `kedaluwarsa_bayar` server dikurangi jam perangkat (tampil `mm:ss`, 15:00 saat dibuat); habis menampilkan layar kedaluwarsa dan tombol buat ulang. Gagal bayar diulang maksimal 3 kali, lalu buat QR baru.

## Celah kontrak (temuan untuk backend/manusia)

| Celah | Dampak | Sikap klien |
|---|---|---|
| Tidak ada `GET /pesanan/{kode}` atau daftar pesanan rumah tangga | Lacak tidak bisa polling status/foto | Status mengikuti respons terakhir (checkout, bayar, rating) dan cermin lokal; timeline 8 langkah dipetakan dari 9 status server (ASUMSI pemetaan) |
| Respons checkout tanpa payload QRIS/URL gateway; `id_gateway` tak jelas asalnya | Layar bayar tak dapat menampilkan QR sungguhan | Ruang QR = placeholder; "Saya sudah bayar" mengirim `id_gateway = QRIS-<kode>` (ASUMSI) |
| `url_foto` hanya dikembalikan ke vendor (`/serah`) | Rumah tangga tak dapat memuat foto serah | Komponen foto (Coil 3) siap; terisi bila API menyediakan URL |
| Contoh `diperbarui: 2026-01-07T04:30:00Z` vs UX "Diperbarui 04.30" | Konversi WIB membuat stempel menjadi 11.30 | Tampil WIB (openapi: "tampil WIB hanya di UI"); dokumen perlu diluruskan |
| Lokasi pengguna belum ada (expect/actual lokasi di `produk/30`) | Query `lat,lng` memakai titik contoh | `LokasiBawaan` (Bandung) ASUMSI sampai modul lokasi ada |
| Langkah stepper kg tak ditetapkan | Tampilan | 500 g (ASUMSI) |

## Konsekuensi

Saat backend menerbitkan endpoint pelacakan dan payload QRIS, hanya `SesiBelanja` dan layar bayar/lacak yang disesuaikan. ADR ini immutable; perubahan ditulis ADR baru.
