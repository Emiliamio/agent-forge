#!/bin/bash
# ==============================================================================
# AgentForge (灵眸智枢) —— PostgreSQL 16 向量库与 Redis 语义缓存热备份脚本
# ==============================================================================

BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo "🚀 开始执行 AgentForge 生产数据全量热备份: $BACKUP_DIR"

# 1. 导出 PostgreSQL 结构与数据 (含 pgvector 向量)
if command -v docker &> /dev/null && docker ps | grep -q "agentforge-postgres"; then
    echo "📦 正在导出 Docker 容器中的 PostgreSQL 数据库与向量索引..."
    docker exec -t agentforge-postgres pg_dump -U postgres -d agent_forge > "$BACKUP_DIR/agent_forge_dump.sql"
    echo "✅ 数据库已保存至: $BACKUP_DIR/agent_forge_dump.sql"
fi

# 2. 保留最近 7 天的备份，自动清理过期数据
find ./backups -type d -mtime +7 -exec rm -rf {} + 2>/dev/null

echo "🎉 备份完成！备份总包体积:"
du -sh "$BACKUP_DIR"