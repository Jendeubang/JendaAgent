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