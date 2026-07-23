$ErrorActionPreference = "Stop"

# Execute the guarded V3 script in an in-memory scope with the calling tools directory preserved.
$v3Path = Join-Path $PSScriptRoot "apply-step14-tool-routing-v3.ps1"
$scriptText = [System.IO.File]::ReadAllText($v3Path)
$lineEndingHelper = @'
function Match-SourceLineEndings {
    param([string]$Value, [string]$Content)
    if ($Content.Contains("`r`n")) {
        return $Value -replace "`r?`n", "`r`n"
    }
    return $Value -replace "`r`n", "`n"
}

'@
$scriptText = $scriptText.Replace('$ErrorActionPreference = "Stop"' + "`n", '$ErrorActionPreference = "Stop"' + "`n`n" + $lineEndingHelper)

$assertOld = @'
    $content = [System.IO.File]::ReadAllText($Replacement.Path)
    $first = $content.IndexOf($Replacement.Old, [System.StringComparison]::Ordinal)
    $second = if ($first -ge 0) { $content.IndexOf($Replacement.Old, $first + $Replacement.Old.Length, [System.StringComparison]::Ordinal) } else { -1 }
'@
$assertNew = @'
    $content = [System.IO.File]::ReadAllText($Replacement.Path)
    $oldValue = Match-SourceLineEndings $Replacement.Old $content
    $first = $content.IndexOf($oldValue, [System.StringComparison]::Ordinal)
    $second = if ($first -ge 0) { $content.IndexOf($oldValue, $first + $oldValue.Length, [System.StringComparison]::Ordinal) } else { -1 }
'@
$scriptText = $scriptText.Replace($assertOld, $assertNew)

$applyOld = @'
    $content = [System.IO.File]::ReadAllText($Replacement.Path)
    $index = $content.IndexOf($Replacement.Old, [System.StringComparison]::Ordinal)
    $updated = $content.Substring(0, $index) + $Replacement.New + $content.Substring($index + $Replacement.Old.Length)
'@
$applyNew = @'
    $content = [System.IO.File]::ReadAllText($Replacement.Path)
    $oldValue = Match-SourceLineEndings $Replacement.Old $content
    $newValue = Match-SourceLineEndings $Replacement.New $content
    $index = $content.IndexOf($oldValue, [System.StringComparison]::Ordinal)
    $updated = $content.Substring(0, $index) + $newValue + $content.Substring($index + $oldValue.Length)
'@
$scriptText = $scriptText.Replace($applyOld, $applyNew)

if ($scriptText.Contains('$content.IndexOf($Replacement.Old')) {
    throw "Unable to prepare the normalized Step 14 procedure. No source files were changed."
}

$escapedToolsPath = $PSScriptRoot.Replace("'", "''")
$bootstrap = '$PSScriptRoot = ''' + $escapedToolsPath + '''' + "`n"
& ([scriptblock]::Create($bootstrap + $scriptText))
