CREATE TABLE sd_action_receipt (
  id VARCHAR(32) NOT NULL COMMENT '所属服务生成的不可变回执',
  actor_id VARCHAR(32) NOT NULL COMMENT '原动作操作者',
  client_action_id VARCHAR(32) NOT NULL COMMENT '操作者范围统一幂等键',
  action_type VARCHAR(40) NOT NULL COMMENT '正式动作类型',
  request_hash VARCHAR(64) NOT NULL COMMENT '规范动作输入摘要',
  source_draft_id VARCHAR(32) NULL COMMENT '外部不可变草稿修订',
  source_draft_root_id VARCHAR(32) NULL COMMENT '外部草稿根，跨修订唯一采用',
  ticket_id VARCHAR(32) NOT NULL COMMENT '正式工单',
  booking_id VARCHAR(32) NULL COMMENT '同工单预约，可空',
  result_code VARCHAR(32) NOT NULL COMMENT 'APPLIED / SOLD_OUT / SLOT_CLOSED',
  result_snapshot JSON NOT NULL COMMENT '原结果身份和版本，不含正文',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sd_action_receipt_actor_key (actor_id,client_action_id),
  UNIQUE KEY uk_sd_action_receipt_draft_root (source_draft_root_id),
  UNIQUE KEY uk_sd_action_receipt_id_ticket (id,ticket_id),
  KEY idx_sd_action_receipt_ticket_created (ticket_id,created_at,id),
  CONSTRAINT fk_sd_action_receipt_ticket FOREIGN KEY (ticket_id) REFERENCES sd_ticket(id),
  CONSTRAINT fk_sd_action_receipt_booking FOREIGN KEY (booking_id,ticket_id) REFERENCES sd_onsite_booking(id,ticket_id),
  CONSTRAINT ck_sd_action_receipt_id CHECK (REGEXP_LIKE(id,'^[0-9a-f]{32}$','c')),
  CONSTRAINT ck_sd_action_receipt_actor CHECK (REGEXP_LIKE(actor_id,'^[0-9a-f]{32}$','c')),
  CONSTRAINT ck_sd_action_receipt_key CHECK (REGEXP_LIKE(client_action_id,'^[0-9a-f]{32}$','c')),
  CONSTRAINT ck_sd_action_receipt_ticket CHECK (REGEXP_LIKE(ticket_id,'^[0-9a-f]{32}$','c')),
  CONSTRAINT ck_sd_action_receipt_booking CHECK (booking_id IS NULL OR REGEXP_LIKE(booking_id,'^[0-9a-f]{32}$','c')),
  CONSTRAINT ck_sd_action_receipt_hash CHECK (REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')),
  CONSTRAINT ck_sd_action_receipt_draft CHECK (((source_draft_id IS NULL AND source_draft_root_id IS NULL) OR
    (source_draft_id IS NOT NULL AND source_draft_root_id IS NOT NULL
     AND REGEXP_LIKE(source_draft_id,'^[0-9a-f]{32}$','c') AND REGEXP_LIKE(source_draft_root_id,'^[0-9a-f]{32}$','c'))) IS TRUE),
  CONSTRAINT ck_sd_action_receipt_type CHECK (action_type IN ('CREATE_TICKET','ACCEPT','ASSIGN','REASSIGN','PRIORITY','CONTEXT_UPDATE','ADD_RECORD','REQUEST_INFORMATION','SUPPLEMENT','WAIT_EXTERNAL','END_WAIT','FOLLOW_UP','RESOLVE_TICKET','CONFIRM','UNRESOLVED','CANCEL','ONSITE_ELIGIBILITY','BOOK_ONSITE','CANCEL_BOOKING','FINISH_BOOKING','ASSIGN_BOOKING','CONFIRM_REDACTION','DELETE_ATTACHMENT')),
  CONSTRAINT ck_sd_action_receipt_result CHECK (result_code='APPLIED' OR (action_type='BOOK_ONSITE' AND booking_id IS NOT NULL AND result_code IN ('SOLD_OUT','SLOT_CLOSED'))),
  CONSTRAINT ck_sd_action_receipt_snapshot CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version","ticket_id","ticket_row_version","ticket_status","event_sequence","event_id","booking_id","booking_status","booking_row_version"],"properties":{"schema_version":{"type":"integer","enum":[1]},"ticket_id":{"type":"string","pattern":"^[0-9a-f]{32}$"},"ticket_row_version":{"type":"integer","minimum":0},"ticket_status":{"type":"string"},"event_sequence":{"type":"integer","minimum":0},"event_id":{"type":["string","null"]},"booking_id":{"type":["string","null"]},"booking_status":{"type":["string","null"]},"booking_row_version":{"type":["integer","null"],"minimum":0}},"additionalProperties":false}',result_snapshot)=1 AND OCTET_LENGTH(result_snapshot)<=8192)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE sd_ticket_event
  ADD COLUMN action_receipt_id VARCHAR(32) NULL COMMENT '本地正式用户动作回执，系统事件为空',
  ADD CONSTRAINT fk_sd_ticket_event_receipt FOREIGN KEY (action_receipt_id,ticket_id) REFERENCES sd_action_receipt(id,ticket_id),
  ADD CONSTRAINT ck_sd_ticket_event_receipt CHECK (action_receipt_id IS NULL OR REGEXP_LIKE(action_receipt_id,'^[0-9a-f]{32}$','c'));

ALTER TABLE sd_onsite_booking
  ADD COLUMN assigned_technician_id VARCHAR(32) NULL COMMENT '已核验现场负责人',
  ADD COLUMN performed_by VARCHAR(32) NULL COMMENT '履约登记执行人',
  ADD COLUMN finish_note VARCHAR(1000) NULL COMMENT '真实履约结果说明',
  ADD COLUMN cancel_reason VARCHAR(500) NULL COMMENT '到访前取消原因',
  ADD CONSTRAINT ck_sd_booking_assigned CHECK (((status='REJECTED' AND assigned_technician_id IS NULL) OR (status<>'REJECTED' AND assigned_technician_id IS NOT NULL AND REGEXP_LIKE(assigned_technician_id,'^[0-9a-f]{32}$','c'))) IS TRUE),
  ADD CONSTRAINT ck_sd_booking_performed CHECK (((status='FINISHED' AND performed_by IS NOT NULL AND REGEXP_LIKE(performed_by,'^[0-9a-f]{32}$','c') AND finish_note IS NOT NULL AND REGEXP_LIKE(finish_note,'[^[:space:]]')) OR (status<>'FINISHED' AND performed_by IS NULL AND finish_note IS NULL)) IS TRUE),
  ADD CONSTRAINT ck_sd_booking_cancel CHECK (((status='CANCELLED' AND cancel_reason IS NOT NULL AND REGEXP_LIKE(cancel_reason,'[^[:space:]]')) OR (status<>'CANCELLED' AND cancel_reason IS NULL)) IS TRUE),
  ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0 COMMENT '预约独立写版本',
  ADD CONSTRAINT ck_sd_onsite_booking_version CHECK (row_version>=0);

ALTER TABLE sd_ticket_event
  DROP CHECK ck_sd_ticket_event__event_type_values,
  DROP CHECK ck_sd_ticket_event__booking_state,
  DROP CHECK ck_sd_ticket_event__action_receipt,
  ADD CONSTRAINT ck_sd_ticket_event__booking_state CHECK (((event_type IN ('ONSITE_BOOKED','ONSITE_BOOKING_CANCELLED','ONSITE_FINISHED','ONSITE_ASSIGNED') AND onsite_booking_id IS NOT NULL AND from_status=to_status) OR (event_type NOT IN ('ONSITE_BOOKED','ONSITE_BOOKING_CANCELLED','ONSITE_FINISHED','ONSITE_ASSIGNED') AND onsite_booking_id IS NULL)) IS TRUE),
  ADD CONSTRAINT ck_sd_ticket_event__action_receipt CHECK (((actor_type='USER' AND event_type NOT IN ('CREATED','ONSITE_BOOKED','ONSITE_BOOKING_CANCELLED','ONSITE_FINISHED','ONSITE_ASSIGNED') AND client_action_id IS NOT NULL AND action_request_hash IS NOT NULL) OR ((actor_type='SYSTEM' OR event_type IN ('CREATED','ONSITE_BOOKED','ONSITE_BOOKING_CANCELLED','ONSITE_FINISHED','ONSITE_ASSIGNED')) AND client_action_id IS NULL AND action_request_hash IS NULL)) IS TRUE),
  ADD CONSTRAINT ck_sd_ticket_event__event_type_values CHECK (event_type IN ('CREATED','ACCEPTED','ASSIGNED','REASSIGNED','NEED_INFO','SUPPLEMENTED','RESOLVED','UNRESOLVED','CLOSED','CANCELLED','COMMENTED','RESPONSE_OVERDUE','ACCEPTANCE_OVERDUE','PRIORITY_CHANGED','ONSITE_ELIGIBILITY_CHANGED','ONSITE_BOOKED','ONSITE_BOOKING_CANCELLED','ONSITE_FINISHED','ONSITE_ASSIGNED','CONTEXT_UPDATED','WAIT_EXTERNAL','END_WAIT','FOLLOW_UP','FOLLOW_UP_DUE'));
