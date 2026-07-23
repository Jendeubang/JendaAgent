$ErrorActionPreference = "Stop"
$path = Join-Path (Split-Path -Parent $PSScriptRoot) "genie-backend/src/main/java/com/jd/genie/service/agent/PersistentAgentWorkspaceCatalog.java"
$content = [System.IO.File]::ReadAllText($path)
$replacements = @(
  @{ Old = "import org.springframework.jdbc.core.JdbcTemplate;"; New = "import org.springframework.jdbc.core.JdbcTemplate;`r`nimport org.springframework.jdbc.core.RowCallbackHandler;" },
  @{ Old = "                resultSet -> {"; New = "                (RowCallbackHandler) resultSet -> {" },
  @{ Old = '                        """, resultSet -> assets.add(new AgentWorkspaceAsset('; New = '                        """, (RowCallbackHandler) resultSet -> assets.add(new AgentWorkspaceAsset(' }
)
foreach ($replacement in $replacements) {
  $count = ([regex]::Matches($content, [regex]::Escape($replacement.Old))).Count
  if ($count -ne 1) { throw "Expected one matching replacement block, found $count. No file was changed." }
}
foreach ($replacement in $replacements) { $content = $content.Replace($replacement.Old, $replacement.New) }
[System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Applied JdbcTemplate overload fix."
