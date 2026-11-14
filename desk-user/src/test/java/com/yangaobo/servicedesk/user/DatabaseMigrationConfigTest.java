package com.yangaobo.servicedesk.user;

import com.yangaobo.servicedesk.user.config.ComponentConfigurationValidator;
import com.yangaobo.servicedesk.user.config.DatabaseMigrationConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 验证数据库归属错误在任何结构迁移副作用之前拒绝。
 *
 * @author yangaobo
 */
class DatabaseMigrationConfigTest {

    /** 数据库配置无效时不得调用Flyway创建历史或修改表结构。 */
    @Test
    void invalidDatabaseConfigurationPreventsMigration() {
        ComponentConfigurationValidator validator = mock(ComponentConfigurationValidator.class);
        Flyway flyway = mock(Flyway.class);
        doThrow(new IllegalStateException("数据库与环境不一致")).when(validator).afterPropertiesSet();

        var strategy = new DatabaseMigrationConfig().validatedDatabaseMigration(validator);

        assertThatThrownBy(() -> strategy.migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据库与环境不一致");
        verifyNoInteractions(flyway);
    }
}
