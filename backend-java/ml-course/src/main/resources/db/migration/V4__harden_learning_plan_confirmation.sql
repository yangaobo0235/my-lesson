CREATE TABLE IF NOT EXISTS agent_learning_plan_operation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    request_id CHAR(36) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    draft_id CHAR(36) NOT NULL,
    plan_id CHAR(36),
    payload_hash CHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uq_agent_plan_operation_request UNIQUE (user_id, request_id),
    INDEX idx_agent_plan_operation_draft (draft_id, operation_type)
);

ALTER TABLE agent_learning_plan_draft
    ADD CONSTRAINT uq_agent_plan_single_successor UNIQUE (previous_draft_id);
