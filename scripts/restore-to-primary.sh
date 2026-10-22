#!/usr/bin/env bash
# ==============================================================================
# AgentForge 主库全量破坏性灾难恢复演练脚本 (Destructive Primary Restore Drill)
# ==============================================================================
set -euo pipefail

export MSYS_NO_PATHCONV=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ "${ALLOW_DESTRUCTIVE_RESTORE:-}" != "yes" ]; then
    echo "======================================================================" >&2
    echo " [ERROR] 危险操作拦截：恢复至主库将彻底销毁并重建 agentforge 数据库！" >&2
    echo " 如需执行破坏性灾难演练，请显式声明环境变量：" >&2
    echo "   ALLOW_DESTRUCTIVE_RESTORE=yes bash scripts/restore-to-primary.sh" >&2
    echo "======================================================================" >&2
    exit 1
fi

echo "======================================================================"
echo "  AgentForge 恢复回主库灾难演练 (Restore to Primary Drill)"
echo "======================================================================"

# 1. 演练前自动拍摄全量物理快照备份
echo "--> [1/5] 执行演练前基准备份..."
bash "${SCRIPT_DIR}/backup.sh"

# 获取最新备份归档文件
LATEST_DUMP=$(ls -t "${ROOT_DIR}"/backups/*.dump 2>/dev/null | head -n 1 || true)
if [ -z "${LATEST_DUMP}" ] || [ ! -f "${LATEST_DUMP}" ]; then
    echo "Error: 未找到有效备份 dump 文件" >&2
    exit 1
fi
echo "[INFO] 使用备份文件: ${LATEST_DUMP}"

# 2. 停掉应用层服务以释放数据库长连接
echo "--> [2/5] 暂停应用层容器以释放数据库连接..."
docker compose --profile full stop backend frontend 2>/dev/null || docker compose stop backend frontend 2>/dev/null || true

# 3. 强制断开连接、删除并重建主库 agentforge
echo "--> [3/5] 强制销毁并重建主库 agentforge..."
PG_CONTAINER="agentforge-postgres"
docker exec "${PG_CONTAINER}" psql -U postgres -c \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='agentforge' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
docker exec "${PG_CONTAINER}" psql -U postgres -c "DROP DATABASE IF EXISTS agentforge WITH (FORCE);" >/dev/null 2>&1 || \
    docker exec "${PG_CONTAINER}" psql -U postgres -c "DROP DATABASE IF EXISTS agentforge;"
docker exec "${PG_CONTAINER}" psql -U postgres -c "CREATE DATABASE agentforge;"

# 4. 从备份文件向主库执行 pg_restore
echo "--> [4/5] 执行 pg_restore 全量恢复回主库 agentforge..."
docker exec -i "${PG_CONTAINER}" pg_restore -U postgres -d agentforge --no-owner --role=postgres < "${LATEST_DUMP}" || true

# 5. 重新拉起应用并等待健康检查就绪，运行冒烟回归
echo "--> [5/5] 启动全栈服务并执行冒烟测试回归..."
docker compose --profile full up -d --wait

echo "--> 验证恢复后主库表数量..."
TABLE_COUNT=$(docker exec "${PG_CONTAINER}" psql -U postgres -d agentforge -tAc \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';")
echo "[INFO] 恢复后 public schema 表数量: ${TABLE_COUNT}"

echo "--> 运行 12 步自动化冒烟巡检..."
bash "${SCRIPT_DIR}/smoke.sh"

echo "======================================================================"
echo "[OK] primary restore passed"
echo "======================================================================"
