CREATE TABLE IF NOT EXISTS agent_security_audit (
    audit_id VARCHAR(64) PRIMARY KEY,
    owner_user_id VARCHAR(64) NULL,
    username VARCHAR(64) NULL,
    action VARCHAR(80) NOT NULL,
    target VARCHAR(256) NULL,
    client_ip VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    detail_json TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_agent_security_audit_owner_time (owner_user_id, created_at),
    INDEX idx_agent_security_audit_action_time (action, created_at)
);

CREATE TABLE IF NOT EXISTS agent_user_plan (
    owner_user_id VARCHAR(64) PRIMARY KEY,
    plan_code VARCHAR(24) NOT NULL,
    daily_model_limit INT NOT NULL,
    monthly_cost_limit_micros BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_usage_ledger (
    usage_id VARCHAR(64) PRIMARY KEY,
    owner_user_id VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    success TINYINT NOT NULL,
    estimated_cost_micros BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_agent_usage_owner_time (owner_user_id, created_at),
    INDEX idx_agent_usage_provider_time (provider, created_at)
);