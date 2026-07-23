$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)
$targets = @(
  "genie-backend/src/main/java/com/jd/genie/service/auth/AgentTokenStore.java",
  "genie-backend/src/main/java/com/jd/genie/service/auth/AgentSmsCodeService.java"
)
$replacements = @{
  'jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_refresh_user ON agent_refresh_token(user_id)");' = 'try { jdbcTemplate.execute("CREATE INDEX idx_agent_refresh_user ON agent_refresh_token(user_id)"); } catch (RuntimeException ignored) { }'
  'jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_sms_lookup ON agent_sms_code(phone, purpose, created_at)");' = 'try { jdbcTemplate.execute("CREATE INDEX idx_agent_sms_lookup ON agent_sms_code(phone, purpose, created_at)"); } catch (RuntimeException ignored) { }'
}
foreach ($relative in $targets) {
  $path = Join-Path $root $relative
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing source file: $path" }
  $source = [System.IO.File]::ReadAllText($path)
  $updated = $source
  foreach ($old in $replacements.Keys) {
    if ($updated.Contains($old)) { $updated = $updated.Replace($old, $replacements[$old]) }
  }
  if ($updated -eq $source) { throw "Expected MySQL index initialization was not found in $path. No source files were changed." }
  [System.IO.File]::WriteAllText($path, $updated, $utf8)
  Write-Host "Updated $path"
}
