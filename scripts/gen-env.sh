#!/usr/bin/env bash
set -euo pipefail

ENV_FILE=".env"
FORCE=false

if [ "${1:-}" = "--force" ]; then
  FORCE=true
fi

if [ -f "$ENV_FILE" ] && [ "$FORCE" = false ]; then
  echo "[WARN] $ENV_FILE 已存在，跳过生成。若需强制重新生成请使用: bash scripts/gen-env.sh --force"
  exit 0
fi

if command -v python3 >/dev/null 2>&1 && python3 -c "import secrets" >/dev/null 2>&1; then
  PYTHON="python3"
elif command -v python >/dev/null 2>&1 && python -c "import secrets" >/dev/null 2>&1; then
  PYTHON="python"
else
  echo "Error: Python 3 with secrets module required" >&2
  exit 1
fi

PG_PASS=$($PYTHON -c "import secrets; print(secrets.token_hex(32))")
REDIS_PASS=$($PYTHON -c "import secrets; print(secrets.token_hex(32))")
JWT_SEC=$($PYTHON -c "import secrets; print(secrets.token_hex(32))")

cat <<EOF > "$ENV_FILE"
# AgentForge Production Environment Secrets
# Generated automatically by scripts/gen-env.sh
POSTGRES_PASSWORD=${PG_PASS}
REDIS_PASSWORD=${REDIS_PASS}
JWT_SECRET=${JWT_SEC}
EOF

chmod 600 "$ENV_FILE" 2>/dev/null || true

echo "[OK] 已成功生成强密码生产环境配置文件: $ENV_FILE (chmod 600)"
echo "     包含密钥项: POSTGRES_PASSWORD, REDIS_PASSWORD, JWT_SECRET"
echo "     下一步可直接启动生产编排: docker compose -f docker-compose-prod.yml up -d"
