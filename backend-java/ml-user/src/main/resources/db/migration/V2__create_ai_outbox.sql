CREATE TABLE IF NOT EXISTS ai_outbox_event (
    event_id CHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_version BIGINT NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    last_error VARCHAR(500) NULL,
    INDEX idx_ai_outbox_pending (status, next_retry_at, created_at)
);
