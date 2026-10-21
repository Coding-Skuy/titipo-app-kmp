# ADR-0005 — 3 layar vendor, kamera serah, dan celah kontrak

Status: Diterima | Tanggal: 2026-10-21 | Tiket: TTP-KMP-004

## Konteks

TTP-KMP-004 membangun 3 layar vendor (masuk, kemas, serah) di atas antrean offline-first TTP-KMP-002. Kontrak yang dikonsumsi: `openapi.yaml` backend branch PR #10 (BE-004).

## Keputusan

1. **Konfirmasi 2 ketukan.** Geser kanan = Terima, geser kiri = Tolak + alasan (`Stok habis` / `Tutup`). Tombol Terima/Tolak (target 48 dp) disediakan sebagai alternatif aksesibilitas. Aksi ditulis ke SQLDelight dan antrean dalam satu transaksi (status lokal langsung berubah, `sinkron = 0` sampai server menjawab), sehingga aman luring. Timer 30 menit dihitung dari saat pesanan masuk.
2. **Checklist kemas.** "Siap serah" aktif hanya bila semua item dicentang (`ChecklistKemas`).
3. **Foto serah.** Android: kamera sistem + FileProvider, sisi terpanjang 1280 px, rotasi EXIF, kompres JPEG kualitas tertinggi yang muat 800 KB (pencarian biner di `KompresFoto`, diuji). iOS: `UIImagePickerController`, skala + kompres sama. Tanpa foto tombol "Selesai serah" mati; foto ganda: yang terakhir menang, yang lama diarsipkan (`diarsipkan = 1`).
4. **Bilah luring.** Status luring diturunkan dari `GET /healthz` (ada di kontrak, tanpa auth) tiap 15 detik, bukan dari status radio; badge menampilkan jumlah antrean. Saat kembali online, antrean diproses; retry otomatis tiap 30 detik.

## Celah kontrak dan batasan (temuan untuk backend/manusia)

| Celah | Dampak | Sikap klien |
|---|---|---|
| Tidak ada endpoint daftar pesanan vendor (`GET /vendor/pesanan`) | "Daftar titip hari ini" tak dapat ditarik | Layar membaca cermin lokal `pesanan`; `RepositoriVendor.simpanPesananMasuk` adalah titik suntik dari push/sinkron |
| Push (kode, total, sisa menit) | Penerima FCM/APNs belum ada (infrastruktur platform, kunci/penyedia belum ditetapkan) | Titik suntik tersedia; penerima push BELUM dibuat |
| Tidak ada endpoint tutup jadwal | Aksi `tutup_jadwal` tak terkirim | Dibatasi 5/minggu di antrean, pengirim menandai `gagal` (`ENDPOINT_BELUM_ADA`); tidak ada UI tutup jadwal di tiket ini |
| Tidak ada endpoint "siap serah"/ambil di Lumbung | Checklist hanya lokal | Tidak diantrekan |
| Detail item pesanan untuk vendor tidak ada di kontrak | Item kemas berasal dari muatan push | Disimpan di `item_pesanan` (harga 0, hanya nama dan berat) |
| iOS: proyek Xcode host belum ada | `NSCameraUsageDescription` belum bisa dideklarasikan | Kode kamera iOS hanya terkompilasi, belum dijalankan |

## Konsekuensi

Saat backend menerbitkan endpoint daftar pesanan vendor dan skema push, hanya penerima data (bukan layar) yang ditambah. ADR ini immutable; perubahan ditulis ADR baru.
