@echo off
setlocal
cd /d "%~dp0"

echo ==============================================================================
echo [AgentForge] Shutting down all services safely...
echo ==============================================================================

echo [1/3] Stopping Docker containers (PostgreSQL + Redis)...
docker compose down

echo.
echo [2/3] Releasing Backend port (8080) if lingering...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)

echo.
echo [3/3] Releasing Frontend port (3000) if lingering...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3000" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)

echo.
echo ==============================================================================
echo [AgentForge] All services have been stopped and ports are released!
echo ==============================================================================
pause