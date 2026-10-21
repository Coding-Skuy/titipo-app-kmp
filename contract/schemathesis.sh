#!/usr/bin/env bash
# Uji berbasis properti (Schemathesis) terhadap openapi.yaml titipo-backend-service.
# Hanya endpoint yang dipakai aplikasi mobile (tag Sistem + Mobile - *) yang diuji.
#
# Variabel lingkungan:
#   BASE_URL   alamat backend/mock          (default http://localhost:8080)
#   SPEC       path/URL openapi.yaml         (default: diunduh via `gh` dari titipo-backend-service)
#   TOKEN      JWT beraudiens titipo (opsional; tanpa token endpoint terlindungi diuji sebagai 401)
#   MAX_EXAMPLES  jumlah kasus per operasi   (default 25)
#   SCHEMATHESIS_BIN  perintah schemathesis  (default `st`)
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MAX_EXAMPLES="${MAX_EXAMPLES:-25}"
ST="${SCHEMATHESIS_BIN:-st}"
OUT_DIR="${OUT_DIR:-contract/out}"
mkdir -p "$OUT_DIR"

if [[ -z "${SPEC:-}" ]]; then
  SPEC="$OUT_DIR/openapi.yaml"
  echo ">> mengunduh spec dari Coding-Skuy/titipo-backend-service (docs/api/openapi.yaml)"
  gh api repos/Coding-Skuy/titipo-backend-service/contents/docs/api/openapi.yaml --jq .content | base64 -d > "$SPEC"
fi

AUTH=()
if [[ -n "${TOKEN:-}" ]]; then
  AUTH=(-H "Authorization: Bearer ${TOKEN}")
fi

echo ">> BASE_URL=$BASE_URL SPEC=$SPEC MAX_EXAMPLES=$MAX_EXAMPLES"
exec "$ST" run "$SPEC" \
  --url "$BASE_URL" \
  --include-tag "Sistem" --include-tag "Mobile - Rumah Tangga" --include-tag "Mobile - Vendor" \
  --max-examples "$MAX_EXAMPLES" \
  --checks all \
  --report junit --report-dir "$OUT_DIR" \
  "${AUTH[@]}"
