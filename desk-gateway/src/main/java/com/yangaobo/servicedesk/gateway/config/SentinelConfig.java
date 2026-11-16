package com.yangaobo.servicedesk.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.gateway.component.web.GatewayIngressFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 在真实Gateway请求生命周期应用API分组和冷启动基线，规则故障不放开流控。
 *
 * @author yangaobo
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "desk.infrastructure.enabled", havingValue = "true")
public class SentinelConfig {

    /**
     * 只记录规则状态，不记录Nacos载荷及凭据。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SentinelConfig.class);

    /**
     * 必须持续受保护的五个稳定分组。
     */
    private static final Set<String> RESOURCES =
            Set.of("desk-login", "desk-generation", "desk-upload", "desk-stream", "desk-public");

    /**
     * 加载本地有效基线，再监听Nacos；空配置沿用基线，非法更新保留旧规则。
     *
     * @param environment 已验证配置
     * @param mapper      应用统一JSON装配
     * @return 应用关闭时释放的Nacos规则监听
     * @throws IOException 随制品交付的基线损坏
     */
    @Bean(destroyMethod = "close")
    public NacosDataSource<Set<GatewayFlowRule>> gatewayFlowSource(
            Environment environment, ObjectMapper mapper) throws IOException {
        Set<GatewayFlowRule> baseline;
        try (var input = getClass().getResourceAsStream("/sentinel/gateway-flow.json")) {
            if (input == null) {
                throw new IllegalStateException("缺少Gateway流控基线");
            }
            baseline = mapper.readValue(input, new TypeReference<Set<GatewayFlowRule>>() {
            });
        }
        GatewayApiDefinitionManager.loadApiDefinitions(
                Set.of(
                        api("desk-login", "^/api/v1/users/auth/login$"),
                        api("desk-generation", "^/api/v1/ai/conversations/[^/]+/generations$"),
                        api("desk-upload", "^/api/v1/admin/document/upload$"),
                        api("desk-stream", "^/api/v1/ai/generations/[^/]+/stream$"),
                        api(
                                "desk-public",
                                "^/api/v1/(?!(?:users/auth/login|admin/document/upload|"
                                        + "ai/conversations/[^/]+/generations|ai/generations/[^/]+/stream)$).+")));
        GatewayCallbackManager.setBlockHandler(
                (exchange, exception) -> {
                    boolean degraded = exception instanceof DegradeException;
                    CommonErrorCode code =
                            degraded
                                    ? CommonErrorCode.SERVICE_UNAVAILABLE
                                    : CommonErrorCode.TOO_MANY_REQUESTS;
                    String traceId = exchange.getAttribute(GatewayIngressFilter.TRACE_ATTRIBUTE);
                    return ServerResponse.status(degraded ? 503 : 429)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Retry-After", "1")
                            .bodyValue(Result.failure(code, traceId));
                });
        Properties properties = new Properties();
        properties.setProperty(
                "serverAddr",
                environment.getRequiredProperty("spring.cloud.nacos.config.server-addr"));
        properties.setProperty(
                "namespace",
                environment.getRequiredProperty("spring.cloud.nacos.config.namespace"));
        properties.setProperty(
                "username", environment.getRequiredProperty("spring.cloud.nacos.config.username"));
        properties.setProperty(
                "password", environment.getRequiredProperty("spring.cloud.nacos.config.password"));
        String resource = environment.getRequiredProperty("desk.infrastructure.resource-env");
        AtomicBoolean loaded = new AtomicBoolean();
        NacosDataSource<Set<GatewayFlowRule>> source =
                new NacosDataSource<>(
                        properties,
                        environment.getRequiredProperty("spring.cloud.nacos.config.group"),
                        "desk-gateway-" + resource + "-gw-flow.json",
                        content -> {
                            Set<GatewayFlowRule> rules = parseRules(mapper, content, baseline);
                            loaded.set(true);
                            return rules;
                        });
        if (!loaded.get()) {
            source.getProperty().updateValue(baseline);
            LOGGER.warn("Sentinel规则中心不可用，Gateway使用制品基线");
        }
        GatewayRuleManager.register2Property(source.getProperty());
        return source;
    }

    /**
     * 校验远端规则覆盖所有稳定分组，不能通过空数组解除保护。
     *
     * @param mapper   应用JSON工具
     * @param content  远端原始配置
     * @param baseline 制品基线
     * @return 有效规则
     */
    public static Set<GatewayFlowRule> parseRules(
            ObjectMapper mapper, String content, Set<GatewayFlowRule> baseline) {
        if (content == null || content.isBlank() || "[]".equals(content.strip())) {
            LOGGER.warn("Sentinel远端规则为空，Gateway使用制品基线");
            return baseline;
        }
        try {
            Set<GatewayFlowRule> rules =
                    mapper.readValue(content, new TypeReference<Set<GatewayFlowRule>>() {
                    });
            if (rules == null
                    || !rules.stream()
                    .map(GatewayFlowRule::getResource)
                    .collect(Collectors.toSet())
                    .equals(RESOURCES)
                    || rules.size() != RESOURCES.size()
                    || rules.stream()
                    .anyMatch(
                            rule ->
                                    rule.getResourceMode() != 1
                                            || !Double.isFinite(rule.getCount())
                                            || rule.getCount() <= 0
                                            || rule.getControlBehavior() != 0
                                            || rule.getBurst() != 0
                                            || rule.getParamItem() != null
                                            || rule.getGrade()
                                            != (Set.of("desk-upload", "desk-stream")
                                            .contains(
                                                    rule
                                                            .getResource())
                                            ? 0
                                            : 1))) {
                throw new IllegalArgumentException();
            }
            return rules;
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Gateway流控配置无效，保留最后有效规则");
        }
    }

    /**
     * 按精确路径正则分组；例外资源与普通流量不重复计数。
     *
     * @param name  固定资源名
     * @param regex 服务器控制的路径模式
     * @return API定义
     */
    private static ApiDefinition api(String name, String regex) {
        return new ApiDefinition(name)
                .setPredicateItems(
                        new HashSet<>(
                                Set.of(
                                        new ApiPathPredicateItem()
                                                .setPattern(regex)
                                                .setMatchStrategy(2))));
    }
}
