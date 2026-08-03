package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.security.AuthenticatedUser;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.tool.dto.LearningPlanProgressRequest;
import com.yangaobo.ai.tool.model.LearningPlan;
import com.yangaobo.ai.tool.repository.LearningPlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningPlanServiceTest {

    private LearningPlanRepository repository;
    private LearningPlanService service;

    @BeforeEach
    void setUp() {
        repository = mock(LearningPlanRepository.class);
        service = new LearningPlanService(repository);
        UserContext.set(new AuthenticatedUser(
                41L,
                "alice",
                List.of("student")));
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void shouldUseCurrentUserWhenReading() {
        LearningPlan stored = new LearningPlan(
                java.util.UUID.randomUUID(),
                "摄影入门",
                30,
                4,
                "ACTIVE",
                0,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null);
        when(repository.findLatestActive(41L))
                .thenReturn(Optional.of(stored));

        assertThat(service.getCurrent()).isSameAs(stored);
        verify(repository).findLatestActive(41L);
    }

    @Test
    void shouldReturnRecoverableErrorWhenPlanDoesNotExist() {
        when(repository.findLatestActive(41L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(service::getCurrent)
                .isInstanceOf(BusinessOperationException.class)
                .hasMessageContaining("还没有学习计划");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUpdateProgressWithAdjustmentSuggestions() {
        java.util.UUID planId = java.util.UUID.randomUUID();
        LearningPlan persisted = new LearningPlan(
                planId,
                "Java 后端进阶",
                45,
                6,
                "ACTIVE",
                78,
                "完成了 Redis",
                List.of(),
                List.of(),
                List.of(new LearningPlan.LearningPlanAdjustment(
                        "SPRINT",
                        "进度良好，建议把剩余课程集中到高优先级知识点并安排一次综合复盘。")),
                null,
                null);
        when(repository.updateProgress(
                org.mockito.ArgumentMatchers.eq(planId),
                org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq(78),
                org.mockito.ArgumentMatchers.eq("完成了 Redis"),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Optional.of(persisted));

        LearningPlan updated = service.updateProgress(
                planId,
                new LearningPlanProgressRequest(78, "  完成了 Redis  "));
        ArgumentCaptor<List> adjustmentsCaptor =
                ArgumentCaptor.forClass(List.class);

        assertThat(updated.progressPercent()).isEqualTo(78);
        assertThat(updated.progressNote()).isEqualTo("完成了 Redis");
        verify(repository).updateProgress(
                org.mockito.ArgumentMatchers.eq(planId),
                org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq(78),
                org.mockito.ArgumentMatchers.eq("完成了 Redis"),
                adjustmentsCaptor.capture());
        assertThat((List<LearningPlan.LearningPlanAdjustment>)
                adjustmentsCaptor.getValue())
                .singleElement()
                .satisfies(adjustment -> {
                    assertThat(adjustment.type()).isEqualTo("SPRINT");
                    assertThat(adjustment.message()).contains("进度良好");
                });
    }
}
