#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1
PG="docker exec agentforge-postgres psql -U postgres"
MVN="mvn"
if command -v mvn.cmd >/dev/null 2>&1 && ! command -v mvn >/dev/null 2>&1; then
  MVN="mvn.cmd"
elif command -v mvn >/dev/null 2>&1; then
  # Git Bash 下官方 mvn 脚本可能找不到 MAVEN_HOME，退回到 mvn.cmd
  if ! mvn -v >/dev/null 2>&1 && command -v mvn.cmd >/dev/null 2>&1; then MVN="mvn.cmd"; fi
fi

$PG -c "DROP DATABASE IF EXISTS agentforge_it;"
$PG -c "CREATE DATABASE agentforge_it;"
(cd backend && "$MVN" -q -B flyway:migrate \
  "-Dflyway.url=jdbc:postgresql://localhost:5432/agentforge_it" \
  "-Dflyway.user=postgres" \
  "-Dflyway.password=password123")
docker cp backend/src/main/resources/sql/seed-data.sql agentforge-postgres:/tmp/02.sql
$PG -d agentforge_it -f /tmp/02.sql
echo "[OK] agentforge_it ready (flyway + seed)"
