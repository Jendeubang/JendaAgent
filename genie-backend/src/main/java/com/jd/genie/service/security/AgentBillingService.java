package com.jd.genie.service.security;

import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.model.security.AgentUsageSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/** Enforces simple per-user plan quotas and records provider cost estimates. */
@Service
public class AgentBillingService {
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public AgentBillingService(@Qualifier("agentPersistenceDataSource") DataSource dataSource) {
        this.jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    public void ensureUser(AgentPrincipal principal) {
        if (principal == null) return;
        try {
            jdbcTemplate.update("INSERT INTO agent_user_plan (owner_user_id, plan_code, daily_model_limit, monthly_cost_limit_micros, updated_at) VALUES (?, 'FREE', 20, 0, ?)", principal.userId(), Timestamp.from(Instant.now()));
        } catch (RuntimeException ignored) { }
    }

    public void assertCanInvoke(AgentPrincipal principal) {
        ensureUser(principal);
        Plan plan = findPlan(principal.userId());
        long today = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_usage_ledger WHERE owner_user_id = ? AND created_at >= ?", Long.class, principal.userId(), Timestamp.from(startOfDay()));
        if (today >= plan.dailyModelLimit()) throw new IllegalStateException("Daily model quota reached for plan " + plan.planCode());
        if (plan.monthlyCostLimitMicros() > 0) {
            long monthCost = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(estimated_cost_micros), 0) FROM agent_usage_ledger WHERE owner_user_id = ? AND created_at >= ?", Long.class, principal.userId(), Timestamp.from(startOfMonth()));
            if (monthCost >= plan.monthlyCostLimitMicros()) throw new IllegalStateException("Monthly model cost limit reached for plan " + plan.planCode());
        }
    }

    public void recordInvocation(AgentPrincipal principal, String provider, String toolName, boolean success) {
        if (principal == null) return;
        ensureUser(principal);
        jdbcTemplate.update("INSERT INTO agent_usage_ledger (usage_id, owner_user_id, provider, tool_name, success, estimated_cost_micros, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), principal.userId(), provider == null ? "default" : provider, toolName, success ? 1 : 0,
                estimatedCost(provider, toolName, success), Timestamp.from(Instant.now()));
    }

    public AgentUsageSummary summary(AgentPrincipal principal) {
        ensureUser(principal);
        Plan plan = findPlan(principal.userId());
        long today = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_usage_ledger WHERE owner_user_id = ? AND created_at >= ?", Long.class, principal.userId(), Timestamp.from(startOfDay()));
        long month = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_usage_ledger WHERE owner_user_id = ? AND created_at >= ?", Long.class, principal.userId(), Timestamp.from(startOfMonth()));
        long cost = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(estimated_cost_micros), 0) FROM agent_usage_ledger WHERE owner_user_id = ? AND created_at >= ?", Long.class, principal.userId(), Timestamp.from(startOfMonth()));
        return new AgentUsageSummary(plan.planCode(), plan.dailyModelLimit(), today, month, cost, plan.monthlyCostLimitMicros());
    }

    public void setPlan(String userId, String requestedPlan) {
        Plan plan = profile(requestedPlan);
        int updated = jdbcTemplate.update("UPDATE agent_user_plan SET plan_code = ?, daily_model_limit = ?, monthly_cost_limit_micros = ?, updated_at = ? WHERE owner_user_id = ?", plan.planCode(), plan.dailyModelLimit(), plan.monthlyCostLimitMicros(), Timestamp.from(Instant.now()), userId);
        if (updated == 0) jdbcTemplate.update("INSERT INTO agent_user_plan (owner_user_id, plan_code, daily_model_limit, monthly_cost_limit_micros, updated_at) VALUES (?, ?, ?, ?, ?)", userId, plan.planCode(), plan.dailyModelLimit(), plan.monthlyCostLimitMicros(), Timestamp.from(Instant.now()));
    }

    private Plan findPlan(String userId) {
        try {
            return jdbcTemplate.queryForObject("SELECT plan_code, daily_model_limit, monthly_cost_limit_micros FROM agent_user_plan WHERE owner_user_id = ?", (rs, row) -> new Plan(rs.getString(1), rs.getInt(2), rs.getLong(3)), userId);
        } catch (RuntimeException missing) {
            return profile("FREE");
        }
    }

    private Plan profile(String value) {
        return switch (value == null ? "FREE" : value.trim().toUpperCase(Locale.ROOT)) {
            case "PRO" -> new Plan("PRO", 200, 50_000_000L);
            case "ADMIN" -> new Plan("ADMIN", 10_000, 0);
            default -> new Plan("FREE", 20, 0);
        };
    }

    private long estimatedCost(String provider, String toolName, boolean success) {
        if (!success) return 0;
        String key = ((provider == null ? "" : provider) + " " + (toolName == null ? "" : toolName)).toLowerCase(Locale.ROOT);
        if (key.contains("pro")) return 250_000L;
        if (key.contains("image")) return 80_000L;
        return 10_000L;
    }

    private Instant startOfDay() { return LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC); }
    private Instant startOfMonth() { return LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC); }
    private record Plan(String planCode, int dailyModelLimit, long monthlyCostLimitMicros) { }
}