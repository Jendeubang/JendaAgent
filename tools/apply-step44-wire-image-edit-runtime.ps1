$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$composePath = Join-Path $root 'deploy\docker-compose.yml'
$envPath = Join-Path $root 'deploy\.env'
$compose = [System.IO.File]::ReadAllText($composePath)

$old = @'
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      TZ: Asia/Shanghai
'@
$new = @'
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      # The agent calls this same-container gateway; both sides share the internal key from deploy/.env.
      AGENT_RUNTIME_TOOLS_IMAGE_EDIT_ENABLED: ${AGENT_GATEWAY_IMAGE_EDIT_ENABLED:-false}
      AGENT_RUNTIME_TOOLS_IMAGE_EDIT_URL: http://127.0.0.1:8080/internal/agent-tools/qwen-image-edit
      AGENT_RUNTIME_TOOLS_IMAGE_EDIT_API_KEY: ${AGENT_GATEWAY_IMAGE_EDIT_INTERNAL_KEY:?Set AGENT_GATEWAY_IMAGE_EDIT_INTERNAL_KEY in deploy/.env}
      AGENT_RUNTIME_TOOLS_IMAGE_EDIT_TIMEOUT: ${AGENT_RUNTIME_TOOLS_IMAGE_EDIT_TIMEOUT:-600s}
      TZ: Asia/Shanghai
'@
$count = ([regex]::Matches($compose, [regex]::Escape($old))).Count
if ($count -ne 1) { throw "Expected exactly one backend environment block in $composePath, found $count. No files were changed." }
$compose = $compose.Replace($old, $new)
[System.IO.File]::WriteAllText($composePath, $compose, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $composePath"

$env = [System.IO.File]::ReadAllText($envPath)
$keyName = 'AGENT_GATEWAY_IMAGE_EDIT_INTERNAL_KEY'
$existing = [regex]::Match($env, "(?m)^$keyName=(.*)$")
if ($existing.Success -and -not [string]::IsNullOrWhiteSpace($existing.Groups[1].Value)) {
  Write-Host "$keyName already exists; kept the existing secret."
} else {
  $bytes = New-Object byte[] 32
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  $secret = [Convert]::ToHexString($bytes).ToLowerInvariant()
  if ($existing.Success) {
    $env = [regex]::Replace($env, "(?m)^$keyName=.*$", "$keyName=$secret", 1)
  } else {
    if (-not $env.EndsWith([Environment]::NewLine)) { $env += [Environment]::NewLine }
    $env += "$keyName=$secret" + [Environment]::NewLine
  }
  [System.IO.File]::WriteAllText($envPath, $env, [System.Text.UTF8Encoding]::new($false))
  Write-Host "Generated and saved $keyName in deploy/.env without printing it."
}

$readme = Join-Path $root 'README_JENDA_AGENT_STATUS_STEP44_IMAGE_EDIT_RUNTIME_WIRING.md'
$readmeContent = @'
# Step 44: Image Edit Runtime Wiring

## Root Cause

The Qwen image-edit gateway and the agent tool router use different configuration namespaces:

- `agent.gateway.image-edit.*`: Qwen provider gateway settings.
- `agent.runtime.tools.image-edit.*`: the main Agent ToolRouter endpoint settings.

Only the first namespace was configured, so `HttpAgentToolClient` marked `IMAGE_EDIT` as unavailable.

## Docker Wiring

The backend Compose service now defines:

```text
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_ENABLED <- AGENT_GATEWAY_IMAGE_EDIT_ENABLED
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_URL     = http://127.0.0.1:8080/internal/agent-tools/qwen-image-edit
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_API_KEY <- AGENT_GATEWAY_IMAGE_EDIT_INTERNAL_KEY
```

`AGENT_GATEWAY_IMAGE_EDIT_INTERNAL_KEY` is generated as a 32-byte random local secret when absent. It is intentionally stored only in `deploy/.env` and must never be committed.

## Runtime Flow

```text
Agent UI imageUrl + prompt
  -> HttpAgentToolClient (IMAGE_EDIT)
  -> internal qwen-image-edit controller
  -> Qwen image edit gateway
  -> archive result to COS
  -> SSE tool_result + image event
```

## Test

1. Open `/zh/agent`.
2. Upload a reference image.
3. Select `Image Editing` in Model Preference.
4. Submit a background replacement instruction.
5. Expect `Qwen image edit completed successfully` and an image artifact event.
'@
[System.IO.File]::WriteAllText($readme, $readmeContent, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $readme"
