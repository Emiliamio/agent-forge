#!/usr/bin/env bash
set -euo pipefail
export MSYS_NO_PATHCONV=1
PG="docker exec agentforge-postgres psql -U postgres"
$PG -c "DROP DATABASE IF EXISTS agentforge_it;"
$PG -c "CREATE DATABASE agentforge_it;"
(cd backend && mvn -q -B flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/agentforge_it \
  -Dflyway.user=postgres \
  -Dflyway.password=password123)
docker cp backend/src/main/resources/sql/seed-data.sql agentforge-postgres:/tmp/02.sql
$PG -d agentforge_it -f /tmp/02.sql
echo "[OK] agentforge_it ready (flyway + seed)"
