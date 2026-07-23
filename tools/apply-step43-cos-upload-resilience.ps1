$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root 'genie-backend\src\main\java\com\jd\genie\service\agent\CosAgentImageStorage.java'
$content = [System.IO.File]::ReadAllText($path)

function Replace-Once([string]$Old, [string]$New) {
  $count = ([regex]::Matches($script:content, [regex]::Escape($Old))).Count
  if ($count -ne 1) { throw "Expected exactly one matching block in $path, found $count. No file was changed." }
  $script:content = $script:content.Replace($Old, $New)
}

Replace-Once 'import org.springframework.stereotype.Service;' "import org.springframework.stereotype.Service;`r`nimport lombok.extern.slf4j.Slf4j;"
Replace-Once 'import java.time.Instant;' "import java.time.Duration;`r`nimport java.time.Instant;"
Replace-Once '@Service`r`n@ConditionalOnProperty' '@Service`r`n@Slf4j`r`n@ConditionalOnProperty'
Replace-Once '    private final OkHttpClient client = new OkHttpClient();' @'
    private static final int MAX_UPLOAD_ATTEMPTS = 3;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .callTimeout(Duration.ofSeconds(90))
            .retryOnConnectionFailure(true)
            .build();
'@

$pattern = '(?s)    private void putObject\(String objectKey, String contentType, byte\[\] bytes\) \{.*?(?=    private String authorization\()'
$count = ([regex]::Matches($content, $pattern)).Count
if ($count -ne 1) { throw "Expected exactly one COS putObject method in $path, found $count. No file was changed." }
$replacement = @'
    private void putObject(String objectKey, String contentType, byte[] bytes) {
        URI uri = URI.create(baseUrl + "/" + encodeObjectKey(objectKey));
        String host = uri.getHost();
        for (int attempt = 1; attempt <= MAX_UPLOAD_ATTEMPTS; attempt++) {
            String authorization = authorization("PUT", "/" + objectKey, host, contentType);
            Request request = new Request.Builder()
                    .url(uri.toString())
                    .header("Host", host)
                    .header("Content-Type", contentType)
                    .header("Authorization", authorization)
                    .put(RequestBody.create(bytes, MediaType.get(contentType)))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return;
                }
                String body = response.body() == null ? "" : response.body().string();
                if (response.code() >= 500 && attempt < MAX_UPLOAD_ATTEMPTS) {
                    log.warn("COS upload returned HTTP {} on attempt {}/{}; retrying", response.code(), attempt, MAX_UPLOAD_ATTEMPTS);
                    pauseBeforeRetry(attempt);
                    continue;
                }
                throw new IllegalStateException("COS upload failed with HTTP " + response.code() + ": " + concise(body));
            } catch (IOException error) {
                if (attempt == MAX_UPLOAD_ATTEMPTS) {
                    throw new IllegalStateException("COS upload request failed after " + MAX_UPLOAD_ATTEMPTS + " attempts: " + error.getMessage(), error);
                }
                log.warn("COS upload attempt {}/{} failed: {}; retrying", attempt, MAX_UPLOAD_ATTEMPTS, error.getMessage());
                pauseBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException("COS upload failed without a response");
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(400L * attempt);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("COS upload retry was interrupted", error);
        }
    }

'@
$content = [regex]::Replace($content, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $replacement })
[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $path"

$readme = Join-Path $root 'README_JENDA_AGENT_STATUS_STEP43_COS_UPLOAD_RESILIENCE.md'
$readmeContent = @'
# Step 43: COS Upload Resilience and CORS

## Backend Resilience

`CosAgentImageStorage` now uses explicit OkHttp connect/read/write/call timeouts and retries transient COS network and 5xx failures up to three times. Non-retryable 4xx responses still fail immediately so configuration errors remain visible.

## Required COS CORS Origins

The deployed Docker site runs at `http://localhost`, not `http://localhost:3000`. In Tencent COS console, edit the bucket CORS rule and include both origins:

```text
http://localhost
http://localhost:3000
```

Set methods to `PUT, GET, HEAD`, allow headers `*`, and expose `ETag`. The browser needs `PUT` CORS permission for STS direct upload.

## Upload Flow

1. Browser asks backend for STS credentials.
2. Browser uploads directly to COS.
3. Backend verifies the object and records the asset metadata.
4. If direct upload fails, backend uploads the file to COS with three retry attempts.

## Diagnostics

```powershell
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml logs --tail 120 backend
```

Look for `COS upload attempt` warnings for a transient failure, or a specific HTTP status for a credentials/policy/CORS independent server-side issue.
'@
[System.IO.File]::WriteAllText($readme, $readmeContent, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $readme"
