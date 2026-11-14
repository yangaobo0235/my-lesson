package com.yangaobo.servicedesk.gateway;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.gateway.config.SentinelConfig;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证冷启动规则和非法远端更新的失败关闭，不连接Nacos。
 *
 * @author yangaobo
 */
class SentinelRulesTest {

    /**
     * 缺失规则和空数组保持基线，正常完整配置可解析。
     * @throws IOException 制品资源读取失败
     */
    @Test
    void emptyRemoteRulesKeepPackagedBaseline() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (var input = getClass().getResourceAsStream("/sentinel/gateway-flow.json")) {
            Set<GatewayFlowRule> baseline = mapper.readValue(input, new TypeReference<Set<GatewayFlowRule>>() { });
            assertThat(baseline).hasSize(5);
            assertThat(SentinelConfig.parseRules(mapper, null, baseline)).isSameAs(baseline);
            assertThat(SentinelConfig.parseRules(mapper, "[]", baseline)).isSameAs(baseline);
            assertThat(SentinelConfig.parseRules(mapper, mapper.writeValueAsString(baseline), baseline))
                    .hasSize(5);
        }
    }

    /** 非法JSON和未覆盖全部分组的更新不能移除既有保护。 */
    @Test
    void rejectsInvalidOrIncompleteRemoteRules() {
        ObjectMapper mapper = new ObjectMapper();
        for (String content : new String[]{"private-value", "null", "[{\"resource\":\"desk-login\",\"count\":0}]"}) {
            assertThatThrownBy(() -> SentinelConfig.parseRules(mapper, content, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Gateway流控配置无效，保留最后有效规则");
        }
    }
}
