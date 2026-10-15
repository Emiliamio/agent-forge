@echo off
chcp 65001 >nul
echo ==============================================================================
echo 🚀 AgentForge (灵眸智枢) —— 工业级 AI Agent 智能体与混合 RAG 中台
echo 一键启动服务控制台 (Windows Server / 本地环境)
echo ==============================================================================

echo.
echo [1/3] 正在检查底层基础存储容器 (PostgreSQL 16 pgvector + Redis 7)...
docker-compose ps | findstr "Up" >nul
if %errorlevel% neq 0 (
    echo [*] 正在拉起 Docker 存储服务...
    docker-compose up -d postgres redis
) else (
    echo [✓] 存储层服务已在后台运行中。
)

echo.
echo [2/3] 正在启动 Spring Boot 3.2 (Java 21 LTS 虚拟线程) 后端中台...
start "AgentForge-Backend" cmd /k "cd backend && mvn spring-boot:run"

echo.
echo [3/3] 正在启动 Vue 3.4 + Vite 5 前端工作台...
start "AgentForge-Frontend" cmd /k "cd frontend && npm run dev"

echo.
echo ==============================================================================
echo 🎉 AgentForge 全栈启动指令已下发！
echo • 前端工作台入口: http://localhost:5173
echo • 员工 Copilot 门户: http://localhost:5173/copilot
echo • OpenAPI / Swagger: http://localhost:8080/doc.html
echo ==============================================================================
