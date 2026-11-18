-- 从切换点开始维护环境版本，不推断旧事件是否改变了诊断环境。
ALTER TABLE sd_ticket
  ADD COLUMN diagnosis_context_version BIGINT NOT NULL DEFAULT 0 COMMENT '分类、设备或关键环境变化递增',
  ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '当前已提交事件序号，锁工单分配',
  ADD CONSTRAINT ck_sd_ticket__diagnosis_version CHECK (diagnosis_context_version >= 0),
  ADD CONSTRAINT ck_sd_ticket__event_sequence CHECK (event_sequence >= 0);

ALTER TABLE sd_ticket_event
  MODIFY COLUMN sequence_number BIGINT NOT NULL COMMENT '工单内递增序号，与事件同事务分配';

UPDATE sd_ticket t
LEFT JOIN (SELECT ticket_id, MAX(sequence_number) AS sequence_number FROM sd_ticket_event GROUP BY ticket_id) e
  ON e.ticket_id = t.id
SET t.event_sequence = COALESCE(e.sequence_number, 0);
