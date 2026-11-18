package com.yangaobo.servicedesk.ticket;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.ticket.model.form.ticket.TicketQueryForm;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 使用真实MyBatis-Plus解析XML，验证参数、动态授权和JSON结果合同，不连接数据库。
 *
 * @author yangaobo
 */
class MapperXmlTest {
    private Configuration configuration;
    private static final String PREFIX = "com.yangaobo.servicedesk.ticket.mapper.";

    /**
     * 按应用默认驼峰规则装配真实XML和实体元数据。
     */
    @BeforeEach
    void loadMappers() throws Exception {
        var bean = new MybatisSqlSessionFactoryBean();
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(config);
        bean.setDataSource(mock(DataSource.class));
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:/mapper/*.xml"));
        configuration = bean.getObject().getConfiguration();
    }

    /**
     * 每个真实接口的自定义方法必须绑定XML，实体通用映射继续由MyBatis-Plus维护。
     */
    @Test
    void everyCustomMethodHasBoundStatement() {
        assertEquals(14, configuration.getMapperRegistry().getMappers().size());
        for (Class<?> mapper : configuration.getMapperRegistry().getMappers()) {
            for (var method : mapper.getDeclaredMethods()) {
                assertTrue(configuration.hasStatement(mapper.getName() + "." + method.getName()),
                        mapper.getName() + "." + method.getName());
                for (var parameter : method.getParameters()) {
                    assertNotNull(parameter.getAnnotation(org.apache.ibatis.annotations.Param.class));
                }
            }
        }
        jsonMapping("ActionReceiptMapper.byAction", "resultSnapshot");
        jsonMapping("TicketMapper.lock", "supportContext");
        jsonMapping("TicketEventMapper.listReadable", "metadata");
        jsonMapping("ServiceEventMapper.listEvents", "details");
    }

    /**
     * 特殊字段的结果映射必须实际使用Jackson处理器。
     *
     * @param statement 查询语句标识
     * @param property  JSON属性
     */
    private void jsonMapping(String statement, String property) {
        var mappings = configuration.getMappedStatement(PREFIX + statement)
                .getResultMaps().getFirst().getResultMappings();
        var mapping = mappings.stream().filter(item -> property.equals(item.getProperty())).findFirst().orElseThrow();
        assertTrue(mapping.getTypeHandler() instanceof JacksonTypeHandler);
    }

    /**
     * 对比列表与计数的实际FROM/WHERE及绑定值，分页只出现在列表。
     *
     * @param list  列表语句
     * @param count 统计语句
     * @param input 命名参数
     */
    private void sameConditions(String list, String count, Map<String, Object> input) {
        BoundSql rows = configuration.getMappedStatement(PREFIX + list).getBoundSql(input);
        BoundSql total = configuration.getMappedStatement(PREFIX + count).getBoundSql(input);
        String rowSql = rows.getSql().replaceAll("\\s+", " ").strip();
        String countSql = total.getSql().replaceAll("\\s+", " ").strip();
        String fromWhere = rowSql.substring(rowSql.indexOf("FROM ")).split(" ORDER BY ")[0];
        assertEquals(countSql.substring(countSql.indexOf("FROM ")), fromWhere);
        assertFalse(countSql.contains("LIMIT"));
        List<Object> rowValues = values(rows, input);
        assertEquals(values(total, input), rowValues.subList(0, rowValues.size() - 2));
        assertEquals(input.get("limit"), rowValues.get(rowValues.size() - 2));
        assertEquals(input.get("offset"), rowValues.getLast());
    }

    /**
     * 解析foreach产生的附加参数及普通属性路径，核对真实预编译绑定值。
     *
     * @param sql   已生成SQL
     * @param input 命名参数
     * @return 按占位符顺序排列的值
     */
    private List<Object> values(BoundSql sql, Map<String, Object> input) {
        List<Object> result = new ArrayList<>();
        for (var mapping : sql.getParameterMappings()) {
            String property = mapping.getProperty();
            result.add(sql.hasAdditionalParameter(property) ? sql.getAdditionalParameter(property)
                    : configuration.newMetaObject(input).getValue(property));
        }
        return result;
    }


    /**
     * 客户本人、服务人员范围及空授权分别展开，所有筛选都同步作用于总数。
     */
    @Test
    void ticketAndNotificationScopesFailClosed() {
        for (boolean customer : List.of(false, true)) {
            for (Set<String> scope : List.of(Set.<String>of(), Set.of("customer-a"), Set.of("customer-a", "customer-b"))) {
                for (int mask = 0; mask < 8; mask++) {
                    var actor = new IdentityDTO();
                    actor.setOwnerId("owner-a");
                    actor.setOrganizationId("organization-a");
                    actor.setCustomerIds(scope);
                    var form = new TicketQueryForm();
                    form.setTriage((mask & 1) != 0);
                    form.setAssignedToMe((mask & 2) != 0);
                    form.setStatus((mask & 4) == 0 ? null : "SUBMITTED");
                    Map<String, Object> input = new HashMap<>();
                    input.put("actor", actor);
                    input.put("customer", customer);
                    input.put("form", form);
                    input.put("overdueAt", mask == 0 ? null : LocalDateTime.of(2026, 11, 14, 1, 0));
                    input.put("limit", 20);
                    input.put("offset", 40L);
                    sameConditions("TicketMapper.listTickets", "TicketMapper.countTickets", input);
                    sameConditions("NotificationQueryMapper.list", "NotificationQueryMapper.count", input);
                    if (!customer && scope.isEmpty()) {
                        assertTrue(configuration.getMappedStatement(PREFIX + "TicketMapper.listTickets")
                                .getBoundSql(input).getSql().contains("1 = 0"));
                        assertTrue(configuration.getMappedStatement(PREFIX + "NotificationQueryMapper.list")
                                .getBoundSql(input).getSql().contains("1 = 0"));
                    }
                }
            }
        }
    }

    /**
     * 空客户范围不会省略过滤，客户事件读取不会暴露内部备注。
     */
    @Test
    void resourceScopeAndEventVisibilityStayAligned() {
        for (Set<String> scope : List.of(Set.<String>of(), Set.of("customer-a"))) {
            Map<String, Object> input = new HashMap<>();
            input.put("customerIds", scope);
            input.put("status", null);
            input.put("limit", 1);
            input.put("offset", 0L);
            sameConditions("DeviceMapper.listDevices", "DeviceMapper.countDevices", input);
            sameConditions("ServiceEntitlementMapper.listEntitlements", "ServiceEntitlementMapper.countEntitlements", input);
            String sql = configuration.getMappedStatement(PREFIX + "DeviceMapper.listDevices").getBoundSql(input).getSql();
            assertEquals(scope.isEmpty(), sql.contains("1 = 0"));
            input.put("ticketId", "ticket-a");
            input.put("customer", true);
            sameConditions("TicketEventMapper.listReadable", "TicketEventMapper.countReadable", input);
            assertTrue(configuration.getMappedStatement(PREFIX + "TicketEventMapper.listReadable")
                    .getBoundSql(input).getSql().contains("visibility = 'PUBLIC'"));
        }
    }

}
