package com.yangaobo.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.dto.ai.AgentLearningPlanModels.ProgressRequest;
import com.yangaobo.dto.ai.AgentLearningPlanModels.CreateDraftRequest;
import com.yangaobo.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLearningPlanServiceTest {

    @Test
    void progressUpdateRejectsPlanNotOwnedByDelegatedUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        AgentLearningPlanService service =
                new AgentLearningPlanService(jdbcTemplate, new ObjectMapper());
        UUID planId = UUID.randomUUID();

        assertThrows(
                ServiceException.class,
                () -> service.updateProgress(planId, 42L, new ProgressRequest(25, "started")));
        verify(jdbcTemplate).update(
                anyString(),
                eq(25),
                eq("started"),
                eq(planId.toString()),
                eq(42L));
    }

    @Test
    void cancelRejectsDraftNotOwnedByDelegatedUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        AgentLearningPlanService service =
                new AgentLearningPlanService(jdbcTemplate, new ObjectMapper());

        assertThrows(
                ServiceException.class,
                () -> service.cancel(UUID.randomUUID(), 42L));
    }

    @Test
    void createDraftReturnsExistingResultForSameRequestId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UUID requestId = UUID.randomUUID();
        AgentLearningPlanService service =
                new AgentLearningPlanService(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq(42L), eq(requestId.toString())))
                .thenReturn(List.of(mock(com.yangaobo.dto.ai.AgentLearningPlanModels.DraftView.class)));

        service.createDraft(
                42L,
                new CreateDraftRequest(requestId, "Java", 30, List.of(), List.of()));

        org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.never())
                .update(anyString(), any(Object[].class));
    }
}
