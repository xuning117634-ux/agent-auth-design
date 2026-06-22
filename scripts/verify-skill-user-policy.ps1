param(
    [string]$BaseUrl = "http://localhost:18080",
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbName = "policy_center",
    [string]$DbUser = "root"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($env:POLICY_CENTER_DB_PASSWORD)) {
    throw "Set POLICY_CENTER_DB_PASSWORD before running this script."
}

$mysql = (Get-Command mysql -ErrorAction Stop).Source
$env:MYSQL_PWD = $env:POLICY_CENTER_DB_PASSWORD
$runId = Get-Date -Format "yyyyMMddHHmmssfff"
$agentId = "verify-skill-agent-$runId"
$allowedUser = "verify-skill-user-$runId"
$batchUser = "verify-skill-batch-$runId"
$otherUser = "verify-skill-other-$runId"
$skillA = "verify-skill-a-$runId"
$skillB = "verify-skill-b-$runId"
$unboundSkill = "verify-skill-unbound-$runId"
$passCount = 0

function Invoke-MySql {
    param([string]$Sql)

    & $mysql -h $DbHost -P $DbPort -u $DbUser --default-character-set=utf8mb4 -D $DbName -e $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed with exit code $LASTEXITCODE"
    }
}

function Invoke-PolicyApi {
    param(
        [string]$Method,
        [string]$Path,
        $Body,
        [int]$ExpectedStatus = 200
    )

    $uri = "$($BaseUrl.TrimEnd('/'))$Path"
    $parameters = @{
        Uri = $uri
        Method = $Method
        Headers = @{ "X-Trace-Id" = "verify-skill-$runId" }
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 20
    }

    Write-Host "`n[REQUEST] $Method $uri" -ForegroundColor Cyan
    if ($null -ne $Body) {
        Write-Host $parameters.Body
    }

    try {
        $response = Invoke-WebRequest @parameters
        $statusCode = [int]$response.StatusCode
        $content = [string]$response.Content
    }
    catch {
        if ($null -eq $_.Exception.Response) {
            throw
        }
        $response = $_.Exception.Response
        $statusCode = [int]$response.StatusCode
        if ($response.Content) {
            $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        }
        else {
            $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
            try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
    }

    Write-Host "[RESPONSE] HTTP $statusCode" -ForegroundColor Cyan
    Write-Host $content
    if ($statusCode -ne $ExpectedStatus) {
        throw "$Method $Path expected HTTP $ExpectedStatus, actual $statusCode"
    }
    $script:passCount++
    if ([string]::IsNullOrWhiteSpace($content)) { return $null }
    return $content | ConvertFrom-Json
}

function Assert-Equal {
    param([string]$Name, $Actual, $Expected)

    if ($Actual -ne $Expected) {
        throw "$Name expected '$Expected', actual '$Actual'"
    }
    $script:passCount++
    Write-Host "[PASS] $Name = $Expected" -ForegroundColor Green
}

function Cleanup {
    Invoke-MySql "DELETE FROM agent_skill_user_access_policy WHERE agent_id = '$agentId'; DELETE FROM agent_skill_user_policy WHERE agent_id = '$agentId'; DELETE FROM agent_policy_skill WHERE agent_id = '$agentId';"
}

try {
    Invoke-MySql "INSERT INTO agent_policy_skill (agent_id, skill_id, skill_name, label, description, status) VALUES ('$agentId', '$skillA', 'Finance Analysis', 'finance', 'Analyze finance data', 1), ('$agentId', '$skillB', 'Customer Insight', 'crm', 'Analyze customer data', 1);"

    $initial = Invoke-PolicyApi GET "/admin/agents/$agentId/skill-user-policies" $null
    Assert-Equal "Initial bound Skill count" @($initial.skills).Count 2
    Assert-Equal "Unconfigured Skill scope" $initial.skills[0].accessScope "PUBLIC"

    $save = Invoke-PolicyApi PUT "/admin/agents/$agentId/skill-user-policies" @{
        skills = @(
            @{
                skillId = $skillA
                accessScope = "RESTRICTED"
                users = @(@{ userId = "$allowedUser,$batchUser;$allowedUser" })
            },
            @{
                skillId = $skillB
                accessScope = "PUBLIC"
                users = @(@{ userId = $otherUser })
            }
        )
    }
    Assert-Equal "Saved Skill policy count" $save.skillPolicyCount 2
    Assert-Equal "Parsed and deduplicated user count" $save.skillUserRuleCount 3

    $allowed = Invoke-PolicyApi GET "/internal/agents/$agentId/users/$allowedUser/skills" $null
    Assert-Equal "Whitelisted user Skill count" @($allowed.skills).Count 2
    $other = Invoke-PolicyApi GET "/internal/agents/$agentId/users/$otherUser/skills" $null
    Assert-Equal "PUBLIC ignores stored whitelist" @($other.skills).Count 1
    Assert-Equal "Other user sees PUBLIC Skill" $other.skills[0].skillId $skillB

    Invoke-MySql "UPDATE agent_policy_skill SET status = 0 WHERE agent_id = '$agentId' AND skill_id = '$skillB';"
    $afterUnbind = Invoke-PolicyApi GET "/internal/agents/$agentId/users/$otherUser/skills" $null
    Assert-Equal "Unbound Skill no longer accessible" @($afterUnbind.skills).Count 0

    $error = Invoke-PolicyApi PUT "/admin/agents/$agentId/skill-user-policies" @{
        skills = @(@{ skillId = $unboundSkill; accessScope = "RESTRICTED"; users = @() })
    } 409
    Assert-Equal "Unbound save error" $error.code "SKILL_NOT_BOUND"

    Write-Host "`nSkill user policy verification passed: $passCount checks." -ForegroundColor Green
}
finally {
    Cleanup
}
