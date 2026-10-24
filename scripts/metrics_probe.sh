#!/usr/bin/env bash
# AgentForge 可观测性与安全暴露自动化探针 (metrics_probe.sh)
#
# 准入控制模型（v1.5 起）：**端口隔离**，不是 IP 白名单。
#   - 管理端口 management.server.port=9090，只在容器内网/本机可达；
#   - compose 仅发布 8080，因此经 Docker NAT 转发的宿主端口请求到不了 9090；
#   - Docker 桥接下外部请求的 remoteAddr 是网桥网关（172.x），IP 白名单无法区分内外，
#     所以本脚本断言的是"宿主端口上根本不存在 /actuator"，而不是"被过滤器挡住了"。
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PUBLIC_HOST="${METRICS_HOST:-127.0.0.1}"
PUBLIC_PORT="${METRICS_PORT:-8080}"
PUBLIC_URL="http://${PUBLIC_HOST}:${PUBLIC_PORT}"

BACKEND_CONTAINER="${BACKEND_CONTAINER:-agentforge-backend}"
MGMT_PORT="${MANAGEMENT_PORT:-9090}"

echo "========================================================"
echo " Starting AgentForge Metrics & Actuator Isolation Probe"
echo " Public surface : $PUBLIC_URL (published)"
echo " Management     : ${BACKEND_CONTAINER}:${MGMT_PORT} (NOT published)"
echo "========================================================"

# 1. 容器内网访问 Prometheus 端点（应成功）
echo "Step 1: 从容器内网校验 /actuator/prometheus 指标..."
PROMETHEUS_OUT=$(docker exec "$BACKEND_CONTAINER" \
  curl -sfS "http://127.0.0.1:${MGMT_PORT}/actuator/prometheus" 2>&1 || true)
if grep -q "http_server_requests_seconds_count" <<< "$PROMETHEUS_OUT" \
   || grep -q "jvm_memory_used_bytes" <<< "$PROMETHEUS_OUT"; then
  METRIC_COUNT=$(grep -c "^[a-zA-Z_]" <<< "$PROMETHEUS_OUT" || echo "0")
  echo "[PASS] 管理端口 ${MGMT_PORT} 正常暴露指标（采集到约 ${METRIC_COUNT} 类指标）"
else
  echo "[FAIL] 管理端口未返回预期指标（输出前 200 字符: ${PROMETHEUS_OUT:0:200}）"
  exit 1
fi

# 2. 容器内网访问 Health（应 UP 且不泄露细节）
echo "Step 2: 从容器内网校验 /actuator/health..."
HEALTH_BODY=$(docker exec "$BACKEND_CONTAINER" \
  curl -sfS "http://127.0.0.1:${MGMT_PORT}/actuator/health" 2>&1 || true)
if grep -q '"status":"UP"' <<< "$HEALTH_BODY"; then
  if grep -qE '"database"|postgres|jdbc:' <<< "$HEALTH_BODY"; then
    echo "[FAIL] /actuator/health 泄露内部组件细节: ${HEALTH_BODY}"
    exit 1
  fi
  echo "[PASS] /actuator/health 返回 status=UP，且 show-details=never 生效"
else
  echo "[FAIL] /actuator/health 未返回 UP: ${HEALTH_BODY:0:200}"
  exit 1
fi

# 3. 宿主端口上 /actuator 与 /api/actuator 都必须 404（结构上不存在）
echo "Step 3: 校验宿主发布端口上不存在任何 actuator 路径..."
TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY"' EXIT
for path in "/actuator/prometheus" "/actuator/health" "/api/actuator/prometheus" "/api/actuator/health"; do
  # 不用 /dev/null：Git Bash 下 MSYS_NO_PATHCONV=1 会让原生 curl 把 /dev/null 当成 D:\dev\null
  code=$(curl -sS -o "$TMP_BODY" -w '%{http_code}' "${PUBLIC_URL}${path}")
  if [ "$code" != "404" ]; then
    echo "[FAIL] ${path} 在宿主端口返回 ${code}（期望 404：管理端点不得发布）"
    exit 1
  fi
done
echo "[PASS] 宿主端口 ${PUBLIC_PORT} 上 /actuator 与 /api/actuator 全部 404"

# 4. 结构性断言：容器端口映射里不得出现管理端口
echo "Step 4: 校验管理端口未被发布到宿主..."
PUBLISHED=$(docker port "$BACKEND_CONTAINER" 2>/dev/null || true)
if grep -qE "(^|:)${MGMT_PORT}(/|$)" <<< "$PUBLISHED"; then
  echo "[FAIL] 管理端口 ${MGMT_PORT} 已发布到宿主：${PUBLISHED}"
  exit 1
fi
echo "[PASS] 管理端口 ${MGMT_PORT} 未发布（docker port 输出中不存在）"

echo "========================================================"
echo " Metrics & Actuator Isolation Probe PASSED"
echo " 4 assertion groups verified (metrics / health / public-404 / port-not-published)"
echo "========================================================"
exit 0