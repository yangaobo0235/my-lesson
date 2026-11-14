-- 服务所属初始结构；已执行版本保持不变，后续变更新增版本。
SET SESSION time_zone = '+00:00';

-- 产品型号
CREATE TABLE `it_product_model` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
    `category` VARCHAR(32) NOT NULL COMMENT 'COMPUTER / PRINTER',
    `brand` VARCHAR(100) NOT NULL COMMENT '品牌，规范化后参与联合唯一',
    `model_name` VARCHAR(100) NOT NULL COMMENT '型号，规范化后参与联合唯一',
    `supported_systems` VARCHAR(500) NULL COMMENT '已知系统适用说明，不猜测兼容性',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    `created_by` VARCHAR(32) NOT NULL COMMENT '外部逻辑引用 → user 账号',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
    `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT '非负，更新比较旧值后加一',
    CONSTRAINT `pk_it_product_model` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_it_product_model_identity` (`category`, ((CAST(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(`brand`, 'A', 'a'), 'B', 'b'), 'C', 'c'), 'D', 'd'), 'E', 'e'), 'F', 'f'), 'G', 'g'), 'H', 'h'), 'I', 'i'), 'J', 'j'), 'K', 'k'), 'L', 'l'), 'M', 'm'), 'N', 'n'), 'O', 'o'), 'P', 'p'), 'Q', 'q'), 'R', 'r'), 'S', 's'), 'T', 't'), 'U', 'u'), 'V', 'v'), 'W', 'w'), 'X', 'x'), 'Y', 'y'), 'Z', 'z') AS CHAR(100)) COLLATE utf8mb4_0900_bin)), ((CAST(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(`model_name`, 'A', 'a'), 'B', 'b'), 'C', 'c'), 'D', 'd'), 'E', 'e'), 'F', 'f'), 'G', 'g'), 'H', 'h'), 'I', 'i'), 'J', 'j'), 'K', 'k'), 'L', 'l'), 'M', 'm'), 'N', 'n'), 'O', 'o'), 'P', 'p'), 'Q', 'q'), 'R', 'r'), 'S', 's'), 'T', 't'), 'U', 'u'), 'V', 'v'), 'W', 'w'), 'X', 'x'), 'Y', 'y'), 'Z', 'z') AS CHAR(100)) COLLATE utf8mb4_0900_bin))),
    CONSTRAINT `ck_it_product_model__category` CHECK (`category` IN ('COMPUTER', 'PRINTER')),
    CONSTRAINT `ck_it_product_model__status` CHECK (`status` IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT `ck_it_product_model__trimmed_identity` CHECK (brand = TRIM(brand) AND model_name = TRIM(model_name)),
    CONSTRAINT `ck_it_product_model__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_it_product_model__category_nonblank` CHECK (REGEXP_LIKE(`category`, '[^[:space:]]')),
    CONSTRAINT `ck_it_product_model__brand_nonblank` CHECK (REGEXP_LIKE(`brand`, '[^[:space:]]')),
    CONSTRAINT `ck_it_product_model__model_name_nonblank` CHECK (REGEXP_LIKE(`model_name`, '[^[:space:]]')),
    CONSTRAINT `ck_it_product_model__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[:space:]]')),
    CONSTRAINT `ck_it_product_model__created_by_format` CHECK (REGEXP_LIKE(`created_by`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_it_product_model__row_version_range` CHECK (`row_version` >= 0),
    CONSTRAINT `ck_it_product_model__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='产品型号';

-- 客户设备实例
CREATE TABLE `it_device` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
    `customer_id` VARCHAR(32) NOT NULL COMMENT '外部逻辑引用 → 客户组织',
    `product_model_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → it_product_model',
    `asset_code` VARCHAR(64) NOT NULL COMMENT '客户内规范化资产编号',
    `serial_number` VARCHAR(100) NULL COMMENT '序列号，敏感展示按权限裁剪',
    `location` VARCHAR(200) NULL COMMENT '使用地点，不作权限依据',
    `service_area_code` VARCHAR(32) NULL COMMENT '上门服务区域代码，规范大写 ASCII，预约前必须存在且地点有效',
    `service_expires_on` DATE NOT NULL COMMENT 'Asia/Shanghai 截止日，当日仍有效',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / RETIRED',
    `created_by` VARCHAR(32) NOT NULL COMMENT '外部账号，负责该客户的客服',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
    `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT '非负，更新比较旧值后加一',
    CONSTRAINT `pk_it_device` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_it_device_customer_asset` (`customer_id`, `asset_code`),
    UNIQUE KEY `uk_it_device_id_customer` (`id`, `customer_id`),
    KEY `idx_it_device_customer_status` (`customer_id`, `status`, `id`),
    KEY `idx_it_device_model` (`product_model_id`),
    CONSTRAINT `ck_it_device__status` CHECK (`status` IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT `ck_it_device__asset_normalized` CHECK (asset_code = TRIM(asset_code) AND asset_code = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(`asset_code`, 'a', 'A'), 'b', 'B'), 'c', 'C'), 'd', 'D'), 'e', 'E'), 'f', 'F'), 'g', 'G'), 'h', 'H'), 'i', 'I'), 'j', 'J'), 'k', 'K'), 'l', 'L'), 'm', 'M'), 'n', 'N'), 'o', 'O'), 'p', 'P'), 'q', 'Q'), 'r', 'R'), 's', 'S'), 't', 'T'), 'u', 'U'), 'v', 'V'), 'w', 'W'), 'x', 'X'), 'y', 'Y'), 'z', 'Z')),
    CONSTRAINT `ck_it_device__area_format` CHECK (service_area_code IS NULL OR (REGEXP_LIKE(service_area_code, '^[!-~]+$', 'c') AND NOT REGEXP_LIKE(service_area_code, '[a-z]', 'c'))),
    CONSTRAINT `ck_it_device__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_it_device__customer_id_format` CHECK (REGEXP_LIKE(`customer_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_it_device__product_model_id_format` CHECK (REGEXP_LIKE(`product_model_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_it_device__asset_code_nonblank` CHECK (REGEXP_LIKE(`asset_code`, '[^[:space:]]')),
    CONSTRAINT `ck_it_device__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[:space:]]')),
    CONSTRAINT `ck_it_device__created_by_format` CHECK (REGEXP_LIKE(`created_by`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_it_device__row_version_range` CHECK (`row_version` >= 0),
    CONSTRAINT `ck_it_device__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='客户设备实例';

-- 正式工单
CREATE TABLE `sd_ticket` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
    `ticket_no` VARCHAR(40) NOT NULL COMMENT '全局唯一后端编号',
    `customer_id` VARCHAR(32) NOT NULL COMMENT '外部客户组织 ID，同时参与本地设备归属复合 FK',
    `requester_id` VARCHAR(32) NOT NULL COMMENT '外部提交人 ID',
    `client_request_id` VARCHAR(32) NOT NULL COMMENT '提交人范围幂等键',
    `request_hash` VARCHAR(64) NOT NULL COMMENT '规范化语义输入 SHA-256',
    `source_draft_id` VARCHAR(32) NULL COMMENT '外部 AI 草稿 ID，非空唯一',
    `device_id` VARCHAR(32) NOT NULL COMMENT '本地复合 FK(device_id, customer_id) → it_device(id, customer_id)',
    `device_snapshot` JSON NOT NULL COMMENT 'schema_version、资产号、型号 ID、品牌、型号、地点，不复制敏感序列号',
    `title` VARCHAR(200) NOT NULL COMMENT '用户确认标题，非空白',
    `symptom` TEXT NOT NULL COMMENT '故障现象，最多5000字符',
    `attempted_actions` TEXT NULL COMMENT '已尝试操作，最多5000字符',
    `priority` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL / HIGH，客服动作调整',
    `status` VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED / PROCESSING / WAITING_CUSTOMER / RESOLVED / CLOSED / CANCELLED',
    `assignee_id` VARCHAR(32) NULL COMMENT '外部当前技术人员 ID，SUBMITTED 为空',
    `first_response_at` DATETIME(6) NULL COMMENT '第一次接单或分派成功，不随转派覆盖',
    `response_due_at` DATETIME(6) NOT NULL COMMENT '创建时默认优先级计算，后续合法调整按原创建时刻重算',
    `response_overdue_recorded_at` DATETIME(6) NULL COMMENT '首次响应超期事件登记时间',
    `resolved_at` DATETIME(6) NULL COMMENT '本轮解决时间，未解决回退时清空',
    `confirmation_due_at` DATETIME(6) NULL COMMENT 'RESOLVED 后72小时；回退清空，关闭保留',
    `onsite_eligible` BOOLEAN NOT NULL DEFAULT 0 COMMENT '客服或当前处理人确认需要现场服务，仅 PROCESSING、WAITING_CUSTOMER 可以为 true',
    `closed_at` DATETIME(6) NULL COMMENT 'CLOSED 终态时有值',
    `cancelled_at` DATETIME(6) NULL COMMENT 'CANCELLED 终态时有值',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
    `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT '非负，更新比较旧值后加一',
    CONSTRAINT `pk_sd_ticket` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sd_ticket_number` (`ticket_no`),
    UNIQUE KEY `uk_sd_ticket_request` (`requester_id`, `client_request_id`),
    UNIQUE KEY `uk_sd_ticket_source_draft` (`source_draft_id`),
    KEY `idx_sd_ticket_requester_updated` (`requester_id`, `customer_id`, `updated_at` DESC, `id`),
    KEY `idx_sd_ticket_customer_status` (`customer_id`, `status`, `created_at`, `id`),
    KEY `idx_sd_ticket_assignee_status` (`assignee_id`, `status`, `updated_at`, `id`),
    KEY `idx_sd_ticket_response_due` (`status`, `response_overdue_recorded_at`, `response_due_at`, `id`),
    KEY `idx_sd_ticket_confirmation_due` (`status`, `confirmation_due_at`, `id`),
    KEY `idx_sd_ticket_device_customer` (`device_id`, `customer_id`),
    CONSTRAINT `ck_sd_ticket__priority` CHECK (`priority` IN ('NORMAL', 'HIGH')),
    CONSTRAINT `ck_sd_ticket__status` CHECK (`status` IN ('SUBMITTED', 'PROCESSING', 'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT `ck_sd_ticket__assignee_state` CHECK ((status = 'SUBMITTED' AND assignee_id IS NULL AND first_response_at IS NULL) OR (status IN ('PROCESSING', 'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED') AND assignee_id IS NOT NULL AND first_response_at IS NOT NULL) OR (status = 'CANCELLED' AND ((assignee_id IS NULL AND first_response_at IS NULL) OR (assignee_id IS NOT NULL AND first_response_at IS NOT NULL)))),
    CONSTRAINT `ck_sd_ticket__resolved_state` CHECK ((status IN ('RESOLVED', 'CLOSED') AND resolved_at IS NOT NULL AND confirmation_due_at IS NOT NULL) OR (status NOT IN ('RESOLVED', 'CLOSED') AND resolved_at IS NULL AND confirmation_due_at IS NULL)),
    CONSTRAINT `ck_sd_ticket__closed_state` CHECK ((status = 'CLOSED' AND closed_at IS NOT NULL) OR (status <> 'CLOSED' AND closed_at IS NULL)),
    CONSTRAINT `ck_sd_ticket__cancelled_state` CHECK ((status = 'CANCELLED' AND cancelled_at IS NOT NULL) OR (status <> 'CANCELLED' AND cancelled_at IS NULL)),
    CONSTRAINT `ck_sd_ticket__onsite_state` CHECK (onsite_eligible = 0 OR status IN ('PROCESSING', 'WAITING_CUSTOMER')),
    CONSTRAINT `ck_sd_ticket__time_order` CHECK (response_due_at > created_at AND (first_response_at IS NULL OR first_response_at >= created_at) AND (response_overdue_recorded_at IS NULL OR response_overdue_recorded_at >= response_due_at) AND (resolved_at IS NULL OR (first_response_at IS NOT NULL AND resolved_at >= first_response_at)) AND (confirmation_due_at IS NULL OR (resolved_at IS NOT NULL AND confirmation_due_at > resolved_at)) AND (closed_at IS NULL OR (resolved_at IS NOT NULL AND closed_at >= resolved_at)) AND (cancelled_at IS NULL OR cancelled_at >= created_at)),
    CONSTRAINT `ck_sd_ticket__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_ticket__ticket_no_nonblank` CHECK (REGEXP_LIKE(`ticket_no`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_ticket__customer_id_format` CHECK (REGEXP_LIKE(`customer_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_ticket__requester_id_format` CHECK (REGEXP_LIKE(`requester_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_ticket__client_request_id_format` CHECK (REGEXP_LIKE(`client_request_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_ticket__request_hash_format` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `ck_sd_ticket__source_draft_id_format` CHECK (`source_draft_id` IS NULL OR (REGEXP_LIKE(`source_draft_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sd_ticket__device_id_format` CHECK (REGEXP_LIKE(`device_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_ticket__device_snapshot_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `device_snapshot`) = 1),
    CONSTRAINT `ck_sd_ticket__title_nonblank` CHECK (REGEXP_LIKE(`title`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_ticket__symptom_nonblank` CHECK (REGEXP_LIKE(`symptom`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_ticket__symptom_length` CHECK (CHAR_LENGTH(`symptom`) <= 5000),
    CONSTRAINT `ck_sd_ticket__attempted_actions_length` CHECK (`attempted_actions` IS NULL OR (CHAR_LENGTH(`attempted_actions`) <= 5000)),
    CONSTRAINT `ck_sd_ticket__priority_nonblank` CHECK (REGEXP_LIKE(`priority`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_ticket__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_ticket__assignee_id_format` CHECK (`assignee_id` IS NULL OR (REGEXP_LIKE(`assignee_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sd_ticket__onsite_eligible_boolean` CHECK (`onsite_eligible` IN (0,1)),
    CONSTRAINT `ck_sd_ticket__row_version_range` CHECK (`row_version` >= 0),
    CONSTRAINT `ck_sd_ticket__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='正式工单';

-- 不可变处理时间线
CREATE TABLE `sd_ticket_event` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
    `ticket_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → sd_ticket',
    `sequence_number` INT NOT NULL COMMENT '工单内正序，锁工单分配',
    `event_type` VARCHAR(32) NOT NULL COMMENT 'CREATED / ACCEPTED / ASSIGNED / REASSIGNED / NEED_INFO / SUPPLEMENTED / RESOLVED / UNRESOLVED / CLOSED / CANCELLED / COMMENTED / RESPONSE_OVERDUE / PRIORITY_CHANGED / ONSITE_ELIGIBILITY_CHANGED / ONSITE_BOOKED / ONSITE_BOOKING_CANCELLED / ONSITE_FINISHED',
    `onsite_booking_id` VARCHAR(32) NULL COMMENT '上门确认、已确认预约取消或履约事件的本地复合 FK(onsite_booking_id,ticket_id) → sd_onsite_booking(id,ticket_id)',
    `actor_type` VARCHAR(16) NOT NULL COMMENT 'USER / SYSTEM',
    `actor_id` VARCHAR(32) NULL COMMENT 'USER 必填外部账号；SYSTEM 为空',
    `client_action_id` VARCHAR(32) NULL COMMENT '人工动作重试键，CREATED 由工单创建键负责，SYSTEM 为空',
    `action_request_hash` VARCHAR(64) NULL COMMENT '人工动作类型、期望版本及语义输入摘要，与 client_action_id 同空同有',
    `visibility` VARCHAR(16) NOT NULL COMMENT 'PUBLIC / INTERNAL',
    `from_status` VARCHAR(32) NULL COMMENT 'CREATED 为空，其他为动作前状态',
    `to_status` VARCHAR(32) NOT NULL COMMENT '动作后状态；普通备注前后相同',
    `content` TEXT NOT NULL COMMENT '公开说明或内部备注，最多5000字符',
    `metadata` JSON NOT NULL COMMENT 'schema_version、前后处理人或优先级等受控字段',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    CONSTRAINT `pk_sd_ticket_event` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sd_ticket_event_sequence` (`ticket_id`, `sequence_number`),
    UNIQUE KEY `uk_sd_ticket_event_id_ticket` (`id`, `ticket_id`),
    UNIQUE KEY `uk_sd_ticket_event_action` (`ticket_id`, `actor_id`, `client_action_id`),
    UNIQUE KEY `uk_sd_ticket_event_booking` (`onsite_booking_id`, `event_type`),
    KEY `idx_sd_ticket_event_booking_ticket` (`onsite_booking_id`, `ticket_id`),
    CONSTRAINT `ck_sd_ticket_event__event_type` CHECK (`event_type` IN ('CREATED', 'ACCEPTED', 'ASSIGNED', 'REASSIGNED', 'NEED_INFO', 'SUPPLEMENTED', 'RESOLVED', 'UNRESOLVED', 'CLOSED', 'CANCELLED', 'COMMENTED', 'RESPONSE_OVERDUE', 'PRIORITY_CHANGED', 'ONSITE_ELIGIBILITY_CHANGED', 'ONSITE_BOOKED', 'ONSITE_BOOKING_CANCELLED', 'ONSITE_FINISHED')),
    CONSTRAINT `ck_sd_ticket_event__actor_type` CHECK (`actor_type` IN ('USER', 'SYSTEM')),
    CONSTRAINT `ck_sd_ticket_event__visibility` CHECK (`visibility` IN ('PUBLIC', 'INTERNAL')),
    CONSTRAINT `ck_sd_ticket_event__from_status` CHECK (`from_status` IN ('SUBMITTED', 'PROCESSING', 'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT `ck_sd_ticket_event__to_status` CHECK (`to_status` IN ('SUBMITTED', 'PROCESSING', 'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT `ck_sd_ticket_event__actor` CHECK ((actor_type = 'USER' AND actor_id IS NOT NULL) OR (actor_type = 'SYSTEM' AND actor_id IS NULL)),
    CONSTRAINT `ck_sd_ticket_event__from_state` CHECK ((event_type = 'CREATED' AND from_status IS NULL AND to_status = 'SUBMITTED') OR (event_type <> 'CREATED' AND from_status IS NOT NULL)),
    CONSTRAINT `ck_sd_ticket_event__booking_state` CHECK ((event_type IN ('ONSITE_BOOKED', 'ONSITE_BOOKING_CANCELLED', 'ONSITE_FINISHED') AND onsite_booking_id IS NOT NULL AND from_status = to_status) OR (event_type NOT IN ('ONSITE_BOOKED', 'ONSITE_BOOKING_CANCELLED', 'ONSITE_FINISHED') AND onsite_booking_id IS NULL)),
    CONSTRAINT `ck_sd_ticket_event__action_receipt` CHECK ((actor_type = 'USER' AND event_type NOT IN ('CREATED', 'ONSITE_BOOKED', 'ONSITE_BOOKING_CANCELLED', 'ONSITE_FINISHED') AND client_action_id IS NOT NULL AND action_request_hash IS NOT NULL) OR ((actor_type = 'SYSTEM' OR event_type IN ('CREATED', 'ONSITE_BOOKED', 'ONSITE_BOOKING_CANCELLED', 'ONSITE_FINISHED')) AND client_action_id IS NULL AND action_request_hash IS NULL)),
    CONSTRAINT `ck_sd_ticket_event__public_reason` CHECK (event_type NOT IN ('NEED_INFO', 'RESOLVED', 'CANCELLED') OR visibility = 'PUBLIC'),
    CONSTRAINT `ck_sd_ticket_event__overdue_visibility` CHECK (event_type <> 'RESPONSE_OVERDUE' OR (actor_type = 'SYSTEM' AND visibility = 'INTERNAL')),
    CONSTRAINT `ck_sd_ticket_event__system_close` CHECK (NOT (actor_type = 'SYSTEM' AND event_type = 'CLOSED') OR visibility = 'PUBLIC'),
    CONSTRAINT `ck_sd_ticket_event__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_ticket_event__ticket_id_format` CHECK (REGEXP_LIKE(`ticket_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_ticket_event__sequence_number_positive` CHECK (`sequence_number` > 0),
    CONSTRAINT `ck_sd_ticket_event__event_type_nonblank` CHECK (REGEXP_LIKE(`event_type`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_ticket_event__onsite_booking_id_format` CHECK (`onsite_booking_id` IS NULL OR (REGEXP_LIKE(`onsite_booking_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sd_ticket_event__actor_type_nonblank` CHECK (REGEXP_LIKE(`actor_type`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_ticket_event__actor_id_format` CHECK (`actor_id` IS NULL OR (REGEXP_LIKE(`actor_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sd_ticket_event__client_action_id_format` CHECK (`client_action_id` IS NULL OR (REGEXP_LIKE(`client_action_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sd_ticket_event__action_request_hash_format` CHECK (`action_request_hash` IS NULL OR (REGEXP_LIKE(`action_request_hash`, '^[0-9a-f]{64}$', 'c'))),
    CONSTRAINT `ck_sd_ticket_event__visibility_nonblank` CHECK (REGEXP_LIKE(`visibility`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_ticket_event__to_status_nonblank` CHECK (REGEXP_LIKE(`to_status`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_ticket_event__content_nonblank` CHECK (REGEXP_LIKE(`content`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_ticket_event__content_length` CHECK (CHAR_LENGTH(`content`) <= 5000),
    CONSTRAINT `ck_sd_ticket_event__metadata_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `metadata`) = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='不可变处理时间线';

-- 站内通知
CREATE TABLE `sd_notification` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
    `ticket_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → sd_ticket',
    `event_id` VARCHAR(32) NOT NULL COMMENT '本地复合 FK(event_id,ticket_id) → sd_ticket_event(id,ticket_id)',
    `recipient_id` VARCHAR(32) NOT NULL COMMENT '外部接收账号 ID',
    `read_at` DATETIME(6) NULL COMMENT '接收人首次已读时间',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    CONSTRAINT `pk_sd_notification` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sd_notification_event_recipient` (`event_id`, `recipient_id`),
    KEY `idx_sd_notification_recipient_created` (`recipient_id`, `created_at` DESC, `id`),
    KEY `idx_sd_notification_ticket` (`ticket_id`),
    KEY `idx_sd_notification_event_ticket` (`event_id`, `ticket_id`),
    CONSTRAINT `ck_sd_notification__read_time` CHECK (read_at IS NULL OR read_at >= created_at),
    CONSTRAINT `ck_sd_notification__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_notification__ticket_id_format` CHECK (REGEXP_LIKE(`ticket_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_notification__event_id_format` CHECK (REGEXP_LIKE(`event_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_notification__recipient_id_format` CHECK (REGEXP_LIKE(`recipient_id`, '^[0-9a-f]{32}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='站内通知';

-- 区域上门时段
CREATE TABLE `sd_onsite_slot` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键',
    `service_area_code` VARCHAR(32) NOT NULL COMMENT '服务商区域代码，与设备使用同一受控词表',
    `starts_at` DATETIME(6) NOT NULL COMMENT '到访窗口开始',
    `ends_at` DATETIME(6) NOT NULL COMMENT '到访窗口结束',
    `booking_opens_at` DATETIME(6) NOT NULL COMMENT '开放申请时间',
    `booking_closes_at` DATETIME(6) NOT NULL COMMENT '申请和确认截止，等于或早于窗口开始',
    `capacity` INT NOT NULL COMMENT '排班已预留的出勤名额，正数',
    `reserved_count` INT NOT NULL DEFAULT 0 COMMENT '已确认或已履约的预约数',
    `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / CLOSED',
    `created_by` VARCHAR(32) NOT NULL COMMENT '外部创建管理员账号',
    `published_by` VARCHAR(32) NULL COMMENT '外部发布账号，与 published_at 同空同有',
    `published_at` DATETIME(6) NULL COMMENT '已发布的时间',
    `closed_by` VARCHAR(32) NULL COMMENT '外部关停账号，与 closed_at 同空同有',
    `closed_at` DATETIME(6) NULL COMMENT '人工关停时间',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT '管理动作与容量更新的行版本',
    CONSTRAINT `pk_sd_onsite_slot` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sd_onsite_slot_area_start` (`service_area_code`, `starts_at`),
    UNIQUE KEY `uk_sd_onsite_slot_id_area` (`id`, `service_area_code`),
    KEY `idx_sd_onsite_slot_area_status_start` (`service_area_code`, `status`, `starts_at`, `id`),
    CONSTRAINT `ck_sd_onsite_slot__status` CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    CONSTRAINT `ck_sd_onsite_slot__capacity` CHECK (capacity > 0 AND reserved_count >= 0 AND reserved_count <= capacity),
    CONSTRAINT `ck_sd_onsite_slot__window` CHECK (TIME(starts_at) IN ('01:00:00', '03:00:00', '06:00:00', '08:00:00') AND MICROSECOND(starts_at) = 0 AND ends_at = starts_at + INTERVAL 2 HOUR AND booking_opens_at < booking_closes_at AND booking_closes_at <= starts_at),
    CONSTRAINT `ck_sd_onsite_slot__publication_state` CHECK ((status = 'DRAFT' AND published_by IS NULL AND published_at IS NULL AND closed_by IS NULL AND closed_at IS NULL) OR (status = 'PUBLISHED' AND published_by IS NOT NULL AND published_at IS NOT NULL AND closed_by IS NULL AND closed_at IS NULL) OR (status = 'CLOSED' AND published_by IS NOT NULL AND published_at IS NOT NULL AND closed_by IS NOT NULL AND closed_at IS NOT NULL)),
    CONSTRAINT `ck_sd_onsite_slot__publication_time` CHECK ((published_at IS NULL OR (published_at >= created_at AND published_at <= booking_opens_at)) AND (closed_at IS NULL OR (published_at IS NOT NULL AND closed_at >= published_at))),
    CONSTRAINT `ck_sd_onsite_slot__area_format` CHECK (REGEXP_LIKE(service_area_code, '^[!-~]+$', 'c') AND NOT REGEXP_LIKE(service_area_code, '[a-z]', 'c')),
    CONSTRAINT `ck_sd_onsite_slot__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_onsite_slot__service_area_code_nonblank` CHECK (REGEXP_LIKE(`service_area_code`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_onsite_slot__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_onsite_slot__created_by_format` CHECK (REGEXP_LIKE(`created_by`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_onsite_slot__published_by_format` CHECK (`published_by` IS NULL OR (REGEXP_LIKE(`published_by`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sd_onsite_slot__closed_by_format` CHECK (`closed_by` IS NULL OR (REGEXP_LIKE(`closed_by`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sd_onsite_slot__row_version_range` CHECK (`row_version` >= 0),
    CONSTRAINT `ck_sd_onsite_slot__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='区域上门时段';

-- 工单上门预约
CREATE TABLE `sd_onsite_booking` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键',
    `ticket_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → sd_ticket',
    `slot_id` VARCHAR(32) NOT NULL COMMENT '本地复合 FK(slot_id,service_area_code) → sd_onsite_slot(id,service_area_code)',
    `service_area_code` VARCHAR(32) NOT NULL COMMENT '申请时设备所属区域，与时段匹配',
    `visit_address` VARCHAR(300) NOT NULL COMMENT '服务地点快照，仅向有权人员展示',
    `client_request_id` VARCHAR(32) NOT NULL COMMENT '工单范围内客户端幂等键',
    `request_hash` VARCHAR(64) NOT NULL COMMENT '工单和所选时段的规范化请求摘要',
    `status` VARCHAR(16) NOT NULL COMMENT 'CONFIRMED / REJECTED / CANCELLED / FINISHED',
    `result_code` VARCHAR(32) NULL COMMENT '当且仅当REJECTED为SOLD_OUT或SLOT_CLOSED',
    `active_ticket_id` VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN status = 'CONFIRMED' THEN ticket_id ELSE NULL END) STORED COMMENT 'CONFIRMED时为ticket_id，否则NULL；唯一',
    `confirmed_at` DATETIME(6) NULL COMMENT 'CONFIRMED/CANCELLED/FINISHED必填，REJECTED为空',
    `ended_at` DATETIME(6) NULL COMMENT 'REJECTED/CANCELLED/FINISHED必填，CONFIRMED为空',
    `finish_result` VARCHAR(16) NULL COMMENT '当且仅当FINISHED必填，VISITED / NOT_PERFORMED',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '申请决策时间',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT `pk_sd_onsite_booking` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sd_onsite_booking_request` (`ticket_id`, `client_request_id`),
    UNIQUE KEY `uk_sd_onsite_booking_id_ticket` (`id`, `ticket_id`),
    UNIQUE KEY `uk_sd_onsite_booking_active` (`active_ticket_id`),
    KEY `idx_sd_onsite_booking_ticket_created` (`ticket_id`, `created_at` DESC, `id`),
    KEY `idx_sd_onsite_booking_slot_status` (`slot_id`, `status`, `id`),
    KEY `idx_sd_onsite_booking_slot_area` (`slot_id`, `service_area_code`),
    CONSTRAINT `ck_sd_onsite_booking__status` CHECK (`status` IN ('CONFIRMED', 'REJECTED', 'CANCELLED', 'FINISHED')),
    CONSTRAINT `ck_sd_onsite_booking__result_code` CHECK (`result_code` IN ('SOLD_OUT', 'SLOT_CLOSED')),
    CONSTRAINT `ck_sd_onsite_booking__finish_result` CHECK (`finish_result` IN ('VISITED', 'NOT_PERFORMED')),
    CONSTRAINT `ck_sd_onsite_booking__state_fields` CHECK ((status = 'CONFIRMED' AND result_code IS NULL AND confirmed_at IS NOT NULL AND ended_at IS NULL AND finish_result IS NULL) OR (status = 'REJECTED' AND result_code IS NOT NULL AND confirmed_at IS NULL AND ended_at IS NOT NULL AND finish_result IS NULL) OR (status = 'CANCELLED' AND result_code IS NULL AND confirmed_at IS NOT NULL AND ended_at IS NOT NULL AND finish_result IS NULL) OR (status = 'FINISHED' AND result_code IS NULL AND confirmed_at IS NOT NULL AND ended_at IS NOT NULL AND finish_result IS NOT NULL)),
    CONSTRAINT `ck_sd_onsite_booking__time_order` CHECK ((confirmed_at IS NULL OR confirmed_at >= created_at) AND (ended_at IS NULL OR (ended_at >= created_at AND (confirmed_at IS NULL OR ended_at >= confirmed_at)))),
    CONSTRAINT `ck_sd_onsite_booking__area_format` CHECK (REGEXP_LIKE(service_area_code, '^[!-~]+$', 'c') AND NOT REGEXP_LIKE(service_area_code, '[a-z]', 'c')),
    CONSTRAINT `ck_sd_onsite_booking__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_onsite_booking__ticket_id_format` CHECK (REGEXP_LIKE(`ticket_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_onsite_booking__slot_id_format` CHECK (REGEXP_LIKE(`slot_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_onsite_booking__service_area_code_nonblank` CHECK (REGEXP_LIKE(`service_area_code`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_onsite_booking__visit_address_nonblank` CHECK (REGEXP_LIKE(`visit_address`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_onsite_booking__client_request_id_format` CHECK (REGEXP_LIKE(`client_request_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sd_onsite_booking__request_hash_format` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `ck_sd_onsite_booking__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[:space:]]')),
    CONSTRAINT `ck_sd_onsite_booking__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='工单上门预约';

-- 全部表建立后补齐本服务外键，包含资料与索引任务的循环引用；不关闭外键检查。

ALTER TABLE `it_device`
    ADD CONSTRAINT `fk_it_device_model` FOREIGN KEY (`product_model_id`)
    REFERENCES `it_product_model` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sd_ticket`
    ADD CONSTRAINT `fk_sd_ticket_device_customer` FOREIGN KEY (`device_id`, `customer_id`)
    REFERENCES `it_device` (`id`, `customer_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sd_ticket_event`
    ADD CONSTRAINT `fk_sd_ticket_event_ticket` FOREIGN KEY (`ticket_id`)
    REFERENCES `sd_ticket` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sd_ticket_event`
    ADD CONSTRAINT `fk_sd_ticket_event_booking_ticket` FOREIGN KEY (`onsite_booking_id`, `ticket_id`)
    REFERENCES `sd_onsite_booking` (`id`, `ticket_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sd_notification`
    ADD CONSTRAINT `fk_sd_notification_ticket` FOREIGN KEY (`ticket_id`)
    REFERENCES `sd_ticket` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sd_notification`
    ADD CONSTRAINT `fk_sd_notification_event_ticket` FOREIGN KEY (`event_id`, `ticket_id`)
    REFERENCES `sd_ticket_event` (`id`, `ticket_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sd_onsite_booking`
    ADD CONSTRAINT `fk_sd_onsite_booking_ticket` FOREIGN KEY (`ticket_id`)
    REFERENCES `sd_ticket` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sd_onsite_booking`
    ADD CONSTRAINT `fk_sd_onsite_booking_slot_area` FOREIGN KEY (`slot_id`, `service_area_code`)
    REFERENCES `sd_onsite_slot` (`id`, `service_area_code`) ON DELETE NO ACTION ON UPDATE NO ACTION;
