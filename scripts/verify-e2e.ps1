# AgentForge Native End-to-End Live Protocol Verification
# Zero Testcontainers Dependency - Runs against live local databases and backend

param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$WidgetToken = ""
)

$ErrorActionPreference = "Continue"

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host " AgentForge Native E2E Live Protocol Verification (Zero Testcontainers)" -ForegroundColor Cyan
Write-Host " Target API Base: $BaseUrl" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

function Test-PortReachable([string]$hostName, [int]$port) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $asyncResult = $client.BeginConnect($hostName, $port, $null, $null)
        $wait = $asyncResult.AsyncWaitHandle.WaitOne(1000, $false)
        if ($wait) {
            $client.EndConnect($asyncResult)
            $client.Close()
            return $true
        }
        $client.Close()
        return $false
    } catch {
        return $false
    }
}

$pgUp = Test-PortReachable "localhost" 5432
$redisUp = Test-PortReachable "localhost" 6379

$pgStatus = if ($pgUp) { "UP (Ready)" } else { "DOWN (Not Detected on localhost:5432)" }
$redisStatus = if ($redisUp) { "UP (Ready)" } else { "DOWN (Not Detected on localhost:6379)" }
$pgColor = if ($pgUp) { "Green" } else { "Yellow" }
$redisColor = if ($redisUp) { "Green" } else { "Yellow" }

Write-Host "[1/3] Infrastructure Probe:" -ForegroundColor Yellow
Write-Host "  - PostgreSQL (5432): $pgStatus" -ForegroundColor $pgColor
Write-Host "  - Redis (6379):      $redisStatus" -ForegroundColor $redisColor

Write-Host "`n[2/3] Backend Service Health Check:" -ForegroundColor Yellow
$backendUp = $false
try {
    $healthResp = Invoke-RestMethod -Uri "$BaseUrl/system/health" -Method Get -TimeoutSec 3 -ErrorAction Stop
    if ($healthResp.code -eq 200) {
        $backendUp = $true
        Write-Host "  - Backend Service: UP ($BaseUrl/system/health)" -ForegroundColor Green
    }
} catch {
    Write-Host "  - Backend Service: Standby / Not Listening ($BaseUrl)" -ForegroundColor Red
    Write-Host "    Hint: Start backend in another terminal: cd backend; mvn spring-boot:run" -ForegroundColor DarkGray
    exit 1
}

Write-Host "`n[3/3] End-to-End Live Protocol Test Cases:" -ForegroundColor Yellow
Write-Host "--------------------------------------------------------------------------------" -ForegroundColor DarkGray

$testResults = @()
function Record-Result([string]$stepName, [bool]$passed, [string]$detail) {
    $script:testResults += [PSCustomObject]@{
        Step   = $stepName
        Status = if ($passed) { "PASS" } else { "FAIL" }
        Detail = $detail
    }
    $color = if ($passed) { "Green" } else { "Red" }
    Write-Host "  $($stepName.PadRight(44)) [$(if ($passed) { 'PASS' } else { 'FAIL' })] - $detail" -ForegroundColor $color
}

# 1. System Health Probe
try {
    $r1 = Invoke-RestMethod -Uri "$BaseUrl/system/health" -Method Get
    Record-Result "1. Health Probe GET /system/health" ($r1.code -eq 200) "HTTP 200, status=UP"
} catch {
    Record-Result "1. Health Probe GET /system/health" $false $_.Exception.Message
}

# 2. Anonymous Access Blocked (Fail-Closed 401)
$r2Blocked = $false
try {
    $r2 = Invoke-RestMethod -Uri "$BaseUrl/agents" -Method Get -ErrorAction Stop
    if ($r2.code -eq 401 -or $r2.code -eq 403) {
        $r2Blocked = $true
    }
} catch {
    $code = 0
    if ($_.Exception.Response) {
        $code = [int]$_.Exception.Response.StatusCode
    }
    if ($code -eq 401 -or $_.Exception.Message -match "401" -or $_.Exception.Message -match "Unauthorized") {
        $r2Blocked = $true
    }
}
Record-Result "2. Anonymous Access Blocked /agents" $r2Blocked "Correctly blocked with 401 Unauthorized"

# 3. Diagnostics Protected (Requires Login 401)
$r3Blocked = $false
try {
    $r3 = Invoke-RestMethod -Uri "$BaseUrl/system/diagnostics" -Method Get -ErrorAction Stop
    if ($r3.code -eq 401 -or $r3.code -eq 403) {
        $r3Blocked = $true
    }
} catch {
    $code = 0
    if ($_.Exception.Response) {
        $code = [int]$_.Exception.Response.StatusCode
    }
    if ($code -eq 401 -or $_.Exception.Message -match "401" -or $_.Exception.Message -match "Unauthorized") {
        $r3Blocked = $true
    }
}
Record-Result "3. Diagnostics Protected /diagnostics" $r3Blocked "Correctly requires login (401)"

# 4. Admin Database Login
$token = $null
try {
    $loginBody = '{"tenantCode":"tenant_default","username":"admin","password":"admin123456"}'
    $loginResp = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json; charset=utf-8"
    if ($loginResp.code -eq 200 -and $loginResp.data.token) {
        $token = $loginResp.data.token
        Record-Result "4. Real DB Login POST /auth/login" $true "Token: $($token.Substring(0, 16))..."
    } else {
        Record-Result "4. Real DB Login POST /auth/login" $false $loginResp.message
    }
} catch {
    Record-Result "4. Real DB Login POST /auth/login" $false $_.Exception.Message
}

if ($token) {
    $authHeaders = @{ Authorization = "Bearer $token" }

    # 5. Authenticated Access
    try {
        $agentsResp = Invoke-RestMethod -Uri "$BaseUrl/agents" -Method Get -Headers $authHeaders
        Record-Result "5. Authenticated Access GET /agents" ($agentsResp.code -eq 200) "Successfully retrieved agent list"
    } catch {
        Record-Result "5. Authenticated Access GET /agents" $false $_.Exception.Message
    }

    # 6. Cross-Tenant Tampering Blocked (403 Forbidden)
    $tamperBlocked = $false
    try {
        $tamperedHeaders = @{
            Authorization = "Bearer $token"
            "X-Tenant-Id" = "999"
        }
        $tamperResp = Invoke-RestMethod -Uri "$BaseUrl/billing/stats" -Method Get -Headers $tamperedHeaders -ErrorAction Stop
        if ($tamperResp.code -eq 403) {
            $tamperBlocked = $true
        }
    } catch {
        $code = 0
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -eq 403 -or $_.Exception.Message -match "403" -or $_.Exception.Message -match "Forbidden") {
            $tamperBlocked = $true
        }
    }
    Record-Result "6. Cross-Tenant Tampering Blocked" $tamperBlocked "Correctly triggered 403 Cross-Tenant Isolation"

    # 7. Public Widget Chat (Multi-tenant lookup & Unauthenticated Execution)
    try {
        $widgetBody = '{"message":"Hello, test widget chat"}'
        $widgetHeaders = @{}
        if ($WidgetToken) {
            $widgetHeaders["X-Public-Token"] = $WidgetToken
        }
        $widgetResp = Invoke-RestMethod -Uri "$BaseUrl/public/agents/1/chat" -Method Post -Headers $widgetHeaders -Body $widgetBody -ContentType "application/json; charset=utf-8"
        Record-Result "7. Public Widget Chat /public/agents/1/chat" ($widgetResp.code -eq 200) "Public widget chat works"
    } catch {
        Record-Result "7. Public Widget Chat /public/agents/1/chat" $false $_.Exception.Message
    }

    # 8. Natural Language RAG Search
    try {
        $searchBody = '{"datasetIds":[1],"query":"\u5317\u4eac\u51fa\u5dee\u4f4f\u5bbf\u62a5\u9500\u6807\u51c6\u662f\u591a\u5c11","topK":5,"minScore":0.0}'
        $searchResp = Invoke-RestMethod -Uri "$BaseUrl/rag/search" -Method Post -Headers $authHeaders -Body $searchBody -ContentType "application/json; charset=utf-8"
        $citationCount = if ($searchResp.data) { $searchResp.data.Count } else { 0 }
        $passed = ($searchResp.code -eq 200 -and $citationCount -gt 0)
        Record-Result "8. Natural Language Search /rag/search" $passed "Returned $citationCount citations"
    } catch {
        Record-Result "8. Natural Language Search /rag/search" $false $_.Exception.Message
    }
}

Write-Host "================================================================================" -ForegroundColor Cyan
$failedCount = @($script:testResults | Where-Object { $_.Status -eq "FAIL" }).Count
if ($failedCount -gt 0) {
    Write-Host " E2E Verification Result: FAILED ($failedCount test(s) failed)" -ForegroundColor Red
    Write-Host "================================================================================" -ForegroundColor Cyan
    exit 1
} else {
    Write-Host " E2E Verification Result: ALL 8 TESTS PASSED (100% GREEN)" -ForegroundColor Green
    Write-Host "================================================================================" -ForegroundColor Cyan
    exit 0
}
