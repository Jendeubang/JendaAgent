$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "genie-backend\src\main\java\com\jd\genie\service\agent\StsDirectUploadTicketService.java"
$source = [System.IO.File]::ReadAllText($path)
$broken = '        if (!record.ownerUserId.equals(AgentRequestUserContext.current().userId())) { throw new AgentSessionAccessDeniedException(); }`n        long uploadedSize = headObject(record.objectKey);'
$fixed = "        if (!record.ownerUserId.equals(AgentRequestUserContext.current().userId())) { throw new AgentSessionAccessDeniedException(); }$([Environment]::NewLine)        long uploadedSize = headObject(record.objectKey);"
if (-not $source.Contains($broken)) {
    throw "Expected malformed Step 27 STS line was not found. No files were changed."
}
[System.IO.File]::WriteAllText($path, $source.Replace($broken, $fixed), [System.Text.UTF8Encoding]::new($false))
Write-Host "Fixed the STS ticket owner validation newline. Rebuild the backend."
