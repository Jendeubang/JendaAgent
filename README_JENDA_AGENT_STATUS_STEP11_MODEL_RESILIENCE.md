# Step 11 Model Timeout Resilience

## Problem

An external DashScope call can exceed the runtime model `callTimeout`, notably
on a first visual request that downloads a signed COS image. Before this step,
the SocketTimeoutException propagated through the SSE request and terminated
the agent run.

## Behavior After This Step

`ResilientOpenAiCompatibleChatClient` catches only timeout failures. It returns
a skipped ModelCompletion, allowing ModelToolAgentRunService to emit the
existing deterministic plan/summary fallback and continue eligible OCR/image
tools. A two-minute local cooldown avoids repeating a second slow model request
for the SummaryAgent in the same outage window.

HTTP 401, 403, and 4xx/5xx provider responses are intentionally not swallowed:
they indicate a configuration or provider error that should be diagnosed.

## Runtime Configuration

For a visual Qwen model, set this before starting Spring Boot:

```powershell
$env:AGENT_RUNTIME_MODEL_TIMEOUT="180s"
```

Run a text-only request first to validate the API key and model endpoint. Then
test a COS image request. If the visual request still times out at 180 seconds,
check provider availability, model entitlement, and whether the COS signed URL
can be fetched outside the local browser.

## Technical Debt

The replacement uses a BeanPostProcessor because the workspace currently does
not allow edits to existing source files. When that restriction is resolved,
fold the resilience behavior into OpenAiCompatibleChatClient and remove the
replacement class.
