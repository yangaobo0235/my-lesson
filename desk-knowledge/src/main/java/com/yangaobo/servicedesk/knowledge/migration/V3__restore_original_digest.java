package com.yangaobo.servicedesk.knowledge.migration;

import java.sql.SQLException;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 补回V2遗漏的原件摘要列；已有原件必须先从可信备份核对，不填伪造摘要。
 *
 * @author yangaobo
 */
public class V3__restore_original_digest extends BaseJavaMigration {
    /**
     * 本迁移内容的固定校验版本。
     */
    @Override
    public Integer getChecksum() {
        return 2026111401;
    }

    /**
     * MySQL DDL不会按普通业务事务回滚。
     */
    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    /**
     * 空原件表可直接补齐合同；非空表在任何DDL之前拒绝。
     */
    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        if (connection.getCatalog() == null
                || !connection.getCatalog().matches("desk_knowledge_(test|ci|prod)"))
            throw new SQLException("迁移目标库不属于knowledge服务");
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT 1 FROM kb_document LIMIT 1")) {
            if (rows.next()) throw new SQLException("原件表非空，须先从可信原件或备份核对缺失摘要，未执行DDL");
        }
        try (var statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE kb_document ADD COLUMN file_sha256 VARCHAR(64) NOT NULL COMMENT '流式摘要，同摘要的资料可有不同授权' AFTER file_size_bytes, ADD CONSTRAINT ck_kb_document__file_sha256_format CHECK (REGEXP_LIKE(file_sha256,'^[0-9a-f]{64}$','c'))");
        }
    }
}
