#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
mkdir -p docs/sbom

# 1) 后端 BOM：Maven 依赖解析与平台无关（已验证 Windows/Linux 归一化后字节一致）
MVN="mvn"
if ! "$MVN" -v >/dev/null 2>&1; then
  if command -v mvn.cmd >/dev/null 2>&1; then MVN="mvn.cmd"; fi
fi
(cd backend && "$MVN" -q -B cyclonedx:makeAggregateBom && cp target/bom.json "$ROOT/docs/sbom/backend-bom.json")

# 2) 前端 BOM：npm 会按当前平台裁剪 optionalDependencies（@esbuild/*、@rollup/rollup-*），
#    直接用本机 npm 生成会导致 Windows 与 Linux 结果不同、CI 的 sbom-drift 恒红。
#    因此固定用 node:20 容器渲染；且只挂载 package.json / package-lock.json，
#    node_modules 在容器内安装（挂载宿主机 node_modules 会因跨平台二进制导致 npm ci EIO）。
if ! command -v docker >/dev/null 2>&1; then
  echo "[FAIL] 生成前端 SBOM 需要 Docker（node:20 容器），请先安装并启动 Docker" >&2
  exit 1
fi
TMP_FE="$(mktemp)"
trap 'rm -f "$TMP_FE"' EXIT
docker run --rm \
  -v "$ROOT/frontend/package.json:/src/package.json:ro" \
  -v "$ROOT/frontend/package-lock.json:/src/package-lock.json:ro" \
  node:20 sh -c '
    mkdir -p /work && cp /src/package.json /src/package-lock.json /work/ && cd /work &&
    npm ci --silent >/tmp/npmci.log 2>&1 || { tail -5 /tmp/npmci.log >&2; exit 1; }
    npm sbom --sbom-format cyclonedx
  ' > "$TMP_FE"
[ -s "$TMP_FE" ] || { echo "[FAIL] 前端 SBOM 生成失败（输出为空，未覆盖仓库文件）" >&2; exit 1; }
mv "$TMP_FE" "$ROOT/docs/sbom/frontend-bom.json"
trap - EXIT

PYTHON_CMD="python"
if python3 -c "import sys" >/dev/null 2>&1; then
  PYTHON_CMD="python3"
fi
$PYTHON_CMD scripts/normalize_sbom.py docs/sbom/backend-bom.json docs/sbom/frontend-bom.json
echo "[OK] docs/sbom 已刷新（后端 mvn / 前端 node:20 容器，跨平台一致）"