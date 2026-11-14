"""建立AI所属初始结构，已执行版本保持不变。"""

# ruff: noqa: E501

from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    """按先建表后补外键的次序安装完整业务结构。"""
    op.execute(
        r"""
        -- 文档处理任务
        CREATE TABLE `ai_index_job` (
            `id` VARCHAR(32) NOT NULL COMMENT '主键，由knowledge分配，AI沿用同一任务身份',
            `document_id` VARCHAR(32) NOT NULL COMMENT '外部 knowledge 文档身份',
            `attempt_number` INT NOT NULL COMMENT '同文档正序 attempt',
            `file_sha256` VARCHAR(64) NOT NULL COMMENT '本次文件摘要',
            `request_hash` VARCHAR(64) NOT NULL COMMENT '任务输入语义摘要',
            `requested_by` VARCHAR(32) NOT NULL COMMENT '外部请求管理员账号',
            `last_command` VARCHAR(16) NOT NULL COMMENT 'SUBMIT / CANCEL，持久受理后仅允许单向变为CANCEL',
            `trace_context` JSON NULL COMMENT '首次有效消息接收的schema_version及W3C上下文，重复/取消不覆盖',
            `status` VARCHAR(16) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED / RUNNING / SUCCEEDED / FAILED / CANCELLED',
            `active_document_id` VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN status IN ('QUEUED', 'RUNNING') THEN document_id ELSE NULL END) STORED COMMENT 'QUEUED/RUNNING 时为 document_id，否则 NULL；唯一',
            `stage` VARCHAR(16) NULL COMMENT 'PARSING / CHUNKING / EMBEDDING / INDEXING',
            `parser_task_id` VARCHAR(200) NULL COMMENT 'MinerU 回执中的任务 ID，确认后固定',
            `parser_file_id` VARCHAR(200) NULL COMMENT 'MinerU 回执中的文件 ID，与任务 ID 同空同有',
            `parser_state` VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT 'NOT_SUBMITTED / SUBMITTING / ACTIVE / SETTLED / UNKNOWN',
            `parser_reconciliation` JSON NULL COMMENT '人工核对未知受理时的 schema_version、运维操作者、时间、核对依据',
            `processing_config` JSON NOT NULL COMMENT 'schema_version、MinerU 合同和档位、模型空间、切片、关键词规则及预算',
            `page_count` INT NULL COMMENT '解析后物理页数，成功必填且正数',
            `claim_token` VARCHAR(36) NULL COMMENT 'RUNNING 执行令牌，每次领取或接管生成新值',
            `claim_until` DATETIME(6) NULL COMMENT 'RUNNING 租约截止，worker 心跳按令牌续约',
            `started_at` DATETIME(6) NULL COMMENT '首次 RUNNING 领取时刻，恢复不覆盖',
            `finished_at` DATETIME(6) NULL COMMENT '终态时刻',
            `error_code` VARCHAR(64) NULL COMMENT '失败安全码',
            `error_message` VARCHAR(500) NULL COMMENT '限长脱敏错误，不含全文',
            `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
            `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
            CONSTRAINT `pk_ai_index_job` PRIMARY KEY (`id`),
            UNIQUE KEY `uk_ai_index_job_id_document` (`id`, `document_id`),
            UNIQUE KEY `uk_ai_index_job_attempt` (`document_id`, `attempt_number`),
            UNIQUE KEY `uk_ai_index_job_active` (`active_document_id`),
            KEY `idx_ai_index_job_queue` (`status`, `created_at`, `id`),
            KEY `idx_ai_index_job_claim` (`status`, `claim_until`, `id`),
            CONSTRAINT `ck_ai_index_job__last_command` CHECK (`last_command` IN ('SUBMIT', 'CANCEL')),
            CONSTRAINT `ck_ai_index_job__status` CHECK (`status` IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
            CONSTRAINT `ck_ai_index_job__stage` CHECK (`stage` IN ('PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING')),
            CONSTRAINT `ck_ai_index_job__parser_state` CHECK (`parser_state` IN ('NOT_SUBMITTED', 'SUBMITTING', 'ACTIVE', 'SETTLED', 'UNKNOWN')),
            CONSTRAINT `ck_ai_index_job__cancel_command` CHECK ((status <> 'CANCELLED' OR last_command = 'CANCEL') AND (last_command <> 'CANCEL' OR status IN ('SUCCEEDED', 'FAILED', 'CANCELLED'))),
            CONSTRAINT `ck_ai_index_job__claim_fields` CHECK ((status = 'RUNNING' AND started_at IS NOT NULL AND stage IS NOT NULL AND claim_token IS NOT NULL AND claim_until IS NOT NULL) OR (status <> 'RUNNING' AND claim_token IS NULL AND claim_until IS NULL)),
            CONSTRAINT `ck_ai_index_job__finished_fields` CHECK ((status IN ('SUCCEEDED', 'FAILED', 'CANCELLED') AND finished_at IS NOT NULL) OR (status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED') AND finished_at IS NULL)),
            CONSTRAINT `ck_ai_index_job__parser_ids` CHECK ((parser_task_id IS NULL AND parser_file_id IS NULL) OR (parser_task_id IS NOT NULL AND parser_file_id IS NOT NULL)),
            CONSTRAINT `ck_ai_index_job__parser_fields` CHECK ((parser_state <> 'ACTIVE' OR parser_task_id IS NOT NULL) AND (parser_state <> 'NOT_SUBMITTED' OR parser_task_id IS NULL) AND (parser_state <> 'SETTLED' OR parser_task_id IS NOT NULL OR parser_reconciliation IS NOT NULL)),
            CONSTRAINT `ck_ai_index_job__success_fields` CHECK (status <> 'SUCCEEDED' OR (started_at IS NOT NULL AND page_count IS NOT NULL AND page_count > 0 AND parser_state = 'SETTLED')),
            CONSTRAINT `ck_ai_index_job__failure_fields` CHECK (status <> 'FAILED' OR (error_code IS NOT NULL AND REGEXP_LIKE(error_code, '[^[\:space\:]]'))),
            CONSTRAINT `ck_ai_index_job__execution_time` CHECK ((started_at IS NULL OR started_at >= created_at) AND (finished_at IS NULL OR (finished_at >= created_at AND (started_at IS NULL OR finished_at >= started_at))) AND (claim_until IS NULL OR (started_at IS NOT NULL AND claim_until > started_at))),
            CONSTRAINT `ck_ai_index_job__queued_fields` CHECK (status <> 'QUEUED' OR started_at IS NULL),
            CONSTRAINT `ck_ai_index_job__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_index_job__document_id_format` CHECK (REGEXP_LIKE(`document_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_index_job__attempt_number_positive` CHECK (`attempt_number` > 0),
            CONSTRAINT `ck_ai_index_job__file_sha256_format` CHECK (REGEXP_LIKE(`file_sha256`, '^[0-9a-f]{64}$', 'c')),
            CONSTRAINT `ck_ai_index_job__request_hash_format` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
            CONSTRAINT `ck_ai_index_job__requested_by_format` CHECK (REGEXP_LIKE(`requested_by`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_index_job__last_command_nonblank` CHECK (REGEXP_LIKE(`last_command`, '[^[\:space\:]]')),
            CONSTRAINT `ck_ai_index_job__trace_context_schema` CHECK (`trace_context` IS NULL OR (JSON_SCHEMA_VALID('{"type"\:"object","required"\:["schema_version","traceparent"],"properties"\:{"schema_version"\:{"type"\:"integer","enum"\:[1]},"traceparent"\:{"type"\:"string","pattern"\:"^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"},"tracestate"\:{"type"\:"string","maxLength"\:512}},"additionalProperties"\:false}', `trace_context`) = 1 AND OCTET_LENGTH(`trace_context`) <= 2048 AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 1, 2) <> 'ff' AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 4, 32) <> REPEAT('0', 32) AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 37, 16) <> REPEAT('0', 16))),
            CONSTRAINT `ck_ai_index_job__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[\:space\:]]')),
            CONSTRAINT `ck_ai_index_job__parser_state_nonblank` CHECK (REGEXP_LIKE(`parser_state`, '[^[\:space\:]]')),
            CONSTRAINT `ck_ai_index_job__parser_reconciliation_schema` CHECK (`parser_reconciliation` IS NULL OR (JSON_SCHEMA_VALID('{"type"\:"object","required"\:["schema_version"],"properties"\:{"schema_version"\:{"type"\:"integer","minimum"\:1}}}', `parser_reconciliation`) = 1)),
            CONSTRAINT `ck_ai_index_job__processing_config_schema` CHECK (JSON_SCHEMA_VALID('{"type"\:"object","required"\:["schema_version"],"properties"\:{"schema_version"\:{"type"\:"integer","minimum"\:1}}}', `processing_config`) = 1),
            CONSTRAINT `ck_ai_index_job__page_count_positive` CHECK (`page_count` IS NULL OR (`page_count` > 0)),
            CONSTRAINT `ck_ai_index_job__claim_token_format` CHECK (`claim_token` IS NULL OR (REGEXP_LIKE(`claim_token`, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'))),
            CONSTRAINT `ck_ai_index_job__update_time` CHECK (updated_at >= created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='文档处理任务'
        """
    )
    op.execute(
        r"""
        -- 知识片段与向量副本定位
        CREATE TABLE `ai_chunk` (
            `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
            `index_job_id` VARCHAR(32) NOT NULL COMMENT '本地复合 FK(index_job_id, document_id) → ai_index_job(id, document_id)',
            `document_id` VARCHAR(32) NOT NULL COMMENT '外部文档 ID，与任务一致',
            `chunk_number` INT NOT NULL COMMENT '任务内从1开始',
            `section_path` TEXT NULL COMMENT '真实章节路径',
            `content` TEXT NOT NULL COMMENT '非空白正文，切片预算控制长度',
            `source_locations` JSON NOT NULL COMMENT 'schema_version、非空 pages 数组，含 page_number 和可空 printed_page_label',
            `error_codes` JSON NOT NULL COMMENT '片段中识别且规范化的完整错误码，供 ES 精确过滤与证据校验',
            `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
            CONSTRAINT `pk_ai_chunk` PRIMARY KEY (`id`),
            UNIQUE KEY `uk_ai_chunk_number` (`index_job_id`, `chunk_number`),
            KEY `idx_ai_chunk_document_job` (`document_id`, `index_job_id`),
            KEY `idx_ai_chunk_job_document` (`index_job_id`, `document_id`),
            CONSTRAINT `ck_ai_chunk__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_chunk__index_job_id_format` CHECK (REGEXP_LIKE(`index_job_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_chunk__document_id_format` CHECK (REGEXP_LIKE(`document_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_chunk__chunk_number_positive` CHECK (`chunk_number` > 0),
            CONSTRAINT `ck_ai_chunk__content_nonblank` CHECK (REGEXP_LIKE(`content`, '[^[\:space\:]]')),
            CONSTRAINT `ck_ai_chunk__source_locations_schema` CHECK (JSON_SCHEMA_VALID('{"type"\:"object","required"\:["schema_version","pages"],"properties"\:{"schema_version"\:{"type"\:"integer","minimum"\:1},"pages"\:{"type"\:"array","minItems"\:1,"items"\:{"type"\:"object","required"\:["page_number"],"properties"\:{"page_number"\:{"type"\:"integer","minimum"\:1},"printed_page_label"\:{"type"\:["string","null"]}}}}}}', `source_locations`) = 1),
            CONSTRAINT `ck_ai_chunk__error_codes_schema` CHECK (JSON_TYPE(`error_codes`) = 'ARRAY')
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='知识片段与向量副本定位'
        """
    )
    op.execute(
        r"""
        -- 本人设备排障会话
        CREATE TABLE `ai_conversation` (
            `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
            `owner_id` VARCHAR(32) NOT NULL COMMENT '外部用户 ID',
            `customer_id` VARCHAR(32) NOT NULL COMMENT '外部客户组织 ID',
            `device_id` VARCHAR(32) NOT NULL COMMENT '外部设备 ID，首轮后不更换',
            `product_model_id` VARCHAR(32) NOT NULL COMMENT '外部型号 ID，来自可信设备合同',
            `title` VARCHAR(100) NOT NULL COMMENT '用户可改标题',
            `deleted_at` DATETIME(6) NULL COMMENT '逻辑删除时间',
            `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
            `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
            `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT '非负，更新比较旧值后加一',
            CONSTRAINT `pk_ai_conversation` PRIMARY KEY (`id`),
            UNIQUE KEY `uk_ai_conversation_id_owner` (`id`, `owner_id`),
            KEY `idx_ai_conversation_owner_history` (`owner_id`, `deleted_at`, `updated_at` DESC, `id`),
            CONSTRAINT `ck_ai_conversation__deleted_time` CHECK (deleted_at IS NULL OR deleted_at >= created_at),
            CONSTRAINT `ck_ai_conversation__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_conversation__owner_id_format` CHECK (REGEXP_LIKE(`owner_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_conversation__customer_id_format` CHECK (REGEXP_LIKE(`customer_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_conversation__device_id_format` CHECK (REGEXP_LIKE(`device_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_conversation__product_model_id_format` CHECK (REGEXP_LIKE(`product_model_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_conversation__title_nonblank` CHECK (REGEXP_LIKE(`title`, '[^[\:space\:]]')),
            CONSTRAINT `ck_ai_conversation__row_version_range` CHECK (`row_version` >= 0),
            CONSTRAINT `ck_ai_conversation__update_time` CHECK (updated_at >= created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='本人设备排障会话'
        """
    )
    op.execute(
        r"""
        -- 单轮排障运行
        CREATE TABLE `ai_generation` (
            `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
            `conversation_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → ai_conversation',
            `owner_id` VARCHAR(32) NOT NULL COMMENT '(conversation_id,owner_id)复合 FK → 会话',
            `active_conversation_id` VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN status IN ('QUEUED', 'RUNNING') THEN conversation_id ELSE NULL END) STORED COMMENT 'QUEUED/RUNNING 时为 conversation_id，否则 NULL；唯一',
            `round_number` INT NOT NULL COMMENT '会话内从1开始，锁会话分配',
            `question_text` TEXT NOT NULL COMMENT '用户问题，非空白且按请求预算限长',
            `answer_text` TEXT NULL COMMENT '成功终态才保存的校验后正文，非空白',
            `client_request_id` VARCHAR(32) NOT NULL COMMENT 'owner范围幂等键',
            `request_hash` VARCHAR(64) NOT NULL COMMENT '会话、问题等语义输入摘要',
            `session_ref` VARCHAR(36) NOT NULL COMMENT 'user 返回的非凭证会话引用，用于核对当前有效性',
            `auth_version` BIGINT NOT NULL COMMENT '受理时身份版本，后续与权威核对',
            `status` VARCHAR(16) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED / RUNNING / SUCCEEDED / FAILED / CANCELLED / INTERRUPTED',
            `answer_type` VARCHAR(32) NULL COMMENT '成功才有 ANSWERED / CLARIFICATION / INSUFFICIENT_EVIDENCE',
            `retrieval_query` TEXT NULL COMMENT '实际检索问题，未检索为空',
            `pipeline_config` JSON NOT NULL COMMENT 'schema_version、Prompt、模型、候选、RRF、Token、超时预算',
            `trace_context` JSON NULL COMMENT 'Web受理的schema_version及受控W3C上下文，供worker接续，不参与幂等摘要',
            `context_snapshot` JSON NOT NULL COMMENT 'schema_version、设备及有序实际 sources 标识，不重复全文',
            `answer_model` VARCHAR(200) NULL COMMENT '实际模型，规则澄清可空',
            `claim_token` VARCHAR(36) NULL COMMENT 'RUNNING 执行令牌，每次领取或接管生成新值',
            `claim_until` DATETIME(6) NULL COMMENT 'RUNNING 租约截止，worker 心跳按令牌续约',
            `started_at` DATETIME(6) NULL COMMENT '领取时刻',
            `finished_at` DATETIME(6) NULL COMMENT '终态时刻',
            `latency_ms` BIGINT NULL COMMENT '完整运行耗时，非负',
            `input_tokens` BIGINT NULL COMMENT '所有调用汇总，任一未知则为空',
            `output_tokens` BIGINT NULL COMMENT '所有调用汇总，任一未知则为空',
            `error_code` VARCHAR(64) NULL COMMENT '技术失败或中断原因',
            `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
            `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
            CONSTRAINT `pk_ai_generation` PRIMARY KEY (`id`),
            UNIQUE KEY `uk_ai_generation_request` (`owner_id`, `client_request_id`),
            UNIQUE KEY `uk_ai_generation_round` (`conversation_id`, `round_number`),
            UNIQUE KEY `uk_ai_generation_active` (`active_conversation_id`),
            UNIQUE KEY `uk_ai_generation_id_owner` (`id`, `owner_id`),
            UNIQUE KEY `uk_ai_generation_id_conversation` (`id`, `conversation_id`),
            KEY `idx_ai_generation_queue` (`status`, `created_at`, `id`),
            KEY `idx_ai_generation_claim` (`status`, `claim_until`, `id`),
            KEY `idx_ai_generation_conversation_owner` (`conversation_id`, `owner_id`),
            CONSTRAINT `ck_ai_generation__status` CHECK (`status` IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED')),
            CONSTRAINT `ck_ai_generation__answer_type` CHECK (`answer_type` IN ('ANSWERED', 'CLARIFICATION', 'INSUFFICIENT_EVIDENCE')),
            CONSTRAINT `ck_ai_generation__answer_fields` CHECK ((status = 'SUCCEEDED' AND answer_type IS NOT NULL AND answer_text IS NOT NULL AND REGEXP_LIKE(answer_text, '[^[\:space\:]]')) OR (status <> 'SUCCEEDED' AND answer_type IS NULL AND answer_text IS NULL)),
            CONSTRAINT `ck_ai_generation__claim_fields` CHECK ((status = 'RUNNING' AND started_at IS NOT NULL AND claim_token IS NOT NULL AND claim_until IS NOT NULL) OR (status <> 'RUNNING' AND claim_token IS NULL AND claim_until IS NULL)),
            CONSTRAINT `ck_ai_generation__finished_fields` CHECK ((status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED') AND finished_at IS NOT NULL) OR (status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED') AND finished_at IS NULL)),
            CONSTRAINT `ck_ai_generation__execution_time` CHECK ((started_at IS NULL OR started_at >= created_at) AND (finished_at IS NULL OR (finished_at >= created_at AND (started_at IS NULL OR finished_at >= started_at))) AND (claim_until IS NULL OR (started_at IS NOT NULL AND claim_until > started_at))),
            CONSTRAINT `ck_ai_generation__queued_fields` CHECK (status <> 'QUEUED' OR started_at IS NULL),
            CONSTRAINT `ck_ai_generation__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_generation__conversation_id_format` CHECK (REGEXP_LIKE(`conversation_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_generation__owner_id_format` CHECK (REGEXP_LIKE(`owner_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_generation__round_number_positive` CHECK (`round_number` > 0),
            CONSTRAINT `ck_ai_generation__question_text_nonblank` CHECK (REGEXP_LIKE(`question_text`, '[^[\:space\:]]')),
            CONSTRAINT `ck_ai_generation__client_request_id_format` CHECK (REGEXP_LIKE(`client_request_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_generation__request_hash_format` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
            CONSTRAINT `ck_ai_generation__session_ref_format` CHECK (REGEXP_LIKE(`session_ref`, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
            CONSTRAINT `ck_ai_generation__auth_version_range` CHECK (`auth_version` >= 0),
            CONSTRAINT `ck_ai_generation__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[\:space\:]]')),
            CONSTRAINT `ck_ai_generation__pipeline_config_schema` CHECK (JSON_SCHEMA_VALID('{"type"\:"object","required"\:["schema_version"],"properties"\:{"schema_version"\:{"type"\:"integer","minimum"\:1}}}', `pipeline_config`) = 1),
            CONSTRAINT `ck_ai_generation__trace_context_schema` CHECK (`trace_context` IS NULL OR (JSON_SCHEMA_VALID('{"type"\:"object","required"\:["schema_version","traceparent"],"properties"\:{"schema_version"\:{"type"\:"integer","enum"\:[1]},"traceparent"\:{"type"\:"string","pattern"\:"^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"},"tracestate"\:{"type"\:"string","maxLength"\:512}},"additionalProperties"\:false}', `trace_context`) = 1 AND OCTET_LENGTH(`trace_context`) <= 2048 AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 1, 2) <> 'ff' AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 4, 32) <> REPEAT('0', 32) AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 37, 16) <> REPEAT('0', 16))),
            CONSTRAINT `ck_ai_generation__context_snapshot_schema` CHECK (JSON_SCHEMA_VALID('{"type"\:"object","required"\:["schema_version"],"properties"\:{"schema_version"\:{"type"\:"integer","minimum"\:1}}}', `context_snapshot`) = 1),
            CONSTRAINT `ck_ai_generation__claim_token_format` CHECK (`claim_token` IS NULL OR (REGEXP_LIKE(`claim_token`, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'))),
            CONSTRAINT `ck_ai_generation__latency_ms_range` CHECK (`latency_ms` IS NULL OR (`latency_ms` >= 0)),
            CONSTRAINT `ck_ai_generation__input_tokens_range` CHECK (`input_tokens` IS NULL OR (`input_tokens` >= 0)),
            CONSTRAINT `ck_ai_generation__output_tokens_range` CHECK (`output_tokens` IS NULL OR (`output_tokens` >= 0)),
            CONSTRAINT `ck_ai_generation__update_time` CHECK (updated_at >= created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='单轮排障运行'
        """
    )
    op.execute(
        r"""
        -- 不可变回答引用
        CREATE TABLE `ai_citation` (
            `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
            `generation_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → ai_generation',
            `citation_number` INT NOT NULL COMMENT '当轮从1开始的实际证据编号',
            `chunk_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → ai_chunk，由片段取得任务和文档 ID',
            `title_snapshot` VARCHAR(255) NOT NULL COMMENT '当轮真实文档标题',
            `revision_label_snapshot` VARCHAR(100) NULL COMMENT '当轮修订标识',
            `section_path_snapshot` TEXT NULL COMMENT '真实章节',
            `source_locations_snapshot` JSON NOT NULL COMMENT '从真实片段复制的非空物理页数组',
            `evidence_excerpt` TEXT NOT NULL COMMENT '限长非空白摘录',
            `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
            CONSTRAINT `pk_ai_citation` PRIMARY KEY (`id`),
            UNIQUE KEY `uk_ai_citation_number` (`generation_id`, `citation_number`),
            KEY `idx_ai_citation_chunk` (`chunk_id`),
            CONSTRAINT `ck_ai_citation__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_citation__generation_id_format` CHECK (REGEXP_LIKE(`generation_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_citation__citation_number_positive` CHECK (`citation_number` > 0),
            CONSTRAINT `ck_ai_citation__chunk_id_format` CHECK (REGEXP_LIKE(`chunk_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_citation__title_snapshot_nonblank` CHECK (REGEXP_LIKE(`title_snapshot`, '[^[\:space\:]]')),
            CONSTRAINT `ck_ai_citation__source_locations_snapshot_schema` CHECK (JSON_SCHEMA_VALID('{"type"\:"array","minItems"\:1,"items"\:{"type"\:"object","required"\:["page_number"],"properties"\:{"page_number"\:{"type"\:"integer","minimum"\:1},"printed_page_label"\:{"type"\:["string","null"]}}}}', `source_locations_snapshot`) = 1),
            CONSTRAINT `ck_ai_citation__evidence_excerpt_nonblank` CHECK (REGEXP_LIKE(`evidence_excerpt`, '[^[\:space\:]]'))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='不可变回答引用'
        """
    )
    op.execute(
        r"""
        -- 报修候选草稿
        CREATE TABLE `ai_ticket_draft` (
            `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
            `owner_id` VARCHAR(32) NOT NULL COMMENT '外部客户用户 ID',
            `conversation_id` VARCHAR(32) NOT NULL COMMENT '本地复合 FK(conversation_id,owner_id) → 会话',
            `source_generation_id` VARCHAR(32) NOT NULL COMMENT '同会话已完成运行，复合 FK → ai_generation(id,conversation_id)',
            `client_request_id` VARCHAR(32) NOT NULL COMMENT '草稿生成请求的owner范围幂等键',
            `request_hash` VARCHAR(64) NOT NULL COMMENT '截止运行、设备及用户确认字段的规范化摘要',
            `content` JSON NOT NULL COMMENT 'schema_version、title、symptom、attempted_actions、missing_information',
            `expires_at` DATETIME(6) NOT NULL COMMENT '创建后24小时，不以浏览器时钟判定',
            `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
            CONSTRAINT `pk_ai_ticket_draft` PRIMARY KEY (`id`),
            UNIQUE KEY `uk_ai_ticket_draft_request` (`owner_id`, `client_request_id`),
            KEY `idx_ai_ticket_draft_conversation_owner` (`conversation_id`, `owner_id`),
            KEY `idx_ai_ticket_draft_generation_conversation` (`source_generation_id`, `conversation_id`),
            CONSTRAINT `ck_ai_ticket_draft__expires_time` CHECK (expires_at > created_at),
            CONSTRAINT `ck_ai_ticket_draft__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_ticket_draft__owner_id_format` CHECK (REGEXP_LIKE(`owner_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_ticket_draft__conversation_id_format` CHECK (REGEXP_LIKE(`conversation_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_ticket_draft__source_generation_id_format` CHECK (REGEXP_LIKE(`source_generation_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_ticket_draft__client_request_id_format` CHECK (REGEXP_LIKE(`client_request_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_ticket_draft__request_hash_format` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
            CONSTRAINT `ck_ai_ticket_draft__content_schema` CHECK (JSON_SCHEMA_VALID('{"type"\:"object","required"\:["schema_version"],"properties"\:{"schema_version"\:{"type"\:"integer","minimum"\:1}}}', `content`) = 1)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='报修候选草稿'
        """
    )
    op.execute(
        r"""
        -- 本人反馈与知识处理
        CREATE TABLE `ai_feedback` (
            `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
            `generation_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → ai_generation，单列唯一',
            `submitted_by` VARCHAR(32) NOT NULL COMMENT '(generation_id,submitted_by)复合 FK → 运行(id,owner_id)',
            `rating` VARCHAR(16) NOT NULL COMMENT 'HELPFUL / UNHELPFUL',
            `issue_category` VARCHAR(32) NULL COMMENT '无帮助必填，INCORRECT_ANSWER / MISSING_CONDITION / INCORRECT_CITATION / OUTDATED_SOURCE / OTHER',
            `comment` TEXT NULL COMMENT '限长1000字符',
            `share_with_knowledge_admin` BOOLEAN NOT NULL DEFAULT 0 COMMENT '用户明确同意展开当轮问题与回答',
            `status` VARCHAR(16) NOT NULL COMMENT 'NOT_REQUIRED / PENDING / RESOLVED / DISMISSED',
            `handled_by` VARCHAR(32) NULL COMMENT '外部知识管理员 ID',
            `handling_note` TEXT NULL COMMENT '处理终态必填，最多2000字符',
            `handled_at` DATETIME(6) NULL COMMENT '与处理人、说明一起保存',
            `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
            `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
            `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT '非负，更新比较旧值后加一',
            CONSTRAINT `pk_ai_feedback` PRIMARY KEY (`id`),
            UNIQUE KEY `uk_ai_feedback_generation` (`generation_id`),
            KEY `idx_ai_feedback_pending_shared` (`status`, `share_with_knowledge_admin`, `created_at`, `id`),
            KEY `idx_ai_feedback_generation_owner` (`generation_id`, `submitted_by`),
            CONSTRAINT `ck_ai_feedback__rating` CHECK (`rating` IN ('HELPFUL', 'UNHELPFUL')),
            CONSTRAINT `ck_ai_feedback__issue_category` CHECK (`issue_category` IN ('INCORRECT_ANSWER', 'MISSING_CONDITION', 'INCORRECT_CITATION', 'OUTDATED_SOURCE', 'OTHER')),
            CONSTRAINT `ck_ai_feedback__status` CHECK (`status` IN ('NOT_REQUIRED', 'PENDING', 'RESOLVED', 'DISMISSED')),
            CONSTRAINT `ck_ai_feedback__rating_state` CHECK ((rating = 'HELPFUL' AND status = 'NOT_REQUIRED' AND issue_category IS NULL) OR (rating = 'UNHELPFUL' AND status IN ('PENDING', 'RESOLVED', 'DISMISSED') AND issue_category IS NOT NULL)),
            CONSTRAINT `ck_ai_feedback__handled_fields` CHECK ((status IN ('RESOLVED', 'DISMISSED') AND handled_by IS NOT NULL AND handling_note IS NOT NULL AND REGEXP_LIKE(handling_note, '[^[\:space\:]]') AND handled_at IS NOT NULL AND handled_at >= created_at) OR (status IN ('NOT_REQUIRED', 'PENDING') AND handled_by IS NULL AND handling_note IS NULL AND handled_at IS NULL)),
            CONSTRAINT `ck_ai_feedback__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_feedback__generation_id_format` CHECK (REGEXP_LIKE(`generation_id`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_feedback__submitted_by_format` CHECK (REGEXP_LIKE(`submitted_by`, '^[0-9a-f]{32}$', 'c')),
            CONSTRAINT `ck_ai_feedback__rating_nonblank` CHECK (REGEXP_LIKE(`rating`, '[^[\:space\:]]')),
            CONSTRAINT `ck_ai_feedback__comment_length` CHECK (`comment` IS NULL OR (CHAR_LENGTH(`comment`) <= 1000)),
            CONSTRAINT `ck_ai_feedback__share_with_knowledge_admin_boolean` CHECK (`share_with_knowledge_admin` IN (0,1)),
            CONSTRAINT `ck_ai_feedback__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[\:space\:]]')),
            CONSTRAINT `ck_ai_feedback__handled_by_format` CHECK (`handled_by` IS NULL OR (REGEXP_LIKE(`handled_by`, '^[0-9a-f]{32}$', 'c'))),
            CONSTRAINT `ck_ai_feedback__handling_note_length` CHECK (`handling_note` IS NULL OR (CHAR_LENGTH(`handling_note`) <= 2000)),
            CONSTRAINT `ck_ai_feedback__row_version_range` CHECK (`row_version` >= 0),
            CONSTRAINT `ck_ai_feedback__update_time` CHECK (updated_at >= created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='本人反馈与知识处理'
        """
    )
    op.execute(
        r"""
        -- 全部表建立后补齐本服务外键，包含资料与索引任务的循环引用；不关闭外键检查。
        
        ALTER TABLE `ai_chunk`
            ADD CONSTRAINT `fk_ai_chunk_job_document` FOREIGN KEY (`index_job_id`, `document_id`)
            REFERENCES `ai_index_job` (`id`, `document_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
        """
    )
    op.execute(
        r"""
        ALTER TABLE `ai_generation`
            ADD CONSTRAINT `fk_ai_generation_conversation_owner` FOREIGN KEY (`conversation_id`, `owner_id`)
            REFERENCES `ai_conversation` (`id`, `owner_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
        """
    )
    op.execute(
        r"""
        ALTER TABLE `ai_citation`
            ADD CONSTRAINT `fk_ai_citation_generation` FOREIGN KEY (`generation_id`)
            REFERENCES `ai_generation` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
        """
    )
    op.execute(
        r"""
        ALTER TABLE `ai_citation`
            ADD CONSTRAINT `fk_ai_citation_chunk` FOREIGN KEY (`chunk_id`)
            REFERENCES `ai_chunk` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
        """
    )
    op.execute(
        r"""
        ALTER TABLE `ai_ticket_draft`
            ADD CONSTRAINT `fk_ai_ticket_draft_conversation_owner` FOREIGN KEY (`conversation_id`, `owner_id`)
            REFERENCES `ai_conversation` (`id`, `owner_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
        """
    )
    op.execute(
        r"""
        ALTER TABLE `ai_ticket_draft`
            ADD CONSTRAINT `fk_ai_ticket_draft_generation_conversation` FOREIGN KEY (`source_generation_id`, `conversation_id`)
            REFERENCES `ai_generation` (`id`, `conversation_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
        """
    )
    op.execute(
        r"""
        ALTER TABLE `ai_feedback`
            ADD CONSTRAINT `fk_ai_feedback_generation_owner` FOREIGN KEY (`generation_id`, `submitted_by`)
            REFERENCES `ai_generation` (`id`, `owner_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
        """
    )


def downgrade() -> None:
    """初始结构不提供自动删表，防止将回退代码误当作数据恢复。"""
    raise RuntimeError("初始结构不支持自动删除；请按备份恢复流程处理")
