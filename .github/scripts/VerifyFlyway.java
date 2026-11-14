import org.flywaydb.core.Flyway;

/**
 * 在专用数据库验证或维护所属应用的原生迁移链，不手写迁移历史。
 *
 * @author yangaobo
 */
class VerifyFlyway {

    /**
     * 连接与凭据仅从环境读取；调用方负责隔离实例与备份。
     *
     * @param arguments 不接受命令行凭据
     */
    public static void main(String[] arguments) {
        String service = System.getenv("DESK_VERIFY_SERVICE");
        if (!java.util.Set.of("user", "ticket", "knowledge").contains(service)) {
            throw new IllegalArgumentException("未知迁移所有者");
        }
        var configuration = Flyway.configure()
                .dataSource(System.getenv("DESK_VERIFY_JDBC_URL"),
                        System.getenv("DESK_VERIFY_USERNAME"), System.getenv("DESK_VERIFY_PASSWORD"))
                .locations("classpath:db/migration", "classpath:com/yangaobo/servicedesk/" + service + "/migration")
                .cleanDisabled(true).baselineOnMigrate(false).validateMigrationNaming(true);
        String target = System.getenv("DESK_VERIFY_TARGET");
        if (target != null && !target.isBlank()) {
            configuration.target(target);
        }
        var flyway = configuration.load();
        if ("true".equals(System.getenv("DESK_VERIFY_REPAIR"))) {
            flyway.repair();
            return;
        }
        flyway.migrate();
        flyway.validate();
    }
}
