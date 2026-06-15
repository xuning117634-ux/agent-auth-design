param(
    [string]$BaseUrl = "http://localhost:18080"
)

$ErrorActionPreference = "Stop"

$runId = Get-Date -Format "yyyyMMddHHmmssfff"
$agentId = "verify-user-policy-agent-$runId"
$userId = "verify-user-$runId"
$otherUserId = "verify-other-user-$runId"
$batchUserId = "verify-batch-user-$runId"
$conversationId = "verify-conversation-$runId"
$tokenId = "$agentId`:$userId`:$conversationId"
$otherTokenId = "$agentId`:$otherUserId`:$conversationId"
$publicNoAuthToolId = "verify-public-no-auth-$runId"
$publicUserAuthToolId = "verify-public-user-auth-$runId"
$restrictedAllowedToolId = "verify-restricted-allowed-$runId"
$restrictedDeniedToolId = "verify-restricted-denied-$runId"
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

function Assert-False {
    param(
        [string]$Name,
        [bool]$Condition
    )

    if (-not $Condition) {
        Write-Pass $Name
        return
    }

    throw "$Name expected false"
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
        [int]$ExpectedStatus = 200
    )

    $uri = "$($BaseUrl.TrimEnd('/'))$Path"
    $traceId = "verify-user-policy-$runId"
    $parameters = @{
        Uri             = $uri
        Method          = $Method
        Headers         = @{ "X-Trace-Id" = $traceId }
        ErrorAction     = "Stop"
        UseBasicParsing = $true
    }

    $requestBodyText = $null
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $requestBodyText = $Body | ConvertTo-Json -Depth 20
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

function Reset-UserPolicy {
    param([bool]$Quiet = $false)

    try {
        Invoke-PolicyApi -Method PUT `
            -Path "/admin/agents/$agentId/user-policies" `
            -Body @{
                accessScope = "PUBLIC"
                agentUsers = @()
                tools = @()
            } | Out-Null
        if (-not $Quiet) {
            Write-Host "[CLEANUP] User policies reset." -ForegroundColor DarkGray
        }
    }
    catch {
        Write-Host "[CLEANUP-WARN] Failed to reset user policies: $($_.Exception.Message)" -ForegroundColor Yellow
    }
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

    Reset-UserPolicy

    try {
        Invoke-PolicyApi -Method PUT `
            -Path "/admin/agents/$agentId/tool-policies" `
            -Body @{ tools = @() } | Out-Null
        Write-Host "[CLEANUP] Tool policies removed." -ForegroundColor DarkGray
    }
    catch {
        Write-Host "[CLEANUP-WARN] Failed to remove tool policies: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

function Test-ToolPresent {
    param(
        $Tools,
        [string]$ToolId,
        [bool]$Expected
    )

    $exists = @($Tools | Where-Object { $_.toolId -eq $ToolId }).Count -gt 0
    if ($Expected) {
        Assert-True "Tool list contains $ToolId" $exists
    }
    else {
        Assert-False "Tool list excludes $ToolId" $exists
    }
}

Write-Host "Policy Center access-scope verification" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl"
Write-Host "Test agent: $agentId"
Write-Host "Test user: $userId"
Write-Host "Token ID: $tokenId"
Write-Host ""

try {
    $health = Invoke-PolicyApi -Method GET -Path "/actuator/health" -Body $null
    Assert-Equal "Health status" $health.status "UP"

    $saveTools = Invoke-PolicyApi -Method PUT `
        -Path "/admin/agents/$agentId/tool-policies" `
        -Body @{
            tools = @(
                @{ toolId = $publicNoAuthToolId; authMode = "NO_AUTH_REQUIRED" },
                @{ toolId = $publicUserAuthToolId; authMode = "USER_AUTH_REQUIRED" },
                @{ toolId = $restrictedAllowedToolId; authMode = "NO_AUTH_REQUIRED" },
                @{ toolId = $restrictedDeniedToolId; authMode = "NO_AUTH_REQUIRED" }
            )
        }
    Assert-Equal "Saved tool count" $saveTools.toolCount 4

    $defaultPolicy = Invoke-PolicyApi -Method GET `
        -Path "/admin/agents/$agentId/user-policies" `
        -Body $null
    Assert-Equal "Default agent access scope" $defaultPolicy.accessScope "PUBLIC"
    Assert-Equal "Default tool policy count" @($defaultPolicy.tools).Count 4
    Assert-True "All tools default to PUBLIC" (@($defaultPolicy.tools | Where-Object { $_.accessScope -ne "PUBLIC" }).Count -eq 0)

    $savePolicy = Invoke-PolicyApi -Method PUT `
        -Path "/admin/agents/$agentId/user-policies" `
        -Body @{
            accessScope = "RESTRICTED"
            agentUsers = @(
                @{ userId = "$userId,$batchUserId;$userId" }
            )
            tools = @(
                @{
                    toolId = $publicNoAuthToolId
                    accessScope = "PUBLIC"
                    users = @(
                        @{ userId = $otherUserId }
                    )
                },
                @{
                    toolId = $publicUserAuthToolId
                    accessScope = "PUBLIC"
                    users = @()
                },
                @{
                    toolId = $restrictedAllowedToolId
                    accessScope = "RESTRICTED"
                    users = @(
                        @{ userId = "$userId,$batchUserId;$userId" }
                    )
                },
                @{
                    toolId = $restrictedDeniedToolId
                    accessScope = "RESTRICTED"
                    users = @(
                        @{ userId = $otherUserId }
                    )
                }
            )
        }
    Assert-Equal "Saved agent whitelist count" $savePolicy.agentUserRuleCount 2
    Assert-Equal "Saved tool whitelist count" $savePolicy.toolUserRuleCount 4

    $savedPolicy = Invoke-PolicyApi -Method GET `
        -Path "/admin/agents/$agentId/user-policies" `
        -Body $null
    Assert-Equal "Saved agent access scope" $savedPolicy.accessScope "RESTRICTED"
    Assert-Equal "Expanded agent whitelist count" @($savedPolicy.agentUsers).Count 2
    Assert-Equal "Saved tool policy count" @($savedPolicy.tools).Count 4
    $publicWithWhitelist = $savedPolicy.tools | Where-Object { $_.toolId -eq $publicNoAuthToolId }
    Assert-Equal "PUBLIC tool keeps whitelist" @($publicWithWhitelist.users).Count 1
    $restrictedAllowedWithBatch = $savedPolicy.tools | Where-Object { $_.toolId -eq $restrictedAllowedToolId }
    Assert-Equal "Expanded tool whitelist count" @($restrictedAllowedWithBatch.users).Count 2

    $agentAllowed = Invoke-PolicyApi -Method POST `
        -Path "/internal/agent-access-decisions" `
        -Body @{ agentId = $agentId; userId = $userId }
    Assert-Equal "Whitelisted agent access" $agentAllowed.allowed $true
    Assert-Equal "Whitelisted agent reason" $agentAllowed.reason "AGENT_USER_WHITELISTED"

    $agentDenied = Invoke-PolicyApi -Method POST `
        -Path "/internal/agent-access-decisions" `
        -Body @{ agentId = $agentId; userId = $otherUserId }
    Assert-Equal "Non-whitelisted agent access" $agentDenied.allowed $false
    Assert-Equal "Non-whitelisted agent reason" $agentDenied.reason "AGENT_USER_NOT_WHITELISTED"

    $accessibleAgents = Invoke-PolicyApi -Method GET `
        -Path "/internal/users/$userId/agents" `
        -Body $null
    Assert-True "Accessible agent list contains $agentId" `
        (@($accessibleAgents.agents | Where-Object { $_.agentId -eq $agentId }).Count -gt 0)

    $accessibleTools = Invoke-PolicyApi -Method GET `
        -Path "/internal/agents/$agentId/users/$userId/tools" `
        -Body $null
    Assert-Equal "Accessible tool count" @($accessibleTools.tools).Count 3
    Test-ToolPresent -Tools $accessibleTools.tools -ToolId $publicNoAuthToolId -Expected $true
    Test-ToolPresent -Tools $accessibleTools.tools -ToolId $publicUserAuthToolId -Expected $true
    Test-ToolPresent -Tools $accessibleTools.tools -ToolId $restrictedAllowedToolId -Expected $true
    Test-ToolPresent -Tools $accessibleTools.tools -ToolId $restrictedDeniedToolId -Expected $false

    $publicDecisionForNonAgentUser = Invoke-PolicyApi -Method POST `
        -Path "/internal/authorization-decisions" `
        -Body @{ tokenId = $otherTokenId; toolId = $publicNoAuthToolId }
    Assert-Equal "Agent restriction does not affect PUBLIC tool" $publicDecisionForNonAgentUser.decision "ALLOW"
    Assert-Equal "PUBLIC tool reason" $publicDecisionForNonAgentUser.reason "NO_AUTH_REQUIRED"

    $restrictedAllowedDecision = Invoke-PolicyApi -Method POST `
        -Path "/internal/authorization-decisions" `
        -Body @{ tokenId = $tokenId; toolId = $restrictedAllowedToolId }
    Assert-Equal "Whitelisted RESTRICTED tool decision" $restrictedAllowedDecision.decision "ALLOW"
    Assert-Equal "Whitelisted RESTRICTED tool reason" $restrictedAllowedDecision.reason "NO_AUTH_REQUIRED"

    $restrictedDeniedDecision = Invoke-PolicyApi -Method POST `
        -Path "/internal/authorization-decisions" `
        -Body @{ tokenId = $tokenId; toolId = $restrictedDeniedToolId }
    Assert-Equal "Non-whitelisted RESTRICTED tool decision" $restrictedDeniedDecision.decision "DENY"
    Assert-Equal "Non-whitelisted RESTRICTED tool reason" $restrictedDeniedDecision.reason "USER_TOOL_ACCESS_DENIED"

    $userAuthDecision = Invoke-PolicyApi -Method POST `
        -Path "/internal/authorization-decisions" `
        -Body @{ tokenId = $tokenId; toolId = $publicUserAuthToolId }
    Assert-Equal "PUBLIC user-auth decision" $userAuthDecision.decision "AUTHORIZATION_REQUIRED"
    Assert-Equal "PUBLIC user-auth reason" $userAuthDecision.reason "USER_AUTHORIZATION_REQUIRED"

    $authorization = Invoke-PolicyApi -Method POST `
        -Path "/internal/conversation-authorizations" `
        -Body @{ tokenId = $tokenId; toolId = $publicUserAuthToolId }
    Assert-Equal "User-auth confirmation" $authorization.status "AUTHORIZED"

    $authorizedDecision = Invoke-PolicyApi -Method POST `
        -Path "/internal/authorization-decisions" `
        -Body @{ tokenId = $tokenId; toolId = $publicUserAuthToolId }
    Assert-Equal "Authorized user-auth decision" $authorizedDecision.decision "ALLOW"
    Assert-Equal "Authorized user-auth reason" $authorizedDecision.reason "CONVERSATION_AUTHORIZED"

    $omittedToolReset = Invoke-PolicyApi -Method PUT `
        -Path "/admin/agents/$agentId/user-policies" `
        -Body @{
            accessScope = "PUBLIC"
            agentUsers = @()
            tools = @(
                @{
                    toolId = $restrictedDeniedToolId
                    accessScope = "RESTRICTED"
                    users = @(
                        @{ userId = $otherUserId }
                    )
                }
            )
        }
    Assert-Equal "Omitted tool reset save count" $omittedToolReset.toolUserRuleCount 1

    $policyAfterOmission = Invoke-PolicyApi -Method GET `
        -Path "/admin/agents/$agentId/user-policies" `
        -Body $null
    $omittedTool = $policyAfterOmission.tools | Where-Object { $_.toolId -eq $restrictedAllowedToolId }
    Assert-Equal "Omitted tool resets to PUBLIC" $omittedTool.accessScope "PUBLIC"
    Assert-Equal "Omitted tool whitelist is cleared" @($omittedTool.users).Count 0

    $unboundToolUserPolicy = Invoke-PolicyApi -Method PUT `
        -Path "/admin/agents/$agentId/user-policies" `
        -ExpectedStatus 409 `
        -Body @{
            accessScope = "PUBLIC"
            agentUsers = @()
            tools = @(
                @{
                    toolId = $unboundToolId
                    accessScope = "RESTRICTED"
                    users = @(
                        @{ userId = $userId }
                    )
                }
            )
        }
    Assert-Equal "Unbound tool user policy error" $unboundToolUserPolicy.code "TOOL_NOT_BOUND"
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
