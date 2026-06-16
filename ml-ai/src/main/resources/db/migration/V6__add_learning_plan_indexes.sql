-- Query support for active learning plans.
CREATE INDEX IF NOT EXISTS idx_ai_learning_plan_user_status_created
    ON ai_learning_plan(user_id, status, created_at DESC);

COMMENT ON INDEX idx_ai_learning_plan_user_status_created
    IS '查找用户当前有效学习计划';
