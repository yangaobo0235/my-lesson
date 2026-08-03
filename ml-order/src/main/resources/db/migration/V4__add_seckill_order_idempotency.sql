CREATE TABLE IF NOT EXISTS seckill_order_consume (
    request_id VARCHAR(36) PRIMARY KEY,
    qualification_id VARCHAR(36) NOT NULL,
    seckill_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    order_id BIGINT,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uq_seckill_qualification (qualification_id),
    KEY idx_seckill_consume_order (order_id),
    KEY idx_seckill_consume_status_updated (status, updated_at)
);
