$ErrorActionPreference = "Stop"

# Safe continuation after apply-step30-asset-center.ps1 stopped before COS injection.
# All inputs are validated before either source file is written.
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "genie-backend"
$showcase = Join-Path $root "showcase"
$cosPath = Join-Path $backend "src/main/java/com/jd/genie/service/agent/CosAgentImageStorage.java"
$menuPath = Join-Path $showcase "components/AgentAccountMenu.tsx"
$utf8 = New-Object System.Text.UTF8Encoding($false)

foreach ($path in @($cosPath, $menuPath)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Required source file is missing: $path" }
}

$cosSource = [System.IO.File]::ReadAllText($cosPath)
$menuSource = [System.IO.File]::ReadAllText($menuPath)
$lineEnding = if ($cosSource.Contains("`r`n")) { "`r`n" } else { "`n" }
$imageUrlPattern = '(?ms)^    public String imageUrl\(StoredAgentImage image\) \{\r?\n        return baseUrl \+ "/" \+ encodeObjectKey\(image\.storedFileName\(\)\);\r?\n    \}'
$imageUrlMatches = [regex]::Matches($cosSource, $imageUrlPattern)

if ($cosSource.Contains("public void deleteObject(String objectKey)")) {
    $updatedCosSource = $cosSource
} elseif ($imageUrlMatches.Count -eq 1) {
    $deleteMethods = @(
        '',
        '    public void deleteObject(String objectKey) {',
        '        if (objectKey == null || objectKey.isBlank() || !objectKey.startsWith(prefix)) {',
        '            throw new IllegalArgumentException("Refusing to delete an object outside the configured COS prefix");',
        '        }',
        '        URI uri = URI.create(baseUrl + "/" + encodeObjectKey(objectKey));',
        '        String contentType = "application/octet-stream";',
        '        Request request = new Request.Builder()',
        '                .url(uri.toString())',
        '                .header("Host", uri.getHost())',
        '                .header("Content-Type", contentType)',
        '                .header("Authorization", authorization("DELETE", "/" + objectKey, uri.getHost(), contentType))',
        '                .delete()',
        '                .build();',
        '        try (Response response = client.newCall(request).execute()) {',
        '            if (!response.isSuccessful() && response.code() != 404) {',
        '                throw new IllegalStateException("COS delete failed with HTTP " + response.code());',
        '            }',
        '        } catch (IOException error) {',
        '            throw new IllegalStateException("COS delete request failed: " + error.getMessage(), error);',
        '        }',
        '    }',
        '',
        '    public void deleteObjectFromUrl(String imageUrl) {',
        '        try {',
        '            URI uri = URI.create(imageUrl);',
        '            String objectKey = java.net.URLDecoder.decode(uri.getRawPath().replaceFirst("^/", ""), StandardCharsets.UTF_8);',
        '            deleteObject(objectKey);',
        '        } catch (IllegalArgumentException error) {',
        '            throw error;',
        '        } catch (Exception error) {',
        '            throw new IllegalStateException("Unable to resolve COS object key from asset URL", error);',
        '        }',
        '    }'
    ) -join $lineEnding
    $replacement = $imageUrlMatches[0].Value + $lineEnding + $deleteMethods
    $updatedCosSource = [regex]::Replace($cosSource, $imageUrlPattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $replacement }, 1)
} else {
    throw "Expected one imageUrl method in $cosPath, found $($imageUrlMatches.Count). No files were changed."
}

if ($menuSource.Contains('key: "assets"')) {
    $updatedMenuSource = $menuSource
} elseif ($menuSource.Contains('export function AgentAccountMenu()')) {
    $updatedMenuSource = @'
"use client";

import { AppstoreOutlined, LoginOutlined, LogoutOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Dropdown } from "antd";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { clearAgentAuthSession, readAgentAuthSession, type AgentAuthSession } from "../lib/agentAuth";

export function AgentAccountMenu() {
  const router = useRouter();
  const [session, setSession] = useState<AgentAuthSession>();

  useEffect(() => setSession(readAgentAuthSession()), []);

  if (!session) {
    return <Button icon={<LoginOutlined />} onClick={() => router.push("/login")}>登录</Button>;
  }

  return <Dropdown menu={{ items: [
    { key: "identity", disabled: true, label: `账号：${session.username}` },
    { type: "divider" },
    { key: "assets", icon: <AppstoreOutlined />, label: "资产中心", onClick: () => router.push("/assets") },
    { key: "logout", icon: <LogoutOutlined />, label: "退出登录", onClick: () => { clearAgentAuthSession(); router.replace("/login"); } },
  ] }} trigger={["click"]}>
    <Button icon={<UserOutlined />}>{session.username}</Button>
  </Dropdown>;
}
'@
} else {
    throw "AgentAccountMenu source has an unexpected structure: $menuPath. No files were changed."
}

[System.IO.File]::WriteAllText($cosPath, $updatedCosSource, $utf8)
[System.IO.File]::WriteAllText($menuPath, $updatedMenuSource, $utf8)
Write-Host "Updated $cosPath"
Write-Host "Updated $menuPath"
Write-Host "Step 30 continuation completed. Build backend and type-check frontend next."
