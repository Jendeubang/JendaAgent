# Step 71: Login Rate Limiter MySQL Compatibility

## Problem

Some MySQL JDBC driver versions can throw a parameter-metadata null pointer exception when `JdbcTemplate` binds timestamp values while recording a failed login. This incorrectly turned an invalid credential response into HTTP 500.

## Fix

- The login guard now uses `CURRENT_TIMESTAMP` and `TIMESTAMPADD` inside SQL.
- Java timestamp objects are no longer bound as parameters by the failed-login path.
- Redis remains the primary short-lived limiter; the MySQL guard remains the persistent fallback.

## Expected Behavior

- Invalid credentials return HTTP 401.
- Invalid captcha returns HTTP 400.
- Rate-limited attempts return HTTP 429.
- Valid credentials return HTTP 200.
