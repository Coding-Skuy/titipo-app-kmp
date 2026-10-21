# ADR-0003 — Navigation3, penyesuaian pin versi, login, dan penyimpanan token

Status: Diterima (perlu persetujuan manusia atas butir 1) | Tanggal: 2026-10-21 | Tiket: TTP-KMP-005

## Konteks

AGENTS.md mengunci Kotlin 2.2.20, Compose Multiplatform 1.8.2, Navigation3 1.0.0 (`org.jetbrains.androidx.navigation3:navigation3-runtime`). Saat membangun nyata (Gradle, bukan hanya ditulis) pin itu tidak dapat dipenuhi bersamaan:

- Artefak `org.jetbrains.androidx.navigation3:navigation3-runtime` tidak ada di Maven Central; JetBrains hanya menerbitkan `navigation3-ui` (1.0.0-alpha06, 1.1.x, 1.2.x). Runtime berasal dari `androidx.navigation3:navigation3-runtime:1.0.0` (Google Maven, multiplatform).
- `navigation3-ui` 1.0.0-alpha06 menarik Compose UI 1.10.0-rc01 secara transitif; `androidx.navigation3:*:1.0.0` mensyaratkan AGP 8.9.1+ dan `compileSdk` 36. Dengan Compose 1.8.2 + AGP 8.5.2 pengecekan metadata AAR gagal (26 masalah).

## Keputusan

1. **Penyesuaian pin minimum agar build nyata hijau** (butuh persetujuan manusia karena AGENTS menyatakan pin terkunci): Compose Multiplatform 1.8.2 menjadi 1.10.0, AGP 8.5.2 menjadi 8.9.3, Gradle wrapper 8.10.2 menjadi 8.13, `compileSdk`/`targetSdk` 36 (`minSdk` tetap 26 = Android 8). Kotlin tetap 2.2.20. Navigation3: runtime `androidx.navigation3:navigation3-runtime:1.0.0`, UI `org.jetbrains.androidx.navigation3:navigation3-ui:1.0.0-alpha06` (alpha06 adalah satu-satunya UI multiplatform dalam garis 1.0.0). Bila manusia menolak, alternatif: tetap pada pin lama dan menunda Navigation3 (tiket 005 terblokir).
2. **Dua graf, satu biner.** `GrafRumah` dan `GrafVendor` masing-masing memiliki backstack sendiri; `AkarApp` memilih graf dari klaim peran JWT dan mereset backstack tiap sesi (`key(sub)`). Ganti peran = keluar lalu masuk ulang. Rute mengikuti `produk/10-ux-pesan-titip.md`.
3. **Klaim peran.** Backend memakai `role` (`vendor`, `household`, `admin`); tiket menyebut `peran`. Klien menerima keduanya (`role` diutamakan) dan memetakan `household`/`rumah` ke GrafRumah, `vendor` ke GrafVendor. `admin` dan peran tak dikenal ditolak di mobile (tidak ada layar admin).
4. **Audiens.** Token tanpa `aud` berisi `titipo` ditolak sebelum disimpan. Klien hanya membaca klaim (tanpa verifikasi tanda tangan); verifikasi sah tetap di backend via JWKS Lumbung.
5. **Penyimpanan token.** Android: AES-256-GCM dengan kunci di Android Keystore. iOS: Keychain (`kSecClassGenericPassword`, `AfterFirstUnlockThisDeviceOnly`). Aplikasi tidak menyimpan service key titipo-lumbung dan tidak mengakses DB titipo/Lumbung.
6. **ASUMSI kontrak login.** Kontrak login Lumbung tidak ada di openapi.yaml TitipO (login dan refresh dilakukan di Lumbung; README app menyebut email/OTP). `LoginLumbungKtor` mengasumsikan `POST {URL_AUTH}/auth/login` dengan `{email, password, audience:"titipo"}` dan respons `{access_token}`. Antarmuka `GerbangLogin` dipisah sehingga hanya implementasinya yang diganti saat kontrak terbit.
7. **TTL JWT.** Dokumen bertentangan (FSD 24 jam vs standar KONTEKS/AGENTS akses 15 menit + refresh 7 hari). Klien tidak mengkodekan TTL: ia hanya membaca `exp`; token kedaluwarsa = sesi tidak dipulihkan dan pengguna masuk ulang. Alur refresh belum diimplementasikan karena kontrak refresh Lumbung belum ada.
8. **iOS.** Modul menghasilkan framework `ComposeApp` (statis) dan `MainViewController()`. Proyek Xcode host (`iosApp`) belum dibuat di tiket ini.

## Konsekuensi

Pin versi di AGENTS.md/`40-TECHSTACK.md` perlu diperbarui lewat PR dokumen oleh manusia. ADR ini immutable; perubahan ditulis ADR baru.
