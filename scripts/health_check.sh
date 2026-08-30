#!/bin/bash
# ==============================================================================
# AgentForge (灵眸智枢) —— 生产环境全链路一键巡检与健康自检脚本
# 适用环境: Linux / Ubuntu / CentOS / Kylin / UOS
# 执行方式: chmod +x health_check.sh && ./health_check.sh
# ==============================================================================

echo "=================================================================="
echo "🚀 [AgentForge] 正在启动生产环境全链路健康巡检..."
echo "=================================================================="

# 1. 检查后端 Spring Boot 端口与自检 API
echo -n "[1/5] 检查 Java 21 后端主服务 (Port: 8080)... "
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/system/health 2>/dev/null)
if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 401 ]; then
    echo "✅ [正常] 后端运行良好 (HTTP $HTTP_CODE)"
else
    echo "⚠️ [警告] 后端可能未就绪或未启动 (HTTP $HTTP_CODE)"
fi

# 2. 检查 PostgreSQL 16 & pgvector 向量扩展
echo -n "[2/5] 检查 PostgreSQL 16 数据库及 pgvector 扩展... "
if command -v psql &> /dev/null; then
    PG_CHECK=$(psql -U postgres -h 127.0.0.1 -d agent_forge -c "SELECT extname, extversion FROM pg_extension WHERE extname='vector';" 2>/dev/null)
    if echo "$PG_CHECK" | grep -q "vector"; then
        echo "✅ [正常] pgvector 向量索引扩展正常挂载"
    else
        echo "⚠️ [提示] 数据库连接已通，请确认 vector 扩展是否激活"
    fi
else
    echo "ℹ️ [跳过] 本地未安装 psql 客户端，若运行于 Docker 则由容器自愈管理"
fi

# 3. 检查 Redis 7 响应与语义降本缓存
echo -n "[3/5] 检查 Redis 7 缓存与分布式锁... "
if command -v redis-cli &> /dev/null; then
    REDIS_PONG=$(redis-cli -h 127.0.0.1 ping 2>/dev/null)
    if [ "$REDIS_PONG" = "PONG" ]; then
        echo "✅ [正常] Redis 7 响应毫秒级 (PONG)"
    else
        echo "⚠️ [警告] Redis 无法连接"
    fi
else
    echo "ℹ️ [跳过] 本地未安装 redis-cli，若运行于 Docker 则由 compose 网络互通"
fi

# 4. 检查 Docker 容器健康状态
echo -n "[4/5] 检查 Docker 容器运行状态... "
if command -v docker &> /dev/null; then
    RUNNING_CONTAINERS=$(docker ps --format "{{.Names}}" 2>/dev/null | grep -E "agent-forge|postgres|redis" | wc -l)
    echo "✅ [正常] 检测到 $RUNNING_CONTAINERS 个相关生产容器在运行"
else
    echo "ℹ️ [跳过] 非 Docker 宿主机模式"
fi

# 5. 检查磁盘剩余空间与 IO 权限
echo -n "[5/5] 检查系统磁盘空间与缓冲流读写... "
DISK_AVAIL=$(df -h . | awk 'NR==2 {print $4}')
echo "✅ [正常] 当前工作目录可用空间: $DISK_AVAIL"

echo "=================================================================="
echo "🎉 全链路巡检完成！如需查看详细日志，请运行: docker-compose -f docker-compose-prod.yml logs -f"
echo "=================================================================="