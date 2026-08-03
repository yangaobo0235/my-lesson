package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.workflow.model.LearningPlanDraft;
import com.yangaobo.ai.workflow.model.LearningPlanReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlanReviewerAgent {

    private static final Logger log =
            LoggerFactory.getLogger(PlanReviewerAgent.class);
    private static final String SYSTEM_PROMPT = """
            你是只读的 PlanReviewerAgent。检查课程前置顺序、学习节奏、
            用户目标覆盖和推荐依据。你不能修改草案，不能调用工具，
            不能覆盖 Java 硬规则。问题必须结构化并引用输入课程序号。
            """;

    private final ChatClient chatClient;
    private final BeanOutputConverter<LearningPlanReview> converter =
            new BeanOutputConverter<>(LearningPlanReview.class);

    public PlanReviewerAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public LearningPlanReview review(
            String goal,
            int minutesPerDay,
            List<CourseAiClient.CourseSummary> candidates,
            LearningPlanDraft draft,
            List<String> javaErrors) {
        if (javaErrors != null && !javaErrors.isEmpty()) {
            return javaRejected(javaErrors);
        }
        try {
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(prompt(goal, minutesPerDay, candidates, draft))
                    .call()
                    .content();
            LearningPlanReview review = converter.convert(result);
            if (review != null) {
                return review;
            }
        } catch (RuntimeException exception) {
            log.warn("Learning plan review failed, accepting Java-valid draft: {}",
                    exception.getClass().getSimpleName());
        }
        return new LearningPlanReview(true, List.of(), "");
    }

    private LearningPlanReview javaRejected(List<String> errors) {
        List<LearningPlanReview.Issue> issues = errors.stream()
                .map(error -> new LearningPlanReview.Issue(
                        "JAVA_HARD_RULE", error, List.of(), List.of()))
                .toList();
        return new LearningPlanReview(false, issues, "先修复 Java 硬规则");
    }

    private String prompt(
            String goal,
            int minutes,
            List<CourseAiClient.CourseSummary> candidates,
            LearningPlanDraft draft) {
        StringBuilder text = new StringBuilder()
                .append("goal=").append(goal)
                .append("\nminutesPerDay=").append(minutes)
                .append("\ncandidates:\n");
        for (int index = 0; index < candidates.size(); index++) {
            CourseAiClient.CourseSummary course = candidates.get(index);
            text.append('[').append(index + 1).append("] id=")
                    .append(course.id()).append(", title=")
                    .append(course.title()).append(", category=")
                    .append(course.category()).append('\n');
        }
        return text.append("draft=").append(draft)
                .append("\n返回符合 JSON Schema 的对象：\n")
                .append(converter.getFormat())
                .toString();
    }
}
