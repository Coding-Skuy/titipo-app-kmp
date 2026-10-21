# CHANGELOG — titipo-app-kmp

## [Belum dirilis]

### Ditambahkan
- 2026-10-21: TTP-KMP-004 3 layar vendor (masuk dengan geser terima/tolak, kemas checklist, serah kamera 1280 px <= 800 KB), `RepositoriVendor`, pemantau koneksi + bilah luring, ADR-0005 (celah kontrak).
- 2026-10-21: TTP-KMP-003 5 layar rumah tangga (daftar vendor, katalog, keranjang, bayar QRIS timer 15:00, lacak), `SesiBelanja` (pratinjau hitungan, checkout online idempoten) dan ADR-0004 (celah kontrak).
- 2026-10-21: TTP-KMP-005 login JWT audiens `titipo` (validasi audiens/peran, token terenkripsi Keystore/Keychain), navigasi 2 graf Navigation3 per peran, modul `composeApp` (Android + framework iOS), penyesuaian pin versi (ADR-0003).
- 2026-10-21: TTP-KMP-002 modul `shared` (model kontrak, validasi pratinjau keranjang, ULID, klien Ktor, skema SQLDelight 4 tabel cermin, antrean offline-first FIFO + retry + batas) dan ADR-0002.
- 2026-10-14: fondasi AI-dev v1.0.0 (`AGENTS.md`, `CLAUDE.md`, `docs/decisions/ADR-0001-fondasi-teknis.md`, `versions/v1.0.0/docs/40-TECHSTACK.md`, `versions/v1.0.0/docs/60-BLAST-RADIUS.md`).
