param(
    [string]$BaseUrl = "http://localhost:18080",
    [string]$AgentId = "finance-agent-local",
    [string]$ServerId = "finance-server",
    [string]$ToolName = "quoteQuery",
    [string]$ToolId = "finance.quote.query",
    [string]$ExpectedServerName = ""
)

$ErrorActionPreference = "Stop"

$runId = Get-Date -Format "yyyyMMddHHmmssfff"
$userId = "verify-finance-user-$runId"
$conversationId = "verify-finance-conversation-$runId"
$tokenId = "$AgentId`:$userId`:$conversationId"

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

function Assert-True {
    param(
        [string]$Name,
        [bool]$Condition
    )

    if ($Condition) {
        Write-Pass $Name
        return
    }

    throw "$Name expected true"
}

function Write-RequestDetails {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers,
        [string]$BodyText
    )

    Write-Host ""
    Write-Host "[REQUEST] $Method $Uri" -ForegroundColor Cyan
    foreach ($header in $Headers.GetEnumerator() | Sort-Object Name) {
        Write-Host "$($header.Name): $($header.Value)" -ForegroundColor DarkGray
    }

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
        $formatted = $ContentText | ConvertFrom-Json | ConvertTo-Json -Depth 20
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
        [int]$ExpectedStatus = 200,
        [hashtable]$ExtraHeaders = @{}
    )

    $uri = "$($BaseUrl.TrimEnd('/'))$Path"
    $headers = @{
        "X-Trace-Id" = "verify-finance-$runId"
    }
    foreach ($header in $ExtraHeaders.GetEnumerator()) {
        $headers[$header.Name] = $header.Value
    }

    $parameters = @{
        Uri             = $uri
        Method          = $Method
        Headers         = $headers
        ErrorAction     = "Stop"
        UseBasicParsing = $true
    }

    $requestBodyText = $null
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $requestBodyText = $Body | ConvertTo-Json -Depth 20
        $parameters.Body = $requestBodyText
    }

    Write-RequestDetails -Method $Method -Uri $uri -Headers $headers -BodyText $requestBodyText

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
            -Path "/admin/agents/$AgentId/tool-policies" `
            -Body @{ tools = @() } | Out-Null
        Write-Host "[CLEANUP] Test tool policy removed." -ForegroundColor DarkGray
    }
    catch {
        Write-Host "[CLEANUP-WARN] Failed to remove test tool policy: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host "Finance Agent authorization API verification" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl"
Write-Host "Agent ID: $AgentId"
Write-Host "Token ID: $tokenId"
Write-Host "Catalog lookup: agent_id=$AgentId, service_id=$ServerId, tool_name=$ToolName, status=1"
Write-Host ""
Write-Host "Before running this script, ensure agent_policy_tool contains a bound row similar to:" -ForegroundColor Yellow
Write-Host "INSERT INTO agent_policy_tool (agent_id, service_id, service_name, tool_name, tool_id, auth_mode, status) VALUES ('$AgentId', '$ServerId', '<server-name>', '$ToolName', '$ToolId', 'USER_AUTH_REQUIRED', 1);"
Write-Host ""

try {
    $health = Invoke-PolicyApi -Method GET -Path "/actuator/health" -Body $null
    Assert-Equal "Health status" $health.status "UP"

    $savePolicy = Invoke-PolicyApi -Method PUT `
        -Path "/admin/agents/$AgentId/tool-policies" `
        -Body @{
            tools = @(
                @{ toolId = $ToolId; authMode = "USER_AUTH_REQUIRED" }
            )
        }
    Assert-Equal "Saved tool policy count" $savePolicy.toolCount 1

    $cleanupBeforeRun = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations/cleanup" `
        -Body @{ tokenId = $tokenId }
    Assert-Equal "Initial cleanup status" $cleanupBeforeRun.status "CLEARED"

    $precheckRequest = @{
        tools = @(
            @{
                serverId = $ServerId
                toolName = $ToolName
            }
        )
    }

    $precheckRequired = Invoke-PolicyApi -Method POST `
        -Path "/internal/tool-authorization-prechecks" `
        -ExtraHeaders @{ "X-AGW-ACCESS-TOKEN" = $tokenId } `
        -Body $precheckRequest
    Assert-Equal "Precheck required tokenid" $precheckRequired.tokenid $tokenId
    Assert-Equal "Precheck required tool count" @($precheckRequired.tools).Count 1
    Assert-Equal "Precheck required serverId" $precheckRequired.tools[0].serverId $ServerId
    Assert-Equal "Precheck required toolId" $precheckRequired.tools[0].toolId $ToolId
    Assert-Equal "Precheck required toolName" $precheckRequired.tools[0].toolName $ToolName
    Assert-Equal "Precheck required decision" $precheckRequired.tools[0].decision "AUTHORIZATION_REQUIRED"
    if (-not [string]::IsNullOrWhiteSpace($ExpectedServerName)) {
        Assert-Equal "Precheck required serverName" $precheckRequired.tools[0].serverName $ExpectedServerName
    }
    else {
        Assert-True "Precheck returned serverName" (-not [string]::IsNullOrWhiteSpace($precheckRequired.tools[0].serverName))
    }

    $batchAuthorization = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations/batch" `
        -ExtraHeaders @{ "X-AGW-ACCESS-TOKEN" = $tokenId } `
        -Body @{
            toolIds = @($ToolId)
        }
    Assert-Equal "Batch authorization status" $batchAuthorization.status "AUTHORIZED"
    Assert-Equal "Batch authorization tokenId" $batchAuthorization.tokenId $tokenId
    Assert-Equal "Batch authorization toolCount" $batchAuthorization.toolCount 1
    Assert-Equal "Batch authorization toolId" $batchAuthorization.toolIds[0] $ToolId

    $precheckAfterAuthorization = Invoke-PolicyApi -Method POST `
        -Path "/internal/tool-authorization-prechecks" `
        -ExpectedStatus 200 `
        -ExtraHeaders @{ "X-AGW-ACCESS-TOKEN" = $tokenId } `
        -Body @{
            tools = @(
                @{
                    serverid = $ServerId
                    toolname = $ToolName
                }
            )
        }
    Assert-Equal "Precheck authorized tokenid" $precheckAfterAuthorization.tokenid $tokenId
    Assert-Equal "Precheck authorized tool count" @($precheckAfterAuthorization.tools).Count 0

    $duplicateBatch = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations/batch" `
        -ExpectedStatus 400 `
        -ExtraHeaders @{ "X-AGW-ACCESS-TOKEN" = $tokenId } `
        -Body @{
            toolIds = @($ToolId, $ToolId)
        }
    Assert-Equal "Duplicate batch error" $duplicateBatch.code "INVALID_REQUEST"
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
