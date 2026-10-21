#!/usr/bin/env bash
set -euo pipefail

export MSYS_NO_PATHCONV=1

if command -v python3 >/dev/null 2>&1 && python3 -c "import sys" >/dev/null 2>&1; then
  PYTHON="python3"
elif command -v python >/dev/null 2>&1 && python -c "import sys" >/dev/null 2>&1; then
  PYTHON="python"
else
  echo "Error: python not found" >&2
  exit 1
fi

VERIFY_MODE=false
DUMP_FILE=""

for arg in "$@"; do
  if [ "$arg" = "--verify" ]; then
    VERIFY_MODE=true
  elif [ -z "$DUMP_FILE" ]; then
    DUMP_FILE="$arg"
  fi
done

if [ -z "$DUMP_FILE" ]; then
  DUMP_FILE=$(ls -t backups/*.dump 2>/dev/null | head -n 1 || true)
fi

if [ -z "$DUMP_FILE" ] || [ ! -f "$DUMP_FILE" ]; then
  echo "Error: dump file not found" >&2
  exit 1
fi

META_FILE="${DUMP_FILE%.dump}.meta.json"
if [ ! -f "$META_FILE" ]; then
  echo "Error: metadata file $META_FILE not found" >&2
  exit 1
fi

# 1. 校验 SHA256
ACTUAL_SHA256=$($PYTHON -c "import hashlib; print(hashlib.sha256(open('$DUMP_FILE','rb').read()).hexdigest())")
EXPECTED_SHA256=$($PYTHON -c "import json; print(json.load(open('$META_FILE'))['sha256'])")

if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  echo "Error: SHA256 checksum mismatch! expected=$EXPECTED_SHA256 actual=$ACTUAL_SHA256" >&2
  exit 1
fi
echo "[OK] sha256 verified"

PG_CMD="docker exec agentforge-postgres"

if [ "$VERIFY_MODE" = false ]; then
  # 普通恢复模式
  docker exec -i agentforge-postgres pg_restore -U postgres -d agentforge --clean --if-exists < "$DUMP_FILE" || true
  TABLE_COUNT=$($PG_CMD psql -U postgres -d agentforge -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';")
  echo "[OK] restored to agentforge ($TABLE_COUNT tables verified)"
  exit 0
fi

# 验证模式 (--verify)
TEMP_DB="agentforge_restore_check"

# 清理并创建临时库
$PG_CMD psql -U postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$TEMP_DB' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
$PG_CMD psql -U postgres -c "DROP DATABASE IF EXISTS $TEMP_DB;" >/dev/null 2>&1
$PG_CMD psql -U postgres -c "CREATE DATABASE $TEMP_DB;" >/dev/null 2>&1

# 恢复到临时库
docker exec -i agentforge-postgres pg_restore -U postgres -d "$TEMP_DB" < "$DUMP_FILE" >/dev/null 2>&1 || true
echo "[OK] restored to temp db $TEMP_DB"

# 采集临时库指标
FLYWAY_ROWS=$($PG_CMD psql -U postgres -d "$TEMP_DB" -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank;")

TABLE_COUNTS=$($PG_CMD psql -U postgres -d "$TEMP_DB" -tAc "
  SELECT 'sys_tenant', count(*) FROM sys_tenant
  UNION ALL SELECT 'sys_user', count(*) FROM sys_user
  UNION ALL SELECT 'agent_app', count(*) FROM agent_app
  UNION ALL SELECT 'dataset', count(*) FROM dataset
  UNION ALL SELECT 'document', count(*) FROM document
  UNION ALL SELECT 'document_chunk', count(*) FROM document_chunk;
")

CHUNK_STATS=$($PG_CMD psql -U postgres -d "$TEMP_DB" -tAc \
  "SELECT count(*), coalesce(sum(length(content)),0), coalesce(md5(string_agg(id::text, ',' order by id)),'') FROM document_chunk;")

# Python 核验元数据与临时库一致性
$PYTHON -c "
import json, sys

meta = json.load(open('''$META_FILE'''))

# 1. 校验 Flyway
actual_flyway = [v.strip() for v in '''$FLYWAY_ROWS'''.splitlines() if v.strip()]
expected_flyway = meta.get('flyway_versions', [])
if actual_flyway != expected_flyway:
    sys.stderr.write(f'Flyway versions mismatch: expected {expected_flyway}, got {actual_flyway}\n')
    sys.exit(1)
print(f'[OK] flyway versions matched ({len(actual_flyway)}/{len(expected_flyway)})')

# 2. 校验 6 表 count
actual_counts = {}
for line in '''$TABLE_COUNTS'''.splitlines():
    if '|' in line:
        k, v = line.split('|', 1)
        actual_counts[k.strip()] = int(v.strip())

expected_counts = meta.get('table_counts', {})
for tbl, exp_cnt in expected_counts.items():
    act_cnt = actual_counts.get(tbl)
    if act_cnt != exp_cnt:
        sys.stderr.write(f'Table count mismatch for {tbl}: expected {exp_cnt}, got {act_cnt}\n')
        sys.exit(1)
print('[OK] 6 table counts matched')

# 3. 校验 document_chunk 3 项指标
chunk_parts = '''$CHUNK_STATS'''.strip().split('|')
act_chunk = {
    'count': int(chunk_parts[0]) if len(chunk_parts) > 0 and chunk_parts[0] else 0,
    'sum_length': int(chunk_parts[1]) if len(chunk_parts) > 1 and chunk_parts[1] else 0,
    'id_md5': chunk_parts[2] if len(chunk_parts) > 2 else ''
}
exp_chunk = meta.get('document_chunk_metrics', {})
for k in ['count', 'sum_length', 'id_md5']:
    if act_chunk.get(k) != exp_chunk.get(k):
        sys.stderr.write(f'Chunk metric mismatch for {k}: expected {exp_chunk.get(k)}, got {act_chunk.get(k)}\n')
        sys.exit(1)
print('[OK] document_chunk metrics matched')
"

# 4. 活体启动验证：启动临时短命后端实例连到 temp db
TEMP_PID=""
TEMP_CONTAINER=""

cleanup_temp_backend() {
  if [ -n "$TEMP_PID" ]; then
    kill "$TEMP_PID" >/dev/null 2>&1 || true
    wait "$TEMP_PID" 2>/dev/null || true
  fi
  if [ -n "$TEMP_CONTAINER" ]; then
    docker stop "$TEMP_CONTAINER" >/dev/null 2>&1 || true
    docker rm -f "$TEMP_CONTAINER" >/dev/null 2>&1 || true
  fi
}

JAR_FILE=$(ls backend/target/agent-forge-backend*.jar 2>/dev/null | grep -v 'findsecbugs' | head -n 1 || true)
if [ -n "$JAR_FILE" ] && command -v java >/dev/null 2>&1; then
  DB_NAME="$TEMP_DB" SERVER_PORT=18081 java -jar "$JAR_FILE" >/dev/null 2>&1 &
  TEMP_PID=$!
elif docker compose --profile full run --help >/dev/null 2>&1; then
  docker compose --profile full run -d --rm --name temp_verify_backend -p 18081:18081 -e DB_NAME="$TEMP_DB" -e SERVER_PORT=18081 backend >/dev/null 2>&1 || true
  TEMP_CONTAINER="temp_verify_backend"
fi

# 轮询探测健康接口
HEALTH_PASSED=false
for i in $(seq 1 40); do
  if curl -sf http://localhost:18081/api/system/health >/dev/null 2>&1 || curl -sf http://localhost:18081/system/health >/dev/null 2>&1; then
    HEALTH_PASSED=true
    break
  fi
  sleep 1
done

cleanup_temp_backend

if [ "$HEALTH_PASSED" != true ]; then
  echo "Error: temp backend health probe failed after 40s" >&2
  exit 1
fi
echo "[OK] temp backend health probe passed (HTTP 200)"

# 清理临时库
$PG_CMD psql -U postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$TEMP_DB' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
$PG_CMD psql -U postgres -c "DROP DATABASE IF EXISTS $TEMP_DB;" >/dev/null 2>&1
echo "[OK] temp db cleaned up"
echo "[OK] round-trip restore verification PASSED"
