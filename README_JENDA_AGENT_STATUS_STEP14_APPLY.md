# Step 14 Apply Instructions

The Codex Windows sandbox could not update existing source files in this workspace, even after both applications were stopped. The source change is packaged as a guarded local script:

```powershell
cd F:\JendaAgent\JendaAgent
powershell -ExecutionPolicy Bypass -File .\tools\apply-step14-tool-routing.ps1
```

The script checks every original block occurs exactly once before replacing it. If a source file has changed, it stops rather than making a partial or ambiguous change.

After it succeeds, compile with the project Maven cache and restart the backend. See `README_JENDA_AGENT_STATUS_STEP14_TOOL_ROUTING.md` for expected behaviour.
