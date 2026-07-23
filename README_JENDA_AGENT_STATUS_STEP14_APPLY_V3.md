# Step 14 Apply V3

Use the V3 script only. It contains the six backend replacements and validates every target before modifying anything. The front end already renders unknown event types and will show `tool_call`; its display label can be refined independently.

```powershell
cd F:\JendaAgent\JendaAgent
powershell -ExecutionPolicy Bypass -File .\tools\apply-step14-tool-routing-v3.ps1
```

After success, report the command output in this task for build verification and restart instructions.
