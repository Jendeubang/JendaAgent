$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)
$pagePath = Join-Path $root "showcase\app\page.tsx"
$readmePath = Join-Path $root "README_JENDA_AGENT_STATUS_STEP32_DOCKER_COMPOSE.md"

$page = @'
"use client";

import { Spin } from "antd";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { readAgentAuthSession } from "../lib/agentAuth";

/**
 * The old root page was a visual prototype. The deployed root must enter the
 * authenticated studio instead of exposing controls that are not connected to
 * the production API.
 */
export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    router.replace(readAgentAuthSession() ? "/agent-studio" : "/login");
  }, [router]);

  return <main style={{ minHeight: "100vh", display: "grid", placeItems: "center", background: "#f5f7f6" }}>
    <Spin tip="Opening Jenda Agent..." size="large" />
  </main>;
}
'@

[System.IO.File]::WriteAllText($pagePath, $page + [Environment]::NewLine, $utf8)

$readme = [System.IO.File]::ReadAllText($readmePath)
$needle = 'Open `http://localhost` when `HTTP_PORT=80`, or `http://localhost:<HTTP_PORT>`.'
$addition = @'
Open `http://localhost` when `HTTP_PORT=80`, or `http://localhost:<HTTP_PORT>`.
The root route checks the browser's local JWT session and redirects to `/login`
or the real `/agent-studio` product page. The previous root-only visual mock is
not part of the deployed product flow.
'@.TrimEnd()

if ($readme.Contains($needle)) {
  $readme = $readme.Replace($needle, $addition)
  [System.IO.File]::WriteAllText($readmePath, $readme, $utf8)
}

Write-Host "Updated deployed root entry and Docker Compose documentation."
