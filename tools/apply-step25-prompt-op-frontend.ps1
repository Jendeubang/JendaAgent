$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "showcase\app\image-studio\page.tsx"
$content = [System.IO.File]::ReadAllText($path)

if ($content.Contains("function eventDetail(event: AgentEvent)")) {
    Write-Host "Step 25 PromptOp frontend rendering is already present."
    exit 0
}

$anchor = @'
function imageFromEvent(event: AgentEvent) {
  return stringValue(event.payload.imageUrl);
}
'@.TrimStart("`n")
$addition = @'
function imageFromEvent(event: AgentEvent) {
  return stringValue(event.payload.imageUrl);
}

function eventDetail(event: AgentEvent) {
  if (event.messageType !== "prompt_optimization") {
    return stringValue(event.payload.content, stringValue(event.payload.message, ""));
  }
  const original = stringValue(event.payload.originalPrompt);
  const optimized = stringValue(event.payload.optimizedPrompt);
  const rules = Array.isArray(event.payload.retrievedRules)
    ? event.payload.retrievedRules.filter((value): value is string => typeof value === "string").join(", ")
    : "";
  return `Original: ${original}\nOptimized: ${optimized}\nRetrieved rules: ${rules || "none"}`;
}
'@.TrimStart("`n")
$oldTrace = '<small>{stringValue(event.payload.content, stringValue(event.payload.message, ""))}</small>'
$newTrace = '<small>{eventDetail(event)}</small>'

if (($content.IndexOf($anchor)) -lt 0 -or ($content.IndexOf($oldTrace)) -lt 0) {
    throw "Expected Image Studio event rendering blocks were not found. No files were changed."
}

$updated = $content.Replace($anchor, $addition).Replace($oldTrace, $newTrace)
[System.IO.File]::WriteAllText($path, $updated, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $path"
Write-Host "Step 25 PromptOp frontend rendering completed. Restart the frontend."
