package com.yangaobo.ai.agent.service;

import com.yangaobo.ai.agent.config.AgentProperties;
import com.yangaobo.ai.agent.model.UserIntent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.task.support.TaskExecutorAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class IntentClassifierRuleTest {

    private final IntentClassifier classifier = new IntentClassifier(
            mock(ChatClient.Builder.class),
            new AgentProperties(),
            new TaskExecutorAdapter(Runnable::run));

    @Test
    void shouldRejectCartWriteActionAsOutOfScope() {
        assertThat(classifier.classifyByRule("把 Java 课程加入购物车").intent())
                .isEqualTo(UserIntent.OUT_OF_SCOPE);
    }

    @Test
    void shouldRejectAdminOperationAsOutOfScope() {
        assertThat(classifier.classifyByRule("请重建知识索引").intent())
                .isEqualTo(UserIntent.OUT_OF_SCOPE);
    }

    @Test
    void shouldRecognizeSupportedScenariosByRule() {
        assertThat(classifier.classifyByRule("什么是 Java 泛型").intent())
                .isEqualTo(UserIntent.KNOWLEDGE_QA);
        assertThat(classifier.classifyByRule("推荐课程").intent())
                .isEqualTo(UserIntent.COURSE_SEARCH);
        assertThat(classifier.classifyByRule("查看我的订单").intent())
                .isEqualTo(UserIntent.PERSONAL_QUERY);
        assertThat(classifier.classifyByRule("制定学习计划").intent())
                .isEqualTo(UserIntent.LEARNING_PLAN);
    }

    @Test
    void shouldUseLowConfidenceForUnknownMessage() {
        assertThat(classifier.classifyByRule("今天天气怎么样").confidence())
                .isLessThan(0.65);
    }

    @Test
    void shouldUseConfiguredLowLatencyRouterModel() {
        AgentProperties properties = new AgentProperties();
        properties.setRouterModel("qwen-flash-test");
        IntentClassifier configuredClassifier = new IntentClassifier(
                mock(ChatClient.Builder.class),
                properties,
                new TaskExecutorAdapter(Runnable::run));

        assertThat(configuredClassifier.routerOptions().getModel())
                .isEqualTo("qwen-flash-test");
        assertThat(configuredClassifier.routerOptions().getTemperature())
                .isZero();
    }

    @Test
    void strictClassificationShouldNotFallBackToRules() {
        assertThatThrownBy(
                        () ->
                                classifier.classifyStrict(
                                        "搜索 Java 课程",
                                        ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("External intent model");
    }
}
