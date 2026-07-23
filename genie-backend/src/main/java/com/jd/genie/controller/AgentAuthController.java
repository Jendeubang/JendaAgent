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