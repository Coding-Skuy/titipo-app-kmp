# ADR-0002 — Tata kelola branch dan alur kontribusi

Status: Diterima | Tanggal: 2026-10-21

## Konteks

Repo dikerjakan manusia dan agen AI secara paralel. Tanpa alur seragam, riwayat `main` sulit ditinjau dan rilis berisiko (QA gate di SRS/40 memblokir rilis).

## Keputusan

1. Kontributor bekerja di fork, bukan langsung di repo organisasi.
2. Branch dibuat dari `upstream/main` dengan pola `<jenis>/<kode-tiket-huruf-kecil>-<slug>` (`feat`, `fix`, `test`, `docs`, `chore`). Satu branch = satu tiket = satu PR.
3. Commit atomic memakai conventional commits berbahasa Indonesia.
4. PR memakai `.github/pull_request_template.md`, memuat `Closes #N`, bukti uji, dan risiko.
5. `.github/CODEOWNERS` menetapkan `@Coding-Skuy` sebagai peninjau default.
6. Usulan proteksi branch `main` (HANYA USULAN, belum diterapkan; perubahan pengaturan repo dilakukan pemilik organisasi):
   - wajib PR dan minimal 1 persetujuan (code owner);
   - wajib status check lulus (uji unit KMP, dan kontrak bila sudah ada di CI);
   - larang force-push dan penghapusan `main`;
   - penulis PR tidak boleh menyetujui/merge PR sendiri.

## Konsekuensi

Riwayat `main` linear dan dapat ditinjau per tiket. Menambah satu langkah (fork) bagi kontributor internal. Panduan operasional ada di `CONTRIBUTING.md`. ADR ini immutable; perubahan ditulis sebagai ADR baru.
