package com.yangaobo.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentLearningPlanModels {

    private AgentLearningPlanModels() {
    }

    public record CreateDraftRequest(
            @NotNull UUID requestId,
            @NotBlank @Size(max = 500) String goal,
            @Min(10) @Max(480) int minutesPerDay,
            @NotNull List<Map<String, Object>> courses,
            @NotNull List<Map<String, Object>> dailyRoutine
    ) {
        public CreateDraftRequest {
            courses = List.copyOf(courses);
            dailyRoutine = List.copyOf(dailyRoutine);
        }
    }

    public record AdjustmentRequest(
            @NotBlank @Size(max = 1000) String adjustment,
            @NotNull UUID requestId
    ) {
    }

    public record ProgressRequest(
            @Min(0) @Max(100) int progressPercent,
            @Size(max = 500) String note
    ) {
    }

    public record DraftView(
            UUID id,
            Long userId,
            String goal,
            int minutesPerDay,
            int version,
            UUID previousDraftId,
            List<Map<String, Object>> courses,
            List<Map<String, Object>> dailyRoutine,
            List<Map<String, Object>> adjustments,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record PlanView(
            UUID id,
            String goal,
            int availableMinutesPerDay,
            int estimatedWeeks,
            String status,
            int progressPercent,
            String progressNote,
            List<Map<String, Object>> courses,
            List<Map<String, Object>> dailyRoutine,
            List<Map<String, Object>> adjustments,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
