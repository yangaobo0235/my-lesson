package com.yangaobo.servicedesk.knowledge;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String PREFIX = "com.yangaobo.servicedesk.knowledge.mapper.";

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
        assertEquals(5, configuration.getMapperRegistry().getMappers().size());
        for (Class<?> mapper : configuration.getMapperRegistry().getMappers()) {
            for (var method : mapper.getDeclaredMethods()) {
                assertTrue(configuration.hasStatement(mapper.getName() + "." + method.getName()),
                        mapper.getName() + "." + method.getName());
                for (var parameter : method.getParameters()) {
                    assertNotNull(parameter.getAnnotation(org.apache.ibatis.annotations.Param.class));
                }
            }
        }
        jsonMapping("IndexJobMapper.lock", "requestPayload");
        jsonMapping("IndexJobMapper.scanPreparingDue", "traceContext");
        jsonMapping("DocumentEventMapper.listEvents", "details");
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
     * 授权修订集合为空时返回空范围，资料目录和事件列表与统计使用相同过滤。
     */
    @Test
    void authorizedSourcesAndManagementFiltersStayAligned() {
        for (List<String> ids : List.of(List.<String>of(), List.of("document-a"), List.of("document-a", "document-b"))) {
            Map<String, Object> input = new HashMap<>();
            input.put("ids", ids);
            input.put("seriesId", ids.isEmpty() ? null : "series-a");
            input.put("documentId", "document-a");
            input.put("limit", 20);
            input.put("offset", 40L);
            sameConditions("DocumentMapper.listAuthorized", "DocumentMapper.countAuthorized", input);
            sameConditions("DocumentMapper.listDocuments", "DocumentMapper.countDocuments", input);
            sameConditions("DocumentEventMapper.listEvents", "DocumentEventMapper.countEvents", input);
            sameConditions("DocumentSeriesMapper.listSeries", "DocumentSeriesMapper.countSeries", input);
            assertEquals(ids.isEmpty(), configuration.getMappedStatement(PREFIX + "DocumentMapper.listAuthorized")
                    .getBoundSql(input).getSql().contains("1 = 0"));
        }
    }

    /**
     * 超时扫描保持两个准备分支和绑定预算，适用规则扫描不拼接SQL。
     */
    @Test
    void boundedScansBindTimeAndLimits() {
        Map<String, Object> input = new HashMap<>();
        var now = LocalDateTime.of(2026, 11, 14, 1, 0);
        input.put("now", now);
        input.put("limit", 100);
        var sql = configuration.getMappedStatement(PREFIX + "IndexJobMapper.scanPreparingDue").getBoundSql(input);
        assertEquals(List.of(now, now, 100), values(sql, input));
        assertTrue(sql.getSql().contains("submit_state = 'PREPARING'"));
        assertTrue(sql.getSql().contains("cancel_state = 'PREPARING'"));
        input.put("serviceItemId", "service-a");
        input.put("limit", 25001);
        var rules = configuration.getMappedStatement(PREFIX + "DocumentRuleMapper.scanForService").getBoundSql(input);
        assertEquals(List.of("service-a", 25001), values(rules, input));
    }

}
