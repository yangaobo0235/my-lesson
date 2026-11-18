-- 规程属于不可变原件修订，不从旧正文自动生成或宣称已经审核。
ALTER TABLE kb_document
  ADD procedure_definition JSON NULL COMMENT '修订内人工审核规程，最多64KiB',
  ADD procedure_sha256 VARCHAR(64) NULL COMMENT '规范化规程摘要',
  ADD procedure_validated_sha256 VARCHAR(64) NULL COMMENT '已校验通过的规程摘要',
  ADD procedure_validated_at DATETIME(6) NULL COMMENT '规程校验提交时间',
  ADD procedure_validated_by VARCHAR(32) NULL COMMENT '执行规程校验的知识管理员',
  ADD CONSTRAINT ck_kb_document_procedure CHECK (
    (procedure_definition IS NULL AND procedure_sha256 IS NULL AND procedure_validated_sha256 IS NULL
      AND procedure_validated_at IS NULL AND procedure_validated_by IS NULL)
    OR (procedure_definition IS NOT NULL AND procedure_sha256 IS NOT NULL
      AND JSON_TYPE(procedure_definition)='OBJECT' AND OCTET_LENGTH(CAST(procedure_definition AS CHAR))<=65536
      AND REGEXP_LIKE(procedure_sha256,'^[0-9a-f]{64}$','c')
      AND ((procedure_validated_sha256 IS NULL AND procedure_validated_at IS NULL AND procedure_validated_by IS NULL)
        OR (procedure_validated_sha256 IS NOT NULL AND procedure_validated_sha256=procedure_sha256
          AND procedure_validated_at IS NOT NULL AND procedure_validated_at>=created_at
          AND procedure_validated_by IS NOT NULL AND REGEXP_LIKE(procedure_validated_by,'^[0-9a-f]{32}$','c'))))),
  ADD CONSTRAINT ck_kb_document_procedure_publish CHECK (
    publication_status IN ('DRAFT','DELETED') OR procedure_definition IS NULL OR procedure_validated_sha256 IS NOT NULL);

ALTER TABLE kb_document_event
  DROP CHECK ck_kb_document_event__event_type,
  ADD CONSTRAINT ck_kb_document_event__event_type CHECK (event_type IN (
    'UPLOADED','METADATA_CHANGED','INDEX_REQUESTED','INDEX_CANCEL_REQUESTED','INDEX_REQUEUED',
    'PUBLISHED','DISABLED','RESTORED','RETIRED','WITHDRAWN','ACCESS_REVOKED','ACCESS_RESTORED',
    'PROCEDURE_VALIDATED','DELETED'));
