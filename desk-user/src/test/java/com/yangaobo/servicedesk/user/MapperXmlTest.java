package com.yangaobo.servicedesk.user;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.yangaobo.servicedesk.user.model.form.auth.IdentityQueryForm;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
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
    private static final String PREFIX = "com.yangaobo.servicedesk.user.mapper.";

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
        assertEquals(3, configuration.getMapperRegistry().getMappers().size());
        for (Class<?> mapper : configuration.getMapperRegistry().getMappers()) {
            for (var method : mapper.getDeclaredMethods()) {
                assertTrue(configuration.hasStatement(mapper.getName() + "." + method.getName()),
                        mapper.getName() + "." + method.getName());
                for (var parameter : method.getParameters()) {
                    assertNotNull(parameter.getAnnotation(org.apache.ibatis.annotations.Param.class));
                }
            }
        }
        jsonMapping("AdminEventMapper.list", "details");
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
     * 管理目标筛选与规范化用户名按值绑定，列表和统计保持同一条件。
     */
    @Test
    void managementFiltersAndPaginationStayAligned() {
        for (int mask = 0; mask < 4; mask++) {
            Map<String, Object> input = new HashMap<>();
            input.put("user", (mask & 1) == 0 ? null : "user-a");
            input.put("organization", (mask & 2) == 0 ? null : "organization-a");
            input.put("limit", 20);
            input.put("offset", 40L);
            sameConditions("AdminEventMapper.list", "AdminEventMapper.count", input);
            var form = new IdentityQueryForm();
            form.setOrganizationId((mask & 1) == 0 ? null : "organization-a");
            form.setStatus((mask & 2) == 0 ? null : "ACTIVE");
            input.put("form", form);
            input.put("keyword", mask == 0 ? null : "name_%");
            sameConditions("AccountMapper.listAccounts", "AccountMapper.countAccounts", input);
            sameConditions("OrganizationMapper.listOrganizations", "OrganizationMapper.countOrganizations", input);
        }
    }

    /**
     * 有效空mapper可以注册接口，不要求预建无用SQL。
     */
    @Test
    void emptyMapperIsAValidLayerFile() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"https://mybatis.org/dtd/mybatis-3-mapper.dtd\">"
                + "<mapper namespace=\"" + EmptyMapper.class.getName() + "\"></mapper>";
        var config = new MybatisConfiguration();
        new XMLMapperBuilder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                config, "empty-mapper.xml", config.getSqlFragments()).parse();
        assertTrue(config.hasMapper(EmptyMapper.class));
    }

    /**
     * 仅用于验证无自定义方法的合法mapper注册。
     */
    interface EmptyMapper {
    }

}
