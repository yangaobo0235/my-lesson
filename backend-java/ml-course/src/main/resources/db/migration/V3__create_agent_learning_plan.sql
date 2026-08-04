CREATE TABLE IF NOT EXISTS agent_learning_plan_draft (
    id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    goal VARCHAR(500) NOT NULL,
    minutes_per_day INT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    previous_draft_id CHAR(36),
    request_id CHAR(36),
    courses_json JSON NOT NULL,
    routine_json JSON NOT NULL,
    adjustments_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uq_agent_plan_draft_request UNIQUE (user_id, request_id),
    INDEX idx_agent_plan_draft_user_status (user_id, status, created_at),
    INDEX idx_agent_plan_draft_previous (previous_draft_id)
);

CREATE TABLE IF NOT EXISTS agent_learning_plan (
    id CHAR(36) PRIMARY KEY,
    source_draft_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    goal VARCHAR(500) NOT NULL,
    minutes_per_day INT NOT NULL,
    estimated_weeks INT NOT NULL,
    courses_json JSON NOT NULL,
    routine_json JSON NOT NULL,
    adjustments_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    progress_note VARCHAR(500),
    last_progress_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uq_agent_plan_source_draft UNIQUE (source_draft_id),
    INDEX idx_agent_plan_user_status (user_id, status, created_at)
);
