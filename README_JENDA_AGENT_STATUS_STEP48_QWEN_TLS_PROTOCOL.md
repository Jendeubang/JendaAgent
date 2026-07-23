# Step 48: Qwen image-edit TLS protocol compatibility

## Observation

The Model Studio endpoint responded to a container `curl` request over TLS, but Java/OkHttp failed its TLS handshake when forced to HTTP/1.1. Before forcing HTTP/1.1, the endpoint was reachable over HTTP/2 but the client used the default 10-second read timeout.

## Resolution

- Keep the Step 47 10-minute connect/read/write/call timeout alignment.
- Remove the forced HTTP/1.1 setting for the Qwen Model Studio client and allow OkHttp's default protocol negotiation, including HTTP/2.

## Verification

Run an image-edit request. The request should pass TLS negotiation and remain active for the configured inference timeout rather than failing after the default read timeout.