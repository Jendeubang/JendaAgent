CREATE TABLE IF NOT EXISTS agent_session (
    session_id VARCHAR(64) PRIMARY KEY,
    owner_user_id VARCHAR(64) NOT NULL,
    latest_run_id VARCHAR(64) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    latest_prompt TEXT NOT NULL,
    run_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_agent_session_owner_updated (owner_user_id, updated_at)
);

CREATE TABLE IF NOT EXISTS agent_session_claim (
    session_id VARCHAR(64) PRIMARY KEY,
    owner_user_id VARCHAR(64) NOT NULL,
    claimed_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_run (
    run_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    owner_user_id VARCHAR(64) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    prompt TEXT NOT NULL,
    image_urls_json TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    INDEX idx_agent_run_session_created (session_id, created_at),
    INDEX idx_agent_run_owner_created (owner_user_id, created_at)
);

-- Kept for replay compatibility with the pre-Flyway JDBC implementation.
CREATE TABLE IF NOT EXISTS agent_message (
    event_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    sequence_no BIGINT NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    agent_name VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    payload_json TEXT NOT NULL,
    CONSTRAINT uk_agent_message_sequence UNIQUE (run_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS agent_event (
    event_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    agent_name VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    payload_json TEXT NOT NULL,
    CONSTRAINT uk_agent_event_sequence UNIQUE (run_id, sequence_no),
    INDEX idx_agent_event_session_time (session_id, occurred_at, sequence_no),
    INDEX idx_agent_event_run_sequence (run_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS agent_tool_call (
    tool_call_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    owner_user_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    call_event_id VARCHAR(64) NOT NULL,
    result_event_id VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    request_json TEXT NULL,
    result_json TEXT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    INDEX idx_agent_tool_call_run (run_id, started_at),
    INDEX idx_agent_tool_call_owner (owner_user_id, started_at)
);

CREATE TABLE IF NOT EXISTS agent_asset (
    asset_id VARCHAR(64) PRIMARY KEY,
    owner_user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64) NULL,
    file_name VARCHAR(512) NULL,
    media_type VARCHAR(128) NULL,
    size_bytes BIGINT NULL,
    object_key VARCHAR(1024) NULL,
    image_url TEXT NOT NULL,
    source VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_agent_asset_owner_session (owner_user_id, session_id, created_at),
    INDEX idx_agent_asset_run (run_id, created_at)
);

CREATE TABLE IF NOT EXISTS agent_plan_execution (
    run_id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    owner_user_id VARCHAR(64) NOT NULL,
    plan_json TEXT NOT NULL,
    request_json TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_agent_plan_execution_owner (owner_user_id, created_at)
);

CREATE TABLE IF NOT EXISTS agent_plan_task_state (
    run_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    state VARCHAR(40) NOT NULL,
    attempts INT NOT NULL,
    result_json TEXT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (run_id, task_id)
);

CREATE TABLE IF NOT EXISTS agent_plan_approval (
    approval_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    owner_user_id VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    note TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NULL,
    INDEX idx_agent_plan_approval_owner (owner_user_id, status, created_at)
);