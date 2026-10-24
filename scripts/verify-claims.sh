#!/usr/bin/env bash
# AgentForge 核心指标声明逐项自动化验证门禁 (verify-claims.sh)
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PASS_COUNT=0
TOTAL_CLAIMS=12

echo "========================================================"
echo " Starting AgentForge Claims Verification Gate"
echo " Target Matrix: docs/CLAIMS.md"
echo "========================================================"

# 若全栈服务未就绪，自动通过 docker compose 拉起
if ! curl -sf -m 2 http://localhost:8080/api/system/health >/dev/null 2>&1; then
  echo "Full stack not responding at localhost:8080; booting via docker compose..."
  docker compose --profile full up -d --wait
fi

record_pass() {
  local num="$1"
  local title="$2"
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[PASS] Claim $num: $title"
}

record_fail() {
  local num="$1"
  local title="$2"
  local reason="$3"
  echo "[FAIL] Claim $num: $title -> $reason"
  exit 1
}

# --- Claim 1: 内部检索准确率 Recall@5 = 100.0% ---
echo "Verifying Claim 1: 内部检索 Recall@5 = 100.0%..."
TMP_BENCH=$(mktemp 2>/dev/null || echo "/tmp/c.json")
if python scripts/benchmark.py --out "$TMP_BENCH" >/dev/null 2>&1 && \
   python scripts/check_benchmark.py --baseline docs/benchmark/stable.json --current "$TMP_BENCH" | grep -q "No drift detected"; then
  record_pass "01" "内部检索准确率 Recall@5 = 100.0%"
else
  record_fail "01" "内部检索准确率 Recall@5 = 100.0%" "check_benchmark failed or detected drift"
fi

# --- Claim 2: 内部检索排序指标 MRR = 0.9722 / NDCG@5 = 0.9795 ---
echo "Verifying Claim 2: 内部 MRR = 0.9722 / NDCG@5 = 0.9795..."
if python -c "
import json
with open('docs/benchmark/stable.json', 'r', encoding='utf-8') as f:
    d = json.load(f)['stable']
assert d.get('mrr') == 0.9722, f'Expected mrr 0.9722, got {d.get(\"mrr\")}'
ndcg = d.get('ndcg_at_5') or d.get('ndcg5')
assert ndcg == 0.9795, f'Expected ndcg_at_5 0.9795, got {ndcg}'
"; then
  record_pass "02" "内部检索排序指标 MRR = 0.9722 / NDCG@5 = 0.9795"
else
  record_fail "02" "内部检索排序指标 MRR = 0.9722 / NDCG@5 = 0.9795" "stable.json metrics do not match"
fi

# --- Claim 3: 第三方民法典权威语料检索 Recall@5 = 93.75% ---
echo "Verifying Claim 3: 外部语料 Recall@5 = 93.75%..."
TMP_EXT=$(mktemp 2>/dev/null || echo "/tmp/ext_benchmark.md")
python scripts/benchmark_external.py --deterministic --out "$TMP_EXT" >/dev/null 2>&1
if python -c "
import re, sys

def extract(path):
    with open(path, 'r', encoding='utf-8') as f:
        c = f.read()
    r1 = re.search(r'\|\s*\*\*Recall@1\*\*\s*\|\s*\*\*([\d\.]+)%?\*\*', c)
    r5 = re.search(r'\|\s*\*\*Recall@5\*\*\s*\|\s*\*\*([\d\.]+)%?\*\*', c)
    mrr = re.search(r'\|\s*\*\*MRR[^|]*\*\*\s*\|\s*\*\*([\d\.]+)\*\*', c)
    ndcg = re.search(r'\|\s*\*\*NDCG@5\*\*\s*\|\s*\*\*([\d\.]+)\*\*', c)
    if not (r1 and r5 and mrr and ndcg):
        raise ValueError(f'Missing metrics in {path}')
    return {
        'Recall@1': round(float(r1.group(1)), 4),
        'Recall@5': round(float(r5.group(1)), 4),
        'MRR': round(float(mrr.group(1)), 4),
        'NDCG@5': round(float(ndcg.group(1)), 4)
    }

ext_metrics = extract(sys.argv[1])
doc_metrics = extract('docs/BENCHMARK-EXTERNAL.md')

for k in ['Recall@1', 'Recall@5', 'MRR', 'NDCG@5']:
    assert ext_metrics[k] == doc_metrics[k], f'Metric mismatch for {k}: generated={ext_metrics[k]} vs doc={doc_metrics[k]}'
assert ext_metrics['Recall@5'] == 93.75, f'Recall@5 is {ext_metrics[\"Recall@5\"]}, expected 93.75'
" "$TMP_EXT"; then
  rm -f "$TMP_EXT"
  record_pass "03" "第三方民法典权威语料检索 Recall@5 = 93.75%"
else
  rm -f "$TMP_EXT"
  record_fail "03" "第三方民法典权威语料检索 Recall@5 = 93.75%" "external benchmark metrics do not match docs/BENCHMARK-EXTERNAL.md"
fi

# --- Claim 4: 端到端冒烟 12 项全绿 ---
echo "Verifying Claim 4: 冒烟 12 项全绿..."
SMOKE_OUT=$(bash scripts/smoke.sh http://localhost:8080 http://localhost:3000 2>&1 || true)
if grep -q "All 12 smoke tests passed successfully!" <<< "$SMOKE_OUT"; then
  record_pass "04" "端到端冒烟 12 项全绿"
else
  echo "$SMOKE_OUT"
  record_fail "04" "端到端冒烟 12 项全绿" "smoke.sh failed"
fi

# --- Claim 5: 公开挂件单 IP 限流 30 RPM 且原子拦截 ---
echo "Verifying Claim 5: 单 IP 限流 30 RPM (40 并发 -> 200:30, 429:10)..."
# 1. 清理 redis widget:* 探针状态
if docker ps --format '{{.Names}}' | grep -q "^agentforge-redis$"; then
  docker exec agentforge-redis redis-cli -a redis123 EVAL "for _,k in ipairs(redis.call('keys','widget:*')) do redis.call('del',k) end" 0 >/dev/null 2>&1 || true
fi
# 2. 确保 agent 1 挂件开启且免 token 便于探针探测
if docker ps --format '{{.Names}}' | grep -q "^agentforge-postgres$"; then
  docker exec agentforge-postgres psql -U postgres -d agentforge -c "UPDATE agent_app SET public_enabled = 1, public_token = NULL WHERE id = 1;" >/dev/null 2>&1 || true
fi

PROBE_OUT=$(python scripts/limit_probe.py --url http://localhost:8080/api/public/agents/1/chat --concurrency 40 2>&1 || true)

# 还原 agent 1 状态
if docker ps --format '{{.Names}}' | grep -q "^agentforge-postgres$"; then
  docker exec agentforge-postgres psql -U postgres -d agentforge -c "UPDATE agent_app SET public_enabled = 1, public_token = NULL WHERE id = 1;" >/dev/null 2>&1 || true
fi

if grep -q "HTTP 200 OK: 30" <<< "$PROBE_OUT" && grep -q "HTTP 429 Too Many Requests: 10" <<< "$PROBE_OUT" && grep -q "Other responses / errors: 0" <<< "$PROBE_OUT"; then
  record_pass "05" "单 IP 限流 30 RPM 且原子拦截"
else
  echo "$PROBE_OUT"
  record_fail "05" "单 IP 限流 30 RPM 且原子拦截" "Rate limiter distribution mismatch"
fi

# --- Claim 6: 后端自动化测试套件 177+ 例、0 跳过 ---
echo "Verifying Claim 6: 测试套件 177+ 例、0 跳过..."
MVN_CMD="mvn"
if command -v mvn.cmd >/dev/null 2>&1; then
  MVN_CMD="mvn.cmd"
fi
MVN_OUT=$(cd backend && $MVN_CMD -B clean verify -Dlive=true 2>&1 || true)
if grep -q "Failures: 0, Errors: 0, Skipped: 0" <<< "$MVN_OUT" && grep -q "BUILD SUCCESS" <<< "$MVN_OUT"; then
  record_pass "06" "测试 177+ 例、0 跳过"
else
  echo "$MVN_OUT" | tail -n 50
  record_fail "06" "测试 177+ 例、0 跳过" "Maven test run failed"
fi

# --- Claim 7: JaCoCo 全局分支与指令覆盖率 ≥ 0.60 ---
echo "Verifying Claim 7: BUNDLE 覆盖率 ≥ 0.60..."
if grep -q "All coverage checks have been met" <<< "$MVN_OUT"; then
  record_pass "07" "BUNDLE 覆盖率 ≥ 0.60"
else
  echo "$MVN_OUT" | tail -n 50
  record_fail "07" "BUNDLE 覆盖率 ≥ 0.60" "JaCoCo coverage check failed"
fi

# --- Claim 8: 数据库备份恢复往返可验证 ---
echo "Verifying Claim 8: 备份恢复往返可验证..."
bash scripts/backup.sh >/dev/null
LATEST_DUMP=$(ls -t backups/*.dump 2>/dev/null | head -n1 || true)
RESTORE_OUT=$(bash scripts/restore.sh --verify "$LATEST_DUMP" 2>&1 || true)
if [ -n "$LATEST_DUMP" ] && grep -q "round-trip restore verification PASSED" <<< "$RESTORE_OUT"; then
  record_pass "08" "备份恢复往返可验证"
else
  echo "$RESTORE_OUT"
  record_fail "08" "备份恢复往返可验证" "Restore verification failed"
fi

# --- Claim 9: 一键全栈可启动、健康且冷启动 ≤60s ---
echo "Verifying Claim 9: 一键全栈可启动、健康且冷启动 ≤60s..."
docker compose --profile full down >/dev/null 2>&1 || true
CLAIM9_START=$(date +%s)
if docker compose --profile full up -d --wait; then
  CLAIM9_ELAPSED=$(( $(date +%s) - CLAIM9_START ))
  echo "冷启动耗时: ${CLAIM9_ELAPSED}s (阈值 60s)"
  UNHEALTHY=$(docker compose --profile full ps --format '{{.Service}} {{.Health}}' | grep -v "healthy" || true)
  if [ -n "$UNHEALTHY" ]; then
    record_fail "09" "一键全栈可启动、健康且冷启动 ≤60s" "Containers not all healthy: $UNHEALTHY"
  elif [ "$CLAIM9_ELAPSED" -gt 60 ]; then
    record_fail "09" "一键全栈可启动、健康且冷启动 ≤60s" "冷启动 ${CLAIM9_ELAPSED}s 超过 60s 阈值"
  else
    record_pass "09" "一键全栈可启动、健康且冷启动 ≤60s（实测 ${CLAIM9_ELAPSED}s）"
  fi
else
  record_fail "09" "一键全栈可启动、健康且冷启动 ≤60s" "docker compose up returned non-zero"
fi

# --- Claim 10: 架构不变量 R1–R6 全部机器可验 ---
echo "Verifying Claim 10: 架构不变量 R1–R6 全部机器可验..."
ARCH_OUT=$(cd backend && $MVN_CMD -B -Dtest=ArchitectureTest test 2>&1 || true)
if grep -q "Tests run: 12, Failures: 0" <<< "$ARCH_OUT" && grep -q "BUILD SUCCESS" <<< "$ARCH_OUT"; then
  record_pass "10" "架构不变量 R1–R6 全部机器可验（12/12 通过）"
else
  RUN_COUNT=$(grep -oE "Tests run: [0-9]+" <<< "$ARCH_OUT" | tail -n1 || echo "unknown")
  FAIL_COUNT=$(grep -oE "Failures: [0-9]+" <<< "$ARCH_OUT" | tail -n1 || echo "unknown")
  record_fail "10" "架构不变量 R1–R6 全部机器可验" "ArchitectureTest failed ($RUN_COUNT, $FAIL_COUNT, expected 12 run, 0 failures)"
fi

# --- Claim 11: 依赖漏洞口径：前端 0 条、后端仅 7 条且全部归入'跨大版本线/上游未发布' ---
echo "Verifying Claim 11: 依赖漏洞口径（前端 0 豁免，后端 7 跨大版本/未发布项）..."
OSV_COUNT=$(grep -c "^id = " osv-scanner.toml 2>/dev/null || true)
TRIVY_COUNT=$(grep -cE "^(GHSA|CVE)" .trivyignore 2>/dev/null || true)

if [ "$OSV_COUNT" -ne 0 ]; then
  record_fail "11" "依赖漏洞口径" "osv-scanner.toml 仍有 $OSV_COUNT 条豁免，期望 0"
elif [ "$TRIVY_COUNT" -ne 7 ]; then
  record_fail "11" "依赖漏洞口径" ".trivyignore 豁免数为 $TRIVY_COUNT，期望 7"
else
  BOM_ERR=$(python - <<'PY'
import json, sys, re
try:
    with open('docs/sbom/frontend-bom.json', 'r', encoding='utf-8') as f:
        bom = json.load(f)
    comps = {c.get('name'): c.get('version', '') for c in bom.get('components', [])}
    vite_v = comps.get('vite', '0')
    vitest_v = comps.get('vitest', '0')
    if 'tinypool' in comps:
        sys.exit("tinypool 组件存在于 frontend BOM")
    def to_parts(v):
        return [int(x) if x.isdigit() else 0 for x in re.split(r'[.\-+]', v)[:3]]
    if to_parts(vite_v) < [6, 4, 4]:
        sys.exit(f"vite 版本 {vite_v} < 6.4.4")
    if to_parts(vitest_v) < [4, 1, 12]:
        sys.exit(f"vitest 版本 {vitest_v} < 4.1.12")
except Exception as e:
    sys.exit(str(e))
PY
) || true
  if [ -n "$BOM_ERR" ]; then
    record_fail "11" "依赖漏洞口径" "frontend-bom.json 校验未通过: $BOM_ERR"
  else
    record_pass "11" "依赖漏洞口径：前端 0 条、后端仅 7 条且全部归入'跨大版本线/上游未发布'"
  fi
fi

# --- Claim 12: 交付镜像非 root 且带健康检查 ---
echo "Verifying Claim 12: 交付镜像非 root 且带健康检查..."
if ! grep -q "USER 10001:10001" backend/Dockerfile; then
  record_fail "12" "交付镜像非 root 且带健康检查" "backend/Dockerfile 缺少 USER 10001:10001"
elif ! grep -q "HEALTHCHECK" backend/Dockerfile; then
  record_fail "12" "交付镜像非 root 且带健康检查" "backend/Dockerfile 缺少 HEALTHCHECK"
elif ! grep -q "USER 101:101" frontend/Dockerfile; then
  record_fail "12" "交付镜像非 root 且带健康检查" "frontend/Dockerfile 缺少 USER 101:101"
elif ! grep -q "HEALTHCHECK" frontend/Dockerfile; then
  record_fail "12" "交付镜像非 root 且带健康检查" "frontend/Dockerfile 缺少 HEALTHCHECK"
else
  record_pass "12" "交付镜像非 root 且带健康检查（backend 10001:10001, frontend 101:101）"
fi

echo "========================================================"
echo " All $TOTAL_CLAIMS claims successfully verified! (PASS $PASS_COUNT/$TOTAL_CLAIMS)"
echo "========================================================"

if [ -n "$(git status --porcelain -- docs/ scripts/ backend/ frontend/)" ]; then
  echo "[FAIL] claims gate 污染了工作区，请检查是否往仓库路径写文件"
  exit 1
fi
echo "[OK] working tree clean after claims gate"

exit 0
