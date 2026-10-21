#!/usr/bin/env bash
set -euo pipefail

export MSYS_NO_PATHCONV=1

BACKUP_DIR="backups"
mkdir -p "$BACKUP_DIR"

if command -v python3 >/dev/null 2>&1 && python3 -c "import sys" >/dev/null 2>&1; then
  PYTHON="python3"
elif command -v python >/dev/null 2>&1 && python -c "import sys" >/dev/null 2>&1; then
  PYTHON="python"
else
  echo "Error: python not found" >&2
  exit 1
fi

TS=$(date -u +"%Y%m%d_%H%M%S")
DUMP_FILE="${BACKUP_DIR}/agentforge-${TS}.dump"
META_FILE="${BACKUP_DIR}/agentforge-${TS}.meta.json"

PG_CMD="docker exec agentforge-postgres"

# 1. 导出 pg_dump 归档 (-Fc)
$PG_CMD pg_dump -U postgres -Fc -d agentforge > "$DUMP_FILE"

# 2. 搜集元数据
PG_VER=$($PG_CMD pg_dump --version)
FLYWAY_ROWS=$($PG_CMD psql -U postgres -d agentforge -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank;")

TABLE_COUNTS=$($PG_CMD psql -U postgres -d agentforge -tAc "
  SELECT 'sys_tenant', count(*) FROM sys_tenant
  UNION ALL SELECT 'sys_user', count(*) FROM sys_user
  UNION ALL SELECT 'agent_app', count(*) FROM agent_app
  UNION ALL SELECT 'dataset', count(*) FROM dataset
  UNION ALL SELECT 'document', count(*) FROM document
  UNION ALL SELECT 'document_chunk', count(*) FROM document_chunk;
")

CHUNK_STATS=$($PG_CMD psql -U postgres -d agentforge -tAc \
  "SELECT count(*), coalesce(sum(length(content)),0), coalesce(md5(string_agg(id::text, ',' order by id)),'') FROM document_chunk;")

# 3. 计算 SHA256 与文件大小
SHA256=$($PYTHON -c "import hashlib; print(hashlib.sha256(open('$DUMP_FILE','rb').read()).hexdigest())")
FILE_SIZE=$($PYTHON -c "import os; print(os.path.getsize('$DUMP_FILE'))")

# 4. 生成 meta.json
$PYTHON -c "
import json

flyway_versions = [v.strip() for v in '''$FLYWAY_ROWS'''.splitlines() if v.strip()]
table_counts = {}
for line in '''$TABLE_COUNTS'''.splitlines():
    if '|' in line:
        k, v = line.split('|', 1)
        table_counts[k.strip()] = int(v.strip())

chunk_parts = '''$CHUNK_STATS'''.strip().split('|')
chunk_metrics = {
    'count': int(chunk_parts[0]) if len(chunk_parts) > 0 and chunk_parts[0] else 0,
    'sum_length': int(chunk_parts[1]) if len(chunk_parts) > 1 and chunk_parts[1] else 0,
    'id_md5': chunk_parts[2] if len(chunk_parts) > 2 else ''
}

meta = {
    'pg_dump_version': '''$PG_VER'''.strip(),
    'created_at_utc': '''$TS''',
    'dump_file': '''$DUMP_FILE''',
    'dump_size_bytes': int('''$FILE_SIZE'''),
    'sha256': '''$SHA256''',
    'flyway_versions': flyway_versions,
    'table_counts': table_counts,
    'document_chunk_metrics': chunk_metrics
}

with open('''$META_FILE''', 'w', encoding='utf-8') as f:
    json.dump(meta, f, indent=2, ensure_ascii=False)
"

echo "[OK] backup $DUMP_FILE ($FILE_SIZE bytes, sha256=$SHA256)"
