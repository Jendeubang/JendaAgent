package com.jd.genie.model.auth;

import java.time.Instant;

/** Browser receives only an SVG challenge; the answer remains server-side. */
public record AgentCaptchaResponse(String captchaId, String imageDataUrl, Instant expiresAt) {
}