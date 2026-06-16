-- Durable workflow drafts and human approval state.
ALTER TABLE ai_approval_task
    ADD COLUMN IF NOT EXISTS request_id UUID,
    ADD COLUMN IF NOT EXISTS response_json JSONB,
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_approval_run_action
    ON ai_approval_task(run_id, action_type);

CREATE INDEX IF NOT EXISTS idx_ai_approval_user_status_created
    ON ai_approval_task(user_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_learning_plan_draft (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ai_agent_run(id),
    user_id BIGINT NOT NULL,
    goal TEXT NOT NULL,
    minutes_per_day INT NOT NULL,
    state_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_learning_plan_draft_run
    ON ai_learning_plan_draft(run_id);

CREATE INDEX IF NOT EXISTS idx_ai_learning_plan_draft_user_status
    ON ai_learning_plan_draft(user_id, status, created_at DESC);

COMMENT ON TABLE ai_learning_plan_draft
    IS 'M12 学习计划确定性工作流草案，服务重启后可继续审批';

COMMENT ON COLUMN ai_approval_task.response_json
    IS '审批执行结果快照，不作为再次执行时的业务事实来源';
