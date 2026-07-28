# Step 72: Image Provider Transport Failover

## Purpose

Keep explicit browser model selection intact while allowing a configured default image provider to recover from transient upstream gateway failures.

## Runtime behavior

1. The browser may explicitly request `qwen`, `seedream`, or a Gemini provider.
2. The selected provider is called first.
3. Failover is attempted only when the selected provider reports a transient gateway failure: timeout, TLS handshake, connection, EOF, SSL, remote-host termination, or HTTP 5xx.
4. The configured `AGENT_IMAGE_PROVIDER_DEFAULT` is tried as the fallback when it differs from the selected provider.
5. Validation, authorization, model capability, and content-policy errors do not fail over, avoiding duplicate paid requests for deterministic failures.
6. A recovered result reports `Fallback from <provider> after transient gateway failure` in the tool event.

## Operations

- Set `AGENT_IMAGE_PROVIDER_DEFAULT=seedream` to recover a stale browser `qwen` preference through SeedDream.
- Configure the SeedDream endpoint, model, and API key in `deploy/.env`.
- A request with an explicit provider still takes precedence. The browser can select `Auto` to use the backend default immediately.

## Docker Flag Reliability

AGENT_GATEWAY_SEEDDREAM_ENABLED is also read directly from the container environment at runtime. This protects production deployments when configuration-property binding does not reflect the Compose environment value.

## SeedDream Configuration Resolution

SeedDreamImageModelProvider resolves AGENT_GATEWAY_SEEDDREAM_ENDPOINT, AGENT_GATEWAY_SEEDDREAM_MODEL, AGENT_GATEWAY_SEEDDREAM_API_KEY, AGENT_GATEWAY_SEEDDREAM_SIZE, and AGENT_GATEWAY_SEEDDREAM_WATERMARK directly from the Docker environment before falling back to Spring configuration properties.

## Validation

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn -q -DskipTests compile
```

## Remaining external dependency

Provider credentials, provider model access, and outbound TLS connectivity must be verified with a paid provider request. The application records the original provider failure and only fails over for retryable transport errors.