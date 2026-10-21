#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"

echo "========================================================"
echo " Starting AgentForge Smoke Verification Suite"
echo " BASE_URL:     $BASE_URL"
echo " FRONTEND_URL: $FRONTEND_URL"
echo "========================================================"

cleanup() {
  echo "[Smoke] Cleaning up database state (restoring agent 1)..."
  docker exec agentforge-postgres psql -U postgres -d agentforge -c \
    "UPDATE agent_app SET public_enabled = 0, public_token = NULL WHERE id = 1;" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# 1. GET /api/system/health -> 200 且 code=200
RESP=$(curl -sS -w "\n%{http_code}" "$BASE_URL/system/health")
HTTP_CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
CODE=$(python3 -c "import sys, json; print(json.loads(sys.stdin.read()).get('code', ''))" <<< "$BODY")
if [ "$HTTP_CODE" -eq 200 ] && [ "$CODE" = "200" ]; then
  echo "[PASS] 01. GET /api/system/health -> 200 & code=200"
else
  echo "[FAIL] 01. GET /api/system/health expected HTTP 200 / code 200, got HTTP $HTTP_CODE, body: $BODY"
  exit 1
fi

# 2. 匿名 GET /api/agents -> 401
HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/agents")
if [ "$HTTP_CODE" -eq 401 ]; then
  echo "[PASS] 02. Anonymous GET /api/agents -> 401"
else
  echo "[FAIL] 02. Anonymous GET /api/agents expected HTTP 401, got $HTTP_CODE"
  exit 1
fi

# 3. POST /api/auth/login (admin/admin123456, tenantCode=tenant_default) -> 200 且拿到 token
RESP=$(curl -sS -w "\n%{http_code}" -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123456","tenantCode":"tenant_default"}')
HTTP_CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
TOKEN=$(python3 -c "import sys, json; print(json.loads(sys.stdin.read()).get('data', {}).get('token', ''))" <<< "$BODY")
if [ "$HTTP_CODE" -eq 200 ] && [ -n "$TOKEN" ]; then
  echo "[PASS] 03. POST /api/auth/login -> 200 & token acquired"
else
  echo "[FAIL] 03. POST /api/auth/login expected 200 & non-empty token, got HTTP $HTTP_CODE, body: $BODY"
  exit 1
fi

# 4. 带 token GET /api/agents -> 200
HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/agents" -H "Authorization: Bearer $TOKEN")
if [ "$HTTP_CODE" -eq 200 ]; then
  echo "[PASS] 04. Authenticated GET /api/agents -> 200"
else
  echo "[FAIL] 04. Authenticated GET /api/agents expected HTTP 200, got $HTTP_CODE"
  exit 1
fi

# 5. X-Tenant-Id: 2 越权访问 -> 403
HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/agents" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: 2")
if [ "$HTTP_CODE" -eq 403 ]; then
  echo "[PASS] 05. Cross-tenant access with X-Tenant-Id: 2 -> 403"
else
  echo "[FAIL] 05. Cross-tenant access expected HTTP 403, got $HTTP_CODE"
  exit 1
fi

# 6. 未知智能体 POST /api/public/agents/9999/chat -> 404 (code=3001)
RESP=$(curl -sS -w "\n%{http_code}" -X POST "$BASE_URL/public/agents/9999/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"hi"}')
HTTP_CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
CODE=$(python3 -c "import sys, json; print(json.loads(sys.stdin.read()).get('code', ''))" <<< "$BODY")
if [ "$HTTP_CODE" -eq 404 ] && [ "$CODE" = "3001" ]; then
  echo "[PASS] 06. Non-existent agent POST /api/public/agents/9999/chat -> 404 & code=3001"
else
  echo "[FAIL] 06. Non-existent agent expected HTTP 404 / code 3001, got HTTP $HTTP_CODE, body: $BODY"
  exit 1
fi

# 7. 关闭态挂件 (public_enabled=0) -> 403
docker exec agentforge-postgres psql -U postgres -d agentforge -c \
  "UPDATE agent_app SET public_enabled = 0, public_token = NULL WHERE id = 1;" >/dev/null
HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/public/agents/1/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"hi"}')
if [ "$HTTP_CODE" -eq 403 ]; then
  echo "[PASS] 07. Disabled widget (public_enabled=0) -> 403"
else
  echo "[FAIL] 07. Disabled widget expected HTTP 403, got $HTTP_CODE"
  exit 1
fi

# 8. 开启挂件 + 正确 token -> 200
docker exec agentforge-postgres psql -U postgres -d agentforge -c \
  "UPDATE agent_app SET public_enabled = 1, public_token = 'smoke-token' WHERE id = 1;" >/dev/null
HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/public/agents/1/chat" \
  -H "Content-Type: application/json" \
  -H "X-Public-Token: smoke-token" \
  -d '{"message":"hi"}')
if [ "$HTTP_CODE" -eq 200 ]; then
  echo "[PASS] 08. Enabled widget with valid token -> 200"
else
  echo "[FAIL] 08. Enabled widget with valid token expected HTTP 200, got $HTTP_CODE"
  exit 1
fi

# 9. 错误 token -> 401
HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/public/agents/1/chat" \
  -H "Content-Type: application/json" \
  -H "X-Public-Token: invalid-token-value" \
  -d '{"message":"hi"}')
if [ "$HTTP_CODE" -eq 401 ]; then
  echo "[PASS] 09. Enabled widget with invalid token -> 401"
else
  echo "[FAIL] 09. Enabled widget with invalid token expected HTTP 401, got $HTTP_CODE"
  exit 1
fi

# 10. GET /api/rag/datasets (带 token) -> 200
HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/rag/datasets" \
  -H "Authorization: Bearer $TOKEN")
if [ "$HTTP_CODE" -eq 200 ]; then
  echo "[PASS] 10. Authenticated GET /api/rag/datasets -> 200"
else
  echo "[FAIL] 10. Authenticated GET /api/rag/datasets expected HTTP 200, got $HTTP_CODE"
  exit 1
fi

# 11. 前端首页 GET $FRONTEND_URL/ -> 200 且含 <div id="app"
RESP=$(curl -sS -w "\n%{http_code}" "$FRONTEND_URL/")
HTTP_CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
if [ "$HTTP_CODE" -eq 200 ] && grep -q '<div id="app"' <<< "$BODY"; then
  echo "[PASS] 11. Frontend index GET $FRONTEND_URL/ -> 200 & contains <div id=\"app\""
else
  echo "[FAIL] 11. Frontend index expected HTTP 200 containing <div id=\"app\", got HTTP $HTTP_CODE"
  exit 1
fi

# 12. 挂件静态资源 GET $FRONTEND_URL/agentforge-widget.js -> 200 且含 buildWidgetHeaders
RESP=$(curl -sS -w "\n%{http_code}" "$FRONTEND_URL/agentforge-widget.js")
HTTP_CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
if [ "$HTTP_CODE" -eq 200 ] && grep -q 'buildWidgetHeaders' <<< "$BODY"; then
  echo "[PASS] 12. Widget static script GET $FRONTEND_URL/agentforge-widget.js -> 200 & contains buildWidgetHeaders"
else
  echo "[FAIL] 12. Widget static script expected HTTP 200 containing buildWidgetHeaders, got HTTP $HTTP_CODE"
  exit 1
fi

echo "========================================================"
echo " [OK] All 12 smoke tests passed successfully!"
echo "========================================================"
