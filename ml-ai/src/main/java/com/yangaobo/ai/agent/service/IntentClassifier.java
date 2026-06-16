package com.yangaobo.ai.agent.service;

import com.yangaobo.ai.agent.config.AgentProperties;
import com.yangaobo.ai.agent.model.IntentDecision;
import com.yangaobo.ai.agent.model.UserIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class IntentClassifier {

    private static final Logger log =
            LoggerFactory.getLogger(IntentClassifier.class);

    private static final String SYSTEM_PROMPT = """
            你是 MyLesson 的意图分类器。只能从以下意图中选择一个：
            KNOWLEDGE_QA：知识解释、课程内容问答。
            COURSE_SEARCH：搜索、推荐或查询课程详情。
            PERSONAL_QUERY：查询当前用户自己的订单、资料、购物车或已有学习计划。
            CART_ACTION：添加或移除购物车课程。
            LEARNING_PLAN：创建、调整或查询学习计划。
            ADMIN_OPERATION：管理员重建知识索引等后台操作。
            OUT_OF_SCOPE：与 MyLesson 学习和业务无关。

            只判断用户意图，不执行任务。对代词和连续问题可参考对话上下文，
            但对话摘要不是真实业务数据来源。
            """;

    private final ChatClient chatClient;
    private final AgentProperties properties;
    private final AsyncTaskExecutor taskExecutor;
    private final BeanOutputConverter<IntentDecision> converter =
            new BeanOutputConverter<>(IntentDecision.class);

    public IntentClassifier(
            ChatClient.Builder chatClientBuilder,
            AgentProperties properties,
            @Qualifier("reactAgentTaskExecutor")
            AsyncTaskExecutor taskExecutor) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
        this.taskExecutor = taskExecutor;
    }

    public IntentDecision classify(
            String message,
            String conversationContext) {
        int attempts = Math.max(
                1,
                properties.getModelRetryCount() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            Future<IntentDecision> future =
                    taskExecutor.submit(() -> classifyWithModel(
                            message,
                            conversationContext));
            try {
                IntentDecision decision = future.get(
                        properties.getIntentTimeout().toMillis(),
                        TimeUnit.MILLISECONDS);
                if (decision != null) {
                    return decision.normalized();
                }
            } catch (TimeoutException exception) {
                future.cancel(true);
                log.warn(
                        "Intent classification attempt {} timed out",
                        attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                return classifyByRule(message);
            } catch (ExecutionException | RuntimeException exception) {
                log.warn(
                        "Intent classification attempt {} failed with {}",
                        attempt,
                        exception.getClass().getSimpleName());
            }
        }
        return classifyByRule(message);
    }

    private IntentDecision classifyWithModel(
            String message,
            String conversationContext) {
        String result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(buildPrompt(message, conversationContext))
                .call()
                .content();
        return converter.convert(result);
    }

    IntentDecision classifyByRule(String message) {
        String text = message == null
                ? ""
                : message.toLowerCase(Locale.ROOT);
        if (containsAny(
                text,
                "重建索引",
                "重建知识",
                "刷新索引",
                "rebuild")) {
            return decision(
                    UserIntent.ADMIN_OPERATION,
                    "命中知识索引管理关键词");
        }
        if (containsAny(text, "加入购物车", "添加购物车", "移出购物车",
                "移除购物车", "删除购物车")) {
            return decision(
                    UserIntent.CART_ACTION,
                    "命中购物车写操作关键词");
        }
        if (containsAny(text, "学习计划", "学习规划", "制定计划",
                "每天学习", "学习目标")) {
            return decision(
                    UserIntent.LEARNING_PLAN,
                    "命中学习计划关键词");
        }
        if (containsAny(text, "我的订单", "最近订单", "我的资料",
                "个人资料", "我的购物车", "我的计划")) {
            return decision(
                    UserIntent.PERSONAL_QUERY,
                    "命中当前用户数据查询关键词");
        }
        if (containsAny(text, "推荐课程", "搜索课程", "查找课程",
                "课程详情", "有哪些课程", "有没有课程")) {
            return decision(
                    UserIntent.COURSE_SEARCH,
                    "命中课程检索关键词");
        }
        if (containsAny(text, "什么是", "为什么", "怎么学", "讲解",
                "解释", "课程内容", "知识点")) {
            return decision(
                    UserIntent.KNOWLEDGE_QA,
                    "命中知识问答关键词");
        }
        return new IntentDecision(
                UserIntent.OUT_OF_SCOPE,
                0.4,
                "模型分类不可用且规则无法确认意图");
    }

    private String buildPrompt(
            String message,
            String conversationContext) {
        StringBuilder prompt = new StringBuilder()
                .append("用户消息：\n")
                .append(message == null ? "" : message);
        if (conversationContext != null
                && !conversationContext.isBlank()) {
            prompt.append("\n\n最近对话：\n")
                    .append(conversationContext);
        }
        prompt.append("\n\n请返回符合以下 JSON Schema 的对象：\n")
                .append(converter.getFormat());
        return prompt.toString();
    }

    private IntentDecision decision(
            UserIntent intent,
            String reason) {
        return new IntentDecision(intent, 0.8, reason);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
