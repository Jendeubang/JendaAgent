# Step 31: Authentication Security Closure

## Delivered

- Short-lived access JWTs have a unique `jti` and server-side revocation table.
- Opaque Refresh Tokens are SHA-256 hashed in MySQL, rotated on every refresh,
  and revoked on logout or password reset.
- Login failures are limited by both normalized username and client IP. The
  default is five failures in a 15 minute rolling lock window.
- SMS registration and password reset use one-time six digit codes, five minute
  expiry, five verification attempts, and a 60 second resend cooldown.
- Tencent SMS uses the official TC3-HMAC-SHA256 HTTPS protocol. Development mode
  never calls Tencent and exposes the code only in the local API response.

## Backend API

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/sms/code`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/password/reset`

## Environment

```powershell
$env:AGENT_AUTH_TOKEN_TTL="15m"
$env:AGENT_AUTH_REFRESH_TOKEN_TTL="30d"
$env:AGENT_AUTH_LOGIN_MAX_FAILURES="5"
$env:AGENT_AUTH_LOGIN_FAILURE_WINDOW="15m"
$env:AGENT_AUTH_SMS_CODE_TTL="5m"
$env:AGENT_AUTH_SMS_ENABLED="false"
$env:AGENT_AUTH_SMS_DEVELOPMENT_MODE="true"
```

For Tencent production SMS, set `SMS_ENABLED=true` and
`SMS_DEVELOPMENT_MODE=false`, then configure `SMS_SECRET_ID`, `SMS_SECRET_KEY`,
`SMS_SDK_APP_ID`, `SMS_SIGN_NAME`, `SMS_TEMPLATE_ID`, and `SMS_REGION` under
the `AGENT_AUTH_` prefix. The template must accept two parameters: code and
validity minutes.

## Production Follow-ups

- Replace the MySQL limiter with Redis for multi-instance deployment.
- Put Refresh Token in a Secure, HttpOnly, SameSite cookie behind HTTPS.
- Add a CAPTCHA before SMS issue and login when public traffic is enabled.
