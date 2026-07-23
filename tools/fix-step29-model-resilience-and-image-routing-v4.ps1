$ErrorActionPreference = "Stop"
$templatePath = Join-Path $PSScriptRoot "fix-step29-model-resilience-and-image-routing-v3.ps1"
$template = [System.IO.File]::ReadAllText($templatePath)
$badLinePattern = '(?m)^\$controllerUpdated = \[regex\]::Replace\(\$controllerSource, \$controllerPattern,.*$'
$replacement = @'
$missingValidation = @'
        if (request.image_urls() == null || request.image_urls().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_edit requires at least one image_url");
        }
'@.TrimEnd()
$controllerUpdated = [regex]::Replace($controllerSource, $controllerPattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $match.Groups[1].Value + $missingValidation + [Environment]::NewLine + $match.Groups[2].Value }, 1)
'@.TrimEnd()
if (([regex]::Matches($template, $badLinePattern)).Count -ne 1) { throw "Step 29 v3 controller replacement line was not found. No files were changed." }
$template = [regex]::Replace($template, $badLinePattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $replacement }, 1)
$generated = Join-Path $PSScriptRoot "fix-step29-model-resilience-and-image-routing-v4.generated.ps1"
[System.IO.File]::WriteAllText($generated, $template, [System.Text.UTF8Encoding]::new($false))
try { & $generated } finally { Remove-Item -LiteralPath $generated -Force -ErrorAction SilentlyContinue }
