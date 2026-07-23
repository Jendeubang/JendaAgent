$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "genie-backend\src\main\java\com\jd\genie\controller\QwenImageEditToolGatewayController.java"
$utf8 = [System.Text.UTF8Encoding]::new($false)
$source = [System.IO.File]::ReadAllText($path)
$needle = '        QwenImageEditGatewayClient.EditedImage result = client.edit(request);'
$insertion = '        if (request.image_urls() == null || request.image_urls().isEmpty()) {' + [Environment]::NewLine + '            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_edit requires at least one image_url");' + [Environment]::NewLine + '        }' + [Environment]::NewLine + $needle
$count = ([regex]::Matches($source, [regex]::Escape($needle))).Count
if ($count -ne 1) { throw "Expected exactly one Qwen image-edit invocation in $path, found $count. No files were changed." }
[System.IO.File]::WriteAllText($path, $source.Replace($needle, $insertion), $utf8)
Write-Host "Added explicit missing-image validation. Rebuild the backend."
