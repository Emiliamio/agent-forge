#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1
PG="docker exec agentforge-postgres psql -U postgres"
DB=agentforge_drill
$PG -c "DROP DATABASE IF EXISTS $DB;" >/dev/null
$PG -c "CREATE DATABASE $DB;" >/dev/null
docker cp scripts/fixtures/legacy-schema-v1.sql agentforge-postgres:/tmp/legacy.sql
$PG -d $DB -v ON_ERROR_STOP=1 -f /tmp/legacy.sql >/dev/null
cd backend
if [ ! -f target/agent-forge-backend-1.0.0-SNAPSHOT.jar ]; then
  mvn.cmd -q -DskipTests package
fi
timeout 180 java -Dserver.port=18080 -DDB_NAME=$DB -jar target/agent-forge-backend-1.0.0-SNAPSHOT.jar > /tmp/drill-spring-boot.log 2>&1 &
APP=$!
cleanup() {
  kill $APP 2>/dev/null || true
  PIDS=$(netstat -ano 2>/dev/null | grep ':18080' | awk '{print $5}' | sort -u || true)
  for p in $PIDS; do
    if [ -n "$p" ] && [ "$p" != "0" ]; then
      taskkill //F //PID "$p" >/dev/null 2>&1 || true
    fi
  done
  $PG -c "DROP DATABASE IF EXISTS $DB;" >/dev/null 2>&1 || true
}
trap cleanup EXIT
HEALTHY=0
for i in $(seq 1 50); do
  if curl -sf http://localhost:18080/api/system/health >/dev/null; then
    HEALTHY=1
    break
  fi
  sleep 3
done
if [ "$HEALTHY" -ne 1 ]; then
  echo "[FAIL] Spring Boot failed to start within timeout"
  cat /tmp/drill-spring-boot.log
  exit 1
fi
$PG -d $DB -c "select version from flyway_schema_history order by installed_rank;"
COLS=$($PG -d $DB -tAc "select count(*) from information_schema.columns where table_name='agent_app' and column_name='public_enabled';")
[ "$COLS" = "1" ] || { echo "[FAIL] public_enabled 未迁移"; exit 1; }
TZOK=$($PG -d $DB -tAc "select count(*) from information_schema.columns where table_name='api_key' and column_name='expired_at' and data_type='timestamp with time zone';")
[ "$TZOK" = "0" ] || { echo "[FAIL] api_key.expired_at 仍为 timestamptz（V5 未生效）"; exit 1; }
echo "[OK] upgrade drill passed"
