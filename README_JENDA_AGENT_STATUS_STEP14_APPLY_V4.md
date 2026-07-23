# Step 14 Apply V4

V4 is required on this Windows checkout because the generated scripts use LF while the Java source files use CRLF. It normalizes only in-memory search fragments before running V3's all-or-nothing preflight.

```powershell
cd F:\JendaAgent\JendaAgent
powershell -ExecutionPolicy Bypass -File .\tools\apply-step14-tool-routing-v4.ps1
```

It changes six backend locations only after every match passes. It then prints `Step 14 backend changes completed`.
