ALTER TABLE ai_learning_plan
    ADD COLUMN IF NOT EXISTS progress_percent INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS progress_note VARCHAR(500),
    ADD COLUMN IF NOT EXISTS last_progress_at TIMESTAMPTZ;

COMMENT ON COLUMN ai_learning_plan.progress_percent
    IS '学习计划完成进度，范围 0 到 100';
COMMENT ON COLUMN ai_learning_plan.progress_note
    IS '最近一次进度更新备注';
COMMENT ON COLUMN ai_learning_plan.last_progress_at
    IS '最近一次进度更新时间';
