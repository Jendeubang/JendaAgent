CREATE TABLE IF NOT EXISTS agent_plan_revision (
    run_id VARCHAR(64) NOT NULL,
    revision_no INT NOT NULL,
    parent_revision_no INT NULL,
    reason VARCHAR(256) NOT NULL,
    plan_json TEXT NOT NULL,
    state_summary_json TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (run_id, revision_no),
    INDEX idx_agent_plan_revision_run (run_id, created_at)
);