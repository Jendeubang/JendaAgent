package com.jd.genie.model.security;

public record AgentUsageSummary(String planCode, int dailyModelLimit, long todayModelCalls, long monthModelCalls,
                                long monthCostMicros, long monthlyCostLimitMicros) {
}