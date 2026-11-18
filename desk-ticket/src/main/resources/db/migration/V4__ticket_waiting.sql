-- 单条原子DDL要求旧库不存在缺少跟进依据的活动客户等待。
-- 若旧WAITING_CUSTOMER行存在，本语句整体失败；须人工核对后结束旧等待，不能伪造历史期限。
ALTER TABLE sd_ticket
  DROP CHECK ck_sd_ticket__status,
  DROP CHECK ck_sd_ticket__assignee_state,
  ADD COLUMN wait_reason VARCHAR(1000) NULL COMMENT '当前等待原因，退出等待清空',
  ADD COLUMN follow_up_at DATETIME(6) NULL COMMENT '当前等待轮次的跟进期限',
  ADD COLUMN wait_cycle INT NOT NULL DEFAULT 0 COMMENT '等待和续期轮次，非等待保留计数',
  ADD COLUMN follow_up_notified_at DATETIME(6) NULL COMMENT '本轮已提醒时刻，续期或结束等待清空',
  ADD KEY idx_sd_ticket_follow_up (status,follow_up_notified_at,follow_up_at,id),
  ADD CONSTRAINT ck_sd_ticket__status CHECK (status IN ('SUBMITTED','PROCESSING','WAITING_CUSTOMER','WAITING_EXTERNAL','RESOLVED','CLOSED','CANCELLED')),
  ADD CONSTRAINT ck_sd_ticket__assignee_state CHECK (
    (status='SUBMITTED' AND assignee_id IS NULL AND first_accepted_at IS NULL)
    OR (status IN ('PROCESSING','WAITING_CUSTOMER','WAITING_EXTERNAL','RESOLVED','CLOSED') AND assignee_id IS NOT NULL AND first_accepted_at IS NOT NULL)
    OR (status='CANCELLED' AND ((assignee_id IS NULL AND first_accepted_at IS NULL) OR (assignee_id IS NOT NULL AND first_accepted_at IS NOT NULL)))),
  ADD CONSTRAINT ck_sd_ticket__wait_state CHECK (
    ((status IN ('WAITING_CUSTOMER','WAITING_EXTERNAL') AND wait_reason IS NOT NULL
      AND REGEXP_LIKE(wait_reason,'[^[:space:]]') AND follow_up_at IS NOT NULL AND wait_cycle>0)
    OR (status NOT IN ('WAITING_CUSTOMER','WAITING_EXTERNAL') AND wait_reason IS NULL
      AND follow_up_at IS NULL AND follow_up_notified_at IS NULL)) IS TRUE),
  ADD CONSTRAINT ck_sd_ticket__wait_cycle CHECK (wait_cycle>=0),
  ADD CONSTRAINT ck_sd_ticket__follow_up_time CHECK (follow_up_at IS NULL OR follow_up_at>created_at),
  ADD CONSTRAINT ck_sd_ticket__notified_time CHECK (follow_up_notified_at IS NULL OR (follow_up_at IS NOT NULL AND follow_up_notified_at>=follow_up_at));

ALTER TABLE sd_ticket_event
  DROP CHECK ck_sd_ticket_event__from_status,
  DROP CHECK ck_sd_ticket_event__to_status,
  DROP CHECK ck_sd_ticket_event__event_type_values,
  ADD CONSTRAINT ck_sd_ticket_event__from_status CHECK (from_status IN ('SUBMITTED','PROCESSING','WAITING_CUSTOMER','WAITING_EXTERNAL','RESOLVED','CLOSED','CANCELLED')),
  ADD CONSTRAINT ck_sd_ticket_event__to_status CHECK (to_status IN ('SUBMITTED','PROCESSING','WAITING_CUSTOMER','WAITING_EXTERNAL','RESOLVED','CLOSED','CANCELLED')),
  ADD CONSTRAINT ck_sd_ticket_event__event_type_values CHECK (event_type IN ('CREATED','ACCEPTED','ASSIGNED','REASSIGNED','NEED_INFO','SUPPLEMENTED','RESOLVED','UNRESOLVED','CLOSED','CANCELLED','COMMENTED','RESPONSE_OVERDUE','ACCEPTANCE_OVERDUE','PRIORITY_CHANGED','ONSITE_ELIGIBILITY_CHANGED','ONSITE_BOOKED','ONSITE_BOOKING_CANCELLED','ONSITE_FINISHED','CONTEXT_UPDATED','WAIT_EXTERNAL','END_WAIT','FOLLOW_UP','FOLLOW_UP_DUE'));
