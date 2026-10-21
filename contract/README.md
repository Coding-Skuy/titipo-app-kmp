# Uji kontrak — Schemathesis

Uji berbasis properti terhadap `docs/api/openapi.yaml` milik `titipo-backend-service` (spec tidak disalin ke repo ini).
Cakupan: endpoint yang dipakai aplikasi mobile (tag `Sistem`, `Mobile - Rumah Tangga`, `Mobile - Vendor`).

## Lokal

```bash
python3 -m venv .venv && .venv/bin/pip install schemathesis
export SCHEMATHESIS_BIN=.venv/bin/st
BASE_URL=http://localhost:8080 ./contract/schemathesis.sh          # spec diunduh via gh
SPEC=/path/openapi.yaml TOKEN=<jwt aud=titipo> ./contract/schemathesis.sh
```

Tanpa `TOKEN`, endpoint terlindungi hanya menguji jalur 401. Jangan commit token; hasil ada di `contract/out/` (di-ignore git).

## CI

Workflow `Uji kontrak Schemathesis (manual)` (`workflow_dispatch`) di tab Actions. Secret opsional `BACKEND_READ_TOKEN`
(token baca ke repo backend) dipakai untuk mengunduh spec; bila tidak ada, set `SPEC` lewat salinan lokal.

## Catatan

- Kegagalan = ketidaksesuaian backend vs spec: laporkan sebagai issue di `titipo-backend-service`, jangan ubah spec dari sini.
- Pengujian terhadap produksi dilarang; gunakan lokal/staging/mock.
