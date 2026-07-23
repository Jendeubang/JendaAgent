# Step 23 Encoding Recovery

## Incident

Windows PowerShell decoded a UTF-8 Java file through the system ANSI code page during a source rewrite. Chinese string literals and adjacent quote characters were corrupted, causing Java compilation errors.

## Recovery

`tools/restore-step23-dynamic-plan-solve-utf8.ps1` replaces the dynamic Plan-Solve source with an ASCII-safe UTF-8 equivalent. Chinese intent keywords use Java Unicode escape sequences, so Windows PowerShell cannot corrupt them on future writes.

## Preserved Behavior

- Three-round Plan-Solve loop and shared memory
- SSE plan, task, tool-call, tool-result, image, summary, and completion events
- History persistence calls
- Generation-first routing
- Explicit OCR routing
- Image editing only when a reference image is attached
