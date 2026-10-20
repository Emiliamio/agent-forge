#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p docs/sbom
(cd backend  && mvn -q -B cyclonedx:makeAggregateBom && cp target/bom.json ../docs/sbom/backend-bom.json)
(cd frontend && npm sbom --sbom-format cyclonedx > ../docs/sbom/frontend-bom.json)
PYTHON_CMD="python"
if python3 -c "import sys" >/dev/null 2>&1; then
  PYTHON_CMD="python3"
fi
$PYTHON_CMD scripts/normalize_sbom.py docs/sbom/backend-bom.json docs/sbom/frontend-bom.json
echo "[OK] docs/sbom 已刷新（如需提交：git add docs/sbom && git commit -m 'chore(sbom): refresh'）"
