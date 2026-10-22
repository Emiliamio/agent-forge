#!/usr/bin/env bash
# ==============================================================================
# AgentForge 离线全栈镜像导出脚本 (Offline Image Packager)
# 用于将全栈 Docker 镜像导出打包至 dist/ 目录，支持离线或纯内网环境交付
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist"

REGISTRY="${AGENTFORGE_REGISTRY:-ghcr.io/emiliamio}"
TAG="${AGENTFORGE_TAG:-1.1.1}"

BACKEND_IMAGE="${REGISTRY}/agentforge-backend:${TAG}"
FRONTEND_IMAGE="${REGISTRY}/agentforge-frontend:${TAG}"
POSTGRES_IMAGE="pgvector/pgvector:pg16"
REDIS_IMAGE="redis:7-alpine"

mkdir -p "${DIST_DIR}"

echo "======================================================================"
echo "  AgentForge 离线全栈镜像打包导出"
echo "  Registry : ${REGISTRY}"
echo "  Tag      : ${TAG}"
echo "  Target   : ${DIST_DIR}"
echo "======================================================================"

# 辅助函数：如果指定 tag 的镜像不存在，尝试使用本地已有构建镜像 tag 回调
resolve_and_tag() {
    local target="$1"
    local local_fallback="$2"
    if ! docker image inspect "${target}" >/dev/null 2>&1; then
        if docker image inspect "${local_fallback}" >/dev/null 2>&1; then
            echo "[INFO] 将本地构建镜像 ${local_fallback} 标记为 ${target}..."
            docker tag "${local_fallback}" "${target}"
        else
            echo "[INFO] 未发现镜像，尝试拉取 ${target}..."
            docker pull "${target}" 2>/dev/null || true
        fi
    fi
}

resolve_and_tag "${BACKEND_IMAGE}" "agent-forge-backend:latest"
resolve_and_tag "${FRONTEND_IMAGE}" "agent-forge-frontend:latest"
docker pull "${POSTGRES_IMAGE}" 2>/dev/null || true
docker pull "${REDIS_IMAGE}" 2>/dev/null || true

echo "--> [1/4] 导出 PostgreSQL (pgvector:pg16) 镜像..."
docker save -o "${DIST_DIR}/postgres-pgvector-pg16.tar" "${POSTGRES_IMAGE}"

echo "--> [2/4] 导出 Redis (7-alpine) 镜像..."
docker save -o "${DIST_DIR}/redis-7-alpine.tar" "${REDIS_IMAGE}"

echo "--> [3/4] 导出 AgentForge Backend (${TAG}) 镜像..."
docker save -o "${DIST_DIR}/agentforge-backend-${TAG}.tar" "${BACKEND_IMAGE}"

echo "--> [4/4] 导出 AgentForge Frontend (${TAG}) 镜像..."
docker save -o "${DIST_DIR}/agentforge-frontend-${TAG}.tar" "${FRONTEND_IMAGE}"

echo "--> 生成离线交付指引 dist/README-offline.txt..."
cat << 'EOF' > "${DIST_DIR}/README-offline.txt"
======================================================================
  AgentForge 离线/内网部署交付指南 (Offline Deployment Guide)
======================================================================

1. 载入离线 Docker 镜像包：
   docker load -i postgres-pgvector-pg16.tar
   docker load -i redis-7-alpine.tar
   docker load -i agentforge-backend-*.tar
   docker load -i agentforge-frontend-*.tar

2. 校验镜像完整性：
   sha256sum -c SHA256SUMS

3. 启动全栈服务（离线无需网络连接）：
   docker compose --profile full up -d --wait

4. 服务健康与冒烟自检：
   curl -sf http://localhost:8080/api/system/health
   curl -sf http://localhost:3000/
   bash scripts/smoke.sh
======================================================================
EOF

echo "--> 计算 SHA256SUMS 校验和..."
(
    cd "${DIST_DIR}"
    sha256sum *.tar README-offline.txt > SHA256SUMS
)

echo "======================================================================"
echo "[OK] 离线镜像导出完成！产物列表位于 ${DIST_DIR}:"
ls -lh "${DIST_DIR}"
echo "======================================================================"
