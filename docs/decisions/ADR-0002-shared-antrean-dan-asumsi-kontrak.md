# ADR-0002 — Modul shared, antrean offline-first, dan asumsi kontrak

Status: Diterima | Tanggal: 2026-10-21 | Tiket: TTP-KMP-002

## Konteks

TTP-KMP-002 membangun modul `shared` (Kotlin murni) dan antrean offline-first SQLDelight. Kontrak HTTP diturunkan dari `docs/api/openapi.yaml` titipo-backend-service pada branch PR #10 (TTP-BE-004, versi kontrak 1.0.0; belum merge). Beberapa hal pada dokumen/kontrak tidak selaras atau belum ada, dan dicatat di sini agar tidak diputuskan diam-diam.

## Keputusan

1. **Batas berat dari konfigurasi.** `KonfigurasiBisnis.batasBeratGram` diinjeksi, tidak dikeraskan di UI. Bawaan 50.000 g mencerminkan `BATAS_BERAT_GRAM` backend (AGENTS/KONTEKS: maksimal 50 kg, di atasnya grosir), sementara FSD dan teks tiket menyebut 20 kg. Selisih ini dilaporkan, bukan diputuskan di klien. Batas 50 baris dan ongkir Rp5.000 mengikuti kontrak.
2. **Struktur modul.** `shared` (target Android + iOS, tanpa target desktop) memuat model, validasi pratinjau, antrean, klien Ktor. Driver SQLDelight dan engine Ktor dipilih per platform lewat dependensi di `androidMain`/`iosMain`; kode `commonMain` tidak mengimpor Compose maupun API Android/iOS. Uji DB berjalan di JVM (`androidUnitTest`, `sqlite-driver`) dan iOS (`iosTest`, driver native in-memory).
3. **Antrean.** FIFO satu per satu, jeda 1 detik antar aksi, retry 30 detik, batas 24 jam sejak dibuat (lalu status `gagal`, terlihat, tombol kirim ulang manual yang memulai ulang jam), `Idempotency-Key` = ULID aksi yang sama di setiap retry. Penolakan server 4xx (selain 401/408/429) menandai `gagal` tanpa retry. Batas: 50 aksi/24 jam, 20 foto belum selesai, foto maksimal 800 KB, 5 perubahan jadwal per 7 hari.
4. **ASUMSI** jendela bergulir untuk "5 perubahan jadwal per minggu" dan "20 foto" (jumlah foto yang belum selesai terkirim); dokumen tidak menyebut awal pekan atau periode foto.
5. **ASUMSI** pembulatan pratinjau subtotal: `jumlah_gram * harga_per_kg / 1000` dibulatkan setengah ke atas. Angka sah tetap dari server.
6. **Tutup jadwal tanpa endpoint.** Jenis aksi `tutup_jadwal` ada di antrean (batas 5/minggu berlaku) tetapi openapi.yaml tidak memiliki endpoint-nya; pengirim menandai `gagal` dengan kode `ENDPOINT_BELUM_ADA` sampai backend menerbitkan kontrak.
7. **Versi.** Mengikuti pin AGENTS (Kotlin 2.2.20, Compose 1.8.2, AGP 8.5.2, Gradle wrapper 8.10.2). SQLDelight 2.0.2 (rilis 2.0.x terbaru; `produk/30` menyebut 2.0.3 yang tidak ada). `produk/30` menyebut Kotlin 2.1.20/Compose 1.7.3, tidak dipakai karena AGENTS menang.

## Konsekuensi

Perubahan kontrak endpoint harus mengikuti openapi.yaml backend (bump versi + ADR). Klien tidak menghitung angka sah. ADR ini immutable; perubahan ditulis ADR baru.
