$ErrorActionPreference = "Stop"

# Full ASCII-safe rewrites for existing authentication files. The script checks all
# source files before changing any of them, avoiding partial updates on Windows.
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "genie-backend"
$showcase = Join-Path $root "showcase"
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Write-Source([string]$relative, [string]$content) {
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path)) { throw "Expected source is missing: $path" }
    [System.IO.File]::WriteAllText($path, $content, $utf8)
    Write-Host "Updated $path"
}

$targets = @(
    "genie-backend/src/main/java/com/jd/genie/config/AgentAuthProperties.java",
    "genie-backend/src/main/java/com/jd/genie/service/auth/AgentJwtTokenService.java",
    "genie-backend/src/main/java/com/jd/genie/service/auth/AgentUserStore.java",
    "genie-backend/src/main/java/com/jd/genie/controller/AgentAuthController.java",
    "genie-backend/src/main/java/com/jd/genie/model/auth/AgentAuthResponse.java",
    "showcase/lib/agentAuth.ts",
    "showcase/components/AgentAccountMenu.tsx",
    "showcase/app/login/page.tsx"
)
foreach ($target in $targets) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $target))) { throw "Expected source is missing: $target" }
}

Write-Source "genie-backend/src/main/java/com/jd/genie/config/AgentAuthProperties.java" @'
package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Product authentication, session and Tencent SMS configuration. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.auth")
public class AgentAuthProperties {
    private boolean enabled;
    private String jwtSecret;
    private String issuer = "jenda-agent";
    private Duration tokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private int loginMaxFailures = 5;
    private Duration loginFailureWindow = Duration.ofMinutes(15);
    private Duration smsCodeTtl = Duration.ofMinutes(5);
    private boolean smsEnabled;
    private boolean smsDevelopmentMode = true;
    private String smsSecretId;
    private String smsSecretKey;
    private String smsSdkAppId;
    private String smsSignName;
    private String smsTemplateId;
    private String smsRegion = "ap-guangzhou";
}
'@

Write-Source "genie-backend/src/main/java/com/jd/genie/service/auth/AgentJwtTokenService.java" @'
package com.jd.genie.service.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentAuthProperties;
import com.jd.genie.model.auth.AgentPrincipal;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Issues short-lived, revocable HS256 access JWTs. */
@Service
public class AgentJwtTokenService {
    private final ObjectMapper objectMapper;
    private final AgentAuthProperties properties;
    private final AgentTokenStore tokenStore;

    public AgentJwtTokenService(ObjectMapper objectMapper, AgentAuthProperties properties, AgentTokenStore tokenStore) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tokenStore = tokenStore;
    }

    public String issue(AgentPrincipal principal) {
        requireSecret();
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + properties.getTokenTtl().toSeconds();
        try {
            String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", properties.getIssuer());
            claims.put("sub", principal.userId());
            claims.put("username", principal.username());
            claims.put("typ", "access");
            claims.put("jti", UUID.randomUUID().toString());
            claims.put("iat", now);
            claims.put("exp", expiresAt);
            String payload = encodeJson(claims);
            String unsigned = header + "." + payload;
            return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(unsigned));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to issue agent access token", error);
        }
    }

    public AgentPrincipal verify(String token) {
        return parse(token, true).principal();
    }

    public void revoke(String token) {
        try {
            VerifiedAccessToken parsed = parse(token, false);
            if (parsed.expiresAt().isAfter(Instant.now())) tokenStore.revokeAccess(parsed.tokenId(), parsed.expiresAt());
        } catch (RuntimeException ignored) {
            // Logout is idempotent. Invalid or expired access tokens need no server action.
        }
    }

    public long expiresInSeconds() {
        return properties.getTokenTtl().toSeconds();
    }

    private VerifiedAccessToken parse(String token, boolean checkRevocation) {
        requireSecret();
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", 3);
            if (parts.length != 3) throw new IllegalArgumentException("Malformed bearer token");
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) throw new IllegalArgumentException("Invalid bearer token signature");
            Map<String, Object> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() { });
            if (!properties.getIssuer().equals(claims.get("iss")) || !"access".equals(claims.get("typ"))) throw new IllegalArgumentException("Unexpected bearer token");
            long expiresAt = ((Number) claims.get("exp")).longValue();
            if (expiresAt <= Instant.now().getEpochSecond()) throw new IllegalArgumentException("Bearer token expired");
            String userId = String.valueOf(claims.get("sub"));
            String username = String.valueOf(claims.get("username"));
            String tokenId = String.valueOf(claims.get("jti"));
            if (userId.isBlank() || username.isBlank() || tokenId.isBlank()) throw new IllegalArgumentException("Bearer token subject missing");
            if (checkRevocation && tokenStore.isAccessRevoked(tokenId)) throw new IllegalArgumentException("Bearer token was revoked");
            return new VerifiedAccessToken(new AgentPrincipal(userId, username), tokenId, Instant.ofEpochSecond(expiresAt));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid bearer token", error);
        }
    }

    private String encodeJson(Map<String, ?> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private byte[] hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private void requireSecret() {
        if (properties.getJwtSecret() == null || properties.getJwtSecret().length() < 32) throw new IllegalStateException("agent.auth.jwt-secret must contain at least 32 characters when JWT auth is enabled");
    }

    private record VerifiedAccessToken(AgentPrincipal principal, String tokenId, Instant expiresAt) {
    }
}
'@

Write-Source "genie-backend/src/main/java/com/jd/genie/service/auth/AgentUserStore.java" @'
package com.jd.genie.service.auth;

import com.jd.genie.model.auth.AgentPrincipal;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Isolated product user table, with a unique mobile number for SMS verification. */
@Repository
public class AgentUserStore {
    private final JdbcTemplate jdbcTemplate;

    public AgentUserStore(Environment environment) {
        String url = environment.getProperty("agent.history.jdbc-url", "jdbc:h2:file:./runtime/agent-history;MODE=MySQL");
        String username = environment.getProperty("agent.history.username", "sa");
        String password = environment.getProperty("agent.history.password", "");
        this.jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agent_user (user_id VARCHAR(64) PRIMARY KEY, username VARCHAR(32) NOT NULL UNIQUE, password_hash VARCHAR(512) NOT NULL, phone VARCHAR(32) NULL, created_at TIMESTAMP NOT NULL)");
        try { jdbcTemplate.execute("ALTER TABLE agent_user ADD COLUMN phone VARCHAR(32) NULL"); } catch (RuntimeException ignored) { }
        try { jdbcTemplate.execute("CREATE UNIQUE INDEX idx_agent_user_phone ON agent_user(phone)"); } catch (RuntimeException ignored) { }
    }

    public AgentPrincipal create(String username, String phone, String passwordHash) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedPhone = normalizePhone(phone);
        if (findByUsername(normalizedUsername) != null) throw new IllegalArgumentException("Username is already registered");
        if (findByPhone(normalizedPhone) != null) throw new IllegalArgumentException("Phone is already registered");
        AgentPrincipal principal = new AgentPrincipal(UUID.randomUUID().toString(), normalizedUsername);
        jdbcTemplate.update("INSERT INTO agent_user (user_id, username, password_hash, phone, created_at) VALUES (?, ?, ?, ?, ?)", principal.userId(), principal.username(), passwordHash, normalizedPhone, Timestamp.from(Instant.now()));
        return principal;
    }

    public StoredUser findByUsername(String username) {
        List<StoredUser> users = jdbcTemplate.query("SELECT user_id, username, password_hash, phone FROM agent_user WHERE username = ?", (resultSet, rowNum) -> new StoredUser(new AgentPrincipal(resultSet.getString("user_id"), resultSet.getString("username")), resultSet.getString("password_hash"), resultSet.getString("phone")), normalizeUsername(username));
        return users.isEmpty() ? null : users.get(0);
    }

    public StoredUser findByPhone(String phone) {
        List<StoredUser> users = jdbcTemplate.query("SELECT user_id, username, password_hash, phone FROM agent_user WHERE phone = ?", (resultSet, rowNum) -> new StoredUser(new AgentPrincipal(resultSet.getString("user_id"), resultSet.getString("username")), resultSet.getString("password_hash"), resultSet.getString("phone")), normalizePhone(phone));
        return users.isEmpty() ? null : users.get(0);
    }

    public void updatePassword(String userId, String passwordHash) {
        jdbcTemplate.update("UPDATE agent_user SET password_hash = ? WHERE user_id = ?", passwordHash, userId);
    }

    public String normalizeUsername(String username) { return username.strip().toLowerCase(java.util.Locale.ROOT); }
    private String normalizePhone(String phone) { return phone == null ? null : phone.strip(); }

    public record StoredUser(AgentPrincipal principal, String passwordHash, String phone) {
    }
}
'@

Write-Source "genie-backend/src/main/java/com/jd/genie/model/auth/AgentAuthResponse.java" @'
package com.jd.genie.model.auth;

public record AgentAuthResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds,
                                long refreshExpiresInSeconds, String userId, String username) {
}
'@

Write-Source "genie-backend/src/main/java/com/jd/genie/controller/AgentAuthController.java" @'
package com.jd.genie.controller;

import com.jd.genie.config.AgentAuthProperties;
import com.jd.genie.model.auth.AgentAuthRequest;
import com.jd.genie.model.auth.AgentAuthResponse;
import com.jd.genie.model.auth.AgentLogoutRequest;
import com.jd.genie.model.auth.AgentPasswordResetRequest;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.model.auth.AgentRefreshRequest;
import com.jd.genie.model.auth.AgentSmsCodeRequest;
import com.jd.genie.model.auth.AgentSmsCodeResponse;
import com.jd.genie.model.auth.AgentSmsPurpose;
import com.jd.genie.model.auth.AgentSmsRegisterRequest;
import com.jd.genie.service.auth.AgentJwtTokenService;
import com.jd.genie.service.auth.AgentLoginRateLimiter;
import com.jd.genie.service.auth.AgentPasswordCodec;
import com.jd.genie.service.auth.AgentSmsCodeService;
import com.jd.genie.service.auth.AgentTokenStore;
import com.jd.genie.service.auth.AgentUserStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/** Password, SMS verification, token rotation and logout endpoints for product users. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class AgentAuthController {
    private final AgentUserStore userStore;
    private final AgentPasswordCodec passwordCodec;
    private final AgentJwtTokenService tokenService;
    private final AgentTokenStore tokenStore;
    private final AgentLoginRateLimiter loginRateLimiter;
    private final AgentSmsCodeService smsCodeService;
    private final AgentAuthProperties properties;

    @PostMapping("/sms/code")
    public AgentSmsCodeResponse sendCode(@Valid @RequestBody AgentSmsCodeRequest request) {
        if (request.purpose() == AgentSmsPurpose.REGISTER && userStore.findByPhone(request.phone()) != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone is already registered");
        AgentSmsCodeService.IssuedCode issued = smsCodeService.issue(request.phone(), request.purpose());
        return new AgentSmsCodeResponse(true, Math.max(1, issued.expiresAt().minusSeconds(Instant.now().getEpochSecond()).getEpochSecond()), issued.debugCode());
    }

    @PostMapping("/register")
    public AgentAuthResponse register(@Valid @RequestBody AgentSmsRegisterRequest request) {
        try {
            smsCodeService.verifyAndConsume(request.phone(), AgentSmsPurpose.REGISTER, request.code());
            AgentPrincipal principal = userStore.create(request.username(), request.phone(), passwordCodec.encode(request.password()));
            return response(principal);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        }
    }

    @PostMapping("/login")
    public AgentAuthResponse login(@Valid @RequestBody AgentAuthRequest request, HttpServletRequest servletRequest) {
        String username = userStore.normalizeUsername(request.username());
        String clientIp = clientIp(servletRequest);
        try {
            loginRateLimiter.assertAllowed(username, clientIp);
        } catch (AgentLoginRateLimiter.LoginRateLimitException error) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, error.getMessage());
        }
        AgentUserStore.StoredUser user = userStore.findByUsername(username);
        if (user == null || !passwordCodec.matches(request.password(), user.passwordHash())) {
            loginRateLimiter.recordFailure(username, clientIp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        loginRateLimiter.reset(username, clientIp);
        return response(user.principal());
    }

    @PostMapping("/refresh")
    public AgentAuthResponse refresh(@Valid @RequestBody AgentRefreshRequest request) {
        try {
            return response(tokenStore.consume(request.refreshToken()));
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody(required = false) AgentLogoutRequest request) {
        if (authorization != null && authorization.startsWith("Bearer ")) tokenService.revoke(authorization.substring(7));
        if (request != null) tokenStore.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    public AgentAuthResponse resetPassword(@Valid @RequestBody AgentPasswordResetRequest request) {
        try {
            smsCodeService.verifyAndConsume(request.phone(), AgentSmsPurpose.RESET_PASSWORD, request.code());
            AgentUserStore.StoredUser user = userStore.findByPhone(request.phone());
            if (user == null) throw new IllegalArgumentException("Verification code is invalid or expired");
            userStore.updatePassword(user.principal().userId(), passwordCodec.encode(request.password()));
            tokenStore.revokeAllForUser(user.principal().userId());
            return response(user.principal());
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        }
    }

    private AgentAuthResponse response(AgentPrincipal principal) {
        Instant refreshExpiry = Instant.now().plus(properties.getRefreshTokenTtl());
        AgentTokenStore.IssuedRefreshToken refresh = tokenStore.issue(principal, refreshExpiry);
        return new AgentAuthResponse(tokenService.issue(principal), refresh.rawToken(), "Bearer", tokenService.expiresInSeconds(), properties.getRefreshTokenTtl().toSeconds(), principal.userId(), principal.username());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
'@

Write-Source "showcase/lib/agentAuth.ts" @'
export type AgentAuthSession = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  refreshExpiresInSeconds: number;
  userId: string;
  username: string;
};

const sessionKey = "jenda-agent-auth-session";
const userScopedKeys = ["jenda-agent-current-session-id", "jenda-image-studio-session"];
let refreshInFlight: Promise<boolean> | undefined;

export function readAgentAuthSession(): AgentAuthSession | undefined {
  if (typeof window === "undefined") return undefined;
  try {
    const value = window.localStorage.getItem(sessionKey);
    return value ? JSON.parse(value) as AgentAuthSession : undefined;
  } catch { return undefined; }
}

export function saveAgentAuthSession(session: AgentAuthSession) { window.localStorage.setItem(sessionKey, JSON.stringify(session)); }

export function clearAgentAuthSession() {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(sessionKey);
  userScopedKeys.forEach((key) => window.localStorage.removeItem(key));
}

export function agentHeaders(headers?: HeadersInit) {
  const result = new Headers(headers);
  const session = readAgentAuthSession();
  if (session?.accessToken) result.set("Authorization", `Bearer ${session.accessToken}`);
  return result;
}

function apiBaseUrl() { return process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080"; }

async function refreshSession(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    const session = readAgentAuthSession();
    if (!session?.refreshToken) return false;
    try {
      const response = await fetch(`${apiBaseUrl()}/api/v1/auth/refresh`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refreshToken: session.refreshToken }) });
      if (!response.ok) return false;
      saveAgentAuthSession(await response.json() as AgentAuthSession);
      return true;
    } catch { return false; }
    finally { refreshInFlight = undefined; }
  })();
  return refreshInFlight;
}

function redirectForExpiredToken() {
  if (typeof window === "undefined" || window.location.pathname === "/login") return;
  clearAgentAuthSession();
  window.location.replace("/login?reason=expired");
}

/** Authenticated JendaAgent request with one safe refresh-and-retry on a 401 response. */
export async function agentFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  let response = await fetch(input, { ...init, headers: agentHeaders(init.headers) });
  if (response.status !== 401) return response;
  if (await refreshSession()) {
    response = await fetch(input, { ...init, headers: agentHeaders(init.headers) });
    if (response.status !== 401) return response;
  }
  redirectForExpiredToken();
  return response;
}

export async function logoutAgentSession() {
  const session = readAgentAuthSession();
  try {
    await fetch(`${apiBaseUrl()}/api/v1/auth/logout`, { method: "POST", headers: agentHeaders({ "Content-Type": "application/json" }), body: JSON.stringify({ refreshToken: session?.refreshToken }) });
  } finally { clearAgentAuthSession(); }
}
'@

Write-Source "showcase/components/AgentAccountMenu.tsx" @'
"use client";

import { AppstoreOutlined, LoginOutlined, LogoutOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Dropdown } from "antd";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { logoutAgentSession, readAgentAuthSession, type AgentAuthSession } from "../lib/agentAuth";

export function AgentAccountMenu() {
  const router = useRouter();
  const [session, setSession] = useState<AgentAuthSession>();
  useEffect(() => setSession(readAgentAuthSession()), []);
  if (!session) return <Button icon={<LoginOutlined />} onClick={() => router.push("/login")}>{"\u767b\u5f55"}</Button>;
  return <Dropdown menu={{ items: [
    { key: "identity", disabled: true, label: `\u8d26\u53f7\uff1a${session.username}` },
    { type: "divider" },
    { key: "assets", icon: <AppstoreOutlined />, label: "\u8d44\u4ea7\u4e2d\u5fc3", onClick: () => router.push("/assets") },
    { key: "logout", icon: <LogoutOutlined />, label: "\u9000\u51fa\u767b\u5f55", onClick: () => { void logoutAgentSession().finally(() => router.replace("/login")); } },
  ] }} trigger={["click"]}>
    <Button icon={<UserOutlined />}>{session.username}</Button>
  </Dropdown>;
}
'@

Write-Source "showcase/app/login/page.tsx" @'
"use client";

import { LockOutlined, MobileOutlined, SafetyCertificateOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Input, Tabs, message } from "antd";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { saveAgentAuthSession, type AgentAuthSession } from "../../lib/agentAuth";
import styles from "./page.module.css";

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
type Mode = "login" | "register" | "reset";

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);

  const sendCode = async () => {
    setSendingCode(true);
    try {
      const purpose = mode === "register" ? "REGISTER" : "RESET_PASSWORD";
      const response = await fetch(`${apiBaseUrl}/api/v1/auth/sms/code`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ phone, purpose }) });
      const payload = await response.json().catch(() => undefined) as { debugCode?: string; message?: string } | undefined;
      if (!response.ok) throw new Error(payload?.message || `SMS request failed: ${response.status}`);
      message.success(payload?.debugCode ? `\u5f00\u53d1\u9a8c\u8bc1\u7801\uff1a${payload.debugCode}` : "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001");
    } catch (error) { message.error(error instanceof Error ? error.message : "\u9a8c\u8bc1\u7801\u53d1\u9001\u5931\u8d25"); }
    finally { setSendingCode(false); }
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoading(true);
    try {
      const endpoint = mode === "login" ? "login" : mode === "register" ? "register" : "password/reset";
      const body = mode === "login" ? { username, password } : mode === "register" ? { username, password, phone, code } : { phone, password, code };
      const response = await fetch(`${apiBaseUrl}/api/v1/auth/${endpoint}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
      const payload = await response.json().catch(() => undefined) as AgentAuthSession & { message?: string };
      if (!response.ok) throw new Error(payload?.message || `Request failed: ${response.status}`);
      saveAgentAuthSession(payload);
      message.success(mode === "login" ? "\u767b\u5f55\u6210\u529f" : mode === "register" ? "\u6ce8\u518c\u6210\u529f" : "\u5bc6\u7801\u5df2\u91cd\u7f6e");
      router.replace("/agent-studio");
    } catch (error) { message.error(error instanceof Error ? error.message : "\u64cd\u4f5c\u5931\u8d25"); }
    finally { setLoading(false); }
  };

  const requiresCode = mode !== "login";
  return <main className={styles.page}><section className={styles.card}>
    <p>JENDA AGENT / ACCOUNT</p><h1>{"\u8fdb\u5165\u4f60\u7684"}<br /><em>{"\u79c1\u4eba\u5de5\u4f5c\u533a"}</em></h1>
    <span>{"\u77ed\u671f Access Token + \u53ef\u64a4\u9500 Refresh Token\uff0c\u767b\u51fa\u540e\u7acb\u5373\u5931\u6548\u3002"}</span>
    <Tabs activeKey={mode} onChange={(value) => setMode(value as Mode)} items={[{ key: "login", label: "\u767b\u5f55" }, { key: "register", label: "\u77ed\u4fe1\u6ce8\u518c" }, { key: "reset", label: "\u627e\u56de\u5bc6\u7801" }]} />
    <form onSubmit={(event) => void submit(event)}>
      {mode !== "reset" && <label>{"\u8d26\u53f7"}<Input prefix={<UserOutlined />} value={username} onChange={(event) => setUsername(event.target.value)} placeholder="3-32 \u4f4d\u5b57\u6bcd\u3001\u6570\u5b57\u3001_ \u6216 -" /></label>}
      {requiresCode && <label>{"\u624b\u673a\u53f7"}<Input prefix={<MobileOutlined />} value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="11 \u4f4d\u4e2d\u56fd\u5927\u9646\u624b\u673a\u53f7" /></label>}
      <label>{"\u5bc6\u7801"}<Input.Password prefix={<LockOutlined />} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="\u81f3\u5c11 8 \u4f4d" /></label>
      {requiresCode && <label>{"\u77ed\u4fe1\u9a8c\u8bc1\u7801"}<div className={styles.codeLine}><Input prefix={<SafetyCertificateOutlined />} value={code} onChange={(event) => setCode(event.target.value)} placeholder="6 \u4f4d\u9a8c\u8bc1\u7801" /><Button htmlType="button" loading={sendingCode} onClick={() => void sendCode()}>{"\u53d1\u9001\u9a8c\u8bc1\u7801"}</Button></div></label>}
      <Button type="primary" htmlType="submit" loading={loading} block>{mode === "login" ? "\u767b\u5f55" : mode === "register" ? "\u9a8c\u8bc1\u5e76\u6ce8\u518c" : "\u91cd\u7f6e\u5bc6\u7801"}</Button>
    </form>
  </section></main>;
}
'@

Write-Host "Step 31 authentication security closure source changes completed."
