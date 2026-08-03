package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.workflow.model.LearningPlanDraft;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LearningPlanValidatorTest {

    private final LearningPlanValidator validator =
            new LearningPlanValidator();

    @Test
    void shouldRejectHallucinatedCourseId() {
        LearningPlanDraft draft = new LearningPlanDraft(
                14,
                List.of(new LearningPlanDraft.DraftCourse(
                        1,
                        999L,
                        "学习")),
                List.of(new LearningPlanDraft.DraftRoutine(
                        "学习",
                        30)),
                "test");

        assertThat(validator.validate(
                30,
                List.of(course(7L)),
                draft))
                .contains("课程 ID 必须来自当前有效候选课程");
    }

    @Test
    void shouldRejectDuplicatesDurationAndInvalidDays() {
        LearningPlanDraft draft = new LearningPlanDraft(
                91,
                List.of(
                        new LearningPlanDraft.DraftCourse(
                                1, 7L, "一"),
                        new LearningPlanDraft.DraftCourse(
                                2, 7L, "二")),
                List.of(new LearningPlanDraft.DraftRoutine(
                        "学习",
                        31)),
                "test");

        assertThat(validator.validate(
                30,
                List.of(course(7L)),
                draft))
                .contains(
                        "计划天数必须在 1 至 90 天",
                        "同一课程不能重复",
                        "每天总时长不能超过用户输入");
    }

    @Test
    void shouldRejectEmptyCourses() {
        LearningPlanDraft draft = new LearningPlanDraft(
                7,
                List.of(),
                List.of(new LearningPlanDraft.DraftRoutine(
                        "学习",
                        30)),
                "test");

        assertThat(validator.validate(30, List.of(), draft))
                .contains("无有效课程时不能保存学习计划");
    }

    private CourseAiClient.CourseSummary course(Long id) {
        return new CourseAiClient.CourseSummary(
                id,
                "课程",
                "老师",
                "分类",
                0.0,
                "",
                LocalDateTime.now());
    }
}
