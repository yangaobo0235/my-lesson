-- 历史初始结构快照：不是2026-11-16协同排障目标DDL。
-- 目标模型见 docs/设计/数据库设计.md；执行及迁移限制见同目录README.md。
-- Service Desk 测试环境初始化 / MySQL 8.4 LTS
-- 在IDEA中使用MySQL管理员连接，执行整个文件；连接字符集为utf8mb4。
-- 目标为四个服务独立库，每环境24张业务表及2张Spring Session技术表。
-- 仅用于首次初始化空业务库；已有同名表会报错，不忽略结构冲突、不自动覆盖。
-- 不含DROP、TRUNCATE、默认管理员、示例业务数据、凭据或跨服务外键。
-- DDL逐条隐式提交；遇错停止并核对已生效内容，不要直接重复执行整份脚本。
-- 不创建Flyway/Alembic历史，也不启用应用迁移；接入迁移前显式核对已安装结构。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_bin;
SET SESSION time_zone = '+00:00';
SET SESSION transaction_isolation = 'READ-COMMITTED';
SET SESSION sql_mode = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';


-- ==================== desk_user_test ====================
CREATE DATABASE IF NOT EXISTS `desk_user_test` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
USE `desk_user_test`;

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


-- ==================== desk_ticket_test ====================
CREATE DATABASE IF NOT EXISTS `desk_ticket_test` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
USE `desk_ticket_test`;

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


-- ==================== desk_knowledge_test ====================
CREATE DATABASE IF NOT EXISTS `desk_knowledge_test` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
USE `desk_knowledge_test`;

-- 资料系列
CREATE TABLE `kb_document_series` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键，也是发布并发锁定对象',
    `source_type` VARCHAR(32) NOT NULL COMMENT 'OFFICIAL_MANUAL / TROUBLESHOOTING_GUIDE / SANITIZED_CASE',
    `source_namespace` VARCHAR(64) NOT NULL COMMENT '规范化发布方或内部目录代码',
    `source_code` VARCHAR(120) NOT NULL COMMENT '发布方范围内稳定文档号，缺少官方编号时由管理员登记受控目录码',
    `source_organization` VARCHAR(200) NOT NULL COMMENT '原发布方展示名称',
    `language_code` VARCHAR(16) NOT NULL COMMENT '规范化语言标识，区分不同语言版',
    `audience` VARCHAR(32) NOT NULL COMMENT 'CUSTOMER / SERVICE_STAFF',
    `created_by` VARCHAR(32) NOT NULL COMMENT '外部知识管理员 ID',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '系列登记时间',
    CONSTRAINT `pk_kb_document_series` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_document_series_identity` (`source_namespace`, `source_code`, `language_code`, `audience`),
    CONSTRAINT `ck_kb_document_series__source_type` CHECK (`source_type` IN ('OFFICIAL_MANUAL', 'TROUBLESHOOTING_GUIDE', 'SANITIZED_CASE')),
    CONSTRAINT `ck_kb_document_series__audience` CHECK (`audience` IN ('CUSTOMER', 'SERVICE_STAFF')),
    CONSTRAINT `ck_kb_document_series__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document_series__source_type_nonblank` CHECK (REGEXP_LIKE(`source_type`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document_series__source_namespace_nonblank` CHECK (REGEXP_LIKE(`source_namespace`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document_series__source_code_nonblank` CHECK (REGEXP_LIKE(`source_code`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document_series__source_organization_nonblank` CHECK (REGEXP_LIKE(`source_organization`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document_series__language_code_nonblank` CHECK (REGEXP_LIKE(`language_code`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document_series__audience_nonblank` CHECK (REGEXP_LIKE(`audience`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document_series__created_by_format` CHECK (REGEXP_LIKE(`created_by`, '^[0-9a-f]{32}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='资料系列';

-- 资料修订
CREATE TABLE `kb_document` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键，由所属后端生成',
    `series_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → kb_document_series',
    `client_upload_id` VARCHAR(32) NOT NULL COMMENT '上传者范围的客户端上传请求键，重试复用',
    `upload_request_hash` VARCHAR(64) NOT NULL COMMENT '系列、规范化元数据及文件 SHA-256 的语义摘要',
    `revision_number` INT NOT NULL COMMENT '系列内递增序号，锁系列后分配',
    `title` VARCHAR(255) NOT NULL COMMENT '真实文档标题',
    `original_file_name` VARCHAR(255) NOT NULL COMMENT '原文件名，仅展示',
    `storage_bucket` VARCHAR(63) NOT NULL COMMENT '所属环境允许的 OSS 私有 Bucket，上传时固定',
    `storage_key` VARCHAR(255) NOT NULL COMMENT '服务端生成的 OSS Object Key，全局唯一且不复用，客户端不能指定',
    `storage_version_id` VARCHAR(1024) NOT NULL COMMENT '开启版本控制后返回的 OSS Version ID，精确读取及恢复使用，不接受空值或字面量 null',
    `file_size_bytes` BIGINT NOT NULL COMMENT '正数，初始最大20MiB',
    `file_sha256` VARCHAR(64) NOT NULL COMMENT '流式摘要，同摘要的资料可有不同授权',
    `source_url` TEXT NULL COMMENT '原始来源说明，不作为自动抓取地址',
    `source_review_note` VARCHAR(1000) NULL COMMENT '复核出处与使用依据，脱敏案例记录审批依据，发布必填',
    `revision_label` VARCHAR(100) NULL COMMENT '原文已知修订标识',
    `issued_on` DATE NULL COMMENT '原文发布日期',
    `applicable_systems` VARCHAR(500) NULL COMMENT '原文已知系统版本条件',
    `publication_status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / DISABLED / RETIRED / DELETED',
    `current_series_id` VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN publication_status IN ('PUBLISHED', 'DISABLED') THEN series_id ELSE NULL END) STORED COMMENT 'PUBLISHED/DISABLED 时为 series_id，否则 NULL；唯一',
    `current_job_id` VARCHAR(32) NULL COMMENT '本地复合 FK(current_job_id,id) → kb_index_job(id,document_id)，外部 AI 任务使用同一 ID',
    `replaces_document_id` VARCHAR(32) NULL COMMENT '本地复合 FK(replaces_document_id,series_id) → kb_document(id,series_id)，发布替换时写入',
    `published_at` DATETIME(6) NULL COMMENT '首次成功发布时刻',
    `published_by` VARCHAR(32) NULL COMMENT '外部知识管理员账号',
    `deleted_at` DATETIME(6) NULL COMMENT 'publication_status=DELETED 时必填',
    `created_by` VARCHAR(32) NOT NULL COMMENT '外部上传人账号',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时显式维护，默认值不自动随 UPDATE 改变',
    `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT '非负，更新比较旧值后加一',
    CONSTRAINT `pk_kb_document` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_document_storage_key` (`storage_key`),
    UNIQUE KEY `uk_kb_document_upload` (`created_by`, `client_upload_id`),
    UNIQUE KEY `uk_kb_document_revision` (`series_id`, `revision_number`),
    UNIQUE KEY `uk_kb_document_id_series` (`id`, `series_id`),
    UNIQUE KEY `uk_kb_document_current` (`current_series_id`),
    UNIQUE KEY `uk_kb_document_replaces` (`replaces_document_id`),
    KEY `idx_kb_document_current_job` (`current_job_id`, `id`),
    KEY `idx_kb_document_replaces_series` (`replaces_document_id`, `series_id`),
    CONSTRAINT `ck_kb_document__publication_status` CHECK (`publication_status` IN ('DRAFT', 'PUBLISHED', 'DISABLED', 'RETIRED', 'DELETED')),
    CONSTRAINT `ck_kb_document__file_size` CHECK (file_size_bytes > 0 AND file_size_bytes <= 20971520),
    CONSTRAINT `ck_kb_document__storage_version` CHECK (storage_version_id <> 'null'),
    CONSTRAINT `ck_kb_document__not_self_replace` CHECK (replaces_document_id IS NULL OR replaces_document_id <> id),
    CONSTRAINT `ck_kb_document__published_pair` CHECK ((published_at IS NULL AND published_by IS NULL) OR (published_at IS NOT NULL AND published_by IS NOT NULL)),
    CONSTRAINT `ck_kb_document__published_state` CHECK ((publication_status IN ('PUBLISHED', 'DISABLED', 'RETIRED') AND published_at IS NOT NULL AND published_by IS NOT NULL AND current_job_id IS NOT NULL AND source_review_note IS NOT NULL AND REGEXP_LIKE(source_review_note, '[^[:space:]]')) OR (publication_status IN ('DRAFT', 'DELETED') AND published_at IS NULL AND published_by IS NULL)),
    CONSTRAINT `ck_kb_document__deleted_state` CHECK ((publication_status = 'DELETED' AND deleted_at IS NOT NULL) OR (publication_status <> 'DELETED' AND deleted_at IS NULL)),
    CONSTRAINT `ck_kb_document__time_order` CHECK ((published_at IS NULL OR published_at >= created_at) AND (deleted_at IS NULL OR deleted_at >= created_at)),
    CONSTRAINT `ck_kb_document__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document__series_id_format` CHECK (REGEXP_LIKE(`series_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document__client_upload_id_format` CHECK (REGEXP_LIKE(`client_upload_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document__upload_request_hash_format` CHECK (REGEXP_LIKE(`upload_request_hash`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `ck_kb_document__revision_number_positive` CHECK (`revision_number` > 0),
    CONSTRAINT `ck_kb_document__title_nonblank` CHECK (REGEXP_LIKE(`title`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document__original_file_name_nonblank` CHECK (REGEXP_LIKE(`original_file_name`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document__storage_bucket_nonblank` CHECK (REGEXP_LIKE(`storage_bucket`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document__storage_key_nonblank` CHECK (REGEXP_LIKE(`storage_key`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document__storage_version_id_nonblank` CHECK (REGEXP_LIKE(`storage_version_id`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document__file_sha256_format` CHECK (REGEXP_LIKE(`file_sha256`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `ck_kb_document__publication_status_nonblank` CHECK (REGEXP_LIKE(`publication_status`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document__current_job_id_format` CHECK (`current_job_id` IS NULL OR (REGEXP_LIKE(`current_job_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_kb_document__replaces_document_id_format` CHECK (`replaces_document_id` IS NULL OR (REGEXP_LIKE(`replaces_document_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_kb_document__published_by_format` CHECK (`published_by` IS NULL OR (REGEXP_LIKE(`published_by`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_kb_document__created_by_format` CHECK (REGEXP_LIKE(`created_by`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document__row_version_range` CHECK (`row_version` >= 0),
    CONSTRAINT `ck_kb_document__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='资料修订';

-- 适用型号关联
CREATE TABLE `kb_document_product_model` (
    `document_id` VARCHAR(32) NOT NULL COMMENT '联合 PK，本地 FK → kb_document',
    `product_model_id` VARCHAR(32) NOT NULL COMMENT '联合 PK，外部逻辑引用 → ticket.it_product_model',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '记录创建时间',
    CONSTRAINT `pk_kb_document_product_model` PRIMARY KEY (`document_id`, `product_model_id`),
    KEY `idx_kb_document_product_model_model_document` (`product_model_id`, `document_id`),
    CONSTRAINT `ck_kb_document_product_model__document_id_format` CHECK (REGEXP_LIKE(`document_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document_product_model__product_model_id_format` CHECK (REGEXP_LIKE(`product_model_id`, '^[0-9a-f]{32}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='适用型号关联';

-- 资料变更历史
CREATE TABLE `kb_document_event` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键',
    `document_id` VARCHAR(32) NOT NULL COMMENT '本地 FK → kb_document',
    `actor_id` VARCHAR(32) NOT NULL COMMENT '外部知识管理员 ID',
    `event_type` VARCHAR(32) NOT NULL COMMENT 'UPLOADED / METADATA_CHANGED / INDEX_REQUESTED / INDEX_CANCEL_REQUESTED / INDEX_REQUEUED / PUBLISHED / DISABLED / RESTORED / RETIRED / DELETED',
    `related_document_id` VARCHAR(32) NULL COMMENT '本地 FK → kb_document，替换对应的另一修订',
    `details` JSON NOT NULL COMMENT 'schema_version、动作原因、状态或型号差异；索引动作记录job_id，取消/重放另记command及operation_id',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '动作提交时间',
    CONSTRAINT `pk_kb_document_event` PRIMARY KEY (`id`),
    KEY `idx_kb_document_event_document_created` (`document_id`, `created_at`, `id`),
    KEY `idx_kb_document_event_related_document` (`related_document_id`),
    CONSTRAINT `ck_kb_document_event__event_type` CHECK (`event_type` IN ('UPLOADED', 'METADATA_CHANGED', 'INDEX_REQUESTED', 'INDEX_CANCEL_REQUESTED', 'INDEX_REQUEUED', 'PUBLISHED', 'DISABLED', 'RESTORED', 'RETIRED', 'DELETED')),
    CONSTRAINT `ck_kb_document_event__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document_event__document_id_format` CHECK (REGEXP_LIKE(`document_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document_event__actor_id_format` CHECK (REGEXP_LIKE(`actor_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_document_event__event_type_nonblank` CHECK (REGEXP_LIKE(`event_type`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_document_event__related_document_id_format` CHECK (`related_document_id` IS NULL OR (REGEXP_LIKE(`related_document_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_kb_document_event__details_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `details`) = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='资料变更历史';

-- 索引尝试与事务事实
CREATE TABLE `kb_index_job` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键，knowledge生成，线合同为jobId，AI沿用同一任务ID',
    `document_id` VARCHAR(32) NOT NULL COMMENT '本地FK→kb_document',
    `attempt_number` INT NOT NULL COMMENT '文档内递增正序尝试号，准备失败也保留历史',
    `requested_by` VARCHAR(32) NOT NULL COMMENT '初始发起知识管理员',
    `client_request_id` VARCHAR(32) NOT NULL COMMENT '发起人的索引提交幂等键，同键沿用原jobId',
    `request_payload` JSON NOT NULL COMMENT 'schema_version、client_request原add输入及command_input不可变信封输入，不含传输事务ID/凭据',
    `request_hash` VARCHAR(64) NOT NULL COMMENT '与MQ/RPC核对的不可变输入摘要',
    `submit_state` VARCHAR(16) NOT NULL DEFAULT 'PREPARING' COMMENT 'PREPARING / COMMITTED / ABORTED，后两者终态',
    `submit_deadline_at` DATETIME(6) NOT NULL COMMENT '本地提交准备截止，首次登记后固定，初值30秒',
    `submit_committed_at` DATETIME(6) NULL COMMENT '当且仅当submit_state=COMMITTED必填，固定本地提交事实',
    `cancel_operation_id` VARCHAR(32) NULL COMMENT '当前取消操作ID；取消重试仅在前次ABORTED后分配新值',
    `cancel_requested_by` VARCHAR(32) NULL COMMENT '当前取消准备的知识管理员，历史由资料事件保留',
    `cancel_state` VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE / PREPARING / COMMITTED / ABORTED',
    `cancel_deadline_at` DATETIME(6) NULL COMMENT '非NONE时必填，当前取消准备截止',
    `cancel_committed_at` DATETIME(6) NULL COMMENT '当且仅当cancel_state=COMMITTED必填，提交后固定',
    `trace_context` JSON NULL COMMENT '初始提交的schema_version及W3C上下文，重放不参与摘要',
    `last_error_code` VARCHAR(64) NULL COMMENT '当前准备/失败的安全诊断，不作为事务提交证明',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '首次登记准备事实',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '业务状态更新时显式维护',
    CONSTRAINT `pk_kb_index_job` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_index_job_attempt` (`document_id`, `attempt_number`),
    UNIQUE KEY `uk_kb_index_job_id_document` (`id`, `document_id`),
    UNIQUE KEY `uk_kb_index_job_request` (`requested_by`, `client_request_id`),
    KEY `idx_kb_index_job_submit_deadline` (`submit_state`, `submit_deadline_at`, `id`),
    KEY `idx_kb_index_job_cancel_deadline` (`cancel_state`, `cancel_deadline_at`, `id`),
    CONSTRAINT `ck_kb_index_job__submit_state` CHECK (`submit_state` IN ('PREPARING', 'COMMITTED', 'ABORTED')),
    CONSTRAINT `ck_kb_index_job__cancel_state` CHECK (`cancel_state` IN ('NONE', 'PREPARING', 'COMMITTED', 'ABORTED')),
    CONSTRAINT `ck_kb_index_job__submit_fields` CHECK (submit_deadline_at > created_at AND ((submit_state = 'COMMITTED' AND submit_committed_at IS NOT NULL AND submit_committed_at >= created_at) OR (submit_state <> 'COMMITTED' AND submit_committed_at IS NULL))),
    CONSTRAINT `ck_kb_index_job__cancel_fields` CHECK ((cancel_state = 'NONE' AND cancel_operation_id IS NULL AND cancel_requested_by IS NULL AND cancel_deadline_at IS NULL AND cancel_committed_at IS NULL) OR (cancel_state <> 'NONE' AND submit_state = 'COMMITTED' AND cancel_operation_id IS NOT NULL AND cancel_requested_by IS NOT NULL AND cancel_deadline_at IS NOT NULL AND cancel_deadline_at > created_at AND ((cancel_state = 'COMMITTED' AND cancel_committed_at IS NOT NULL AND cancel_committed_at >= submit_committed_at) OR (cancel_state <> 'COMMITTED' AND cancel_committed_at IS NULL)))),
    CONSTRAINT `ck_kb_index_job__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_index_job__document_id_format` CHECK (REGEXP_LIKE(`document_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_index_job__attempt_number_positive` CHECK (`attempt_number` > 0),
    CONSTRAINT `ck_kb_index_job__requested_by_format` CHECK (REGEXP_LIKE(`requested_by`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_index_job__client_request_id_format` CHECK (REGEXP_LIKE(`client_request_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_kb_index_job__request_payload_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `request_payload`) = 1),
    CONSTRAINT `ck_kb_index_job__request_hash_format` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `ck_kb_index_job__submit_state_nonblank` CHECK (REGEXP_LIKE(`submit_state`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_index_job__cancel_operation_id_format` CHECK (`cancel_operation_id` IS NULL OR (REGEXP_LIKE(`cancel_operation_id`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_kb_index_job__cancel_requested_by_format` CHECK (`cancel_requested_by` IS NULL OR (REGEXP_LIKE(`cancel_requested_by`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_kb_index_job__cancel_state_nonblank` CHECK (REGEXP_LIKE(`cancel_state`, '[^[:space:]]')),
    CONSTRAINT `ck_kb_index_job__trace_context_schema` CHECK (`trace_context` IS NULL OR (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version","traceparent"],"properties":{"schema_version":{"type":"integer","enum":[1]},"traceparent":{"type":"string","pattern":"^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"},"tracestate":{"type":"string","maxLength":512}},"additionalProperties":false}', `trace_context`) = 1 AND OCTET_LENGTH(`trace_context`) <= 2048 AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 1, 2) <> 'ff' AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 4, 32) <> REPEAT('0', 32) AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 37, 16) <> REPEAT('0', 16))),
    CONSTRAINT `ck_kb_index_job__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='索引尝试与事务事实';

-- 全部表建立后补齐本服务外键，包含资料与索引任务的循环引用；不关闭外键检查。

ALTER TABLE `kb_document`
    ADD CONSTRAINT `fk_kb_document_series` FOREIGN KEY (`series_id`)
    REFERENCES `kb_document_series` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_document`
    ADD CONSTRAINT `fk_kb_document_current_job` FOREIGN KEY (`current_job_id`, `id`)
    REFERENCES `kb_index_job` (`id`, `document_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_document`
    ADD CONSTRAINT `fk_kb_document_replaces_series` FOREIGN KEY (`replaces_document_id`, `series_id`)
    REFERENCES `kb_document` (`id`, `series_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_document_product_model`
    ADD CONSTRAINT `fk_kb_document_product_model_document` FOREIGN KEY (`document_id`)
    REFERENCES `kb_document` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_document_event`
    ADD CONSTRAINT `fk_kb_document_event_document` FOREIGN KEY (`document_id`)
    REFERENCES `kb_document` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_document_event`
    ADD CONSTRAINT `fk_kb_document_event_related_document` FOREIGN KEY (`related_document_id`)
    REFERENCES `kb_document` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `kb_index_job`
    ADD CONSTRAINT `fk_kb_index_job_document` FOREIGN KEY (`document_id`)
    REFERENCES `kb_document` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;


-- ==================== desk_ai_test ====================
CREATE DATABASE IF NOT EXISTS `desk_ai_test` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
USE `desk_ai_test`;

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
    CONSTRAINT `ck_ai_index_job__failure_fields` CHECK (status <> 'FAILED' OR (error_code IS NOT NULL AND REGEXP_LIKE(error_code, '[^[:space:]]'))),
    CONSTRAINT `ck_ai_index_job__execution_time` CHECK ((started_at IS NULL OR started_at >= created_at) AND (finished_at IS NULL OR (finished_at >= created_at AND (started_at IS NULL OR finished_at >= started_at))) AND (claim_until IS NULL OR (started_at IS NOT NULL AND claim_until > started_at))),
    CONSTRAINT `ck_ai_index_job__queued_fields` CHECK (status <> 'QUEUED' OR started_at IS NULL),
    CONSTRAINT `ck_ai_index_job__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_ai_index_job__document_id_format` CHECK (REGEXP_LIKE(`document_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_ai_index_job__attempt_number_positive` CHECK (`attempt_number` > 0),
    CONSTRAINT `ck_ai_index_job__file_sha256_format` CHECK (REGEXP_LIKE(`file_sha256`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `ck_ai_index_job__request_hash_format` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `ck_ai_index_job__requested_by_format` CHECK (REGEXP_LIKE(`requested_by`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_ai_index_job__last_command_nonblank` CHECK (REGEXP_LIKE(`last_command`, '[^[:space:]]')),
    CONSTRAINT `ck_ai_index_job__trace_context_schema` CHECK (`trace_context` IS NULL OR (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version","traceparent"],"properties":{"schema_version":{"type":"integer","enum":[1]},"traceparent":{"type":"string","pattern":"^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"},"tracestate":{"type":"string","maxLength":512}},"additionalProperties":false}', `trace_context`) = 1 AND OCTET_LENGTH(`trace_context`) <= 2048 AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 1, 2) <> 'ff' AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 4, 32) <> REPEAT('0', 32) AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 37, 16) <> REPEAT('0', 16))),
    CONSTRAINT `ck_ai_index_job__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[:space:]]')),
    CONSTRAINT `ck_ai_index_job__parser_state_nonblank` CHECK (REGEXP_LIKE(`parser_state`, '[^[:space:]]')),
    CONSTRAINT `ck_ai_index_job__parser_reconciliation_schema` CHECK (`parser_reconciliation` IS NULL OR (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `parser_reconciliation`) = 1)),
    CONSTRAINT `ck_ai_index_job__processing_config_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `processing_config`) = 1),
    CONSTRAINT `ck_ai_index_job__page_count_positive` CHECK (`page_count` IS NULL OR (`page_count` > 0)),
    CONSTRAINT `ck_ai_index_job__claim_token_format` CHECK (`claim_token` IS NULL OR (REGEXP_LIKE(`claim_token`, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'))),
    CONSTRAINT `ck_ai_index_job__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='文档处理任务';

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
    CONSTRAINT `ck_ai_chunk__content_nonblank` CHECK (REGEXP_LIKE(`content`, '[^[:space:]]')),
    CONSTRAINT `ck_ai_chunk__source_locations_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version","pages"],"properties":{"schema_version":{"type":"integer","minimum":1},"pages":{"type":"array","minItems":1,"items":{"type":"object","required":["page_number"],"properties":{"page_number":{"type":"integer","minimum":1},"printed_page_label":{"type":["string","null"]}}}}}}', `source_locations`) = 1),
    CONSTRAINT `ck_ai_chunk__error_codes_schema` CHECK (JSON_TYPE(`error_codes`) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='知识片段与向量副本定位';

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
    CONSTRAINT `ck_ai_conversation__title_nonblank` CHECK (REGEXP_LIKE(`title`, '[^[:space:]]')),
    CONSTRAINT `ck_ai_conversation__row_version_range` CHECK (`row_version` >= 0),
    CONSTRAINT `ck_ai_conversation__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='本人设备排障会话';

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
    CONSTRAINT `ck_ai_generation__answer_fields` CHECK ((status = 'SUCCEEDED' AND answer_type IS NOT NULL AND answer_text IS NOT NULL AND REGEXP_LIKE(answer_text, '[^[:space:]]')) OR (status <> 'SUCCEEDED' AND answer_type IS NULL AND answer_text IS NULL)),
    CONSTRAINT `ck_ai_generation__claim_fields` CHECK ((status = 'RUNNING' AND started_at IS NOT NULL AND claim_token IS NOT NULL AND claim_until IS NOT NULL) OR (status <> 'RUNNING' AND claim_token IS NULL AND claim_until IS NULL)),
    CONSTRAINT `ck_ai_generation__finished_fields` CHECK ((status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED') AND finished_at IS NOT NULL) OR (status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED') AND finished_at IS NULL)),
    CONSTRAINT `ck_ai_generation__execution_time` CHECK ((started_at IS NULL OR started_at >= created_at) AND (finished_at IS NULL OR (finished_at >= created_at AND (started_at IS NULL OR finished_at >= started_at))) AND (claim_until IS NULL OR (started_at IS NOT NULL AND claim_until > started_at))),
    CONSTRAINT `ck_ai_generation__queued_fields` CHECK (status <> 'QUEUED' OR started_at IS NULL),
    CONSTRAINT `ck_ai_generation__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_ai_generation__conversation_id_format` CHECK (REGEXP_LIKE(`conversation_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_ai_generation__owner_id_format` CHECK (REGEXP_LIKE(`owner_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_ai_generation__round_number_positive` CHECK (`round_number` > 0),
    CONSTRAINT `ck_ai_generation__question_text_nonblank` CHECK (REGEXP_LIKE(`question_text`, '[^[:space:]]')),
    CONSTRAINT `ck_ai_generation__client_request_id_format` CHECK (REGEXP_LIKE(`client_request_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_ai_generation__request_hash_format` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `ck_ai_generation__session_ref_format` CHECK (REGEXP_LIKE(`session_ref`, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT `ck_ai_generation__auth_version_range` CHECK (`auth_version` >= 0),
    CONSTRAINT `ck_ai_generation__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[:space:]]')),
    CONSTRAINT `ck_ai_generation__pipeline_config_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `pipeline_config`) = 1),
    CONSTRAINT `ck_ai_generation__trace_context_schema` CHECK (`trace_context` IS NULL OR (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version","traceparent"],"properties":{"schema_version":{"type":"integer","enum":[1]},"traceparent":{"type":"string","pattern":"^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"},"tracestate":{"type":"string","maxLength":512}},"additionalProperties":false}', `trace_context`) = 1 AND OCTET_LENGTH(`trace_context`) <= 2048 AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 1, 2) <> 'ff' AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 4, 32) <> REPEAT('0', 32) AND SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(`trace_context`, '$.traceparent')), 37, 16) <> REPEAT('0', 16))),
    CONSTRAINT `ck_ai_generation__context_snapshot_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `context_snapshot`) = 1),
    CONSTRAINT `ck_ai_generation__claim_token_format` CHECK (`claim_token` IS NULL OR (REGEXP_LIKE(`claim_token`, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'))),
    CONSTRAINT `ck_ai_generation__latency_ms_range` CHECK (`latency_ms` IS NULL OR (`latency_ms` >= 0)),
    CONSTRAINT `ck_ai_generation__input_tokens_range` CHECK (`input_tokens` IS NULL OR (`input_tokens` >= 0)),
    CONSTRAINT `ck_ai_generation__output_tokens_range` CHECK (`output_tokens` IS NULL OR (`output_tokens` >= 0)),
    CONSTRAINT `ck_ai_generation__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='单轮排障运行';

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
    CONSTRAINT `ck_ai_citation__title_snapshot_nonblank` CHECK (REGEXP_LIKE(`title_snapshot`, '[^[:space:]]')),
    CONSTRAINT `ck_ai_citation__source_locations_snapshot_schema` CHECK (JSON_SCHEMA_VALID('{"type":"array","minItems":1,"items":{"type":"object","required":["page_number"],"properties":{"page_number":{"type":"integer","minimum":1},"printed_page_label":{"type":["string","null"]}}}}', `source_locations_snapshot`) = 1),
    CONSTRAINT `ck_ai_citation__evidence_excerpt_nonblank` CHECK (REGEXP_LIKE(`evidence_excerpt`, '[^[:space:]]'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='不可变回答引用';

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
    CONSTRAINT `ck_ai_ticket_draft__content_schema` CHECK (JSON_SCHEMA_VALID('{"type":"object","required":["schema_version"],"properties":{"schema_version":{"type":"integer","minimum":1}}}', `content`) = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='报修候选草稿';

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
    CONSTRAINT `ck_ai_feedback__handled_fields` CHECK ((status IN ('RESOLVED', 'DISMISSED') AND handled_by IS NOT NULL AND handling_note IS NOT NULL AND REGEXP_LIKE(handling_note, '[^[:space:]]') AND handled_at IS NOT NULL AND handled_at >= created_at) OR (status IN ('NOT_REQUIRED', 'PENDING') AND handled_by IS NULL AND handling_note IS NULL AND handled_at IS NULL)),
    CONSTRAINT `ck_ai_feedback__id_format` CHECK (REGEXP_LIKE(`id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_ai_feedback__generation_id_format` CHECK (REGEXP_LIKE(`generation_id`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_ai_feedback__submitted_by_format` CHECK (REGEXP_LIKE(`submitted_by`, '^[0-9a-f]{32}$', 'c')),
    CONSTRAINT `ck_ai_feedback__rating_nonblank` CHECK (REGEXP_LIKE(`rating`, '[^[:space:]]')),
    CONSTRAINT `ck_ai_feedback__comment_length` CHECK (`comment` IS NULL OR (CHAR_LENGTH(`comment`) <= 1000)),
    CONSTRAINT `ck_ai_feedback__share_with_knowledge_admin_boolean` CHECK (`share_with_knowledge_admin` IN (0,1)),
    CONSTRAINT `ck_ai_feedback__status_nonblank` CHECK (REGEXP_LIKE(`status`, '[^[:space:]]')),
    CONSTRAINT `ck_ai_feedback__handled_by_format` CHECK (`handled_by` IS NULL OR (REGEXP_LIKE(`handled_by`, '^[0-9a-f]{32}$', 'c'))),
    CONSTRAINT `ck_ai_feedback__handling_note_length` CHECK (`handling_note` IS NULL OR (CHAR_LENGTH(`handling_note`) <= 2000)),
    CONSTRAINT `ck_ai_feedback__row_version_range` CHECK (`row_version` >= 0),
    CONSTRAINT `ck_ai_feedback__update_time` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin ROW_FORMAT=DYNAMIC COMMENT='本人反馈与知识处理';

-- 全部表建立后补齐本服务外键，包含资料与索引任务的循环引用；不关闭外键检查。

ALTER TABLE `ai_chunk`
    ADD CONSTRAINT `fk_ai_chunk_job_document` FOREIGN KEY (`index_job_id`, `document_id`)
    REFERENCES `ai_index_job` (`id`, `document_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `ai_generation`
    ADD CONSTRAINT `fk_ai_generation_conversation_owner` FOREIGN KEY (`conversation_id`, `owner_id`)
    REFERENCES `ai_conversation` (`id`, `owner_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `ai_citation`
    ADD CONSTRAINT `fk_ai_citation_generation` FOREIGN KEY (`generation_id`)
    REFERENCES `ai_generation` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `ai_citation`
    ADD CONSTRAINT `fk_ai_citation_chunk` FOREIGN KEY (`chunk_id`)
    REFERENCES `ai_chunk` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `ai_ticket_draft`
    ADD CONSTRAINT `fk_ai_ticket_draft_conversation_owner` FOREIGN KEY (`conversation_id`, `owner_id`)
    REFERENCES `ai_conversation` (`id`, `owner_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `ai_ticket_draft`
    ADD CONSTRAINT `fk_ai_ticket_draft_generation_conversation` FOREIGN KEY (`source_generation_id`, `conversation_id`)
    REFERENCES `ai_generation` (`id`, `conversation_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `ai_feedback`
    ADD CONSTRAINT `fk_ai_feedback_generation_owner` FOREIGN KEY (`generation_id`, `submitted_by`)
    REFERENCES `ai_generation` (`id`, `owner_id`) ON DELETE NO ACTION ON UPDATE NO ACTION;


-- 预期结果：user=7、ticket=7、knowledge=5、AI=7，总计26张。
SELECT TABLE_SCHEMA AS database_name, COUNT(*) AS table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ('desk_user_test', 'desk_ticket_test', 'desk_knowledge_test', 'desk_ai_test') AND TABLE_TYPE = 'BASE TABLE'
GROUP BY TABLE_SCHEMA ORDER BY TABLE_SCHEMA;
