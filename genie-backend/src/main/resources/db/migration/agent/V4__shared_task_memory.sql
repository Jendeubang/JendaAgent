CREATE TABLE IF NOT EXISTS agent_shared_task_memory (
    run_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    attempt INT NOT NULL,
    input_json TEXT NOT NULL,
    output_json TEXT NULL,
    asset_ids_json TEXT NULL,
    failure_reason TEXT NULL,
    summary TEXT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (run_id, task_id),
    INDEX idx_agent_shared_task_memory_run (run_id, updated_at)
);