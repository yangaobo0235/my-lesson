-- 服务所属初始结构；已执行版本保持不变，后续变更新增版本。
SET SESSION time_zone = '+00:00';

-- 客户与服务商组织
CREATE TABLE `sys_organization` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
    `organization_code` VARCHAR(64) NOT NULL COMMENT '规范化小写后全局唯一，停用不复用',
    `organization_name` VARCHAR(100) NOT NULL COMMENT '展示名称，不作为授权标识',
    `organization_type` VARCHAR(32) NOT NULL COMMENT 'CUSTOMER / SERVICE_PROVIDER',
    `service_provider_guard` TINYINT GENERATED ALWAYS AS (CASE WHEN organization_type = 'SERVICE_PROVIDER' THEN 1 ELSE NULL END) STORED COMMENT 'SERVICE_PROVIDER 为1，否则 NULL；唯一',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
    `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT '非负，更新比较旧值后加一',
    CONSTRAINT `pk_sys_organization` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_organization_code` (`organization_code`),
    UNIQUE KEY `uk_sys_organization_provider` (`service_provider_guard`),
    KEY `idx_sys_organization_status` (`status`, `id`),
    CONSTRAINT `ck_sys_organization__organization_type` CHECK (`organization_type` IN ('CUSTOMER', 'SERVICE_PROVIDER')),
    CONSTRAINT `ck_sys_organization__status` CHECK (`status` IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT `ck_sys_organization__code_normalized` CHECK (organization_code = TRIM(organization_code) AND organization_code = LOWER(organization_code)),
    CONSTRAINT `ck_sys_organization__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sys_organization__organization_code_nonblank` CHECK (REGEXP_LIKE(`organization_code`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_organization__organization_name_nonblank` CHECK (REGEXP_LIKE(`organization_name`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_organization__organization_type_nonblank` CHECK (REGEXP_LIKE(`organization_type`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_organization__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_organization__row_version_range` CHECK (`row_version` >= 0),
    CONSTRAINT `ck_sys_organization__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='客户与服务商组织';

-- 账号
CREATE TABLE `sys_user` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
    `organization_id` VARCHAR(32) NOT NULL COMMENT '本库 FK → sys_organization',
    `username` VARCHAR(64) NOT NULL COMMENT '去首尾空格、转小写，全局唯一',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'Spring Security 哈希及算法标识，不能输出',
    `display_name` VARCHAR(100) NOT NULL COMMENT '姓名，不作授权条件',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    `must_change_password` BOOLEAN NOT NULL DEFAULT 1 COMMENT '初始化和重置后强制改密',
    `password_changed_at` DATETIME(6) NULL COMMENT '首次主动改密前为空',
    `last_login_at` DATETIME(6) NULL COMMENT '仅认证成功更新',
    `auth_version` BIGINT NOT NULL DEFAULT 0 COMMENT '改密、角色、组织、客户范围或停用等认证相关变更加一',
    `created_by` VARCHAR(32) NULL COMMENT '本库 FK → sys_user，首次管理员初始化可空',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
    `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT '非负，更新比较旧值后加一',
    CONSTRAINT `pk_sys_user` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`),
    KEY `idx_sys_user_organization_status` (`organization_id`, `status`, `id`),
    KEY `idx_sys_user_creator` (`created_by`),
    CONSTRAINT `ck_sys_user__status` CHECK (`status` IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT `ck_sys_user__username_format` CHECK (REGEXP_LIKE(username, '^[a-z0-9][a-z0-9._-]{2,63}$', 'c')),
    CONSTRAINT `ck_sys_user__password_changed_at_time` CHECK (password_changed_at IS NULL OR password_changed_at >= created_at),
    CONSTRAINT `ck_sys_user__last_login_at_time` CHECK (last_login_at IS NULL OR last_login_at >= created_at),
    CONSTRAINT `ck_sys_user__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sys_user__organization_id_format` CHECK (REGEXP_LIKE(`organization_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sys_user__username_nonblank` CHECK (REGEXP_LIKE(`username`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_user__password_hash_nonblank` CHECK (REGEXP_LIKE(`password_hash`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_user__display_name_nonblank` CHECK (REGEXP_LIKE(`display_name`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_user__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_user__must_change_password_boolean` CHECK (`must_change_password` IN (0,1)),
    CONSTRAINT `ck_sys_user__auth_version_range` CHECK (`auth_version` >= 0),
    CONSTRAINT `ck_sys_user__created_by_format` CHECK (`created_by` IS NULL OR (REGEXP_LIKE(`created_by`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sys_user__row_version_range` CHECK (`row_version` >= 0),
    CONSTRAINT `ck_sys_user__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='账号';

-- 账号角色
CREATE TABLE `sys_user_role` (
    `user_id` VARCHAR(32) NOT NULL COMMENT '联合 PK，本地 FK → sys_user',
    `role_code` VARCHAR(32) NOT NULL COMMENT '联合 PK，五种固定角色码',
    `granted_by` VARCHAR(32) NULL COMMENT '本地 FK → sys_user，首次初始化可空',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    CONSTRAINT `pk_sys_user_role` PRIMARY KEY (`user_id`, `role_code`),
    KEY `idx_sys_user_role_role_user` (`role_code`, `user_id`),
    KEY `idx_sys_user_role_grantor` (`granted_by`),
    CONSTRAINT `ck_sys_user_role__role_code` CHECK (`role_code` IN ('CUSTOMER_USER', 'SERVICE_AGENT', 'TECHNICIAN', 'KNOWLEDGE_ADMIN', 'SYSTEM_ADMIN')),
    CONSTRAINT `ck_sys_user_role__user_id_format` CHECK (REGEXP_LIKE(`user_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sys_user_role__role_code_nonblank` CHECK (REGEXP_LIKE(`role_code`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_user_role__granted_by_format` CHECK (`granted_by` IS NULL OR (REGEXP_LIKE(`granted_by`, '^[0-9a-f]{32}$', 'c')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='账号角色';

-- 服务人员客户范围
CREATE TABLE `sys_user_customer_scope` (
    `user_id` VARCHAR(32) NOT NULL COMMENT '联合 PK，本地 FK → sys_user',
    `customer_id` VARCHAR(32) NOT NULL COMMENT '联合 PK，本地 FK → CUSTOMER 类型的 sys_organization',
    `granted_by` VARCHAR(32) NOT NULL COMMENT '本地 FK → sys_user，授权管理员',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    CONSTRAINT `pk_sys_user_customer_scope` PRIMARY KEY (`user_id`, `customer_id`),
    KEY `idx_sys_user_customer_scope_customer_user` (`customer_id`, `user_id`),
    KEY `idx_sys_user_customer_scope_grantor` (`granted_by`),
    CONSTRAINT `ck_sys_user_customer_scope__user_id_format` CHECK (REGEXP_LIKE(`user_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sys_user_customer_scope__customer_id_format` CHECK (REGEXP_LIKE(`customer_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sys_user_customer_scope__granted_by_format` CHECK (REGEXP_LIKE(`granted_by`, '^[0-9a-f]{32}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='服务人员客户范围';

-- 身份管理审计
CREATE TABLE `sys_admin_event` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键',
    `actor_type` VARCHAR(16) NOT NULL COMMENT 'USER / BOOTSTRAP',
    `actor_user_id` VARCHAR(32) NULL COMMENT '本地 FK → sys_user，USER 必填，BOOTSTRAP 为空',
    `target_user_id` VARCHAR(32) NULL COMMENT '本地 FK → sys_user',
    `target_organization_id` VARCHAR(32) NULL COMMENT '本地 FK → sys_organization，与目标账号恰有一个',
    `event_type` VARCHAR(32) NOT NULL COMMENT 'ORGANIZATION_CREATED / ORGANIZATION_UPDATED / ACCOUNT_CREATED / ACCOUNT_UPDATED / ACCOUNT_ENABLED / ACCOUNT_DISABLED / PASSWORD_RESET / ROLES_CHANGED / CUSTOMER_SCOPE_CHANGED',
    `details` JSON NOT NULL COMMENT 'schema_version、按事件定义的前后值及变更原因，凭据字段排除',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '变更时间',
    CONSTRAINT `pk_sys_admin_event` PRIMARY KEY (`id`),
    KEY `idx_sys_admin_event_user_created` (`target_user_id`, `created_at` DESC, `id`),
    KEY `idx_sys_admin_event_organization_created` (`target_organization_id`, `created_at` DESC, `id`),
    KEY `idx_sys_admin_event_actor` (`actor_user_id`),
    CONSTRAINT `ck_sys_admin_event__actor_type` CHECK (`actor_type` IN ('USER', 'BOOTSTRAP')),
    CONSTRAINT `ck_sys_admin_event__event_type` CHECK (`event_type` IN ('ORGANIZATION_CREATED', 'ORGANIZATION_UPDATED', 'ACCOUNT_CREATED', 'ACCOUNT_UPDATED', 'ACCOUNT_ENABLED', 'ACCOUNT_DISABLED', 'PASSWORD_RESET', 'ROLES_CHANGED', 'CUSTOMER_SCOPE_CHANGED')),
    CONSTRAINT `ck_sys_admin_event__actor` CHECK ((actor_type = 'USER' AND actor_user_id IS NOT NULL) OR (actor_type = 'BOOTSTRAP' AND actor_user_id IS NULL)),
    CONSTRAINT `ck_sys_admin_event__target` CHECK ((target_user_id IS NULL) <> (target_organization_id IS NULL)),
    CONSTRAINT `ck_sys_admin_event__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_sys_admin_event__actor_type_nonblank` CHECK (REGEXP_LIKE(`actor_type`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_admin_event__actor_user_id_format` CHECK (`actor_user_id` IS NULL OR (REGEXP_LIKE(`actor_user_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sys_admin_event__target_user_id_format` CHECK (`target_user_id` IS NULL OR (REGEXP_LIKE(`target_user_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sys_admin_event__target_organization_id_format` CHECK (`target_organization_id` IS NULL OR (REGEXP_LIKE(`target_organization_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_sys_admin_event__event_type_nonblank` CHECK (REGEXP_LIKE(`event_type`, '[^[:space:]]')),
    CONSTRAINT `ck_sys_admin_event__details_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `details`) = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='身份管理审计';

-- 全部表建立后补齐本服务外键，包含资料与索引任务的循环引用；不关闭外键检查。

ALTER TABLE `sys_user`
    ADD CONSTRAINT `fk_sys_user_organization` FOREIGN KEY (`organization_id`)
    REFERENCES `sys_organization` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sys_user`
    ADD CONSTRAINT `fk_sys_user_creator` FOREIGN KEY (`created_by`)
    REFERENCES `sys_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sys_user_role`
    ADD CONSTRAINT `fk_sys_user_role_user` FOREIGN KEY (`user_id`)
    REFERENCES `sys_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sys_user_role`
    ADD CONSTRAINT `fk_sys_user_role_grantor` FOREIGN KEY (`granted_by`)
    REFERENCES `sys_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sys_user_customer_scope`
    ADD CONSTRAINT `fk_sys_user_customer_scope_user` FOREIGN KEY (`user_id`)
    REFERENCES `sys_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sys_user_customer_scope`
    ADD CONSTRAINT `fk_sys_user_customer_scope_customer` FOREIGN KEY (`customer_id`)
    REFERENCES `sys_organization` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sys_user_customer_scope`
    ADD CONSTRAINT `fk_sys_user_customer_scope_grantor` FOREIGN KEY (`granted_by`)
    REFERENCES `sys_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sys_admin_event`
    ADD CONSTRAINT `fk_sys_admin_event_actor` FOREIGN KEY (`actor_user_id`)
    REFERENCES `sys_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sys_admin_event`
    ADD CONSTRAINT `fk_sys_admin_event_target_user` FOREIGN KEY (`target_user_id`)
    REFERENCES `sys_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `sys_admin_event`
    ADD CONSTRAINT `fk_sys_admin_event_target_organization` FOREIGN KEY (`target_organization_id`)
    REFERENCES `sys_organization` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- Spring Session JDBC 3.5.7官方MySQL结构；仅补中文注释及明确字符集。
-- 来源：https://github.com/spring-projects/spring-session/blob/3.5.7/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-mysql.sql
CREATE TABLE `SPRING_SESSION` (
    `PRIMARY_ID` CHAR(36) NOT NULL COMMENT '框架内部会话行标识，非Cookie凭据',
    `SESSION_ID` CHAR(36) NOT NULL COMMENT '用于Cookie的会话标识，仅身份服务解析',
    `CREATION_TIME` BIGINT NOT NULL COMMENT '创建时刻，Unix毫秒',
    `LAST_ACCESS_TIME` BIGINT NOT NULL COMMENT '最近访问时刻，Unix毫秒',
    `MAX_INACTIVE_INTERVAL` INT NOT NULL COMMENT '最大空闲时长，秒',
    `EXPIRY_TIME` BIGINT NOT NULL COMMENT '失效时刻，Unix毫秒',
    `PRINCIPAL_NAME` VARCHAR(100) NULL COMMENT '框架记录的认证主体名称',
    CONSTRAINT `SPRING_SESSION_PK` PRIMARY KEY (`PRIMARY_ID`),
    UNIQUE KEY `SPRING_SESSION_IX1` (`SESSION_ID`),
    KEY `SPRING_SESSION_IX2` (`EXPIRY_TIME`),
    KEY `SPRING_SESSION_IX3` (`PRINCIPAL_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='Spring Session会话';

CREATE TABLE `SPRING_SESSION_ATTRIBUTES` (
    `SESSION_PRIMARY_ID` CHAR(36) NOT NULL COMMENT '所属框架会话行标识',
    `ATTRIBUTE_NAME` VARCHAR(200) NOT NULL COMMENT '框架会话属性名',
    `ATTRIBUTE_BYTES` BLOB NOT NULL COMMENT '框架序列化属性，不存明文密码',
    CONSTRAINT `SPRING_SESSION_ATTRIBUTES_PK` PRIMARY KEY (`SESSION_PRIMARY_ID`, `ATTRIBUTE_NAME`),
    CONSTRAINT `SPRING_SESSION_ATTRIBUTES_FK` FOREIGN KEY (`SESSION_PRIMARY_ID`)
        REFERENCES `SPRING_SESSION` (`PRIMARY_ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='Spring Session会话属性';
