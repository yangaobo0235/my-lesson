-- 保留既有修订和审计；旧资料沿用原读取资格，不推断历史撤回原因。
ALTER TABLE kb_document
  ADD COLUMN access_status VARCHAR(16) NOT NULL DEFAULT 'ALLOWED' COMMENT 'ALLOWED / REVOKED，独立于发布态',
  ADD COLUMN access_reason VARCHAR(1000) NULL COMMENT '最近访问权限变更原因',
  ADD COLUMN withdrawal_reason VARCHAR(1000) NULL COMMENT '风险撤回原因，不可直接恢复',
  DROP CHECK ck_kb_document__publication_status,
  DROP CHECK ck_kb_document__published_state,
  ADD CONSTRAINT ck_kb_document__publication_status CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'DISABLED', 'RETIRED', 'WITHDRAWN', 'DELETED')),
  ADD CONSTRAINT ck_kb_document__published_state CHECK (
    (publication_status IN ('PUBLISHED', 'DISABLED', 'RETIRED', 'WITHDRAWN')
      AND published_at IS NOT NULL AND published_by IS NOT NULL AND current_job_id IS NOT NULL
      AND source_review_note IS NOT NULL AND REGEXP_LIKE(source_review_note, '[^[:space:]]'))
    OR (publication_status IN ('DRAFT', 'DELETED') AND published_at IS NULL AND published_by IS NULL)),
  ADD CONSTRAINT ck_kb_document__access_status CHECK (access_status IN ('ALLOWED', 'REVOKED')),
  ADD CONSTRAINT ck_kb_document__access_reason CHECK (
    (access_reason IS NULL OR REGEXP_LIKE(access_reason, '[^[:space:]]'))
    AND (access_status <> 'REVOKED' OR access_reason IS NOT NULL)),
  ADD CONSTRAINT ck_kb_document__withdrawal CHECK (
    (publication_status = 'WITHDRAWN' AND withdrawal_reason IS NOT NULL AND REGEXP_LIKE(withdrawal_reason, '[^[:space:]]'))
    OR (publication_status <> 'WITHDRAWN' AND withdrawal_reason IS NULL));

ALTER TABLE kb_document_event
  ADD COLUMN client_action_id VARCHAR(32) NULL COMMENT '操作者及修订范围内动作键',
  ADD COLUMN request_hash VARCHAR(64) NULL COMMENT '动作和规范输入摘要，与动作键同空同有',
  ADD UNIQUE KEY uk_kb_document_event_action (document_id, actor_id, client_action_id),
  ADD CONSTRAINT ck_kb_document_event__action_pair CHECK (
    (client_action_id IS NULL AND request_hash IS NULL)
    OR (client_action_id IS NOT NULL AND request_hash IS NOT NULL
      AND REGEXP_LIKE(client_action_id, '^[0-9a-f]{32}$', 'c')
      AND REGEXP_LIKE(request_hash, '^[0-9a-f]{64}$', 'c'))),
  DROP CHECK ck_kb_document_event__event_type,
  ADD CONSTRAINT ck_kb_document_event__event_type CHECK (event_type IN (
    'UPLOADED', 'METADATA_CHANGED', 'INDEX_REQUESTED', 'INDEX_CANCEL_REQUESTED', 'INDEX_REQUEUED',
    'PUBLISHED', 'DISABLED', 'RESTORED', 'RETIRED', 'WITHDRAWN', 'ACCESS_REVOKED', 'ACCESS_RESTORED', 'DELETED'));
