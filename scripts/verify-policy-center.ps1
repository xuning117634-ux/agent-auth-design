param(
    [string]$BaseUrl = "http://localhost:18080"
)

$ErrorActionPreference = "Stop"

$runId = Get-Date -Format "yyyyMMddHHmmssfff"
$agentId = "verify-agent-$runId"
$userId = "verify-user-$runId"
$conversationId = "verify-conversation-$runId"
$tokenId = "$agentId`:$userId`:$conversationId"
$noAuthToolId = "verify-no-auth-$runId"
$userAuthToolId = "verify-user-auth-$runId"
$unboundToolId = "verify-unbound-$runId"

$script:passCount = 0
$script:failCount = 0

function Write-Pass {
    param([string]$Message)

    $script:passCount++
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Message)

    $script:failCount++
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Assert-Equal {
    param(
        [string]$Name,
        $Actual,
        $Expected
    )

    if ($Actual -eq $Expected) {
        Write-Pass "$Name = $Expected"
        return
    }

    throw "$Name expected '$Expected', actual '$Actual'"
}

function Write-RequestDetails {
    param(
        [string]$Method,
        [string]$Uri,
        [string]$TraceId,
        [string]$BodyText
    )

    Write-Host ""
    Write-Host "[REQUEST] $Method $Uri" -ForegroundColor Cyan
    Write-Host "X-Trace-Id: $TraceId" -ForegroundColor DarkGray
    if ([string]::IsNullOrWhiteSpace($BodyText)) {
        Write-Host "Body: <empty>" -ForegroundColor DarkGray
    }
    else {
        Write-Host "Body:"
        Write-Host $BodyText
    }
}

function Write-ResponseDetails {
    param(
        [int]$StatusCode,
        [string]$ContentText
    )

    Write-Host "[RESPONSE] HTTP $StatusCode" -ForegroundColor Cyan
    if ([string]::IsNullOrWhiteSpace($ContentText)) {
        Write-Host "Body: <empty>" -ForegroundColor DarkGray
        return
    }

    try {
        $formatted = $ContentText | ConvertFrom-Json | ConvertTo-Json -Depth 10
        Write-Host $formatted
    }
    catch {
        Write-Host $ContentText
    }
}

function Invoke-PolicyApi {
    param(
        [ValidateSet("GET", "POST", "PUT")]
        [string]$Method,
        [string]$Path,
        $Body,
        [int]$ExpectedStatus = 200
    )

    $uri = "$($BaseUrl.TrimEnd('/'))$Path"
    $traceId = "verify-$runId"
    $parameters = @{
        Uri         = $uri
        Method      = $Method
        Headers     = @{ "X-Trace-Id" = $traceId }
        ErrorAction = "Stop"
        UseBasicParsing = $true
    }

    $requestBodyText = $null
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $requestBodyText = $Body | ConvertTo-Json -Depth 10
        $parameters.Body = $requestBodyText
    }

    Write-RequestDetails -Method $Method -Uri $uri -TraceId $traceId -BodyText $requestBodyText

    try {
        $response = Invoke-WebRequest @parameters
        $statusCode = [int]$response.StatusCode
        $content = $response.Content
    }
    catch {
        if ($null -eq $_.Exception.Response) {
            Write-Host "[RESPONSE] TRANSPORT ERROR" -ForegroundColor Red
            Write-Host $_.Exception.Message
            throw
        }

        $response = $_.Exception.Response
        $statusCode = [int]$response.StatusCode
        $stream = $response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        try {
            $content = $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
            $stream.Dispose()
        }
    }

    $contentText = if ($content -is [byte[]]) {
        [System.Text.Encoding]::UTF8.GetString($content)
    }
    else {
        [string]$content
    }

    Write-ResponseDetails -StatusCode $statusCode -ContentText $contentText

    if ($statusCode -ne $ExpectedStatus) {
        throw "$Method $Path expected HTTP $ExpectedStatus, actual $statusCode. Body: $contentText"
    }

    if ([string]::IsNullOrWhiteSpace($contentText)) {
        return $null
    }

    return $contentText | ConvertFrom-Json
}

function Invoke-Cleanup {
    try {
        Invoke-PolicyApi -Method POST `
            -Path "/internal/conversation-authorizations/cleanup" `
            -Body @{ tokenId = $tokenId } | Out-Null
        Write-Host "[CLEANUP] Conversation authorization cleared." -ForegroundColor DarkGray
    }
    catch {
        Write-Host "[CLEANUP-WARN] Failed to clear conversation authorization: $($_.Exception.Message)" -ForegroundColor Yellow
    }

    try {
        Invoke-PolicyApi -Method PUT `
            -Path "/admin/agents/$agentId/tool-policies" `
            -Body @{ tools = @() } | Out-Null
        Write-Host "[CLEANUP] Test tool policies removed." -ForegroundColor DarkGray
    }
    catch {
        Write-Host "[CLEANUP-WARN] Failed to remove test tool policies: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host "Policy Center manual verification" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl"
Write-Host "Test agent: $agentId"
Write-Host "Token ID: $tokenId"
Write-Host ""

try {
    $health = Invoke-PolicyApi -Method GET -Path "/actuator/health" -Body $null
    Assert-Equal "Health status" $health.status "UP"

    $unboundDecision = Invoke-PolicyApi -Method POST `
        -Path "/internal/authorization-decisions" `
        -Body @{ tokenId = $tokenId; toolId = $unboundToolId }
    Assert-Equal "Unbound decision" $unboundDecision.decision "DENY"
    Assert-Equal "Unbound reason" $unboundDecision.reason "TOOL_NOT_BOUND"

    $saveResponse = Invoke-PolicyApi -Method PUT `
        -Path "/admin/agents/$agentId/tool-policies" `
        -Body @{
            tools = @(
                @{ toolId = $noAuthToolId; authMode = "NO_AUTH_REQUIRED" },
                @{ toolId = $userAuthToolId; authMode = "USER_AUTH_REQUIRED" }
            )
        }
    Assert-Equal "Saved tool count" $saveResponse.toolCount 2

    $policies = Invoke-PolicyApi -Method GET `
        -Path "/admin/agents/$agentId/tool-policies" `
        -Body $null
    Assert-Equal "Queried tool count" $policies.tools.Count 2
    Assert-Equal "No-auth policy mode" `
        ($policies.tools | Where-Object toolId -eq $noAuthToolId).authMode `
        "NO_AUTH_REQUIRED"
    Assert-Equal "User-auth policy mode" `
        ($policies.tools | Where-Object toolId -eq $userAuthToolId).authMode `
        "USER_AUTH_REQUIRED"

    $noAuthDecision = Invoke-PolicyApi -Method POST `
        -Path "/internal/authorization-decisions" `
        -Body @{ tokenId = $tokenId; toolId = $noAuthToolId }
    Assert-Equal "No-auth decision" $noAuthDecision.decision "ALLOW"
    Assert-Equal "No-auth reason" $noAuthDecision.reason "NO_AUTH_REQUIRED"

    $requiredDecision = Invoke-PolicyApi -Method POST `
        -Path "/internal/authorization-decisions" `
        -Body @{ tokenId = $tokenId; toolId = $userAuthToolId }
    Assert-Equal "User-auth initial decision" $requiredDecision.decision "AUTHORIZATION_REQUIRED"
    Assert-Equal "User-auth initial reason" $requiredDecision.reason "USER_AUTHORIZATION_REQUIRED"

    $initialStatus = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations/status" `
        -Body @{ tokenId = $tokenId; toolId = $userAuthToolId }
    Assert-Equal "Initial authorization status" $initialStatus.status "NOT_AUTHORIZED"

    $authorization = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations" `
        -Body @{ tokenId = $tokenId; toolId = $userAuthToolId }
    Assert-Equal "Authorization confirmation" $authorization.status "AUTHORIZED"

    $authorizedStatus = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations/status" `
        -Body @{ tokenId = $tokenId; toolId = $userAuthToolId }
    Assert-Equal "Authorized status" $authorizedStatus.status "AUTHORIZED"

    $authorizedDecision = Invoke-PolicyApi -Method POST `
        -Path "/internal/authorization-decisions" `
        -Body @{ tokenId = $tokenId; toolId = $userAuthToolId }
    Assert-Equal "Authorized decision" $authorizedDecision.decision "ALLOW"
    Assert-Equal "Authorized reason" $authorizedDecision.reason "CONVERSATION_AUTHORIZED"

    $invalidTokenDecision = Invoke-PolicyApi -Method POST `
        -Path "/internal/authorization-decisions" `
        -Body @{ tokenId = "$agentId`:$userId"; toolId = $userAuthToolId }
    Assert-Equal "Invalid token decision" $invalidTokenDecision.decision "DENY"
    Assert-Equal "Invalid token reason" $invalidTokenDecision.reason "INVALID_TOKEN_ID"

    $duplicateError = Invoke-PolicyApi -Method PUT `
        -Path "/admin/agents/$agentId/tool-policies" `
        -ExpectedStatus 400 `
        -Body @{
            tools = @(
                @{ toolId = $noAuthToolId; authMode = "NO_AUTH_REQUIRED" },
                @{ toolId = $noAuthToolId; authMode = "USER_AUTH_REQUIRED" }
            )
        }
    Assert-Equal "Duplicate tool error" $duplicateError.code "INVALID_REQUEST"

    $notRequiredError = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations" `
        -ExpectedStatus 409 `
        -Body @{ tokenId = $tokenId; toolId = $noAuthToolId }
    Assert-Equal "No-auth confirmation error" $notRequiredError.code "AUTHORIZATION_NOT_REQUIRED"

    $unboundError = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations" `
        -ExpectedStatus 409 `
        -Body @{ tokenId = $tokenId; toolId = $unboundToolId }
    Assert-Equal "Unbound confirmation error" $unboundError.code "TOOL_NOT_BOUND"

    $cleanup = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations/cleanup" `
        -Body @{ tokenId = $tokenId }
    Assert-Equal "Cleanup status" $cleanup.status "CLEARED"

    $statusAfterCleanup = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations/status" `
        -Body @{ tokenId = $tokenId; toolId = $userAuthToolId }
    Assert-Equal "Status after cleanup" $statusAfterCleanup.status "NOT_AUTHORIZED"
}
catch {
    Write-Fail $_.Exception.Message
}
finally {
    Invoke-Cleanup
}

Write-Host ""
Write-Host "Verification result: $($script:passCount) passed, $($script:failCount) failed."

if ($script:failCount -gt 0) {
    exit 1
}

exit 0
