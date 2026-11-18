package com.yangaobo.servicedesk.ticket.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.zip.CRC32;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 将尚无正式动作的工单库升级为统一回执；旧动作不能推断原提交版本或草稿采用历史。
 *
 * @author yangaobo
 */
public class V5__action_receipts extends BaseJavaMigration {

    /**
     * 需要在任何DDL之前核验的全部旧业务表。
     */
    private static final String[] BASELINE_TABLES =
            "sd_ticket sd_ticket_event sd_onsite_booking"
                    .split(" ");

    /**
     * 受版本管理的明确增量DDL资源。
     */
    private static final String RESOURCE = "/db/upgrade/V5__action_receipts.sql";

    /**
     * 对SQL内容建立校验和，拒绝迁移成功后静默改写版本。
     *
     * @return 资源内容的CRC32校验和
     */
    @Override
    public Integer getChecksum() {
        CRC32 checksum = new CRC32();
        checksum.update(readSql().getBytes(StandardCharsets.UTF_8));
        return (int) checksum.getValue();
    }

    /**
     * MySQL结构变更会隐式提交，不声明虚假的事务回滚能力。
     *
     * @return 固定为false
     */
    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    /**
     * 先验证库名与全部旧表，再依次执行受控SQL，不删除任何业务记录。
     *
     * @param context Flyway拥有的数据库连接
     * @throws SQLException 数据检查或DDL失败
     */
    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        String database = connection.getCatalog();
        if (database == null || !database.matches("desk_ticket_(test|ci|prod)")) {
            throw new SQLException("迁移目标库不属于当前服务");
        }
        for (String table : BASELINE_TABLES) {
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT 1 FROM `" + table + "` LIMIT 1")) {
                if (rows.next()) {
                    throw new SQLException("旧动作非空且缺少完整原结果快照，须先制定回执映射，未执行DDL：" + table);
                }
            }
        }
        for (String sql : readSql().split(";\\s*(?:\\r?\\n|$)")) {
            if (!sql.isBlank()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        }
    }

    /**
     * 读取随迁移版本打包的SQL，缺失资源时明确失败。
     *
     * @return UTF-8编码的完整DDL
     */
    private static String readSql() {
        try (var stream = V5__action_receipts.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("缺少数据库升级资源");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取数据库升级资源", exception);
        }
    }
}
