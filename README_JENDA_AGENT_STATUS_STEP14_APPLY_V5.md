# Step 14 Apply V5

V5 handles both Windows source line endings and the PowerShell script-block scope used by the guarded procedure.

```powershell
cd F:\JendaAgent\JendaAgent
powershell -ExecutionPolicy Bypass -File .\tools\apply-step14-tool-routing-v5.ps1
```

No source file is modified until all six backend search blocks have been checked exactly once.
