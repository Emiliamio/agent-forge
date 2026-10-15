#!/usr/bin/env bash
# ==============================================================================
# 🚀 AgentForge (灵眸智枢) 一键启动脚本 (Linux / 信创 Kylin & UOS 环境)
# ==============================================================================
set -e

echo "=============================================================================="
echo "🚀 启动 AgentForge 企业级 AI 智能体编排与混合 RAG 中台 (Linux/信创交付版)"
echo "=============================================================================="

# 1. 检查并拉起底层 Docker 存储
echo "[1/3] 检查基础存储依赖 (PostgreSQL 16 pgvector + Redis 7)..."
if command -v docker-compose &> /dev/null; then
    docker-compose up -d postgres redis
elif command -v docker &> /dev/null; then
    docker compose up -d postgres redis
else
    echo "⚠️ 未检测到 Docker 命令，请确保宿主机已安装并启动 PostgreSQL 16 与 Redis 7！"
fi

# 2. 后端构建并启动
echo "[2/3] 启动 Spring Boot 3.2 (Java 21) 后端服务..."
nohup mvn -f backend/pom.xml spring-boot:run > backend.log 2>&1 &
echo "[✓] 后端已在后台启动，日志记录于 backend.log (PID: $!)"

# 3. 前端编译与预览启动
echo "[3/3] 启动 Vue 3.4 前端服务..."
cd frontend
if [ ! -d "node_modules" ]; then
    echo "[*] 正在安装前端依赖..."
    npm install
fi
nohup npm run dev -- --host 0.0.0.0 > ../frontend.log 2>&1 &
echo "[✓] 前端服务已在后台启动，日志记录于 frontend.log (PID: $!)"

echo "=============================================================================="
echo "🎉 AgentForge 全链路启动完成！"
echo "• 前端工作台: http://localhost:5173"
echo "• 员工 Copilot 门户: http://localhost:5173/copilot"
echo "• 后端 API 接口文档: http://localhost:8080/doc.html"
echo "=============================================================================="
