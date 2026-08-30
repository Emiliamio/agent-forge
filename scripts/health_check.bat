@echo off
chcp 65001 >nul
echo ==================================================================
echo [AgentForge] 正在启动 Windows 生产/测试环境全链路健康巡检...
echo ==================================================================

echo [1/3] 正在检查 Java 21 运行时...
java -version 2>&1
echo.

echo [2/3] 正在检查后端 Spring Boot 服务状态 (Port 8080)...
powershell -Command "try { $res = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/system/health' -TimeoutSec 3 -UseBasicParsing; Write-Host '后端服务健康 (HTTP ' $res.StatusCode ')' -ForegroundColor Green } catch { Write-Host '后端尚未启动或正在初始化...' -ForegroundColor Yellow }"
echo.

echo [3/3] 正在检查 Docker 容器运行情况...
docker ps
echo.

echo ==================================================================
echo 巡检完毕！若有异常，请检查 logs/ 目录或执行 docker-compose logs
echo ==================================================================
pause