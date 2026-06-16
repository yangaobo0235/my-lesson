package com.yangaobo.ai.workflow.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.workflow.model.LearningPlanDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class LearningPlanValidator {

    public List<String> validate(
            int minutesPerDay,
            List<CourseAiClient.CourseSummary> candidates,
            LearningPlanDraft draft) {
        List<String> errors = new ArrayList<>();
        if (draft == null) {
            return List.of("模型没有生成学习计划草案");
        }
        if (draft.planDays() < 1 || draft.planDays() > 90) {
            errors.add("计划天数必须在 1 至 90 天");
        }
        Set<Long> validCourseIds = new HashSet<>();
        for (CourseAiClient.CourseSummary candidate : candidates) {
            if (candidate != null && candidate.id() != null) {
                validCourseIds.add(candidate.id());
            }
        }
        if (draft.courses().isEmpty()) {
            errors.add("无有效课程时不能保存学习计划");
        }
        Set<Long> selected = new HashSet<>();
        for (LearningPlanDraft.DraftCourse course : draft.courses()) {
            if (course == null
                    || course.courseId() == null
                    || !validCourseIds.contains(course.courseId())) {
                errors.add("课程 ID 必须来自当前有效候选课程");
                continue;
            }
            if (!selected.add(course.courseId())) {
                errors.add("同一课程不能重复");
            }
        }
        int totalMinutes = draft.dailyRoutine().stream()
                .filter(routine -> routine != null)
                .mapToInt(LearningPlanDraft.DraftRoutine::minutes)
                .sum();
        if (draft.dailyRoutine().stream()
                .filter(routine -> routine != null)
                .anyMatch(routine -> routine.minutes() <= 0)) {
            errors.add("每日安排中的时长必须大于 0");
        }
        if (totalMinutes > minutesPerDay) {
            errors.add("每天总时长不能超过用户输入");
        }
        return errors.stream().distinct().toList();
    }
}
