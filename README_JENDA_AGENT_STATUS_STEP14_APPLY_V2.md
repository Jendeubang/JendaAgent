# Step 14 Apply V2

Use only the V2 script. It validates all seven target blocks before writing any source file.

```powershell
cd F:\JendaAgent\JendaAgent
powershell -ExecutionPolicy Bypass -File .\tools\apply-step14-tool-routing-v2.ps1
```

When it reports `Step 14 completed`, return to this task. The backend then needs a Maven compile and restart so the new `agent_tool_call_message` table is created.

Do not use `patches/step14-tool-routing-and-tool-call.patch` or `tools/apply-step14-tool-routing.ps1`; they are superseded by this V2 procedure.
