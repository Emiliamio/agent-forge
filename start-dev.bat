@echo off
setlocal
cd /d "%~dp0"
echo ==============================================================================
echo [AgentForge] Starting Infrastructure (PostgreSQL 16 + Redis 7)...
echo ==============================================================================
docker compose up -d postgres redis
echo.
echo ==============================================================================
echo [AgentForge] Launching Backend (Java 21 / Spring Boot 3.2)...
echo ==============================================================================
start "AgentForge-Backend" cmd /k "cd /d "%~dp0backend" && mvn spring-boot:run"
echo.
echo ==============================================================================
echo [AgentForge] Launching Frontend (Vue 3.4 / Vite 5)...
echo ==============================================================================
start "AgentForge-Frontend" cmd /k "cd /d "%~dp0frontend" && npm run dev"
echo.
echo ==============================================================================
echo [AgentForge] All services have been launched!
echo - Frontend Studio: http://localhost:3000
echo - Copilot Portal:  http://localhost:3000/copilot
echo - Backend Swagger: http://localhost:8080/api/swagger-ui.html
echo ==============================================================================
pause