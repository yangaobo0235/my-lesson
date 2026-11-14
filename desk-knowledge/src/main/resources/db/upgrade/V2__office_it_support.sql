-- 新目标的结构变更仅在同版本前置核验全部旧业务表为空后执行。

-- 不关闭外键检查，不改写V1/0001；每条DDL可能隐式提交。

ALTER TABLE `kb_document` DROP FOREIGN KEY `fk_kb_document_series`;

ALTER TABLE `kb_document` DROP FOREIGN KEY `fk_kb_document_current_job`;

ALTER TABLE `kb_document` DROP FOREIGN KEY `fk_kb_document_replaces_series`;

ALTER TABLE `kb_document_product_model` DROP FOREIGN KEY `fk_kb_document_product_model_document`;

ALTER TABLE `kb_document_event` DROP FOREIGN KEY `fk_kb_document_event_document`;

ALTER TABLE `kb_document_event` DROP FOREIGN KEY `fk_kb_document_event_related_document`;

ALTER TABLE `kb_index_job` DROP FOREIGN KEY `fk_kb_index_job_document`;

ALTER TABLE `kb_document_series`
    DROP PRIMARY KEY,
    DROP INDEX `uk_kb_document_series_identity`,
    DROP CHECK `ck_kb_document_series__source_type`,
    DROP CHECK `ck_kb_document_series__audience`,
    DROP CHECK `ck_kb_document_series__id_format`,
    DROP CHECK `ck_kb_document_series__source_type_nonblank`,
    DROP CHECK `ck_kb_document_series__source_namespace_nonblank`,
    DROP CHECK `ck_kb_document_series__source_code_nonblank`,
    DROP CHECK `ck_kb_document_series__source_organization_nonblank`,
    DROP CHECK `ck_kb_document_series__language_code_nonblank`,
    DROP CHECK `ck_kb_document_series__audience_nonblank`,
    DROP CHECK `ck_kb_document_series__created_by_format`;

ALTER TABLE `kb_document_series`
    MODIFY COLUMN `id` VARCHAR(32) NOT NULL COMMENT '主键，也是发布并发锁定对象',
    MODIFY COLUMN `source_type` VARCHAR(32) NOT NULL COMMENT 'OFFICIAL_MANUAL / TROUBLESHOOTING_GUIDE / SANITIZED_CASE',
    MODIFY COLUMN `source_namespace` VARCHAR(64) NOT NULL COMMENT '规范化发布方或内部目录代码',
    MODIFY COLUMN `source_code` VARCHAR(120) NOT NULL COMMENT '发布方范围内稳定文档号，缺少官方编号时由管理员登记受控目录码',
    MODIFY COLUMN `source_organization` VARCHAR(200) NOT NULL COMMENT '原发布方展示名称',
    MODIFY COLUMN `language_code` VARCHAR(16) NOT NULL COMMENT '规范化语言标识，区分不同语言版',
    MODIFY COLUMN `audience` VARCHAR(32) NOT NULL COMMENT 'CUSTOMER / SERVICE_STAFF',
    MODIFY COLUMN `created_by` VARCHAR(32) NOT NULL COMMENT '外部知识管理员 ID',
    MODIFY COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '系列登记时间';

ALTER TABLE `kb_document_series`
    ADD CONSTRAINT `pk_kb_document_series` PRIMARY KEY (`id`),
    ADD UNIQUE KEY `uk_kb_document_series_identity` (`source_namespace`, `source_code`, `language_code`, `audience`),
    ADD CONSTRAINT `ck_kb_document_series__source_type` CHECK (`source_type` IN ('OFFICIAL_MANUAL', 'TROUBLESHOOTING_GUIDE', 'SANITIZED_CASE')),
    ADD CONSTRAINT `ck_kb_document_series__audience` CHECK (`audience` IN ('CUSTOMER', 'SERVICE_STAFF')),
    ADD CONSTRAINT `ck_kb_document_series__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_document_series__source_type_nonblank` CHECK (REGEXP_LIKE(`source_type`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document_series__source_namespace_nonblank` CHECK (REGEXP_LIKE(`source_namespace`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document_series__source_code_nonblank` CHECK (REGEXP_LIKE(`source_code`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document_series__source_organization_nonblank` CHECK (REGEXP_LIKE(`source_organization`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document_series__language_code_nonblank` CHECK (REGEXP_LIKE(`language_code`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document_series__audience_nonblank` CHECK (REGEXP_LIKE(`audience`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document_series__created_by_format` CHECK (REGEXP_LIKE(`created_by`, '^[0-9a-f]{32}$', 'c'));

ALTER TABLE `kb_document`
    DROP PRIMARY KEY,
    DROP INDEX `uk_kb_document_storage_key`,
    DROP INDEX `uk_kb_document_upload`,
    DROP INDEX `uk_kb_document_revision`,
    DROP INDEX `uk_kb_document_id_series`,
    DROP INDEX `uk_kb_document_current`,
    DROP INDEX `uk_kb_document_replaces`,
    DROP INDEX `idx_kb_document_current_job`,
    DROP INDEX `idx_kb_document_replaces_series`,
    DROP CHECK `ck_kb_document__publication_status`,
    DROP CHECK `ck_kb_document__file_size`,
    DROP CHECK `ck_kb_document__storage_version`,
    DROP CHECK `ck_kb_document__not_self_replace`,
    DROP CHECK `ck_kb_document__published_pair`,
    DROP CHECK `ck_kb_document__published_state`,
    DROP CHECK `ck_kb_document__deleted_state`,
    DROP CHECK `ck_kb_document__time_order`,
    DROP CHECK `ck_kb_document__id_format`,
    DROP CHECK `ck_kb_document__series_id_format`,
    DROP CHECK `ck_kb_document__client_upload_id_format`,
    DROP CHECK `ck_kb_document__upload_request_hash_format`,
    DROP CHECK `ck_kb_document__revision_number_positive`,
    DROP CHECK `ck_kb_document__title_nonblank`,
    DROP CHECK `ck_kb_document__original_file_name_nonblank`,
    DROP CHECK `ck_kb_document__storage_bucket_nonblank`,
    DROP CHECK `ck_kb_document__storage_key_nonblank`,
    DROP CHECK `ck_kb_document__storage_version_id_nonblank`,
    DROP CHECK `ck_kb_document__file_sha256_format`,
    DROP CHECK `ck_kb_document__publication_status_nonblank`,
    DROP CHECK `ck_kb_document__current_job_id_format`,
    DROP CHECK `ck_kb_document__replaces_document_id_format`,
    DROP CHECK `ck_kb_document__published_by_format`,
    DROP CHECK `ck_kb_document__created_by_format`,
    DROP CHECK `ck_kb_document__row_version_range`,
    DROP CHECK `ck_kb_document__update_time`;

ALTER TABLE `kb_document`
    MODIFY COLUMN `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
    MODIFY COLUMN `series_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → kb_document_series',
    MODIFY COLUMN `client_upload_id` VARCHAR(32) NOT NULL COMMENT '上传者范围的客户端上传请求键，重试复用',
    MODIFY COLUMN `upload_request_hash` VARCHAR(64) NOT NULL COMMENT '系列、规范化元数据及文件 SHA-256 的语义摘要',
    MODIFY COLUMN `revision_number` INT NOT NULL COMMENT '系列内递增序号，锁系列后分配',
    MODIFY COLUMN `title` VARCHAR(255) NOT NULL COMMENT '真实文档标题',
    ADD COLUMN `content_format` VARCHAR(16) NOT NULL COMMENT 'PDF / MARKDOWN，创建后固定',
    MODIFY COLUMN `original_file_name` VARCHAR(255) NOT NULL COMMENT '原文件名，仅展示，Markdown为UTF-8原件',
    MODIFY COLUMN `storage_bucket` VARCHAR(63) NOT NULL COMMENT '所属环境允许的 OSS 私有 Bucket，上传时固定',
    MODIFY COLUMN `storage_key` VARCHAR(255) NOT NULL COMMENT '服务端生成的 OSS Object Key，全局唯一且不复用，客户端不能指定',
    MODIFY COLUMN `storage_version_id` VARCHAR(1024) NOT NULL COMMENT '开启版本控制后返回的 OSS Version ID，精确读取及恢复使用，不接受空值或字面量 null',
    MODIFY COLUMN `file_size_bytes` BIGINT NOT NULL COMMENT '正数，PDF至多20MiB、MARKDOWN至多1MiB',
    MODIFY COLUMN `source_url` TEXT NULL COMMENT '原始来源说明，不作为自动抓取地址',
    MODIFY COLUMN `source_review_note` VARCHAR(1000) NULL COMMENT '复核出处与使用依据，脱敏案例记录审批依据，发布必填',
    MODIFY COLUMN `revision_label` VARCHAR(100) NULL COMMENT '原文已知修订标识',
    MODIFY COLUMN `issued_on` DATE NULL COMMENT '原文发布日期',
    CHANGE COLUMN `applicable_systems` `applicability_note` VARCHAR(500) NULL COMMENT '人类可读适用条件摘要，实际匹配使用kb_document_applicability',
    MODIFY COLUMN `publication_status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / DISABLED / RETIRED / DELETED',
    MODIFY COLUMN `current_series_id` VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN publication_status IN ('PUBLISHED', 'DISABLED') THEN series_id ELSE NULL END) STORED COMMENT 'PUBLISHED/DISABLED 时为 series_id，否则 NULL；唯一',
    MODIFY COLUMN `current_job_id` VARCHAR(32) NULL COMMENT '本地复合 FK(current_job_id,id) → kb_index_job(id,document_id)，外部 AI 任务使用同一 ID',
    MODIFY COLUMN `replaces_document_id` VARCHAR(32) NULL COMMENT '本地复合 FK(replaces_document_id,series_id) → kb_document(id,series_id)，发布替换时写入',
    MODIFY COLUMN `published_at` DATETIME(6) NULL COMMENT '首次成功发布时刻',
    MODIFY COLUMN `published_by` VARCHAR(32) NULL COMMENT '外部知识管理员账号',
    MODIFY COLUMN `deleted_at` DATETIME(6) NULL COMMENT 'publication_status=DELETED 时必填',
    MODIFY COLUMN `created_by` VARCHAR(32) NOT NULL COMMENT '外部上传人账号',
    MODIFY COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
    MODIFY COLUMN `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT '非负，更新比较旧值后加一',
    DROP COLUMN `file_sha256`;

ALTER TABLE `kb_document`
    ADD CONSTRAINT `pk_kb_document` PRIMARY KEY (`id`),
    ADD UNIQUE KEY `uk_kb_document_storage_key` (`storage_key`),
    ADD UNIQUE KEY `uk_kb_document_upload` (`created_by`, `client_upload_id`),
    ADD UNIQUE KEY `uk_kb_document_revision` (`series_id`, `revision_number`),
    ADD UNIQUE KEY `uk_kb_document_id_series` (`id`, `series_id`),
    ADD UNIQUE KEY `uk_kb_document_current` (`current_series_id`),
    ADD UNIQUE KEY `uk_kb_document_replaces` (`replaces_document_id`),
    ADD KEY `idx_kb_document_current_job` (`current_job_id`, `id`),
    ADD KEY `idx_kb_document_replaces_series` (`replaces_document_id`, `series_id`),
    ADD CONSTRAINT `ck_kb_document__publication_status` CHECK (`publication_status` IN ('DRAFT', 'PUBLISHED', 'DISABLED', 'RETIRED', 'DELETED')),
    ADD CONSTRAINT `ck_kb_document__file_size` CHECK (file_size_bytes > 0 AND file_size_bytes <= 20971520),
    ADD CONSTRAINT `ck_kb_document__storage_version` CHECK (storage_version_id <> 'null'),
    ADD CONSTRAINT `ck_kb_document__not_self_replace` CHECK (replaces_document_id IS NULL OR replaces_document_id <> id),
    ADD CONSTRAINT `ck_kb_document__published_pair` CHECK ((published_at IS NULL AND published_by IS NULL) OR (published_at IS NOT NULL AND published_by IS NOT NULL)),
    ADD CONSTRAINT `ck_kb_document__published_state` CHECK ((publication_status IN ('PUBLISHED', 'DISABLED', 'RETIRED') AND published_at IS NOT NULL AND published_by IS NOT NULL AND current_job_id IS NOT NULL AND source_review_note IS NOT NULL AND REGEXP_LIKE(source_review_note, '[^[:space:]]')) OR (publication_status IN ('DRAFT', 'DELETED') AND published_at IS NULL AND published_by IS NULL)),
    ADD CONSTRAINT `ck_kb_document__deleted_state` CHECK ((publication_status = 'DELETED' AND deleted_at IS NOT NULL) OR (publication_status <> 'DELETED' AND deleted_at IS NULL)),
    ADD CONSTRAINT `ck_kb_document__time_order` CHECK ((published_at IS NULL OR published_at >= created_at) AND (deleted_at IS NULL OR deleted_at >= created_at)),
    ADD CONSTRAINT `ck_kb_document__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_document__series_id_format` CHECK (REGEXP_LIKE(`series_id`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_document__client_upload_id_format` CHECK (REGEXP_LIKE(`client_upload_id`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_document__upload_request_hash_format` CHECK (REGEXP_LIKE(`upload_request_hash`, '^[0-9a-f]{64}$', 'c')),
    ADD CONSTRAINT `ck_kb_document__revision_number_positive` CHECK (`revision_number` > 0),
    ADD CONSTRAINT `ck_kb_document__title_nonblank` CHECK (REGEXP_LIKE(`title`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document__original_file_name_nonblank` CHECK (REGEXP_LIKE(`original_file_name`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document__storage_bucket_nonblank` CHECK (REGEXP_LIKE(`storage_bucket`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document__storage_key_nonblank` CHECK (REGEXP_LIKE(`storage_key`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document__storage_version_id_nonblank` CHECK (REGEXP_LIKE(`storage_version_id`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document__publication_status_nonblank` CHECK (REGEXP_LIKE(`publication_status`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document__current_job_id_format` CHECK (`current_job_id` IS NULL OR (REGEXP_LIKE(`current_job_id`, '^[0-9a-f]{32}$', 'c'))),
    ADD CONSTRAINT `ck_kb_document__replaces_document_id_format` CHECK (`replaces_document_id` IS NULL OR (REGEXP_LIKE(`replaces_document_id`, '^[0-9a-f]{32}$', 'c'))),
    ADD CONSTRAINT `ck_kb_document__published_by_format` CHECK (`published_by` IS NULL OR (REGEXP_LIKE(`published_by`, '^[0-9a-f]{32}$', 'c'))),
    ADD CONSTRAINT `ck_kb_document__created_by_format` CHECK (REGEXP_LIKE(`created_by`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_document__row_version_range` CHECK (`row_version` >= 0),
    ADD CONSTRAINT `ck_kb_document__update_time` CHECK (updated_at >= created_at),
    ADD CONSTRAINT `ck_kb_document__content_format_contract` CHECK (REGEXP_LIKE(`content_format`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document__file_size_bytes_contract` CHECK (`file_size_bytes` > 0),
    ADD CONSTRAINT `ck_kb_document__source_url_contract` CHECK (`source_url` IS NULL OR (REGEXP_LIKE(`source_url`, '[^[:space:]]'))),
    ADD CONSTRAINT `ck_kb_document__source_review_note_contract` CHECK (`source_review_note` IS NULL OR (REGEXP_LIKE(`source_review_note`, '[^[:space:]]'))),
    ADD CONSTRAINT `ck_kb_document__revision_label_contract` CHECK (`revision_label` IS NULL OR (REGEXP_LIKE(`revision_label`, '[^[:space:]]'))),
    ADD CONSTRAINT `ck_kb_document__applicability_note_contract` CHECK (`applicability_note` IS NULL OR (REGEXP_LIKE(`applicability_note`, '[^[:space:]]'))),
    ADD CONSTRAINT `ck_kb_document__content_format_values` CHECK (`content_format` IN ('PDF', 'MARKDOWN')),
    ADD CONSTRAINT `ck_kb_document__business_1` CHECK ((((content_format = 'PDF' AND file_size_bytes BETWEEN 1 AND 20971520) OR (content_format = 'MARKDOWN' AND file_size_bytes BETWEEN 1 AND 1048576)) IS TRUE));

CREATE TABLE `kb_document_applicability` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键',
    `document_id` VARCHAR(32) NOT NULL COMMENT '本地FK→kb_document',
    `rule_number` INT NOT NULL COMMENT '修订内正序编号',
    `service_item_id` VARCHAR(32) NOT NULL COMMENT '外部ticket服务项目ID',
    `product_model_id` VARCHAR(32) NULL COMMENT '外部ticket型号ID；空表示本规则不限制型号',
    `os_name` VARCHAR(100) NULL COMMENT '规范化精确匹配，非空白',
    `os_version` VARCHAR(100) NULL COMMENT '精确版本文本，非空时要求os_name',
    `software_name` VARCHAR(100) NULL COMMENT '规范化精确软件名称',
    `software_version` VARCHAR(100) NULL COMMENT '精确版本文本，非空时要求software_name',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '规则登记时间',
    CONSTRAINT `pk_kb_document_applicability` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_document_applicability__rule` (`document_id`, `rule_number`),
    KEY `idx_kb_document_applicability__service` (`service_item_id`, `document_id`),
    CONSTRAINT `ck_kb_document_applicability__id_contract` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document_applicability__document_id_contract` CHECK (REGEXP_LIKE(`document_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document_applicability__rule_number_contract` CHECK (`rule_number` > 0),
    CONSTRAINT `ck_kb_document_applicability__service_item_id_contract` CHECK (REGEXP_LIKE(`service_item_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document_applicability__product_model_id_contract` CHECK (`product_model_id` IS NULL OR (REGEXP_LIKE(`product_model_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_kb_document_applicability__os_name_contract` CHECK (`os_name` IS NULL OR (REGEXP_LIKE(`os_name`, '[^[:space:]]'))),
    CONSTRAINT `ck_kb_document_applicability__os_version_contract` CHECK (`os_version` IS NULL OR (REGEXP_LIKE(`os_version`, '[^[:space:]]'))),
    CONSTRAINT `ck_kb_document_applicability__software_name_contract` CHECK (`software_name` IS NULL OR (REGEXP_LIKE(`software_name`, '[^[:space:]]'))),
    CONSTRAINT `ck_kb_document_applicability__software_version_contract` CHECK (`software_version` IS NULL OR (REGEXP_LIKE(`software_version`, '[^[:space:]]'))),
    CONSTRAINT `ck_kb_document_applicability__business_1` CHECK (((os_version IS NULL OR os_name IS NOT NULL) IS TRUE)),
    CONSTRAINT `ck_kb_document_applicability__business_2` CHECK (((software_version IS NULL OR software_name IS NOT NULL) IS TRUE)),
    CONSTRAINT `ck_kb_document_applicability__business_3` CHECK (((rule_number <= 50) IS TRUE))) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='资料适用规则';

ALTER TABLE `kb_document_event`
    DROP PRIMARY KEY,
    DROP INDEX `idx_kb_document_event_document_created`,
    DROP INDEX `idx_kb_document_event_related_document`,
    DROP CHECK `ck_kb_document_event__event_type`,
    DROP CHECK `ck_kb_document_event__id_format`,
    DROP CHECK `ck_kb_document_event__document_id_format`,
    DROP CHECK `ck_kb_document_event__actor_id_format`,
    DROP CHECK `ck_kb_document_event__event_type_nonblank`,
    DROP CHECK `ck_kb_document_event__related_document_id_format`,
    DROP CHECK `ck_kb_document_event__details_schema`;

ALTER TABLE `kb_document_event`
    MODIFY COLUMN `id` VARCHAR(32) NOT NULL COMMENT '主键',
    MODIFY COLUMN `document_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → kb_document',
    MODIFY COLUMN `actor_id` VARCHAR(32) NOT NULL COMMENT '外部知识管理员 ID',
    MODIFY COLUMN `event_type` VARCHAR(32) NOT NULL COMMENT 'UPLOADED / METADATA_CHANGED / INDEX_REQUESTED / INDEX_CANCEL_REQUESTED / INDEX_REQUEUED / PUBLISHED / DISABLED / RESTORED / RETIRED / DELETED',
    MODIFY COLUMN `related_document_id` VARCHAR(32) NULL COMMENT '本地 FK → kb_document，替换对应的另一修订',
    MODIFY COLUMN `details` JSON NOT NULL COMMENT 'schema_version、动作原因、状态或适用规则差异；索引动作记录job_id，取消/重放另记command及operation_id',
    MODIFY COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '动作提交时间';

ALTER TABLE `kb_document_event`
    ADD CONSTRAINT `pk_kb_document_event` PRIMARY KEY (`id`),
    ADD KEY `idx_kb_document_event_document_created` (`document_id`, `created_at`, `id`),
    ADD KEY `idx_kb_document_event_related_document` (`related_document_id`),
    ADD CONSTRAINT `ck_kb_document_event__event_type` CHECK (`event_type` IN ('UPLOADED', 'METADATA_CHANGED', 'INDEX_REQUESTED', 'INDEX_CANCEL_REQUESTED', 'INDEX_REQUEUED', 'PUBLISHED', 'DISABLED', 'RESTORED', 'RETIRED', 'DELETED')),
    ADD CONSTRAINT `ck_kb_document_event__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_document_event__document_id_format` CHECK (REGEXP_LIKE(`document_id`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_document_event__actor_id_format` CHECK (REGEXP_LIKE(`actor_id`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_document_event__event_type_nonblank` CHECK (REGEXP_LIKE(`event_type`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_document_event__related_document_id_format` CHECK (`related_document_id` IS NULL OR (REGEXP_LIKE(`related_document_id`, '^[0-9a-f]{32}$', 'c'))),
    ADD CONSTRAINT `ck_kb_document_event__details_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `details`) = 1);

ALTER TABLE `kb_index_job`
    DROP PRIMARY KEY,
    DROP INDEX `uk_kb_index_job_attempt`,
    DROP INDEX `uk_kb_index_job_id_document`,
    DROP INDEX `uk_kb_index_job_request`,
    DROP INDEX `idx_kb_index_job_submit_deadline`,
    DROP INDEX `idx_kb_index_job_cancel_deadline`,
    DROP CHECK `ck_kb_index_job__submit_state`,
    DROP CHECK `ck_kb_index_job__cancel_state`,
    DROP CHECK `ck_kb_index_job__submit_fields`,
    DROP CHECK `ck_kb_index_job__cancel_fields`,
    DROP CHECK `ck_kb_index_job__id_format`,
    DROP CHECK `ck_kb_index_job__document_id_format`,
    DROP CHECK `ck_kb_index_job__attempt_number_positive`,
    DROP CHECK `ck_kb_index_job__requested_by_format`,
    DROP CHECK `ck_kb_index_job__client_request_id_format`,
    DROP CHECK `ck_kb_index_job__request_payload_schema`,
    DROP CHECK `ck_kb_index_job__request_hash_format`,
    DROP CHECK `ck_kb_index_job__submit_state_nonblank`,
    DROP CHECK `ck_kb_index_job__cancel_operation_id_format`,
    DROP CHECK `ck_kb_index_job__cancel_requested_by_format`,
    DROP CHECK `ck_kb_index_job__cancel_state_nonblank`,
    DROP CHECK `ck_kb_index_job__trace_context_schema`,
    DROP CHECK `ck_kb_index_job__update_time`;

ALTER TABLE `kb_index_job`
    MODIFY COLUMN `id` VARCHAR(32) NOT NULL COMMENT '主键，knowledge生成，线合同为jobId，AI沿用同一任务ID',
    MODIFY COLUMN `document_id` VARCHAR(32) NOT NULL COMMENT '本地FK→kb_document',
    MODIFY COLUMN `attempt_number` INT NOT NULL COMMENT '文档内递增正序尝试号，准备失败也保留历史',
    MODIFY COLUMN `requested_by` VARCHAR(32) NOT NULL COMMENT '初始发起知识管理员',
    MODIFY COLUMN `client_request_id` VARCHAR(32) NOT NULL COMMENT '发起人的索引提交幂等键，同键沿用原jobId',
    MODIFY COLUMN `request_payload` JSON NOT NULL COMMENT 'schema_version、client_request原add输入及command_input不可变信封输入，不含传输事务ID/凭据',
    MODIFY COLUMN `request_hash` VARCHAR(64) NOT NULL COMMENT '与MQ/RPC核对的不可变输入摘要',
    MODIFY COLUMN `submit_state` VARCHAR(16) NOT NULL DEFAULT 'PREPARING' COMMENT 'PREPARING / COMMITTED / ABORTED，后两者终态',
    MODIFY COLUMN `submit_deadline_at` DATETIME(6) NOT NULL COMMENT '本地提交准备截止，首次登记后固定，初值30秒',
    MODIFY COLUMN `submit_committed_at` DATETIME(6) NULL COMMENT '当且仅当submit_state=COMMITTED必填，固定本地提交事实',
    MODIFY COLUMN `cancel_operation_id` VARCHAR(32) NULL COMMENT '当前取消操作ID；取消重试仅在前次ABORTED后分配新值',
    MODIFY COLUMN `cancel_requested_by` VARCHAR(32) NULL COMMENT '当前取消准备的知识管理员，历史由资料事件保留',
    MODIFY COLUMN `cancel_state` VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE / PREPARING / COMMITTED / ABORTED',
    MODIFY COLUMN `cancel_deadline_at` DATETIME(6) NULL COMMENT '非NONE时必填，当前取消准备截止',
    MODIFY COLUMN `cancel_committed_at` DATETIME(6) NULL COMMENT '当且仅当cancel_state=COMMITTED必填，提交后固定',
    MODIFY COLUMN `trace_context` JSON NULL COMMENT '初始提交的schema_version及W3C上下文，重放不参与摘要',
    MODIFY COLUMN `last_error_code` VARCHAR(64) NULL COMMENT '当前准备/失败的安全诊断，不作为事务提交证明',
    MODIFY COLUMN `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '首次登记准备事实',
    MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '业务状态更新时显式维护';

ALTER TABLE `kb_index_job`
    ADD CONSTRAINT `pk_kb_index_job` PRIMARY KEY (`id`),
    ADD UNIQUE KEY `uk_kb_index_job_attempt` (`document_id`, `attempt_number`),
    ADD UNIQUE KEY `uk_kb_index_job_id_document` (`id`, `document_id`),
    ADD UNIQUE KEY `uk_kb_index_job_request` (`requested_by`, `client_request_id`),
    ADD KEY `idx_kb_index_job_submit_deadline` (`submit_state`, `submit_deadline_at`, `id`),
    ADD KEY `idx_kb_index_job_cancel_deadline` (`cancel_state`, `cancel_deadline_at`, `id`),
    ADD CONSTRAINT `ck_kb_index_job__submit_state` CHECK (`submit_state` IN ('PREPARING', 'COMMITTED', 'ABORTED')),
    ADD CONSTRAINT `ck_kb_index_job__cancel_state` CHECK (`cancel_state` IN ('NONE', 'PREPARING', 'COMMITTED', 'ABORTED')),
    ADD CONSTRAINT `ck_kb_index_job__submit_fields` CHECK (submit_deadline_at > created_at AND ((submit_state = 'COMMITTED' AND submit_committed_at IS NOT NULL AND submit_committed_at >= created_at) OR (submit_state <> 'COMMITTED' AND submit_committed_at IS NULL))),
    ADD CONSTRAINT `ck_kb_index_job__cancel_fields` CHECK ((cancel_state = 'NONE' AND cancel_operation_id IS NULL AND cancel_requested_by IS NULL AND cancel_deadline_at IS NULL AND cancel_committed_at IS NULL) OR (cancel_state <> 'NONE' AND submit_state = 'COMMITTED' AND cancel_operation_id IS NOT NULL AND cancel_requested_by IS NOT NULL AND cancel_deadline_at IS NOT NULL AND cancel_deadline_at > created_at AND ((cancel_state = 'COMMITTED' AND cancel_committed_at IS NOT NULL AND cancel_committed_at >= submit_committed_at) OR (cancel_state <> 'COMMITTED' AND cancel_committed_at IS NULL)))),
    ADD CONSTRAINT `ck_kb_index_job__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_index_job__document_id_format` CHECK (REGEXP_LIKE(`document_id`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_index_job__attempt_number_positive` CHECK (`attempt_number` > 0),
    ADD CONSTRAINT `ck_kb_index_job__requested_by_format` CHECK (REGEXP_LIKE(`requested_by`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_index_job__client_request_id_format` CHECK (REGEXP_LIKE(`client_request_id`, '^[0-9a-f]{32}$', 'c')),
    ADD CONSTRAINT `ck_kb_index_job__request_payload_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `request_payload`) = 1),
    ADD CONSTRAINT `ck_kb_index_job__request_hash_format` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
    ADD CONSTRAINT `ck_kb_index_job__submit_state_nonblank` CHECK (REGEXP_LIKE(`submit_state`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_index_job__cancel_operation_id_format` CHECK (`cancel_operation_id` IS NULL OR (REGEXP_LIKE(`cancel_operation_id`, '^[0-9a-f]{32}$', 'c'))),
    ADD CONSTRAINT `ck_kb_index_job__cancel_requested_by_format` CHECK (`cancel_requested_by` IS NULL OR (REGEXP_LIKE(`cancel_requested_by`, '^[0-9a-f]{32}$', 'c'))),
    ADD CONSTRAINT `ck_kb_index_job__cancel_state_nonblank` CHECK (REGEXP_LIKE(`cancel_state`, '[^[:space:]]')),
    ADD CONSTRAINT `ck_kb_index_job__trace_context_schema` CHECK (`trace_context` IS NULL OR (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version","traceparent"],"properties":{"schema_version":{"type":"integer","enum":[1]},"traceparent":{"type":"string","pattern":"^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"},"tracestate":{"type":"string","maxLength":512}},"additionalProperties":false}', `trace_context`) = 1 AND OCTET_LENGTH(`trace_context`) <= 2048 AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 1, 2) <> 'ff' AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 4, 32) <> REPEAT('0', 32) AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 37, 16) <> REPEAT('0', 16))),
    ADD CONSTRAINT `ck_kb_index_job__update_time` CHECK (updated_at >= created_at),
    ADD CONSTRAINT `ck_kb_index_job__trace_context_contract` CHECK (`trace_context` IS NULL OR (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `trace_context`) = 1)),
    ADD CONSTRAINT `ck_kb_index_job__last_error_code_contract` CHECK (`last_error_code` IS NULL OR (REGEXP_LIKE(`last_error_code`, '[^[:space:]]')));

DROP TABLE `kb_document_product_model`;

ALTER TABLE `kb_document_applicability` ADD CONSTRAINT `fk_kb_document_applicability__document` FOREIGN KEY (`document_id`) REFERENCES `kb_document` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_document` ADD CONSTRAINT `fk_kb_document__series` FOREIGN KEY (`series_id`) REFERENCES `kb_document_series` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_document` ADD CONSTRAINT `fk_kb_document__current_job` FOREIGN KEY (`current_job_id`, `id`) REFERENCES `kb_index_job` (`id`, `document_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_document` ADD CONSTRAINT `fk_kb_document__replaces_series` FOREIGN KEY (`replaces_document_id`, `series_id`) REFERENCES `kb_document` (`id`, `series_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_document_event` ADD CONSTRAINT `fk_kb_document_event__document` FOREIGN KEY (`document_id`) REFERENCES `kb_document` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_document_event` ADD CONSTRAINT `fk_kb_document_event__related_document` FOREIGN KEY (`related_document_id`) REFERENCES `kb_document` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_index_job` ADD CONSTRAINT `fk_kb_index_job__document` FOREIGN KEY (`document_id`) REFERENCES `kb_document` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;
