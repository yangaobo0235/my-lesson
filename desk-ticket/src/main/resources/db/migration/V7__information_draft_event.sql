-- 五用途草稿共用正式回执；补充请求事件也允许固定已人工确认的修订身份。
-- 不更改旧事件或放宽SYSTEM事件来源；预约来源仍由统一回执和booking关联定位。
ALTER TABLE sd_ticket_event
  DROP CHECK ck_sd_ticket_event__business_1,
  ADD CONSTRAINT ck_sd_ticket_event__business_1 CHECK (
    (source_draft_id IS NULL OR (actor_type='USER'
      AND event_type IN ('COMMENTED','RESOLVED','NEED_INFO')
      AND client_action_id IS NOT NULL)) IS TRUE);
