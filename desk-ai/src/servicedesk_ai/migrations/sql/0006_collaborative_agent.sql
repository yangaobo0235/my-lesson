-- 协同诊断持久核心：须由同版本 Python 迁移预检后执行，不关闭外键校验。

CREATE TABLE ai_execution_gate (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  gate_key VARCHAR(32) NOT NULL COMMENT 'INTERACTIVE；固定迁移初始化，不由用户创建',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_execution_gate_0 (gate_key),
  CONSTRAINT ck_ai_execution_gate_gate_key CHECK ((gate_key IN ('INTERACTIVE')) IS TRUE),
  CONSTRAINT ck_ai_execution_gate_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_conversation (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  owner_id VARCHAR(32) NOT NULL COMMENT '外部user账号，固定',
  customer_id VARCHAR(32) NULL COMMENT '自助/技术必填；CONSULT客户账号为本组织、服务商咨询为空',
  mode VARCHAR(32) NOT NULL COMMENT 'CONSULT / CUSTOMER_SELF_SERVICE / TECHNICIAN_ASSIST',
  ticket_id VARCHAR(32) NULL COMMENT '仅TECHNICIAN_ASSIST必填，外部固定工单',
  client_request_id VARCHAR(32) NOT NULL COMMENT '拥有者范围创建会话幂等键',
  request_hash VARCHAR(64) NOT NULL COMMENT '模式/绑定目标/初始标题摘要',
  title VARCHAR(100) NOT NULL COMMENT '可改名，非空',
  message_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '锁会话分配消息序号',
  deleted_at DATETIME(6) NULL COMMENT '逻辑归档/隐藏，先终止活动运行',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '显式维护',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '非负，比较后递增',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_conversation_0 (owner_id,client_request_id),
  UNIQUE KEY uk_ai_conversation_1 (id,owner_id),
  KEY idx_ai_conversation_0 (owner_id,deleted_at,updated_at,id),
  KEY idx_ai_conversation_1 (ticket_id,owner_id,id),
  CONSTRAINT ck_ai_conversation_mode CHECK ((mode IN ('CONSULT','CUSTOMER_SELF_SERVICE','TECHNICIAN_ASSIST')) IS TRUE),
  CONSTRAINT ck_ai_conversation_binding CHECK (((mode='TECHNICIAN_ASSIST' AND ticket_id IS NOT NULL AND customer_id IS NOT NULL) OR (mode='CUSTOMER_SELF_SERVICE' AND ticket_id IS NULL AND customer_id IS NOT NULL) OR (mode='CONSULT' AND ticket_id IS NULL)) IS TRUE),
  CONSTRAINT ck_ai_conversation_title CHECK ((REGEXP_LIKE(title,'[^[:space:]]')) IS TRUE),
  CONSTRAINT ck_ai_conversation_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_conversation_owner_id_id CHECK ((REGEXP_LIKE(owner_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_conversation_customer_id_id CHECK ((customer_id IS NULL OR REGEXP_LIKE(customer_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_conversation_ticket_id_id CHECK ((ticket_id IS NULL OR REGEXP_LIKE(ticket_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_conversation_client_request_id_id CHECK ((REGEXP_LIKE(client_request_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_conversation_request_hash_hex CHECK ((REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')) IS TRUE),
  CONSTRAINT ck_ai_conversation_message_sequence_number CHECK ((message_sequence>=0) IS TRUE),
  CONSTRAINT ck_ai_conversation_row_version_number CHECK ((row_version>=0) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_diagnosis_task (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  conversation_id VARCHAR(32) NOT NULL COMMENT '本地FK→ai_conversation，CONSULT不得有诊断任务',
  service_item_id VARCHAR(32) NULL COMMENT '外部项目，自助必填；未分类技术任务可空',
  device_id VARCHAR(32) NULL COMMENT '外部可选设备',
  product_model_id VARCHAR(32) NULL COMMENT '外部可信型号，不能由模型伪造',
  create_action_id VARCHAR(32) NOT NULL COMMENT '会话范围任务创建键，来自clientActionId',
  create_request_hash VARCHAR(64) NOT NULL COMMENT '目标、范围、初始环境的规范摘要',
  goal VARCHAR(1000) NOT NULL COMMENT '一个明确故障目标',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / WAITING_USER / WAITING_APPROVAL / BLOCKED_EXTERNAL / RESOLVED_REPORTED / HANDED_OFF / CLOSED / CANCELLED',
  active_conversation_id VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN status IN ('ACTIVE','WAITING_USER','WAITING_APPROVAL','BLOCKED_EXTERNAL') THEN conversation_id ELSE NULL END) STORED COMMENT '前四种非终态为conversation_id，否则NULL；唯一',
  task_state_version BIGINT NOT NULL DEFAULT 0 COMMENT '诊断事实/检查/假设投影变更递增',
  diagnosis_context_version BIGINT NOT NULL DEFAULT 0 COMMENT '自助由本库维护，技术取ticket同名版本',
  support_context JSON NOT NULL COMMENT 'schema_version、具名环境及来源',
  hypothesis_state JSON NOT NULL COMMENT 'schema_version、items；最多8个当前假设及具名支持/反证事实引用，最多32KiB',
  ticket_event_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '已读取到的正式事件游标，技术模式使用',
  procedure_document_id VARCHAR(32) NULL COMMENT '外部固定知识修订',
  procedure_sha256 VARCHAR(64) NULL COMMENT '与procedure_document_id同空同有',
  budget_policy JSON NOT NULL COMMENT 'schema_version、累计调用/Token/费用上限及币种，显式调整记事件',
  blocking_draft_root_id VARCHAR(32) NULL COMMENT '本地FK→ai_action_draft，WAITING_APPROVAL必填且同任务根草稿',
  blocked_reason VARCHAR(1000) NULL COMMENT '当且仅当BLOCKED_EXTERNAL非空，说明外部条件',
  outcome_source VARCHAR(32) NULL COMMENT '终态来源USER_REPORT / HUMAN_RECORD / SYSTEM_RULE',
  outcome_note VARCHAR(2000) NULL COMMENT '终态非空，来源和未决事项',
  handover_ticket_id VARCHAR(32) NULL COMMENT '外部正式回执定位，HANDED_OFF须已证实存在',
  closed_at DATETIME(6) NULL COMMENT '当且仅当终态非空',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '显式维护',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '非负，比较后递增',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_diagnosis_task_0 (conversation_id,create_action_id),
  UNIQUE KEY uk_ai_diagnosis_task_1 (active_conversation_id),
  UNIQUE KEY uk_ai_diagnosis_task_2 (id,conversation_id),
  KEY idx_ai_diagnosis_task_0 (conversation_id,created_at,id),
  KEY idx_ai_diagnosis_task_1 (status,updated_at,id),
  CONSTRAINT ck_ai_diagnosis_task_status CHECK ((status IN ('ACTIVE','WAITING_USER','WAITING_APPROVAL','BLOCKED_EXTERNAL','RESOLVED_REPORTED','HANDED_OFF','CLOSED','CANCELLED')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_outcome_source CHECK ((outcome_source IS NULL OR (outcome_source IN ('USER_REPORT','HUMAN_RECORD','SYSTEM_RULE'))) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_procedure CHECK (((procedure_document_id IS NULL AND procedure_sha256 IS NULL) OR (procedure_document_id IS NOT NULL AND procedure_sha256 IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_approval CHECK (((status='WAITING_APPROVAL')=(blocking_draft_root_id IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_blocked CHECK (((status='BLOCKED_EXTERNAL' AND blocked_reason IS NOT NULL AND REGEXP_LIKE(blocked_reason,'[^[:space:]]')) OR (status<>'BLOCKED_EXTERNAL' AND blocked_reason IS NULL)) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_terminal CHECK (((status IN ('RESOLVED_REPORTED','HANDED_OFF','CLOSED','CANCELLED') AND closed_at IS NOT NULL AND outcome_source IS NOT NULL AND outcome_note IS NOT NULL AND REGEXP_LIKE(outcome_note,'[^[:space:]]')) OR (status NOT IN ('RESOLVED_REPORTED','HANDED_OFF','CLOSED','CANCELLED') AND closed_at IS NULL AND outcome_source IS NULL AND outcome_note IS NULL)) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_handover CHECK ((status<>'HANDED_OFF' OR handover_ticket_id IS NOT NULL) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_goal CHECK ((REGEXP_LIKE(goal,'[^[:space:]]')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_hypotheses CHECK ((JSON_TYPE(hypothesis_state->'$.items')='ARRAY' AND JSON_LENGTH(hypothesis_state->'$.items')<=8 AND OCTET_LENGTH(hypothesis_state)<=32768) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_conversation_id_id CHECK ((REGEXP_LIKE(conversation_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_service_item_id_id CHECK ((service_item_id IS NULL OR REGEXP_LIKE(service_item_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_device_id_id CHECK ((device_id IS NULL OR REGEXP_LIKE(device_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_product_model_id_id CHECK ((product_model_id IS NULL OR REGEXP_LIKE(product_model_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_create_action_id_id CHECK ((REGEXP_LIKE(create_action_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_create_request_hash_hex CHECK ((REGEXP_LIKE(create_request_hash,'^[0-9a-f]{64}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_active_conversation_id_id CHECK ((active_conversation_id IS NULL OR REGEXP_LIKE(active_conversation_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_task_state_version_number CHECK ((task_state_version>=0) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_diagnosis_context_version_number CHECK ((diagnosis_context_version>=0) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_support_context_json CHECK (((JSON_TYPE(support_context)='OBJECT' AND JSON_TYPE(support_context->'$.schema_version')='INTEGER' AND support_context->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_hypothesis_state_json CHECK (((JSON_TYPE(hypothesis_state)='OBJECT' AND JSON_TYPE(hypothesis_state->'$.schema_version')='INTEGER' AND hypothesis_state->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_ticket_event_sequence_number CHECK ((ticket_event_sequence>=0) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_procedure_document_id_id CHECK ((procedure_document_id IS NULL OR REGEXP_LIKE(procedure_document_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_procedure_sha256_hex CHECK ((procedure_sha256 IS NULL OR REGEXP_LIKE(procedure_sha256,'^[0-9a-f]{64}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_budget_policy_json CHECK (((JSON_TYPE(budget_policy)='OBJECT' AND JSON_TYPE(budget_policy->'$.schema_version')='INTEGER' AND budget_policy->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_blocking_draft_root_id_id CHECK ((blocking_draft_root_id IS NULL OR REGEXP_LIKE(blocking_draft_root_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_handover_ticket_id_id CHECK ((handover_ticket_id IS NULL OR REGEXP_LIKE(handover_ticket_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnosis_task_row_version_number CHECK ((row_version>=0) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_message (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  conversation_id VARCHAR(32) NOT NULL COMMENT '本地FK→ai_conversation',
  task_id VARCHAR(32) NULL COMMENT '本地复合FK(task_id,conversation_id)→ai_diagnosis_task(id,conversation_id)',
  sequence_number BIGINT NOT NULL COMMENT '会话内正序',
  role VARCHAR(16) NOT NULL COMMENT 'USER / ASSISTANT / SYSTEM；不把供应商tool消息直接当用户聊天',
  content TEXT NOT NULL COMMENT '用户输入或已核验输出，最多12000字符/48KiB',
  content_sha256 VARCHAR(64) NOT NULL COMMENT '正文摘要',
  client_request_id VARCHAR(32) NULL COMMENT 'USER提交的幂等键，其他为空',
  request_hash VARCHAR(64) NULL COMMENT '与client_request_id同空同有',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_message_0 (conversation_id,sequence_number),
  UNIQUE KEY uk_ai_message_1 (conversation_id,client_request_id),
  UNIQUE KEY uk_ai_message_2 (id,conversation_id),
  CONSTRAINT ck_ai_message_role CHECK ((role IN ('USER','ASSISTANT','SYSTEM')) IS TRUE),
  CONSTRAINT ck_ai_message_user_key CHECK (((role='USER' AND client_request_id IS NOT NULL AND request_hash IS NOT NULL) OR (role<>'USER' AND client_request_id IS NULL AND request_hash IS NULL)) IS TRUE),
  CONSTRAINT ck_ai_message_body CHECK ((CHAR_LENGTH(content) BETWEEN 1 AND 12000 AND OCTET_LENGTH(content)<=49152 AND REGEXP_LIKE(content,'[^[:space:]]')) IS TRUE),
  CONSTRAINT ck_ai_message_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_message_conversation_id_id CHECK ((REGEXP_LIKE(conversation_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_message_task_id_id CHECK ((task_id IS NULL OR REGEXP_LIKE(task_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_message_sequence_number_number CHECK ((sequence_number>0) IS TRUE),
  CONSTRAINT ck_ai_message_content_sha256_hex CHECK ((REGEXP_LIKE(content_sha256,'^[0-9a-f]{64}$','c')) IS TRUE),
  CONSTRAINT ck_ai_message_client_request_id_id CHECK ((client_request_id IS NULL OR REGEXP_LIKE(client_request_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_message_request_hash_hex CHECK ((request_hash IS NULL OR REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_task_event (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  task_id VARCHAR(32) NOT NULL COMMENT '本地FK→ai_diagnosis_task',
  state_version BIGINT NOT NULL COMMENT '任务提交后的版本，正数',
  actor_type VARCHAR(16) NOT NULL COMMENT 'USER / WORKER / SYSTEM',
  actor_id VARCHAR(32) NULL COMMENT 'USER必填，其他为空',
  client_action_id VARCHAR(32) NULL COMMENT 'USER必填，其他为空',
  request_hash VARCHAR(64) NULL COMMENT '与动作键同空同有',
  event_type VARCHAR(32) NOT NULL COMMENT 'TASK_CREATED / FACT_CHANGED / CHECK_ANSWERED / CONTEXT_SYNCED / RUN_COMPLETED / TASK_CLOSED / BUDGET_CHANGED / CHECK_EXPIRED / CHECK_CHANGED / HYPOTHESIS_CHANGED',
  details JSON NOT NULL COMMENT 'schema_version、变更ID集合/版本、结果状态及安全原因',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_task_event_0 (task_id,state_version),
  UNIQUE KEY uk_ai_task_event_1 (task_id,actor_id,client_action_id),
  CONSTRAINT ck_ai_task_event_actor_type CHECK ((actor_type IN ('USER','WORKER','SYSTEM')) IS TRUE),
  CONSTRAINT ck_ai_task_event_event_type CHECK ((event_type IN ('TASK_CREATED','FACT_CHANGED','CHECK_ANSWERED','CONTEXT_SYNCED','RUN_COMPLETED','TASK_CLOSED','BUDGET_CHANGED','CHECK_EXPIRED','CHECK_CHANGED','HYPOTHESIS_CHANGED')) IS TRUE),
  CONSTRAINT ck_ai_task_event_actor CHECK (((actor_type='USER' AND actor_id IS NOT NULL AND client_action_id IS NOT NULL AND request_hash IS NOT NULL) OR (actor_type<>'USER' AND actor_id IS NULL AND client_action_id IS NULL AND request_hash IS NULL)) IS TRUE),
  CONSTRAINT ck_ai_task_event_details_size CHECK ((OCTET_LENGTH(details)<=98304) IS TRUE),
  CONSTRAINT ck_ai_task_event_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_task_event_task_id_id CHECK ((REGEXP_LIKE(task_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_task_event_state_version_number CHECK ((state_version>0) IS TRUE),
  CONSTRAINT ck_ai_task_event_actor_id_id CHECK ((actor_id IS NULL OR REGEXP_LIKE(actor_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_task_event_client_action_id_id CHECK ((client_action_id IS NULL OR REGEXP_LIKE(client_action_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_task_event_request_hash_hex CHECK ((request_hash IS NULL OR REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')) IS TRUE),
  CONSTRAINT ck_ai_task_event_details_json CHECK (((JSON_TYPE(details)='OBJECT' AND JSON_TYPE(details->'$.schema_version')='INTEGER' AND details->'$.schema_version'=1)) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_run (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  conversation_id VARCHAR(32) NOT NULL COMMENT '本地FK→ai_conversation',
  task_id VARCHAR(32) NULL COMMENT '本地复合FK(task_id,conversation_id)→ai_diagnosis_task(id,conversation_id)，CONSULT为空',
  owner_id VARCHAR(32) NOT NULL COMMENT '复合FK(conversation_id,owner_id)→ai_conversation(id,owner_id)',
  input_message_id VARCHAR(32) NOT NULL COMMENT '复合FK(input_message_id,conversation_id)→ai_message(id,conversation_id)',
  output_message_id VARCHAR(32) NULL COMMENT '复合FK(output_message_id,conversation_id)→ai_message(id,conversation_id)，SUCCEEDED必填',
  resumes_run_id VARCHAR(32) NULL COMMENT '本地复合FK(resumes_run_id,conversation_id)→ai_run(id,conversation_id)，续行原运行',
  client_request_id VARCHAR(32) NOT NULL COMMENT '拥有者范围受理键',
  request_hash VARCHAR(64) NOT NULL COMMENT '输入/任务版本/续行身份摘要',
  status VARCHAR(16) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED / RUNNING / SUCCEEDED / FAILED / CANCELLED / INTERRUPTED',
  active_conversation_id VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN status IN ('QUEUED','RUNNING') THEN conversation_id ELSE NULL END) STORED COMMENT 'QUEUED/RUNNING为conversation_id，否则NULL；唯一',
  active_task_id VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN status IN ('QUEUED','RUNNING') THEN task_id ELSE NULL END) STORED COMMENT 'QUEUED/RUNNING为task_id，否则NULL；唯一',
  input_state_version BIGINT NULL COMMENT '有task时必填，受理时版本',
  output_state_version BIGINT NULL COMMENT '成功任务运行提交后版本',
  diagnosis_context_version BIGINT NULL COMMENT '任务运行固定环境版本',
  ticket_row_version BIGINT NULL COMMENT '技术输入业务版本，仅快照，不作为历史阅读授权',
  ticket_event_sequence BIGINT NULL COMMENT '本轮输入正式时间线游标',
  session_ref VARCHAR(36) NOT NULL COMMENT '当前非凭证Session引用',
  auth_version BIGINT NOT NULL COMMENT '原身份版本',
  pipeline_config JSON NOT NULL COMMENT '模型、工具、Prompt、空间、估算器、策略及预算不可变配置',
  context_manifest JSON NOT NULL COMMENT '输入消息/事实/检查/来源ID和版本，不复制原始隐藏推理',
  execution_phase VARCHAR(16) NOT NULL DEFAULT 'CONTEXT' COMMENT 'CONTEXT / MODEL / TOOL / COMMIT；仅表示本轮最后持久执行位置',
  iteration_number INT NOT NULL DEFAULT 0 COMMENT '有界循环序号，非负；调用身份由各call表编号确定',
  structured_result JSON NULL COMMENT '成功结构化回答/请求检查/建议/交接结果',
  answer_type VARCHAR(32) NULL COMMENT 'ANSWERED / CLARIFICATION / INSUFFICIENT_EVIDENCE / HANDOVER；成功必填',
  trace_context JSON NULL COMMENT '受控W3C上下文',
  deadline_at DATETIME(6) NOT NULL COMMENT '受理含排队总截止',
  claim_token VARCHAR(36) NULL COMMENT 'RUNNING执行令牌',
  claim_until DATETIME(6) NULL COMMENT 'RUNNING租约截止',
  started_at DATETIME(6) NULL COMMENT '首次执行时刻',
  finished_at DATETIME(6) NULL COMMENT '终态时刻',
  error_code VARCHAR(64) NULL COMMENT '失败/中断诊断，不是HTTP响应码',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '显式维护',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_run_0 (owner_id,client_request_id),
  UNIQUE KEY uk_ai_run_1 (active_conversation_id),
  UNIQUE KEY uk_ai_run_2 (active_task_id),
  UNIQUE KEY uk_ai_run_3 (output_message_id),
  UNIQUE KEY uk_ai_run_4 (id,conversation_id),
  UNIQUE KEY uk_ai_run_5 (id,task_id),
  KEY idx_ai_run_0 (status,created_at,id),
  KEY idx_ai_run_1 (status,claim_until,id),
  KEY idx_ai_run_2 (task_id,created_at,id),
  CONSTRAINT ck_ai_run_status CHECK ((status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED','INTERRUPTED')) IS TRUE),
  CONSTRAINT ck_ai_run_execution_phase CHECK ((execution_phase IN ('CONTEXT','MODEL','TOOL','COMMIT')) IS TRUE),
  CONSTRAINT ck_ai_run_answer_type CHECK ((answer_type IS NULL OR (answer_type IN ('ANSWERED','CLARIFICATION','INSUFFICIENT_EVIDENCE','HANDOVER'))) IS TRUE),
  CONSTRAINT ck_ai_run_lease CHECK (((status='RUNNING' AND claim_token IS NOT NULL AND claim_until IS NOT NULL AND started_at IS NOT NULL) OR (status<>'RUNNING' AND claim_token IS NULL AND claim_until IS NULL)) IS TRUE),
  CONSTRAINT ck_ai_run_terminal CHECK (((status IN ('SUCCEEDED','FAILED','CANCELLED','INTERRUPTED'))=(finished_at IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_run_success CHECK (((status='SUCCEEDED' AND output_message_id IS NOT NULL AND structured_result IS NOT NULL AND answer_type IS NOT NULL AND started_at IS NOT NULL) OR (status<>'SUCCEEDED' AND output_message_id IS NULL AND structured_result IS NULL AND answer_type IS NULL AND output_state_version IS NULL)) IS TRUE),
  CONSTRAINT ck_ai_run_task_version CHECK (((task_id IS NULL AND input_state_version IS NULL AND output_state_version IS NULL AND diagnosis_context_version IS NULL) OR (task_id IS NOT NULL AND input_state_version IS NOT NULL AND diagnosis_context_version IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_run_task_success CHECK ((status<>'SUCCEEDED' OR task_id IS NULL OR output_state_version IS NOT NULL) IS TRUE),
  CONSTRAINT ck_ai_run_not_self CHECK ((resumes_run_id IS NULL OR resumes_run_id<>id) IS TRUE),
  CONSTRAINT ck_ai_run_context_size CHECK ((OCTET_LENGTH(context_manifest)<=65536 AND OCTET_LENGTH(pipeline_config)<=32768) IS TRUE),
  CONSTRAINT ck_ai_run_time CHECK ((deadline_at>created_at AND (started_at IS NULL OR started_at>=created_at) AND (finished_at IS NULL OR finished_at>=created_at)) IS TRUE),
  CONSTRAINT ck_ai_run_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_conversation_id_id CHECK ((REGEXP_LIKE(conversation_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_task_id_id CHECK ((task_id IS NULL OR REGEXP_LIKE(task_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_owner_id_id CHECK ((REGEXP_LIKE(owner_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_input_message_id_id CHECK ((REGEXP_LIKE(input_message_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_output_message_id_id CHECK ((output_message_id IS NULL OR REGEXP_LIKE(output_message_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_resumes_run_id_id CHECK ((resumes_run_id IS NULL OR REGEXP_LIKE(resumes_run_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_client_request_id_id CHECK ((REGEXP_LIKE(client_request_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_request_hash_hex CHECK ((REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_active_conversation_id_id CHECK ((active_conversation_id IS NULL OR REGEXP_LIKE(active_conversation_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_active_task_id_id CHECK ((active_task_id IS NULL OR REGEXP_LIKE(active_task_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_run_input_state_version_number CHECK ((input_state_version IS NULL OR input_state_version>=0) IS TRUE),
  CONSTRAINT ck_ai_run_output_state_version_number CHECK ((output_state_version IS NULL OR output_state_version>=0) IS TRUE),
  CONSTRAINT ck_ai_run_diagnosis_context_version_number CHECK ((diagnosis_context_version IS NULL OR diagnosis_context_version>=0) IS TRUE),
  CONSTRAINT ck_ai_run_ticket_row_version_number CHECK ((ticket_row_version IS NULL OR ticket_row_version>=0) IS TRUE),
  CONSTRAINT ck_ai_run_ticket_event_sequence_number CHECK ((ticket_event_sequence IS NULL OR ticket_event_sequence>=0) IS TRUE),
  CONSTRAINT ck_ai_run_auth_version_number CHECK ((auth_version>=0) IS TRUE),
  CONSTRAINT ck_ai_run_pipeline_config_json CHECK (((JSON_TYPE(pipeline_config)='OBJECT' AND JSON_TYPE(pipeline_config->'$.schema_version')='INTEGER' AND pipeline_config->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_run_context_manifest_json CHECK (((JSON_TYPE(context_manifest)='OBJECT' AND JSON_TYPE(context_manifest->'$.schema_version')='INTEGER' AND context_manifest->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_run_iteration_number_number CHECK ((iteration_number>=0) IS TRUE),
  CONSTRAINT ck_ai_run_structured_result_json CHECK ((structured_result IS NULL OR (JSON_TYPE(structured_result)='OBJECT' AND JSON_TYPE(structured_result->'$.schema_version')='INTEGER' AND structured_result->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_run_trace_context_json CHECK ((trace_context IS NULL OR (JSON_TYPE(trace_context)='OBJECT' AND JSON_TYPE(trace_context->'$.schema_version')='INTEGER' AND trace_context->'$.schema_version'=1)) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_diagnostic_fact (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  task_id VARCHAR(32) NOT NULL COMMENT '本地FK→ai_diagnosis_task',
  state_version BIGINT NOT NULL COMMENT '本地复合FK(task_id,state_version)→ai_task_event(task_id,state_version)',
  fact_kind VARCHAR(32) NOT NULL COMMENT 'SYMPTOM / ENVIRONMENT / ACTION_REPORT / OBSERVATION / OUTCOME',
  operation VARCHAR(16) NOT NULL COMMENT 'ASSERT / CORRECT / RETRACT',
  statement TEXT NOT NULL COMMENT '最多2000字符，原始反馈或明确观测',
  source_type VARCHAR(32) NOT NULL COMMENT 'USER_REPORT / HUMAN_RECORD / TOOL_OBSERVATION',
  message_id VARCHAR(32) NULL COMMENT '本地FK→ai_message，人工反馈来源',
  source_tool_call_id VARCHAR(32) NULL COMMENT '本地FK→ai_tool_call，工具观测必填',
  reported_by VARCHAR(32) NULL COMMENT '用户/人工来源必填，工具为空',
  source_locator JSON NOT NULL COMMENT 'schema_version、原消息/正式事件/工具字段定位',
  environment_snapshot JSON NOT NULL COMMENT 'schema_version、当时环境和版本',
  supersedes_fact_id VARCHAR(32) NULL COMMENT '本地复合FK(supersedes_fact_id,task_id)→ai_diagnostic_fact(id,task_id)',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_diagnostic_fact_0 (id,task_id),
  UNIQUE KEY uk_ai_diagnostic_fact_1 (supersedes_fact_id),
  KEY idx_ai_diagnostic_fact_0 (task_id,state_version,id),
  CONSTRAINT ck_ai_diagnostic_fact_fact_kind CHECK ((fact_kind IN ('SYMPTOM','ENVIRONMENT','ACTION_REPORT','OBSERVATION','OUTCOME')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_operation CHECK ((operation IN ('ASSERT','CORRECT','RETRACT')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_source_type CHECK ((source_type IN ('USER_REPORT','HUMAN_RECORD','TOOL_OBSERVATION')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_predecessor CHECK (((operation='ASSERT' AND supersedes_fact_id IS NULL) OR (operation IN ('CORRECT','RETRACT') AND supersedes_fact_id IS NOT NULL AND supersedes_fact_id<>id)) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_origin CHECK (((source_type IN ('USER_REPORT','HUMAN_RECORD') AND message_id IS NOT NULL AND reported_by IS NOT NULL AND source_tool_call_id IS NULL) OR (source_type='TOOL_OBSERVATION' AND message_id IS NULL AND reported_by IS NULL AND source_tool_call_id IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_statement CHECK ((CHAR_LENGTH(statement) BETWEEN 1 AND 2000 AND REGEXP_LIKE(statement,'[^[:space:]]')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_task_id_id CHECK ((REGEXP_LIKE(task_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_state_version_number CHECK ((state_version>0) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_message_id_id CHECK ((message_id IS NULL OR REGEXP_LIKE(message_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_source_tool_call_id_id CHECK ((source_tool_call_id IS NULL OR REGEXP_LIKE(source_tool_call_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_reported_by_id CHECK ((reported_by IS NULL OR REGEXP_LIKE(reported_by,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_source_locator_json CHECK (((JSON_TYPE(source_locator)='OBJECT' AND JSON_TYPE(source_locator->'$.schema_version')='INTEGER' AND source_locator->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_environment_snapshot_json CHECK (((JSON_TYPE(environment_snapshot)='OBJECT' AND JSON_TYPE(environment_snapshot->'$.schema_version')='INTEGER' AND environment_snapshot->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_fact_supersedes_fact_id_id CHECK ((supersedes_fact_id IS NULL OR REGEXP_LIKE(supersedes_fact_id,'^[0-9a-f]{32}$','c')) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_diagnostic_check (
  id VARCHAR(32) NOT NULL COMMENT '主键，同时为唯一反馈请求身份checkId',
  task_id VARCHAR(32) NOT NULL COMMENT '本地FK→ai_diagnosis_task',
  proposed_run_id VARCHAR(32) NOT NULL COMMENT '复合FK(proposed_run_id,task_id)→ai_run(id,task_id)',
  check_key VARCHAR(64) NOT NULL COMMENT '类型化澄清或检查键，不由任意文本生成执行逻辑',
  check_kind VARCHAR(16) NOT NULL COMMENT 'CLARIFICATION / CHECK，澄清不要求伪造已执行检查',
  attempt_number INT NOT NULL COMMENT '同任务同检查键正序实例',
  procedure_document_id VARCHAR(32) NULL COMMENT '外部固定知识修订，采用规程时必填',
  procedure_check_key VARCHAR(64) NULL COMMENT '与procedure_document_id同空同有，属于其不可变定义',
  instruction TEXT NOT NULL COMMENT '提问或检查指引及风险/停止条件，最多4000字符',
  answer_schema JSON NOT NULL COMMENT 'schema_version、TEXT/CHOICE/BOOLEAN/NUMBER及选项/范围；无默认回答',
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / ANSWERED / OBSERVED_DONE / FAILED / SKIPPED / WITHDRAWN / EXPIRED / NEEDS_RECHECK',
  executor_type VARCHAR(16) NOT NULL COMMENT 'CUSTOMER / TECHNICIAN / TOOL',
  requested_actor_id VARCHAR(32) NULL COMMENT '人工回复固定为会话拥有者；TOOL为空',
  context_version BIGINT NOT NULL COMMENT '本实例创建时的环境版本',
  dependency_fields JSON NOT NULL COMMENT 'schema_version、环境依赖字段items；白名单字段',
  answer_message_id VARCHAR(32) NULL COMMENT '本地FK→ai_message，人工答案消息；同任务由用例校验',
  normalized_answer JSON NULL COMMENT 'schema_version、规范值及来源；人工回答或工具观测完成后保存',
  answered_by VARCHAR(32) NULL COMMENT '人工回答时必填且等于requested_actor_id；TOOL为空',
  answered_at DATETIME(6) NULL COMMENT '人工答案提交时刻；TOOL为空',
  result_fact_id VARCHAR(32) NULL COMMENT '复合FK(result_fact_id,task_id)→ai_diagnostic_fact(id,task_id)',
  previous_check_id VARCHAR(32) NULL COMMENT '复合FK(previous_check_id,task_id)→ai_diagnostic_check(id,task_id)，复查前驱',
  expires_at DATETIME(6) NULL COMMENT '人工请求创建后7天；TOOL为空并受Run截止约束',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '显式维护',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '非负，比较后递增',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_diagnostic_check_0 (task_id,check_key,attempt_number),
  UNIQUE KEY uk_ai_diagnostic_check_1 (id,task_id),
  UNIQUE KEY uk_ai_diagnostic_check_2 (answer_message_id),
  KEY idx_ai_diagnostic_check_0 (task_id,status,created_at,id),
  KEY idx_ai_diagnostic_check_1 (status,expires_at,id),
  CONSTRAINT ck_ai_diagnostic_check_check_kind CHECK ((check_kind IN ('CLARIFICATION','CHECK')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_status CHECK ((status IN ('OPEN','ANSWERED','OBSERVED_DONE','FAILED','SKIPPED','WITHDRAWN','EXPIRED','NEEDS_RECHECK')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_executor_type CHECK ((executor_type IN ('CUSTOMER','TECHNICIAN','TOOL')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_procedure CHECK (((procedure_document_id IS NULL AND procedure_check_key IS NULL) OR (procedure_document_id IS NOT NULL AND procedure_check_key IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_human_answer CHECK (((answer_message_id IS NULL AND answered_by IS NULL AND answered_at IS NULL) OR (answer_message_id IS NOT NULL AND answered_by IS NOT NULL AND answered_at IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_executor CHECK (((executor_type='TOOL' AND check_kind='CHECK' AND requested_actor_id IS NULL AND expires_at IS NULL) OR (executor_type IN ('CUSTOMER','TECHNICIAN') AND requested_actor_id IS NOT NULL AND expires_at>created_at)) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_answer CHECK (((status='ANSWERED' AND (executor_type<>'TOOL' AND answer_message_id IS NOT NULL AND answered_by=requested_actor_id AND answered_by IS NOT NULL AND answered_at IS NOT NULL AND normalized_answer IS NOT NULL AND result_fact_id IS NOT NULL)) OR (status='OBSERVED_DONE' AND (executor_type='TOOL' AND answer_message_id IS NULL AND answered_by IS NULL AND answered_at IS NULL AND normalized_answer IS NOT NULL AND result_fact_id IS NOT NULL)) OR (status IN ('OPEN','FAILED','SKIPPED','WITHDRAWN','EXPIRED') AND (answer_message_id IS NULL AND answered_by IS NULL AND answered_at IS NULL AND normalized_answer IS NULL AND result_fact_id IS NULL)) OR (status='NEEDS_RECHECK' AND ((executor_type<>'TOOL' AND answer_message_id IS NOT NULL AND answered_by=requested_actor_id AND answered_by IS NOT NULL AND answered_at IS NOT NULL AND normalized_answer IS NOT NULL AND result_fact_id IS NOT NULL) OR (executor_type='TOOL' AND answer_message_id IS NULL AND answered_by IS NULL AND answered_at IS NULL AND normalized_answer IS NOT NULL AND result_fact_id IS NOT NULL) OR (answer_message_id IS NULL AND answered_by IS NULL AND answered_at IS NULL AND normalized_answer IS NULL AND result_fact_id IS NULL)))) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_not_self CHECK ((previous_check_id IS NULL OR previous_check_id<>id) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_instruction CHECK ((CHAR_LENGTH(instruction) BETWEEN 1 AND 4000 AND REGEXP_LIKE(instruction,'[^[:space:]]')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_task_id_id CHECK ((REGEXP_LIKE(task_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_proposed_run_id_id CHECK ((REGEXP_LIKE(proposed_run_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_attempt_number_number CHECK ((attempt_number>0) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_procedure_document_id_id CHECK ((procedure_document_id IS NULL OR REGEXP_LIKE(procedure_document_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_answer_schema_json CHECK (((JSON_TYPE(answer_schema)='OBJECT' AND JSON_TYPE(answer_schema->'$.schema_version')='INTEGER' AND answer_schema->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_requested_actor_id_id CHECK ((requested_actor_id IS NULL OR REGEXP_LIKE(requested_actor_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_context_version_number CHECK ((context_version>=0) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_dependency_fields_json CHECK (((JSON_TYPE(dependency_fields)='OBJECT' AND JSON_TYPE(dependency_fields->'$.schema_version')='INTEGER' AND dependency_fields->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_answer_message_id_id CHECK ((answer_message_id IS NULL OR REGEXP_LIKE(answer_message_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_normalized_answer_json CHECK ((normalized_answer IS NULL OR (JSON_TYPE(normalized_answer)='OBJECT' AND JSON_TYPE(normalized_answer->'$.schema_version')='INTEGER' AND normalized_answer->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_answered_by_id CHECK ((answered_by IS NULL OR REGEXP_LIKE(answered_by,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_result_fact_id_id CHECK ((result_fact_id IS NULL OR REGEXP_LIKE(result_fact_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_previous_check_id_id CHECK ((previous_check_id IS NULL OR REGEXP_LIKE(previous_check_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_diagnostic_check_row_version_number CHECK ((row_version>=0) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_model_call (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  run_id VARCHAR(32) NULL COMMENT '本地FK→ai_run；与index_job_id恰有一个',
  index_job_id VARCHAR(32) NULL COMMENT '本地FK→ai_index_job，索引Embedding',
  call_number INT NOT NULL COMMENT '所属运行/索引任务内正序，实际重试也新增',
  purpose VARCHAR(24) NOT NULL COMMENT 'CHAT / QUERY_EMBEDDING / INDEX_EMBEDDING / SUMMARY / RERANK / VALIDATION',
  status VARCHAR(16) NOT NULL DEFAULT 'PREPARED' COMMENT 'PREPARED / IN_FLIGHT / SUCCEEDED / FAILED / UNKNOWN / CANCELLED',
  claim_token VARCHAR(36) NOT NULL COMMENT '外调意图所属父任务领取令牌；提交须同时复核父执行权',
  provider VARCHAR(64) NOT NULL COMMENT '供应商标识',
  requested_model VARCHAR(200) NOT NULL COMMENT '实际请求模型',
  response_model VARCHAR(200) NULL COMMENT '供应商回传模型，不猜测',
  provider_request_id VARCHAR(200) NULL COMMENT '已知远端回执身份',
  input_manifest JSON NOT NULL COMMENT 'schema_version、来源/配置/请求摘要和完整性',
  response_payload JSON NULL COMMENT '已校验的规范化可见结果及工具建议/配对ID，最多64KiB；不存隐藏思维链，Embedding仅存数量/维度/目标批次摘要，不存向量',
  reserved_tokens BIGINT NOT NULL COMMENT '输入估算加输出上限，非负',
  reserved_cost DECIMAL(20,8) NULL COMMENT '有价格时预留，未知不填0',
  input_tokens BIGINT NULL COMMENT '供应商实际回传，未知null',
  output_tokens BIGINT NULL COMMENT '实际输出，Embedding明确无输出才0',
  cached_input_tokens BIGINT NULL COMMENT '供应商可区分时记录',
  estimated_cost DECIMAL(20,8) NULL COMMENT '真实用量乘价格快照的估算，非账单',
  pricing_snapshot JSON NOT NULL COMMENT 'schema_version、币种、单位、日期及已知费率；unknown明确标记',
  attempt_of_call_id VARCHAR(32) NULL COMMENT '本地FK→ai_model_call，明确重试链；原UNKNOWN不自动重发',
  started_at DATETIME(6) NULL COMMENT 'IN_FLIGHT前登记',
  finished_at DATETIME(6) NULL COMMENT '本地终结时刻',
  error_code VARCHAR(64) NULL COMMENT '诊断',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_model_call_0 (run_id,call_number),
  UNIQUE KEY uk_ai_model_call_1 (index_job_id,call_number),
  UNIQUE KEY uk_ai_model_call_2 (id,run_id),
  KEY idx_ai_model_call_0 (index_job_id,created_at,id),
  CONSTRAINT ck_ai_model_call_purpose CHECK ((purpose IN ('CHAT','QUERY_EMBEDDING','INDEX_EMBEDDING','SUMMARY','RERANK','VALIDATION')) IS TRUE),
  CONSTRAINT ck_ai_model_call_status CHECK ((status IN ('PREPARED','IN_FLIGHT','SUCCEEDED','FAILED','UNKNOWN','CANCELLED')) IS TRUE),
  CONSTRAINT ck_ai_model_call_parent CHECK (((run_id IS NULL)<>(index_job_id IS NULL)) IS TRUE),
  CONSTRAINT ck_ai_model_call_purpose_parent CHECK (((purpose='INDEX_EMBEDDING')=(index_job_id IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_model_call_accounting CHECK (((cached_input_tokens IS NULL OR input_tokens IS NULL OR cached_input_tokens<=input_tokens) AND (reserved_cost IS NULL OR reserved_cost>=0) AND (estimated_cost IS NULL OR estimated_cost>=0)) IS TRUE),
  CONSTRAINT ck_ai_model_call_success CHECK ((status<>'SUCCEEDED' OR (response_payload IS NOT NULL AND started_at IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_model_call_terminal CHECK (((status IN ('SUCCEEDED','FAILED','UNKNOWN','CANCELLED'))=(finished_at IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_model_call_in_flight CHECK ((status<>'IN_FLIGHT' OR started_at IS NOT NULL) IS TRUE),
  CONSTRAINT ck_ai_model_call_not_self CHECK ((attempt_of_call_id IS NULL OR attempt_of_call_id<>id) IS TRUE),
  CONSTRAINT ck_ai_model_call_response_size CHECK ((response_payload IS NULL OR OCTET_LENGTH(response_payload)<=65536) IS TRUE),
  CONSTRAINT ck_ai_model_call_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_model_call_run_id_id CHECK ((run_id IS NULL OR REGEXP_LIKE(run_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_model_call_index_job_id_id CHECK ((index_job_id IS NULL OR REGEXP_LIKE(index_job_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_model_call_call_number_number CHECK ((call_number>0) IS TRUE),
  CONSTRAINT ck_ai_model_call_input_manifest_json CHECK (((JSON_TYPE(input_manifest)='OBJECT' AND JSON_TYPE(input_manifest->'$.schema_version')='INTEGER' AND input_manifest->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_model_call_response_payload_json CHECK ((response_payload IS NULL OR (JSON_TYPE(response_payload)='OBJECT' AND JSON_TYPE(response_payload->'$.schema_version')='INTEGER' AND response_payload->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_model_call_reserved_tokens_number CHECK ((reserved_tokens>=0) IS TRUE),
  CONSTRAINT ck_ai_model_call_input_tokens_number CHECK ((input_tokens IS NULL OR input_tokens>=0) IS TRUE),
  CONSTRAINT ck_ai_model_call_output_tokens_number CHECK ((output_tokens IS NULL OR output_tokens>=0) IS TRUE),
  CONSTRAINT ck_ai_model_call_cached_input_tokens_number CHECK ((cached_input_tokens IS NULL OR cached_input_tokens>=0) IS TRUE),
  CONSTRAINT ck_ai_model_call_pricing_snapshot_json CHECK (((JSON_TYPE(pricing_snapshot)='OBJECT' AND JSON_TYPE(pricing_snapshot->'$.schema_version')='INTEGER' AND pricing_snapshot->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_model_call_attempt_of_call_id_id CHECK ((attempt_of_call_id IS NULL OR REGEXP_LIKE(attempt_of_call_id,'^[0-9a-f]{32}$','c')) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_tool_call (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  run_id VARCHAR(32) NOT NULL COMMENT '本地FK→ai_run',
  model_call_id VARCHAR(32) NULL COMMENT '复合FK(model_call_id,run_id)→ai_model_call(id,run_id)，规则调用为空',
  provider_tool_call_id VARCHAR(200) NULL COMMENT '供应商工具调用配对ID',
  attempt_number INT NOT NULL DEFAULT 1 COMMENT '同一个模型工具建议的实际尝试号，正整数',
  tool_number INT NOT NULL COMMENT '运行内正序',
  tool_name VARCHAR(64) NOT NULL COMMENT '后端注册表白名单',
  arguments JSON NOT NULL COMMENT 'schema_version、已校验且无伪造身份的具名参数',
  input_fingerprint VARCHAR(64) NOT NULL COMMENT '工具+规范参数+环境版本',
  status VARCHAR(24) NOT NULL DEFAULT 'PREPARED' COMMENT 'PREPARED / RUNNING / OK / EMPTY / INVALID_ARGUMENT / FORBIDDEN / STALE_CONTEXT / DEPENDENCY_FAILED / UNKNOWN / CANCELLED',
  claim_token VARCHAR(36) NOT NULL COMMENT '外调意图所属父任务领取令牌；提交须同时复核父执行权',
  result_summary TEXT NULL COMMENT '受控安全摘要，最多4000字符',
  result_refs JSON NULL COMMENT 'schema_version、结果ID及完整性/续读游标；引用必须属于本会话/任务',
  result_payload JSON NULL COMMENT 'schema_version、类型化可见工具结果或待提交领域提议，最多64KiB；大正文以已持久来源/结果定位引用',
  result_fingerprint VARCHAR(64) NULL COMMENT '规范结果指纹，用于无进展检测',
  started_at DATETIME(6) NULL COMMENT '实际开始',
  finished_at DATETIME(6) NULL COMMENT '结果终结',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_tool_call_0 (run_id,tool_number),
  UNIQUE KEY uk_ai_tool_call_1 (id,run_id),
  UNIQUE KEY uk_ai_tool_call_2 (model_call_id,provider_tool_call_id,attempt_number),
  KEY idx_ai_tool_call_0 (run_id,input_fingerprint,id),
  CONSTRAINT ck_ai_tool_call_status CHECK ((status IN ('PREPARED','RUNNING','OK','EMPTY','INVALID_ARGUMENT','FORBIDDEN','STALE_CONTEXT','DEPENDENCY_FAILED','UNKNOWN','CANCELLED')) IS TRUE),
  CONSTRAINT ck_ai_tool_call_model_pair CHECK (((model_call_id IS NULL AND provider_tool_call_id IS NULL) OR (model_call_id IS NOT NULL AND provider_tool_call_id IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_tool_call_terminal CHECK (((status NOT IN ('PREPARED','RUNNING'))=(finished_at IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_tool_call_success CHECK ((status NOT IN ('OK','EMPTY') OR ((result_payload IS NOT NULL OR result_refs IS NOT NULL) AND result_fingerprint IS NOT NULL AND started_at IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_tool_call_in_flight CHECK ((status<>'RUNNING' OR started_at IS NOT NULL) IS TRUE),
  CONSTRAINT ck_ai_tool_call_sizes CHECK (((result_payload IS NULL OR OCTET_LENGTH(result_payload)<=65536) AND (result_summary IS NULL OR CHAR_LENGTH(result_summary)<=4000)) IS TRUE),
  CONSTRAINT ck_ai_tool_call_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_tool_call_run_id_id CHECK ((REGEXP_LIKE(run_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_tool_call_model_call_id_id CHECK ((model_call_id IS NULL OR REGEXP_LIKE(model_call_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_tool_call_attempt_number_number CHECK ((attempt_number>0) IS TRUE),
  CONSTRAINT ck_ai_tool_call_tool_number_number CHECK ((tool_number>0) IS TRUE),
  CONSTRAINT ck_ai_tool_call_arguments_json CHECK (((JSON_TYPE(arguments)='OBJECT' AND JSON_TYPE(arguments->'$.schema_version')='INTEGER' AND arguments->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_tool_call_input_fingerprint_hex CHECK ((REGEXP_LIKE(input_fingerprint,'^[0-9a-f]{64}$','c')) IS TRUE),
  CONSTRAINT ck_ai_tool_call_result_refs_json CHECK ((result_refs IS NULL OR (JSON_TYPE(result_refs)='OBJECT' AND JSON_TYPE(result_refs->'$.schema_version')='INTEGER' AND result_refs->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_tool_call_result_payload_json CHECK ((result_payload IS NULL OR (JSON_TYPE(result_payload)='OBJECT' AND JSON_TYPE(result_payload->'$.schema_version')='INTEGER' AND result_payload->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_tool_call_result_fingerprint_hex CHECK ((result_fingerprint IS NULL OR REGEXP_LIKE(result_fingerprint,'^[0-9a-f]{64}$','c')) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_source_usage (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  run_id VARCHAR(32) NOT NULL COMMENT '本地FK→ai_run',
  source_number INT NOT NULL COMMENT '本轮正序来源号',
  source_type VARCHAR(24) NOT NULL COMMENT 'CHUNK / TICKET_EVENT / FACT / MESSAGE / ATTACHMENT / PROCEDURE_CHECK',
  chunk_id VARCHAR(32) NULL COMMENT '本地FK→ai_chunk',
  ticket_event_id VARCHAR(32) NULL COMMENT '外部工单事件',
  fact_id VARCHAR(32) NULL COMMENT '本地FK→ai_diagnostic_fact',
  message_id VARCHAR(32) NULL COMMENT '本地FK→ai_message',
  attachment_id VARCHAR(32) NULL COMMENT '外部sd_attachment，仅脱敏版本',
  procedure_document_id VARCHAR(32) NULL COMMENT '外部kb_document固定修订，PROCEDURE_CHECK来源必填',
  procedure_check_key VARCHAR(64) NULL COMMENT '与procedure_document_id同空同有，属于该定义',
  title_snapshot VARCHAR(255) NOT NULL COMMENT '后端核验的实际来源标题，不接受模型编造',
  source_version JSON NOT NULL COMMENT 'schema_version、document/job/context/redaction等准确版本及正文摘要',
  locator_snapshot JSON NOT NULL COMMENT 'schema_version、页/行/事件/字段及完整性',
  excerpt_snapshot TEXT NOT NULL COMMENT '实际使用片段或元信息，受控正文，最多48KiB',
  usage_kind VARCHAR(16) NOT NULL COMMENT 'METADATA / CONTENT',
  first_model_call_id VARCHAR(32) NULL COMMENT '复合FK(first_model_call_id,run_id)→ai_model_call(id,run_id)，纯规则为空',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_source_usage_0 (run_id,source_number),
  UNIQUE KEY uk_ai_source_usage_1 (id,run_id),
  KEY idx_ai_source_usage_0 (chunk_id),
  KEY idx_ai_source_usage_1 (ticket_event_id),
  KEY idx_ai_source_usage_2 (fact_id),
  KEY idx_ai_source_usage_3 (message_id),
  KEY idx_ai_source_usage_4 (attachment_id),
  KEY idx_ai_source_usage_5 (procedure_document_id),
  CONSTRAINT ck_ai_source_usage_source_type CHECK ((source_type IN ('CHUNK','TICKET_EVENT','FACT','MESSAGE','ATTACHMENT','PROCEDURE_CHECK')) IS TRUE),
  CONSTRAINT ck_ai_source_usage_usage_kind CHECK ((usage_kind IN ('METADATA','CONTENT')) IS TRUE),
  CONSTRAINT ck_ai_source_usage_one_source CHECK (((chunk_id IS NOT NULL) + (ticket_event_id IS NOT NULL) + (fact_id IS NOT NULL) + (message_id IS NOT NULL) + (attachment_id IS NOT NULL) + (procedure_document_id IS NOT NULL)=1) IS TRUE),
  CONSTRAINT ck_ai_source_usage_kind_matches CHECK (((source_type='CHUNK' AND chunk_id IS NOT NULL) OR (source_type='TICKET_EVENT' AND ticket_event_id IS NOT NULL) OR (source_type='FACT' AND fact_id IS NOT NULL) OR (source_type='MESSAGE' AND message_id IS NOT NULL) OR (source_type='ATTACHMENT' AND attachment_id IS NOT NULL) OR (source_type='PROCEDURE_CHECK' AND procedure_document_id IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_source_usage_procedure CHECK (((procedure_document_id IS NULL AND procedure_check_key IS NULL) OR (procedure_document_id IS NOT NULL AND procedure_check_key IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_source_usage_excerpt CHECK ((OCTET_LENGTH(excerpt_snapshot)<=49152) IS TRUE),
  CONSTRAINT ck_ai_source_usage_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_source_usage_run_id_id CHECK ((REGEXP_LIKE(run_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_source_usage_source_number_number CHECK ((source_number>0) IS TRUE),
  CONSTRAINT ck_ai_source_usage_chunk_id_id CHECK ((chunk_id IS NULL OR REGEXP_LIKE(chunk_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_source_usage_ticket_event_id_id CHECK ((ticket_event_id IS NULL OR REGEXP_LIKE(ticket_event_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_source_usage_fact_id_id CHECK ((fact_id IS NULL OR REGEXP_LIKE(fact_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_source_usage_message_id_id CHECK ((message_id IS NULL OR REGEXP_LIKE(message_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_source_usage_attachment_id_id CHECK ((attachment_id IS NULL OR REGEXP_LIKE(attachment_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_source_usage_procedure_document_id_id CHECK ((procedure_document_id IS NULL OR REGEXP_LIKE(procedure_document_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_source_usage_source_version_json CHECK (((JSON_TYPE(source_version)='OBJECT' AND JSON_TYPE(source_version->'$.schema_version')='INTEGER' AND source_version->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_source_usage_locator_snapshot_json CHECK (((JSON_TYPE(locator_snapshot)='OBJECT' AND JSON_TYPE(locator_snapshot->'$.schema_version')='INTEGER' AND locator_snapshot->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_source_usage_first_model_call_id_id CHECK ((first_model_call_id IS NULL OR REGEXP_LIKE(first_model_call_id,'^[0-9a-f]{32}$','c')) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_action_draft (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  draft_root_id VARCHAR(32) NOT NULL COMMENT '本地FK→ai_action_draft.id，首版自指，正式采用按根唯一',
  revision_number INT NOT NULL COMMENT '根内正整数，首版1',
  previous_draft_id VARCHAR(32) NULL COMMENT '本地FK→ai_action_draft，首版为空，后继唯一',
  owner_id VARCHAR(32) NOT NULL COMMENT '外部账号',
  conversation_id VARCHAR(32) NOT NULL COMMENT '复合FK(conversation_id,owner_id)→ai_conversation(id,owner_id)',
  task_id VARCHAR(32) NOT NULL COMMENT '复合FK(task_id,conversation_id)→ai_diagnosis_task(id,conversation_id)',
  source_run_id VARCHAR(32) NULL COMMENT '复合FK(source_run_id,task_id)→ai_run(id,task_id)；事实模板允许为空',
  purpose VARCHAR(32) NOT NULL COMMENT 'CREATE_TICKET / ADD_RECORD / RESOLVE_TICKET / REQUEST_INFORMATION / BOOK_ONSITE',
  ticket_id VARCHAR(32) NULL COMMENT 'CREATE_TICKET为空，其他外部固定工单',
  ticket_row_version BIGINT NULL COMMENT '与ticket_id同空同有',
  task_state_version BIGINT NOT NULL COMMENT '固定诊断版本',
  diagnosis_context_version BIGINT NOT NULL COMMENT '固定环境版本',
  client_request_id VARCHAR(32) NOT NULL COMMENT '拥有者范围创建/编辑幂等键',
  request_hash VARCHAR(64) NOT NULL COMMENT '用途/目标/原修订/最终字段语义摘要',
  content JSON NOT NULL COMMENT 'schema_version、用途类型化最终可确认字段',
  content_sha256 VARCHAR(64) NOT NULL COMMENT '确认针对该不可变内容',
  source_refs JSON NOT NULL COMMENT 'schema_version、事实ID和source_usage IDs；同任务及传递来源',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SUPERSEDED / WITHDRAWN / EXPIRED；不存CONSUMED',
  expires_at DATETIME(6) NOT NULL COMMENT '根创建后24小时，编辑不延期',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_action_draft_0 (owner_id,client_request_id),
  UNIQUE KEY uk_ai_action_draft_1 (draft_root_id,revision_number),
  UNIQUE KEY uk_ai_action_draft_2 (previous_draft_id),
  KEY idx_ai_action_draft_0 (task_id,created_at,id),
  KEY idx_ai_action_draft_1 (status,expires_at,id),
  CONSTRAINT ck_ai_action_draft_purpose CHECK ((purpose IN ('CREATE_TICKET','ADD_RECORD','RESOLVE_TICKET','REQUEST_INFORMATION','BOOK_ONSITE')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_status CHECK ((status IN ('PENDING','SUPERSEDED','WITHDRAWN','EXPIRED')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_root CHECK (((revision_number=1 AND draft_root_id=id AND previous_draft_id IS NULL) OR (revision_number>1 AND draft_root_id<>id AND previous_draft_id IS NOT NULL AND previous_draft_id<>id)) IS TRUE),
  CONSTRAINT ck_ai_action_draft_target CHECK (((purpose='CREATE_TICKET' AND ticket_id IS NULL AND ticket_row_version IS NULL) OR (purpose<>'CREATE_TICKET' AND ticket_id IS NOT NULL AND ticket_row_version IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_action_draft_time CHECK ((expires_at>created_at) IS TRUE),
  CONSTRAINT ck_ai_action_draft_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_draft_root_id_id CHECK ((REGEXP_LIKE(draft_root_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_revision_number_number CHECK ((revision_number>0) IS TRUE),
  CONSTRAINT ck_ai_action_draft_previous_draft_id_id CHECK ((previous_draft_id IS NULL OR REGEXP_LIKE(previous_draft_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_owner_id_id CHECK ((REGEXP_LIKE(owner_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_conversation_id_id CHECK ((REGEXP_LIKE(conversation_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_task_id_id CHECK ((REGEXP_LIKE(task_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_source_run_id_id CHECK ((source_run_id IS NULL OR REGEXP_LIKE(source_run_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_ticket_id_id CHECK ((ticket_id IS NULL OR REGEXP_LIKE(ticket_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_ticket_row_version_number CHECK ((ticket_row_version IS NULL OR ticket_row_version>=0) IS TRUE),
  CONSTRAINT ck_ai_action_draft_task_state_version_number CHECK ((task_state_version>=0) IS TRUE),
  CONSTRAINT ck_ai_action_draft_diagnosis_context_version_number CHECK ((diagnosis_context_version>=0) IS TRUE),
  CONSTRAINT ck_ai_action_draft_client_request_id_id CHECK ((REGEXP_LIKE(client_request_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_request_hash_hex CHECK ((REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_content_json CHECK (((JSON_TYPE(content)='OBJECT' AND JSON_TYPE(content->'$.schema_version')='INTEGER' AND content->'$.schema_version'=1)) IS TRUE),
  CONSTRAINT ck_ai_action_draft_content_sha256_hex CHECK ((REGEXP_LIKE(content_sha256,'^[0-9a-f]{64}$','c')) IS TRUE),
  CONSTRAINT ck_ai_action_draft_source_refs_json CHECK (((JSON_TYPE(source_refs)='OBJECT' AND JSON_TYPE(source_refs->'$.schema_version')='INTEGER' AND source_refs->'$.schema_version'=1)) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE ai_feedback (
  id VARCHAR(32) NOT NULL COMMENT '主键，所属服务生成',
  run_id VARCHAR(32) NOT NULL COMMENT '本地FK→ai_run',
  submitted_by VARCHAR(32) NOT NULL COMMENT '原会话拥有者',
  rating VARCHAR(16) NOT NULL COMMENT 'HELPFUL / UNHELPFUL',
  adoption VARCHAR(16) NOT NULL COMMENT 'NOT_USED / ACCEPTED / MODIFIED / REJECTED',
  issue_category VARCHAR(32) NULL COMMENT 'INCORRECT / IRRELEVANT / MISSING_CONTEXT / UNSUPPORTED / OTHER',
  comment VARCHAR(2000) NULL COMMENT '本人反馈，不自动共享',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时刻',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '显式维护',
  row_version BIGINT NOT NULL DEFAULT 0 COMMENT '非负，比较后递增',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_feedback_0 (run_id,submitted_by),
  KEY idx_ai_feedback_0 (submitted_by,created_at,id),
  CONSTRAINT ck_ai_feedback_rating CHECK ((rating IN ('HELPFUL','UNHELPFUL')) IS TRUE),
  CONSTRAINT ck_ai_feedback_adoption CHECK ((adoption IN ('NOT_USED','ACCEPTED','MODIFIED','REJECTED')) IS TRUE),
  CONSTRAINT ck_ai_feedback_issue_category CHECK ((issue_category IS NULL OR (issue_category IN ('INCORRECT','IRRELEVANT','MISSING_CONTEXT','UNSUPPORTED','OTHER'))) IS TRUE),
  CONSTRAINT ck_ai_feedback_category CHECK (((rating='UNHELPFUL')=(issue_category IS NOT NULL)) IS TRUE),
  CONSTRAINT ck_ai_feedback_id_id CHECK ((REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_feedback_run_id_id CHECK ((REGEXP_LIKE(run_id,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_feedback_submitted_by_id CHECK ((REGEXP_LIKE(submitted_by,'^[0-9a-f]{32}$','c')) IS TRUE),
  CONSTRAINT ck_ai_feedback_row_version_number CHECK ((row_version>=0) IS TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE ai_diagnosis_task ADD CONSTRAINT fk_ai_diagnosis_task_0 FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id);

ALTER TABLE ai_diagnosis_task ADD CONSTRAINT fk_ai_diagnosis_task_1 FOREIGN KEY (blocking_draft_root_id) REFERENCES ai_action_draft(id);

ALTER TABLE ai_message ADD CONSTRAINT fk_ai_message_2 FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id);

ALTER TABLE ai_message ADD CONSTRAINT fk_ai_message_3 FOREIGN KEY (task_id,conversation_id) REFERENCES ai_diagnosis_task(id,conversation_id);

ALTER TABLE ai_task_event ADD CONSTRAINT fk_ai_task_event_4 FOREIGN KEY (task_id) REFERENCES ai_diagnosis_task(id);

ALTER TABLE ai_run ADD CONSTRAINT fk_ai_run_5 FOREIGN KEY (conversation_id,owner_id) REFERENCES ai_conversation(id,owner_id);

ALTER TABLE ai_run ADD CONSTRAINT fk_ai_run_6 FOREIGN KEY (task_id,conversation_id) REFERENCES ai_diagnosis_task(id,conversation_id);

ALTER TABLE ai_run ADD CONSTRAINT fk_ai_run_7 FOREIGN KEY (input_message_id,conversation_id) REFERENCES ai_message(id,conversation_id);

ALTER TABLE ai_run ADD CONSTRAINT fk_ai_run_8 FOREIGN KEY (output_message_id,conversation_id) REFERENCES ai_message(id,conversation_id);

ALTER TABLE ai_run ADD CONSTRAINT fk_ai_run_9 FOREIGN KEY (resumes_run_id,conversation_id) REFERENCES ai_run(id,conversation_id);

ALTER TABLE ai_diagnostic_fact ADD CONSTRAINT fk_ai_diagnostic_fact_10 FOREIGN KEY (task_id,state_version) REFERENCES ai_task_event(task_id,state_version);

ALTER TABLE ai_diagnostic_fact ADD CONSTRAINT fk_ai_diagnostic_fact_11 FOREIGN KEY (message_id) REFERENCES ai_message(id);

ALTER TABLE ai_diagnostic_fact ADD CONSTRAINT fk_ai_diagnostic_fact_12 FOREIGN KEY (source_tool_call_id) REFERENCES ai_tool_call(id);

ALTER TABLE ai_diagnostic_fact ADD CONSTRAINT fk_ai_diagnostic_fact_13 FOREIGN KEY (supersedes_fact_id,task_id) REFERENCES ai_diagnostic_fact(id,task_id);

ALTER TABLE ai_diagnostic_check ADD CONSTRAINT fk_ai_diagnostic_check_14 FOREIGN KEY (task_id) REFERENCES ai_diagnosis_task(id);

ALTER TABLE ai_diagnostic_check ADD CONSTRAINT fk_ai_diagnostic_check_15 FOREIGN KEY (proposed_run_id,task_id) REFERENCES ai_run(id,task_id);

ALTER TABLE ai_diagnostic_check ADD CONSTRAINT fk_ai_diagnostic_check_16 FOREIGN KEY (answer_message_id) REFERENCES ai_message(id);

ALTER TABLE ai_diagnostic_check ADD CONSTRAINT fk_ai_diagnostic_check_17 FOREIGN KEY (result_fact_id,task_id) REFERENCES ai_diagnostic_fact(id,task_id);

ALTER TABLE ai_diagnostic_check ADD CONSTRAINT fk_ai_diagnostic_check_18 FOREIGN KEY (previous_check_id,task_id) REFERENCES ai_diagnostic_check(id,task_id);

ALTER TABLE ai_model_call ADD CONSTRAINT fk_ai_model_call_19 FOREIGN KEY (run_id) REFERENCES ai_run(id);

ALTER TABLE ai_model_call ADD CONSTRAINT fk_ai_model_call_20 FOREIGN KEY (index_job_id) REFERENCES ai_index_job(id);

ALTER TABLE ai_model_call ADD CONSTRAINT fk_ai_model_call_21 FOREIGN KEY (attempt_of_call_id) REFERENCES ai_model_call(id);

ALTER TABLE ai_tool_call ADD CONSTRAINT fk_ai_tool_call_22 FOREIGN KEY (run_id) REFERENCES ai_run(id);

ALTER TABLE ai_tool_call ADD CONSTRAINT fk_ai_tool_call_23 FOREIGN KEY (model_call_id,run_id) REFERENCES ai_model_call(id,run_id);

ALTER TABLE ai_source_usage ADD CONSTRAINT fk_ai_source_usage_24 FOREIGN KEY (run_id) REFERENCES ai_run(id);

ALTER TABLE ai_source_usage ADD CONSTRAINT fk_ai_source_usage_25 FOREIGN KEY (chunk_id) REFERENCES ai_chunk(id);

ALTER TABLE ai_source_usage ADD CONSTRAINT fk_ai_source_usage_26 FOREIGN KEY (fact_id) REFERENCES ai_diagnostic_fact(id);

ALTER TABLE ai_source_usage ADD CONSTRAINT fk_ai_source_usage_27 FOREIGN KEY (message_id) REFERENCES ai_message(id);

ALTER TABLE ai_source_usage ADD CONSTRAINT fk_ai_source_usage_28 FOREIGN KEY (first_model_call_id,run_id) REFERENCES ai_model_call(id,run_id);

ALTER TABLE ai_action_draft ADD CONSTRAINT fk_ai_action_draft_29 FOREIGN KEY (draft_root_id) REFERENCES ai_action_draft(id);

ALTER TABLE ai_action_draft ADD CONSTRAINT fk_ai_action_draft_30 FOREIGN KEY (previous_draft_id) REFERENCES ai_action_draft(id);

ALTER TABLE ai_action_draft ADD CONSTRAINT fk_ai_action_draft_31 FOREIGN KEY (conversation_id,owner_id) REFERENCES ai_conversation(id,owner_id);

ALTER TABLE ai_action_draft ADD CONSTRAINT fk_ai_action_draft_32 FOREIGN KEY (task_id,conversation_id) REFERENCES ai_diagnosis_task(id,conversation_id);

ALTER TABLE ai_action_draft ADD CONSTRAINT fk_ai_action_draft_33 FOREIGN KEY (source_run_id,task_id) REFERENCES ai_run(id,task_id);

ALTER TABLE ai_feedback ADD CONSTRAINT fk_ai_feedback_34 FOREIGN KEY (run_id) REFERENCES ai_run(id);

INSERT INTO ai_execution_gate(id,gate_key) VALUES ('00000000000000000000000000000001','INTERACTIVE');
