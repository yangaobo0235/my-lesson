package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.workflow.model.LearningPlanDraft;
import com.yangaobo.ai.workflow.model.LearningPlanDraft.DraftCourse;
import com.yangaobo.ai.workflow.model.LearningPlanDraft.DraftRoutine;
import com.yangaobo.ai.workflow.model.LearningPlanReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LearningPlanDraftGenerator {

    private static final Logger log =
            LoggerFactory.getLogger(LearningPlanDraftGenerator.class);

    private static final String SYSTEM_PROMPT = """
            你是 MyLesson 的 PlanDesignerAgent。
            只能从输入的候选课程中选择课程，不得创造或修改课程 ID。
            输出建议计划即可，最终课程真实性、时长、重复项和天数由 Java 校验。
            每日安排的分钟数总和不得超过用户提供的 minutesPerDay。
            planDays 必须在 1 到 90 天之间。
            """;

    private final ChatClient chatClient;
    private final BeanOutputConverter<LearningPlanDraft> converter =
            new BeanOutputConverter<>(LearningPlanDraft.class);

    public LearningPlanDraftGenerator(
            ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public LearningPlanDraft generate(
            String goal,
            int minutesPerDay,
            UserAiClient.UserProfile profile,
            List<CourseAiClient.CourseSummary> candidates) {
        try {
            return call(buildPrompt(
                    goal, minutesPerDay, profile, candidates));
        } catch (RuntimeException exception) {
            log.warn(
                    "Learning plan model generation failed, using deterministic fallback: {}",
                    exception.getClass().getSimpleName());
        }
        return deterministicFallback(goal, minutesPerDay, candidates);
    }

    public LearningPlanDraft repair(
            String goal,
            int minutesPerDay,
            UserAiClient.UserProfile profile,
            List<CourseAiClient.CourseSummary> candidates,
            LearningPlanDraft currentDraft,
            List<String> validationErrors,
            LearningPlanReview review) {
        StringBuilder repair = new StringBuilder(buildPrompt(
                goal, minutesPerDay, profile, candidates))
                .append("\n当前草案：").append(currentDraft)
                .append("\nJava 硬规则错误：")
                .append(validationErrors == null ? List.of() : validationErrors)
                .append("\nReviewer 结果：").append(review)
                .append("\n仅修复明确问题，课程 ID、用户目标和每日时间预算不可改变。");
        try {
            return call(repair.toString());
        } catch (RuntimeException exception) {
            log.warn("Learning plan repair failed, using deterministic fallback: {}",
                    exception.getClass().getSimpleName());
            return deterministicFallback(goal, minutesPerDay, candidates);
        }
    }

    private LearningPlanDraft call(String prompt) {
        String content = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(prompt)
                .call()
                .content();
        LearningPlanDraft result = converter.convert(content);
        if (result == null) {
            throw new IllegalStateException("PlanDesignerAgent returned no draft");
        }
        return result;
    }

    private String buildPrompt(
            String goal,
            int minutesPerDay,
            UserAiClient.UserProfile profile,
            List<CourseAiClient.CourseSummary> candidates) {
        StringBuilder prompt = new StringBuilder()
                .append("goal: ").append(goal)
                .append("\nminutesPerDay: ").append(minutesPerDay)
                .append("\nuserProfile: ")
                .append(profile == null
                        ? "unknown"
                        : "nickname=" + profile.nickname()
                                + ", age=" + profile.age()
                                + ", info=" + profile.info())
                .append("\ncandidates:\n");
        for (CourseAiClient.CourseSummary course : candidates) {
            prompt.append("- id=").append(course.id())
                    .append(", title=").append(course.title())
                    .append(", category=").append(course.category())
                    .append(", author=").append(course.author())
                    .append('\n');
        }
        return prompt.append(
                        "\n返回符合以下 JSON Schema 的对象：\n")
                .append(converter.getFormat())
                .toString();
    }

    public LearningPlanDraft deterministicFallback(
            String goal,
            int minutesPerDay,
            List<CourseAiClient.CourseSummary> candidates) {
        List<DraftCourse> courses = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            CourseAiClient.CourseSummary course = candidates.get(index);
            courses.add(new DraftCourse(
                    index + 1,
                    course.id(),
                    "完成“" + course.title() + "”并围绕“"
                            + goal + "”整理实践笔记"));
        }
        int learning = Math.max(1, minutesPerDay * 60 / 100);
        int practice = Math.max(1, minutesPerDay * 30 / 100);
        int review = Math.max(
                0,
                minutesPerDay - learning - practice);
        List<DraftRoutine> routines = new ArrayList<>();
        routines.add(new DraftRoutine("学习课程新内容", learning));
        routines.add(new DraftRoutine("练习并整理笔记", practice));
        if (review > 0) {
            routines.add(new DraftRoutine("复盘当天重点", review));
        }
        int days = Math.max(
                7,
                Math.min(90, candidates.size() * 14));
        return new LearningPlanDraft(
                days,
                courses,
                routines,
                "根据当前目标和有效候选课程生成的学习建议");
    }
}
