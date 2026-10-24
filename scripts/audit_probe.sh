#!/usr/bin/env bash
# AgentForge 公开挂件调用审计留痕自动化探针 (audit_probe.sh)
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
DB_CONTAINER="${DB_CONTAINER:-agentforge-postgres}"
REDIS_CONTAINER="${REDIS_CONTAINER:-agentforge-redis}"
DB_NAME="${DB_NAME:-agentforge}"

echo "========================================================"
echo " Starting AgentForge Widget Call Audit Probe"
echo " Target Endpoint: $BASE_URL/public/agents/1/chat"
echo "========================================================"

# 0. 确保基础环境就绪并清理解除限流残留与存量审计数据
if docker ps --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
  docker exec "$REDIS_CONTAINER" redis-cli -a redis123 EVAL \
    "for _,k in ipairs(redis.call('keys','widget:*')) do redis.call('del',k) end" 0 >/dev/null 2>&1 || true
fi

if docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
  docker exec "$DB_CONTAINER" psql -U postgres -d "$DB_NAME" -c \
    "UPDATE agent_app SET public_enabled = 1, public_token = NULL WHERE id = 1; TRUNCATE TABLE widget_call_audit;" >/dev/null 2>&1
else
  echo "[FAIL] Database container $DB_CONTAINER is not running"
  exit 1
fi

# 1. 发起 1 次正常请求 (预期 HTTP 200 -> outcome=ok)
echo "Step 1: 发起单次正常调用 (预期 200 OK)..."
RESP_SINGLE=$(curl -sS -w "\n%{http_code}" -X POST "$BASE_URL/public/agents/1/chat" \
  -H "Content-Type: application/json" \
  -H "User-Agent: AuditProbe/1.0" \
  -d '{"message":"probe-single-ok"}')
CODE_SINGLE=$(echo "$RESP_SINGLE" | tail -n1)
if [ "$CODE_SINGLE" -ne 200 ]; then
  echo "[FAIL] Single request expected HTTP 200, got $CODE_SINGLE, body: $RESP_SINGLE"
  exit 1
fi

# 重置 Redis 限流计数器，保证并发 40 请求获得全新的 30 RPM 配额窗口 (30=200, 10=429)
if docker ps --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
  docker exec "$REDIS_CONTAINER" redis-cli -a redis123 EVAL \
    "for _,k in ipairs(redis.call('keys','widget:*')) do redis.call('del',k) end" 0 >/dev/null 2>&1 || true
fi

# 2. 连续并发发起 40 次调用触发原子限流 (预期 30 次 200，10 次 429)
echo "Step 2: 并发发起 40 次请求触发限流 (预期 30 次 200, 10 次 429)..."
python scripts/limit_probe.py --url "$BASE_URL/public/agents/1/chat" --concurrency 40 >/dev/null

# 等待异步审计写入线程池落盘
sleep 2

# 3. 查库断言审计记录字段与分布
echo "Step 3: 查库断言审计记录完整性与脱敏规范..."

AUDIT_STATS=$(docker exec "$DB_CONTAINER" psql -U postgres -d "$DB_NAME" -t -A -F"," -c \
  "SELECT count(*), 
          count(*) FILTER (WHERE outcome = 'ok'),
          count(*) FILTER (WHERE outcome = 'rate_limited'),
          count(*) FILTER (WHERE ip_hash ~ '^[0-9a-f]{64}$'),
          count(*) FILTER (WHERE ua_hash ~ '^[0-9a-f]{64}$'),
          count(*) FILTER (WHERE latency_ms >= 0),
          count(*) FILTER (WHERE trace_id IS NOT NULL AND length(trace_id) > 0)
   FROM widget_call_audit;")

IFS=',' read -r TOTAL_COUNT OK_COUNT RATE_LIMIT_COUNT IP_HASH_VALID UA_HASH_VALID LATENCY_VALID TRACE_VALID <<< "$AUDIT_STATS"

echo "数据库实测统计: 总数=$TOTAL_COUNT, ok=$OK_COUNT, rate_limited=$RATE_LIMIT_COUNT"

if [ "$TOTAL_COUNT" -ne 41 ]; then
  echo "[FAIL] 审计总记录数不匹配: 期望 41 (1单次 + 40并发)，实际 $TOTAL_COUNT"
  exit 1
fi

if [ "$OK_COUNT" -ne 31 ]; then
  echo "[FAIL] outcome=ok 记录数不匹配: 期望 31，实际 $OK_COUNT"
  exit 1
fi

if [ "$RATE_LIMIT_COUNT" -ne 10 ]; then
  echo "[FAIL] outcome=rate_limited 记录数不匹配: 期望 10，实际 $RATE_LIMIT_COUNT"
  exit 1
fi

if [ "$IP_HASH_VALID" -ne "$TOTAL_COUNT" ]; then
  echo "[FAIL] 存在不满足 64 位 SHA-256 十六进制规范的 ip_hash 记录"
  exit 1
fi

if [ "$UA_HASH_VALID" -ne "$TOTAL_COUNT" ]; then
  echo "[FAIL] 存在不满足 64 位 SHA-256 十六进制规范的 ua_hash 记录"
  exit 1
fi

if [ "$LATENCY_VALID" -ne "$TOTAL_COUNT" ]; then
  echo "[FAIL] 存在 latency_ms < 0 的非法记录"
  exit 1
fi

if [ "$TRACE_VALID" -ne "$TOTAL_COUNT" ]; then
  echo "[FAIL] 存在 trace_id 缺失的非法记录"
  exit 1
fi

# 4. 断言明文 IP 绝对未泄露进数据库任何字段
LEAK_COUNT=$(docker exec "$DB_CONTAINER" psql -U postgres -d "$DB_NAME" -t -A -c \
  "SELECT count(*) FROM widget_call_audit 
   WHERE trace_id LIKE '%127.0.0.1%' OR ip_hash LIKE '%127.0.0.1%' OR outcome LIKE '%127.0.0.1%';")

if [ "$LEAK_COUNT" -ne 0 ]; then
  echo "[FAIL] 数据库记录中检测到明文 IP (127.0.0.1) 泄露!"
  exit 1
fi

echo "[OK] 检查了 $TOTAL_COUNT 条审计记录，所有字段与状态均验证通过 (ok=$OK_COUNT, rate_limited=$RATE_LIMIT_COUNT, 明文IP泄露=0)"

# 恢复现场：清理限流 key，还原 agent 1 配置
if docker ps --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
  docker exec "$REDIS_CONTAINER" redis-cli -a redis123 EVAL \
    "for _,k in ipairs(redis.call('keys','widget:*')) do redis.call('del',k) end" 0 >/dev/null 2>&1 || true
fi

echo "========================================================"
echo " Widget Call Audit Probe PASSED"
echo "========================================================"
exit 0
