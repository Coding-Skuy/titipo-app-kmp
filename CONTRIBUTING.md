# Panduan Kontribusi — titipo-app-kmp

Bahasa semua artefak (issue, commit, PR, dokumen): Indonesia. Baca dahulu `AGENTS.md` dan seluruh ADR di `docs/decisions/`.

## Alur: fork → branch → atomic commit → PR

1. Fork `Coding-Skuy/titipo-app-kmp` ke akun sendiri, clone fork, dan tambahkan remote `upstream`:
   ```bash
   gh repo fork Coding-Skuy/titipo-app-kmp --clone
   git remote add upstream https://github.com/Coding-Skuy/titipo-app-kmp.git   # bila belum ada
   ```
2. Buat branch dari `upstream/main`. Satu branch = satu tiket = satu PR.
   ```bash
   git fetch upstream && git switch -c feat/ttp-kmp-005-login-jwt upstream/main
   ```
3. Penamaan branch: `<jenis>/<kode-tiket-huruf-kecil>-<slug>`; jenis: `feat`, `fix`, `test`, `docs`, `chore`.
   Kode tiket berpola `TTP-KMP-NNN` (contoh `feat/ttp-kmp-005-login-jwt`).
4. Commit atomic (satu perubahan logis per commit), conventional commits dalam bahasa Indonesia, mis. `feat: simpan JWT di Keystore`.
   Awalan yang dipakai: `feat`, `fix`, `test`, `docs`, `chore`. Perbarui `CHANGELOG.md` untuk perubahan yang terlihat pengguna.
5. TDD merah-hijau: tulis test yang gagal dahulu. Jalankan test lokal sebelum membuka PR.
6. Push ke fork, lalu buka PR ke `Coding-Skuy/titipo-app-kmp` base `main` memakai template PR. Tulis `Closes #N` agar issue tertutup saat merge.

## Aturan

- Dilarang commit rahasia (`.env`, kunci, keystore, service key). Aplikasi tidak pernah memuat service key titipo→lumbung.
- Dokumen `versions/v*` dan ADR lama immutable. Keputusan baru = ADR baru bernomor berurutan.
- Kontrak API/event hanya berubah lewat bump versi di `titipo-backend-service` + ADR.
- Jangan force-push ke `main`; PR tidak di-merge sendiri oleh penulisnya, tinjauan manusia wajib.
- Perubahan butuh backend: buka issue di `titipo-backend-service`, bukan mengubah kodenya dari repo ini.
