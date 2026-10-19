# AgentForge Native End-to-End Live Protocol Verification
# Zero Testcontainers Dependency - Runs against live local databases and backend

param(
    [string]$BaseUrl = "http://localhost:8080/api"
)

$ErrorActionPreference = "Continue"

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host " AgentForge Native E2E Live Protocol Verification (Zero Testcontainers)" -ForegroundColor Cyan
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

$pgStatus = if ($pgUp) { "UP (Ready)" } else { "DOWN (Not Detected)" }
$redisStatus = if ($redisUp) { "UP (Ready)" } else { "DOWN (Not Detected)" }
$pgColor = if ($pgUp) { "Green" } else { "Red" }
$redisColor = if ($redisUp) { "Green" } else { "Red" }

Write-Host "[1/3] Infrastructure Probe:" -ForegroundColor Yellow
Write-Host "  - PostgreSQL (5432): $pgStatus" -ForegroundColor $pgColor
Write-Host "  - Redis (6379):      $redisStatus" -ForegroundColor $redisColor

Write-Host "`n[2/3] Backend Service Health Check:" -ForegroundColor Yellow
$backendUp = $false
try {
    $healthResp = Invoke-RestMethod -Uri "$BaseUrl/system/health" -Method Get -TimeoutSec 3 -ErrorAction Stop
    if ($healthResp.code -eq 200 -or $healthResp.data -eq "UP") {
        $backendUp = $true
        Write-Host "  - Backend Service: UP ($BaseUrl/system/health)" -ForegroundColor Green
    }
} catch {
    Write-Host "  - Backend Service: Standby / Not Listening ($BaseUrl)" -ForegroundColor Yellow
    Write-Host "    Hint: Start backend in another terminal: cd backend; mvn spring-boot:run" -ForegroundColor DarkGray
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
    Write-Host "  $($stepName.PadRight(42)) [$(if ($passed) { 'PASS' } else { 'FAIL' })] - $detail" -ForegroundColor $color
}

if ($backendUp) {
    # 1. System Health Probe
    try {
        $r1 = Invoke-RestMethod -Uri "$BaseUrl/system/health" -Method Get
        Record-Result "1. Health Probe GET /system/health" ($r1.code -eq 200) "HTTP 200, status=UP"
    } catch {
        Record-Result "1. Health Probe GET /system/health" $false $_.Exception.Message
    }

    # 2. Anonymous Access Blocked
    try {
        $r2 = Invoke-RestMethod -Uri "$BaseUrl/agents" -Method Get -ErrorAction Stop
        Record-Result "2. Anonymous Access Blocked /agents" $false "Expected 401 but succeeded"
    } catch {
        $msg = $_.Exception.Message
        $is401 = $msg -match "401" -or $msg -match "Unauthorized"
        Record-Result "2. Anonymous Access Blocked /agents" $is401 "Correctly blocked by Fail-Closed 401"
    }

    # 3. Diagnostics Security Check
    try {
        $r3 = Invoke-RestMethod -Uri "$BaseUrl/system/diagnostics" -Method Get -ErrorAction Stop
        Record-Result "3. Diagnostics Protected /diagnostics" $false "Diagnostics must require authentication"
    } catch {
        $msg = $_.Exception.Message
        $is401 = $msg -match "401" -or $msg -match "Unauthorized"
        Record-Result "3. Diagnostics Protected /diagnostics" $is401 "Correctly requires login (401)"
    }

    # 4. Admin Database Login
    $token = $null
    try {
        $loginBody = '{"tenantCode":"tenant_default","username":"admin","password":"admin123456"}'
        $loginResp = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
        if ($loginResp.code -eq 200 -and $loginResp.data.token) {
            $token = $loginResp.data.token
            Record-Result "4. Real DB Login POST /auth/login" $true "Token: $($token.Substring(0, 16))..."
        } else {
            Record-Result "4. Real DB Login POST /auth/login" $false $loginResp.message
        }
    } catch {
        Record-Result "4. Real DB Login POST /auth/login" $false $_.Exception.Message
    }

    # 5. Authenticated Access
    if ($token) {
        $authHeaders = @{ Authorization = "Bearer $token" }
        try {
            $agentsResp = Invoke-RestMethod -Uri "$BaseUrl/agents" -Method Get -Headers $authHeaders
            Record-Result "5. Authenticated Access GET /agents" ($agentsResp.code -eq 200) "Successfully retrieved agent list"
        } catch {
            Record-Result "5. Authenticated Access GET /agents" $false $_.Exception.Message
        }

        # 6. Cross-Tenant Tampering Blocked
        try {
            $tamperedHeaders = @{
                Authorization = "Bearer $token"
                "X-Tenant-Id" = "999"
            }
            $tamperResp = Invoke-RestMethod -Uri "$BaseUrl/billing/stats" -Method Get -Headers $tamperedHeaders -ErrorAction Stop
            Record-Result "6. Cross-Tenant Tampering Blocked" $false "Expected 403 Forbidden"
        } catch {
            $msg = $_.Exception.Message
            $is403 = $msg -match "403" -or $msg -match "Forbidden"
            Record-Result "6. Cross-Tenant Tampering Blocked" $is403 "Correctly triggered 403 Cross-Tenant Isolation"
        }

        # 7. Public Widget Chat
        try {
            $widgetBody = '{"message":"Hello, test widget chat"}'
            $widgetResp = Invoke-RestMethod -Uri "$BaseUrl/public/agents/1/chat" -Method Post -Body $widgetBody -ContentType "application/json"
            Record-Result "7. Public Widget Chat /public/agents/1/chat" ($widgetResp.code -eq 200) "Unauthenticated widget chat works"
        } catch {
            Record-Result "7. Public Widget Chat /public/agents/1/chat" $false $_.Exception.Message
        }

        # 8. Natural Language RAG Search
        try {
            $searchBody = '{"datasetIds":[1],"query":"北京出差住宿报销标准是多少","topK":5,"minScore":0.0}'
            $searchResp = Invoke-RestMethod -Uri "$BaseUrl/rag/search" -Method Post -Headers $authHeaders -Body $searchBody -ContentType "application/json"
            $hasResults = ($searchResp.code -eq 200)
            Record-Result "8. Natural Language Search /rag/search" $hasResults "Returned $($searchResp.data.Count) citations"
        } catch {
            Record-Result "8. Natural Language Search /rag/search" $false $_.Exception.Message
        }
    }
}

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host " E2E Verification Script Ready. Execute anytime against running backend." -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
