#!/usr/bin/env bash
# AgentForge 可观测性与安全暴露自动化探针 (metrics_probe.sh)
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

METRICS_HOST="${METRICS_HOST:-127.0.0.1}"
METRICS_PORT="${METRICS_PORT:-8080}"
BASE_URL="http://${METRICS_HOST}:${METRICS_PORT}"

echo "========================================================"
echo " Starting AgentForge Metrics & Actuator Guard Probe"
echo " Target Base: $BASE_URL"
echo "========================================================"

# 1. 验证 Prometheus 指标端点 (/actuator/prometheus)
echo "Step 1: 验证本地回环访问 /actuator/prometheus 指标端点..."
PROMETHEUS_OUT=$(curl -sfS "$BASE_URL/actuator/prometheus" 2>&1 || true)
if grep -q "http_server_requests_seconds_count" <<< "$PROMETHEUS_OUT" || grep -q "jvm_memory_used_bytes" <<< "$PROMETHEUS_OUT"; then
  METRIC_COUNT=$(grep -c "^[a-zA-Z_]" <<< "$PROMETHEUS_OUT" || echo "0")
  echo "[PASS] /actuator/prometheus 正常暴露指标 (采集到约 $METRIC_COUNT 类指标)"
else
  echo "[FAIL] /actuator/prometheus 未检测到预期监控指标 (输出前 200 字符: ${PROMETHEUS_OUT:0:200})"
  exit 1
fi

# 2. 验证 Health 探针端点 (/actuator/health)
echo "Step 2: 验证本地回环访问 /actuator/health 端点..."
HEALTH_RESP=$(curl -sS -w "\n%{http_code}" "$BASE_URL/actuator/health")
HEALTH_CODE=$(echo "$HEALTH_RESP" | tail -n1)
HEALTH_BODY=$(echo "$HEALTH_RESP" | sed '$d')

if [ "$HEALTH_CODE" -eq 200 ] && grep -q '"status":"UP"' <<< "$HEALTH_BODY"; then
  # 验证 show-details: never，确保未泄露敏感拓扑
  if grep -q "database" <<< "$HEALTH_BODY" || grep -q "postgres" <<< "$HEALTH_BODY"; then
    echo "[FAIL] /actuator/health 泄露了内部组件敏感细节: $HEALTH_BODY"
    exit 1
  fi
  echo "[PASS] /actuator/health 返回状态 UP 且内部敏感细节已隐藏"
else
  echo "[FAIL] /actuator/health 期望 HTTP 200 且 status=UP，实际 HTTP $HEALTH_CODE, body: $HEALTH_BODY"
  exit 1
fi

# 3. 验证从公网源访问被静默拦截为 404 (不对公网暴露端点指纹)
echo "Step 3: 模拟公网源访问验证 404 静默拦截..."

# (A) 模拟通过受信任反代传递公网客户端 IP (203.0.113.199)
PUBLIC_RESP=$(curl -sS -w "\n%{http_code}" \
  -H "X-Forwarded-For: 203.0.113.199" \
  "$BASE_URL/actuator/prometheus")
PUBLIC_RESP_CODE=$(echo "$PUBLIC_RESP" | tail -n1)

if [ "$PUBLIC_RESP_CODE" -eq 404 ]; then
  echo "[PASS] 公网源访问 /actuator/prometheus 成功拦截并返回 HTTP 404 (无端点指纹泄露)"
else
  echo "[FAIL] 公网源访问期望 HTTP 404，实际返回 HTTP $PUBLIC_RESP_CODE"
  exit 1
fi

# (B) 验证 /actuator/health 在公网源访问下同样返回 404
PUBLIC_HEALTH_RESP=$(curl -sS -w "\n%{http_code}" \
  -H "X-Forwarded-For: 198.51.100.88" \
  "$BASE_URL/actuator/health")
PUBLIC_HEALTH_CODE=$(echo "$PUBLIC_HEALTH_RESP" | tail -n1)

if [ "$PUBLIC_HEALTH_CODE" -eq 404 ]; then
  echo "[PASS] 公网源访问 /actuator/health 成功拦截并返回 HTTP 404"
else
  echo "[FAIL] 公网源访问 /actuator/health 期望 HTTP 404，实际返回 HTTP $PUBLIC_HEALTH_CODE"
  exit 1
fi

echo "========================================================"
echo " Metrics & Actuator Guard Probe PASSED"
echo " All 3 assertion criteria verified successfully"
echo "========================================================"
exit 0
