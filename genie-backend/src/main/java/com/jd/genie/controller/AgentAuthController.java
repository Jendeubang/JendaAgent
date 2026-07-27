package com.jd.genie.controller;

import com.jd.genie.config.AgentAuthProperties;
import com.jd.genie.model.auth.AgentAuthRequest;
import com.jd.genie.model.auth.AgentAuthResponse;
import com.jd.genie.model.auth.AgentCaptchaResponse;
import com.jd.genie.model.auth.AgentLogoutRequest;
import com.jd.genie.model.auth.AgentPasswordResetRequest;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.model.auth.AgentRefreshRequest;
import com.jd.genie.model.auth.AgentSmsCodeRequest;
import com.jd.genie.model.auth.AgentSmsCodeResponse;
import com.jd.genie.model.auth.AgentSmsPurpose;
import com.jd.genie.model.auth.AgentSmsRegisterRequest;
import com.jd.genie.service.auth.AgentCaptchaService;
import com.jd.genie.service.auth.AgentJwtTokenService;
import com.jd.genie.service.auth.AgentLoginRateLimiter;
import com.jd.genie.service.auth.AgentPasswordCodec;
import com.jd.genie.service.auth.AgentRefreshCookieService;
import com.jd.genie.service.auth.AgentSmsCodeService;
import com.jd.genie.service.auth.AgentTokenStore;
import com.jd.genie.service.auth.AgentUserStore;
import com.jd.genie.service.security.AgentAuditLogService;
import com.jd.genie.service.security.AgentBillingService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/** Password, SMS verification, cookie rotation, CAPTCHA and logout endpoints for product users. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class AgentAuthController {
    private final AgentUserStore userStore;
    private final AgentPasswordCodec passwordCodec;
    private final AgentJwtTokenService tokenService;
    private final AgentTokenStore tokenStore;
    private final AgentLoginRateLimiter loginRateLimiter;
    private final AgentSmsCodeService smsCodeService;
    private final AgentCaptchaService captchaService;
    private final AgentRefreshCookieService refreshCookieService;
    private final AgentAuditLogService auditLogService;
    private final AgentBillingService billingService;
    private final AgentAuthProperties properties;

    @GetMapping("/captcha")
    public AgentCaptchaResponse captcha() { return captchaService.create(); }

    @PostMapping("/sms/code")
    public AgentSmsCodeResponse sendCode(@Valid @RequestBody AgentSmsCodeRequest request, HttpServletRequest servletRequest) {
        try {
            captchaService.verify(request.captchaId(), request.captchaAnswer());
            if (request.purpose() == AgentSmsPurpose.REGISTER && userStore.findByPhone(request.phone()) != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone is already registered");
            AgentSmsCodeService.IssuedCode issued = smsCodeService.issue(request.phone(), request.purpose());
            auditLogService.record(null, "SMS_CODE_ISSUED", request.purpose().name(), clientIp(servletRequest), "SUCCESS", Map.of("phoneSuffix", suffix(request.phone())));
            return new AgentSmsCodeResponse(true, Math.max(1, issued.expiresAt().minusSeconds(Instant.now().getEpochSecond()).getEpochSecond()), issued.debugCode());
        } catch (IllegalArgumentException error) {
            auditLogService.record(null, "SMS_CODE_ISSUED", request.purpose().name(), clientIp(servletRequest), "REJECTED", Map.of());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AgentAuthResponse> register(@Valid @RequestBody AgentSmsRegisterRequest request, HttpServletRequest servletRequest) {
        try {
            smsCodeService.verifyAndConsume(request.phone(), AgentSmsPurpose.REGISTER, request.code());
            AgentPrincipal principal = userStore.create(request.username(), request.phone(), passwordCodec.encode(request.password()));
            auditLogService.record(principal, "REGISTER", principal.userId(), clientIp(servletRequest), "SUCCESS", Map.of());
            return response(principal);
        } catch (IllegalArgumentException error) {
            auditLogService.record(null, "REGISTER", request.username(), clientIp(servletRequest), "REJECTED", Map.of());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AgentAuthResponse> login(@Valid @RequestBody AgentAuthRequest request, HttpServletRequest servletRequest) {
        String username = userStore.normalizeUsername(request.username());
        String clientIp = clientIp(servletRequest);
        try {
            captchaService.verify(request.captchaId(), request.captchaAnswer());
            loginRateLimiter.assertAllowed(username, clientIp);
        } catch (AgentLoginRateLimiter.LoginRateLimitException error) {
            auditLogService.record(null, "LOGIN", username, clientIp, "RATE_LIMITED", Map.of());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, error.getMessage());
        } catch (IllegalArgumentException error) {
            auditLogService.record(null, "LOGIN", username, clientIp, "REJECTED", Map.of("reason", "captcha"));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        }
        AgentUserStore.StoredUser user = userStore.findByUsername(username);
        if (user == null || !passwordCodec.matches(request.password(), user.passwordHash())) {
            loginRateLimiter.recordFailure(username, clientIp);
            auditLogService.record(null, "LOGIN", username, clientIp, "REJECTED", Map.of("reason", "credentials"));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        loginRateLimiter.reset(username, clientIp);
        auditLogService.record(user.principal(), "LOGIN", user.principal().userId(), clientIp, "SUCCESS", Map.of());
        return response(user.principal());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AgentAuthResponse> refresh(@RequestBody(required = false) AgentRefreshRequest request, HttpServletRequest servletRequest) {
        String rawToken = cookieToken(servletRequest);
        if ((rawToken == null || rawToken.isBlank()) && properties.isExposeRefreshTokenInBody() && request != null) rawToken = request.refreshToken();
        try {
            ResponseEntity<AgentAuthResponse> result = response(tokenStore.consume(rawToken));
            auditLogService.record(null, "TOKEN_REFRESH", "cookie", clientIp(servletRequest), "SUCCESS", Map.of());
            return result;
        } catch (IllegalArgumentException error) {
            auditLogService.record(null, "TOKEN_REFRESH", "cookie", clientIp(servletRequest), "REJECTED", Map.of());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization, @RequestBody(required = false) AgentLogoutRequest request, HttpServletRequest servletRequest) {
        if (authorization != null && authorization.startsWith("Bearer ")) tokenService.revoke(authorization.substring(7));
        String rawToken = cookieToken(servletRequest);
        if ((rawToken == null || rawToken.isBlank()) && properties.isExposeRefreshTokenInBody() && request != null) rawToken = request.refreshToken();
        tokenStore.revoke(rawToken);
        HttpHeaders headers = new HttpHeaders();
        refreshCookieService.clear(headers);
        auditLogService.record(null, "LOGOUT", "session", clientIp(servletRequest), "SUCCESS", Map.of());
        return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
    }

    @PostMapping("/password/reset")
    public ResponseEntity<AgentAuthResponse> resetPassword(@Valid @RequestBody AgentPasswordResetRequest request, HttpServletRequest servletRequest) {
        try {
            smsCodeService.verifyAndConsume(request.phone(), AgentSmsPurpose.RESET_PASSWORD, request.code());
            AgentUserStore.StoredUser user = userStore.findByPhone(request.phone());
            if (user == null) throw new IllegalArgumentException("Verification code is invalid or expired");
            userStore.updatePassword(user.principal().userId(), passwordCodec.encode(request.password()));
            tokenStore.revokeAllForUser(user.principal().userId());
            auditLogService.record(user.principal(), "PASSWORD_RESET", user.principal().userId(), clientIp(servletRequest), "SUCCESS", Map.of());
            return response(user.principal());
        } catch (IllegalArgumentException error) {
            auditLogService.record(null, "PASSWORD_RESET", suffix(request.phone()), clientIp(servletRequest), "REJECTED", Map.of());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        }
    }

    private ResponseEntity<AgentAuthResponse> response(AgentPrincipal principal) {
        billingService.ensureUser(principal);
        Instant refreshExpiry = Instant.now().plus(properties.getRefreshTokenTtl());
        AgentTokenStore.IssuedRefreshToken refresh = tokenStore.issue(principal, refreshExpiry);
        HttpHeaders headers = new HttpHeaders();
        refreshCookieService.write(headers, refresh.rawToken());
        String bodyToken = properties.isExposeRefreshTokenInBody() ? refresh.rawToken() : null;
        return new ResponseEntity<>(new AgentAuthResponse(tokenService.issue(principal), bodyToken, "Bearer", tokenService.expiresInSeconds(), properties.getRefreshTokenTtl().toSeconds(), principal.userId(), principal.username()), headers, HttpStatus.OK);
    }

    private String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (properties.getRefreshCookieName().equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private String clientIp(HttpServletRequest request) { String forwarded = request.getHeader("X-Forwarded-For"); return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim(); }
    private String suffix(String value) { return value == null || value.length() < 4 ? "" : value.substring(value.length() - 4); }
}