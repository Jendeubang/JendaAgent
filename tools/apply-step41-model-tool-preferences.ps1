$ErrorActionPreference = "Stop"

function Replace-Once([string]$Path, [string]$Old, [string]$New) {
    $content = [System.IO.File]::ReadAllText($Path)
    $count = ([regex]::Matches($content, [regex]::Escape($Old))).Count
    if ($count -ne 1) { throw "Expected exactly one matching block in $Path, found $count. No files were changed." }
    $updated = $content.Replace($Old, $New)
    [System.IO.File]::WriteAllText($Path, $updated, [System.Text.UTF8Encoding]::new($false))
    Write-Host "Updated $Path"
}

function Insert-Once([string]$Path, [string]$Needle, [string]$Insert) {
    $content = [System.IO.File]::ReadAllText($Path)
    $count = ([regex]::Matches($content, [regex]::Escape($Needle))).Count
    if ($count -ne 1) { throw "Expected exactly one matching insertion point in $Path, found $count. No files were changed." }
    $updated = $content.Replace($Needle, $Needle + $Insert)
    [System.IO.File]::WriteAllText($Path, $updated, [System.Text.UTF8Encoding]::new($false))
    Write-Host "Updated $Path"
}

$root = Split-Path -Parent $PSScriptRoot
$page = Join-Path $root "showcase\app\zh\agent\page.tsx"
$css = Join-Path $root "showcase\app\zh\agent\page.module.css"
$request = Join-Path $root "genie-backend\src\main\java\com\jd\genie\model\agent\AgentRunRequest.java"
$model = Join-Path $root "genie-backend\src\main\java\com\jd\genie\service\agent\ModelToolAgentRunService.java"
$dynamic = Join-Path $root "genie-backend\src\main\java\com\jd\genie\service\agent\DynamicPlanSolveAgentRunService.java"

Replace-Once $page 'import { Button } from "antd";' 'import { Button, Checkbox, Popover } from "antd";'
Replace-Once $page 'type Mode = "plan-solve" | "react";' 'type Mode = "plan-solve" | "react";`r`ntype PreferredTool = "OCR" | "IMAGE_GENERATE" | "IMAGE_EDIT";'
Replace-Once $page 'const sessionStorageKey = "jenda-agent-crispix-session";' @'
const sessionStorageKey = "jenda-agent-crispix-session";
const toolPreferenceStorageKey = "jenda-agent-tool-preferences";
const toolOptions: Array<{ value: PreferredTool; label: string; description: string }> = [
  { value: "OCR", label: "OCR \u6587\u5b57\u8bc6\u522b", description: "\u4ece\u53c2\u8003\u56fe\u4e2d\u63d0\u53d6\u6587\u5b57" },
  { value: "IMAGE_GENERATE", label: "\u56fe\u50cf\u751f\u6210", description: "\u6839\u636e\u63d0\u793a\u8bcd\u521b\u4f5c\u89c6\u89c9\u5185\u5bb9" },
  { value: "IMAGE_EDIT", label: "\u56fe\u50cf\u7f16\u8f91", description: "\u57fa\u4e8e\u53c2\u8003\u56fe\u8fdb\u884c\u7f16\u8f91\u548c\u91cd\u7ed8" },
];
'@
Replace-Once $page '  viewAsset: "\u67e5\u770b\u56fe\u7247\u4ea7\u7269",' @'
  viewAsset: "\u67e5\u770b\u56fe\u7247\u4ea7\u7269",
  preferenceTitle: "\u6a21\u578b\u504f\u597d",
  preferenceHint: "\u9009\u62e9\u4f7f\u7528\u7684\u5de5\u5177\uff08\u53ef\u591a\u9009\uff09",
  preferenceAuto: "\u6062\u590d\u81ea\u52a8\u9009\u62e9",
  preferenceAria: "\u914d\u7f6e\u6a21\u578b\u5de5\u5177\u504f\u597d",
'@
Replace-Once $page '  const [mode, setMode] = useState<Mode>("react");' '  const [mode, setMode] = useState<Mode>("react");`r`n  const [preferredTools, setPreferredTools] = useState<PreferredTool[]>([]);'
Insert-Once $page '  }, []);' @'
  useEffect(() => {
    const stored = window.localStorage.getItem(toolPreferenceStorageKey);
    if (!stored) return;
    try {
      const parsed = JSON.parse(stored);
      if (Array.isArray(parsed)) {
        setPreferredTools(parsed.filter((value): value is PreferredTool => toolOptions.some((tool) => tool.value === value)));
      }
    } catch {
      window.localStorage.removeItem(toolPreferenceStorageKey);
    }
  }, []);
  useEffect(() => {
    window.localStorage.setItem(toolPreferenceStorageKey, JSON.stringify(preferredTools));
  }, [preferredTools]);
'@
Replace-Once $page 'body: JSON.stringify({ prompt: task, mode, imageUrls: upload ? [upload.imageUrl] : [] }),' 'body: JSON.stringify({ prompt: task, mode, imageUrls: upload ? [upload.imageUrl] : [], preferredTools }),' 

$toolButton = '<button type="button" className={styles.iconButton}><AppstoreOutlined /></button>'
$toolPopover = @'
<Popover
  trigger="click"
  placement="topLeft"
  overlayClassName={styles.preferencePopover}
  content={<div className={styles.preferencePanel}>
    <div className={styles.preferenceHead}><div><b>{copy.preferenceTitle}</b><p>{copy.preferenceHint}</p></div><span>{preferredTools.length || "AUTO"}</span></div>
    <Checkbox.Group value={preferredTools} onChange={(values) => setPreferredTools(values as PreferredTool[])} className={styles.preferenceChoices}>
      {toolOptions.map((tool) => <Checkbox value={tool.value} key={tool.value}><span><b>{tool.label}</b><small>{tool.description}</small></span></Checkbox>)}
    </Checkbox.Group>
    <button type="button" className={styles.resetPreference} onClick={() => setPreferredTools([])} disabled={preferredTools.length === 0}>{copy.preferenceAuto}</button>
  </div>}
>
  <button type="button" className={`${styles.iconButton} ${preferredTools.length ? styles.preferenceActive : ""}`} aria-label={copy.preferenceAria}>
    <AppstoreOutlined />{preferredTools.length > 0 && <span className={styles.preferenceCount}>{preferredTools.length}</span>}
  </button>
</Popover>
'@
Replace-Once $page $toolButton $toolPopover.Trim()

$cssBlock = @'

.preferencePopover { padding-bottom: 9px; }
.preferencePanel { width: 286px; padding: 2px; color: #1c2940; }
.preferenceHead { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 6px 6px 12px; border-bottom: 1px solid #edf1f7; }
.preferenceHead b { display: block; font-size: 14px; line-height: 1.3; }
.preferenceHead p { margin: 4px 0 0; color: #8390a4; font-size: 11px; line-height: 1.45; }
.preferenceHead > span { display: inline-flex; min-width: 35px; height: 22px; align-items: center; justify-content: center; padding: 0 6px; color: #397cf5; font-size: 10px; font-weight: 800; letter-spacing: .04em; background: #edf5ff; border-radius: 999px; }
.preferenceChoices { display: grid; gap: 6px; padding: 10px 2px 8px; }
.preferenceChoices :global(.ant-checkbox-wrapper) { display: flex; align-items: center; min-height: 48px; margin: 0; padding: 7px 8px; border: 1px solid transparent; border-radius: 9px; transition: background .18s ease, border-color .18s ease; }
.preferenceChoices :global(.ant-checkbox-wrapper:hover) { background: #f7faff; border-color: #deebff; }
.preferenceChoices :global(.ant-checkbox-wrapper > span:last-child) { flex: 1; padding-inline-start: 9px; }
.preferenceChoices span b, .preferenceChoices span small { display: block; }
.preferenceChoices span b { color: #26354c; font-size: 12px; line-height: 1.25; }
.preferenceChoices span small { margin-top: 3px; color: #8995a8; font-size: 10px; line-height: 1.25; }
.resetPreference { width: 100%; padding: 8px; color: #5b85d8; font-size: 12px; cursor: pointer; background: #f6f9ff; border: 0; border-radius: 7px; }
.resetPreference:disabled { color: #b5bfce; cursor: default; }
.iconButton { position: relative; }
.preferenceActive { color: #2563eb; background: #eff6ff; border-color: #b8d4ff; }
.preferenceCount { position: absolute; top: -6px; right: -6px; display: grid; min-width: 15px; height: 15px; place-items: center; padding: 0 3px; color: #fff; font-size: 9px; font-style: normal; font-weight: 800; line-height: 1; background: #3478ef; border: 2px solid #fff; border-radius: 99px; }
'@
$cssContent = [System.IO.File]::ReadAllText($css)
if ($cssContent.Contains('.preferencePanel')) { throw "Model preference CSS already exists in $css. No files were changed." }
[System.IO.File]::AppendAllText($css, $cssBlock, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $css"

Replace-Once $request '    private List<String> imageUrls = new ArrayList<>();' @'
    private List<String> imageUrls = new ArrayList<>();
    @Size(max = 3, message = "at most 3 preferred tools are supported")
    private List<String> preferredTools = new ArrayList<>();
'@

foreach ($javaPath in @($model, $dynamic)) {
    Insert-Once $javaPath 'import java.util.List;' "`r`nimport java.util.Locale;"
}

$modelNeedle = '    private List<AgentToolType> selectTools(AgentRunRequest request) {'
$modelPreference = @'

        List<AgentToolType> preferred = preferredTools(request);
        if (!preferred.isEmpty()) {
            return preferred;
        }
'@
Insert-Once $model $modelNeedle $modelPreference
$modelEndNeedle = '    private List<AgentToolResult> executeTools(List<AgentToolType> tools, AgentRunRequest request) {'
$modelHelper = @'
    private List<AgentToolType> preferredTools(AgentRunRequest request) {
        List<AgentToolType> selected = new ArrayList<>();
        if (request.getPreferredTools() == null) {
            return selected;
        }
        for (String value : request.getPreferredTools()) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                selected.add(AgentToolType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                log.warn("Ignoring unsupported preferred tool: {}", value);
            }
        }
        return selected.stream().distinct().toList();
    }

'@
Replace-Once $model $modelEndNeedle ($modelHelper + $modelEndNeedle)

$dynamicNeedle = '    private List<AgentToolType> selectInitialTools(AgentRunRequest request) {'
$dynamicPreference = @'

        List<AgentToolType> preferred = preferredTools(request);
        if (!preferred.isEmpty()) {
            return preferred;
        }
'@
Insert-Once $dynamic $dynamicNeedle $dynamicPreference
$dynamicEndNeedle = '    private boolean hasImageTool(List<AgentToolType> tools) {'
$dynamicHelper = @'
    private List<AgentToolType> preferredTools(AgentRunRequest request) {
        List<AgentToolType> selected = new ArrayList<>();
        if (request.getPreferredTools() == null) {
            return selected;
        }
        for (String value : request.getPreferredTools()) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                selected.add(AgentToolType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                log.warn("Ignoring unsupported preferred tool: {}", value);
            }
        }
        return selected.stream().distinct().toList();
    }

'@
Replace-Once $dynamic $dynamicEndNeedle ($dynamicHelper + $dynamicEndNeedle)

$readme = Join-Path $root 'README_JENDA_AGENT_STATUS_STEP41_MODEL_TOOL_PREFERENCES.md'
$readmeContent = @'
# Step 41: Agent Model Tool Preferences

## Goal

The second button in `/zh/agent` is now an interactive **Model Preference** control. It allows the user to select one or more real agent tools before submitting a task.

## User Flow

1. Open `/zh/agent`.
2. Click the grid button beside the reference-image upload button.
3. Select any combination of OCR, Image Generation, and Image Editing.
4. Submit the prompt. The selected values are sent as `preferredTools` with the SSE run request.
5. Click `Restore automatic selection` to clear the preference and resume prompt-based tool routing.

Preferences are saved in browser local storage under `jenda-agent-tool-preferences`.

## Architecture

```text
Ant Design X Sender footer
  -> Ant Design Popover + Checkbox.Group
  -> POST /api/v1/agent/sessions/{sessionId}/runs
       { prompt, mode, imageUrls, preferredTools }
  -> AgentRunRequest.preferredTools
  -> ModelToolAgentRunService (ReAct) or DynamicPlanSolveAgentRunService (Plan-Solve)
  -> AgentToolType execution list
```

## Supported Values

| Value | UI label | Runtime tool |
| --- | --- | --- |
| `OCR` | OCR text recognition | OCR adapter |
| `IMAGE_GENERATE` | Image generation | Qwen image generation gateway |
| `IMAGE_EDIT` | Image editing | Qwen image editing gateway |

## Routing Rule

- Empty `preferredTools`: retain the existing prompt/image based automatic routing.
- Non-empty `preferredTools`: execute only the validated selected tools, in both ReAct and Plan-Solve mode.
- Invalid values from external clients are ignored and logged; the request is not failed solely because of an unsupported preference.

## Verification

Run from `showcase`:

```powershell
corepack pnpm exec tsc --noEmit --skipLibCheck
corepack pnpm build
```

Run from `genie-backend`:

```powershell
mvn -DskipTests compile
```

## Next Improvements

- Persist run-level tool preferences in MySQL for exact historical replay.
- Add tool capability checks before dispatch, such as requiring an image for `IMAGE_EDIT`.
- Expose tool availability and provider health in the preference popover.
'@
[System.IO.File]::WriteAllText($readme, $readmeContent, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $readme"
Write-Host 'Step 41 model tool preferences completed.'
