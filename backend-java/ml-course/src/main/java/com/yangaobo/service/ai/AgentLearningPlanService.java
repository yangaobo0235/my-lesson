package com.yangaobo.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.dto.ai.AgentLearningPlanModels.AdjustmentRequest;
import com.yangaobo.dto.ai.AgentLearningPlanModels.CreateDraftRequest;
import com.yangaobo.dto.ai.AgentLearningPlanModels.DraftView;
import com.yangaobo.dto.ai.AgentLearningPlanModels.PlanView;
import com.yangaobo.dto.ai.AgentLearningPlanModels.ProgressRequest;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.result.ResultCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentLearningPlanService {

    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentLearningPlanService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DraftView createDraft(Long userId, CreateDraftRequest request) {
        List<DraftView> idempotent = jdbcTemplate.query(
                "SELECT * FROM agent_learning_plan_draft WHERE user_id = ? AND request_id = ? LIMIT 1",
                this::draft,
                userId,
                request.requestId().toString());
        if (!idempotent.isEmpty()) {
            return idempotent.get(0);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO agent_learning_plan_draft (
                        id, user_id, request_id, goal, minutes_per_day, version,
                        courses_json, routine_json, adjustments_json,
                        status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 1, ?, ?, '[]',
                              'WAITING_CONFIRMATION', NOW(), NOW())
                    """,
                    id.toString(),
                    userId,
                    request.requestId().toString(),
                    request.goal(),
                    request.minutesPerDay(),
                    json(request.courses()),
                    json(request.dailyRoutine()));
        } catch (DuplicateKeyException exception) {
            return jdbcTemplate.query(
                    "SELECT * FROM agent_learning_plan_draft WHERE user_id = ? AND request_id = ? LIMIT 1",
                    this::draft,
                    userId,
                    request.requestId().toString()).get(0);
        }
        return requireDraft(id, userId);
    }

    public List<DraftView> drafts(Long userId) {
        return jdbcTemplate.query(
                """
                SELECT * FROM agent_learning_plan_draft
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 50
                """,
                this::draft,
                userId);
    }

    public List<DraftView> versions(UUID draftId, Long userId) {
        DraftView selected = requireDraft(draftId, userId);
        UUID rootId = selected.id();
        while (selected.previousDraftId() != null) {
            selected = requireDraft(selected.previousDraftId(), userId);
            rootId = selected.id();
        }
        List<DraftView> result = new ArrayList<>();
        DraftView current = requireDraft(rootId, userId);
        result.add(current);
        while (true) {
            List<DraftView> next = jdbcTemplate.query(
                    """
                    SELECT * FROM agent_learning_plan_draft
                    WHERE user_id = ? AND previous_draft_id = ?
                    ORDER BY version ASC LIMIT 1
                    """,
                    this::draft,
                    userId,
                    current.id().toString());
            if (next.isEmpty()) {
                return List.copyOf(result);
            }
            current = next.get(0);
            result.add(current);
        }
    }

    @Transactional
    public DraftView adjust(
            UUID draftId,
            Long userId,
            AdjustmentRequest request) {
        List<DraftView> idempotent = jdbcTemplate.query(
                "SELECT * FROM agent_learning_plan_draft WHERE user_id = ? AND request_id = ? LIMIT 1",
                this::draft,
                userId,
                request.requestId().toString());
        if (!idempotent.isEmpty()) {
            return idempotent.get(0);
        }
        DraftView current = requireWaitingDraft(draftId, userId);
        UUID nextId = UUID.randomUUID();
        List<Map<String, Object>> adjustments = new ArrayList<>(current.adjustments());
        adjustments.add(Map.of(
                "type", "USER_REQUEST",
                "message", request.adjustment()));
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO agent_learning_plan_draft (
                        id, user_id, goal, minutes_per_day, version,
                        previous_draft_id, request_id, courses_json,
                        routine_json, adjustments_json, status,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                              'WAITING_CONFIRMATION', NOW(), NOW())
                    """,
                    nextId.toString(),
                    userId,
                    current.goal(),
                    current.minutesPerDay(),
                    current.version() + 1,
                    current.id().toString(),
                    request.requestId().toString(),
                    json(current.courses()),
                    json(current.dailyRoutine()),
                    json(adjustments));
        } catch (DuplicateKeyException exception) {
            return jdbcTemplate.query(
                    "SELECT * FROM agent_learning_plan_draft WHERE user_id = ? AND request_id = ? LIMIT 1",
                    this::draft,
                    userId,
                    request.requestId().toString()).get(0);
        }
        jdbcTemplate.update(
                "UPDATE agent_learning_plan_draft SET status = 'SUPERSEDED', updated_at = NOW() WHERE id = ? AND user_id = ?",
                current.id().toString(),
                userId);
        return requireDraft(nextId, userId);
    }

    @Transactional
    public PlanView confirm(UUID draftId, Long userId) {
        DraftView draft = requireWaitingDraft(draftId, userId);
        List<PlanView> existing = jdbcTemplate.query(
                "SELECT * FROM agent_learning_plan WHERE source_draft_id = ? AND user_id = ? LIMIT 1",
                this::plan,
                draftId.toString(),
                userId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbcTemplate.update(
                "UPDATE agent_learning_plan SET status = 'SUPERSEDED', updated_at = NOW() WHERE user_id = ? AND status = 'ACTIVE'",
                userId);
        UUID planId = UUID.randomUUID();
        int estimatedWeeks = Math.max(1, draft.courses().size() * 2);
        jdbcTemplate.update(
                """
                INSERT INTO agent_learning_plan (
                    id, source_draft_id, user_id, goal,
                    minutes_per_day, estimated_weeks, courses_json,
                    routine_json, adjustments_json, status,
                    progress_percent, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0, NOW(), NOW())
                """,
                planId.toString(),
                draftId.toString(),
                userId,
                draft.goal(),
                draft.minutesPerDay(),
                estimatedWeeks,
                json(draft.courses()),
                json(draft.dailyRoutine()),
                json(draft.adjustments()));
        jdbcTemplate.update(
                "UPDATE agent_learning_plan_draft SET status = 'CONFIRMED', updated_at = NOW() WHERE id = ? AND user_id = ?",
                draftId.toString(),
                userId);
        return requirePlan(planId, userId);
    }

    public void cancel(UUID draftId, Long userId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE agent_learning_plan_draft
                SET status = 'CANCELLED', updated_at = NOW()
                WHERE id = ? AND user_id = ? AND status = 'WAITING_CONFIRMATION'
                """,
                draftId.toString(),
                userId);
        if (updated != 1) {
            throw notFound("学习计划草案不存在或不可取消");
        }
    }

    public List<PlanView> plans(Long userId, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM agent_learning_plan WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
                this::plan,
                userId,
                limit);
    }

    @Transactional
    public PlanView updateProgress(
            UUID planId,
            Long userId,
            ProgressRequest request) {
        int updated = jdbcTemplate.update(
                """
                UPDATE agent_learning_plan
                SET progress_percent = ?, progress_note = ?,
                    last_progress_at = NOW(), updated_at = NOW()
                WHERE id = ? AND user_id = ? AND status = 'ACTIVE'
                """,
                request.progressPercent(),
                request.note(),
                planId.toString(),
                userId);
        if (updated != 1) {
            throw notFound("正式学习计划不存在或不可更新");
        }
        return requirePlan(planId, userId);
    }

    private DraftView requireWaitingDraft(UUID id, Long userId) {
        DraftView draft = requireDraft(id, userId);
        if (!"WAITING_CONFIRMATION".equals(draft.status())) {
            throw new ServiceException(
                    ResultCode.ILLEGAL_PARAM,
                    "学习计划草案状态不允许执行该操作");
        }
        return draft;
    }

    private DraftView requireDraft(UUID id, Long userId) {
        List<DraftView> rows = jdbcTemplate.query(
                "SELECT * FROM agent_learning_plan_draft WHERE id = ? AND user_id = ? LIMIT 1",
                this::draft,
                id.toString(),
                userId);
        if (rows.isEmpty()) {
            throw notFound("学习计划草案不存在");
        }
        return rows.get(0);
    }

    private PlanView requirePlan(UUID id, Long userId) {
        List<PlanView> rows = jdbcTemplate.query(
                "SELECT * FROM agent_learning_plan WHERE id = ? AND user_id = ? LIMIT 1",
                this::plan,
                id.toString(),
                userId);
        if (rows.isEmpty()) {
            throw notFound("正式学习计划不存在");
        }
        return rows.get(0);
    }

    private DraftView draft(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new DraftView(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getLong("user_id"),
                resultSet.getString("goal"),
                resultSet.getInt("minutes_per_day"),
                resultSet.getInt("version"),
                uuid(resultSet.getString("previous_draft_id")),
                list(resultSet.getString("courses_json")),
                list(resultSet.getString("routine_json")),
                list(resultSet.getString("adjustments_json")),
                resultSet.getString("status"),
                resultSet.getObject("created_at", LocalDateTime.class),
                resultSet.getObject("updated_at", LocalDateTime.class));
    }

    private PlanView plan(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new PlanView(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getString("goal"),
                resultSet.getInt("minutes_per_day"),
                resultSet.getInt("estimated_weeks"),
                resultSet.getString("status"),
                resultSet.getInt("progress_percent"),
                resultSet.getString("progress_note"),
                list(resultSet.getString("courses_json")),
                list(resultSet.getString("routine_json")),
                list(resultSet.getString("adjustments_json")),
                resultSet.getObject("created_at", LocalDateTime.class),
                resultSet.getObject("updated_at", LocalDateTime.class));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化学习计划", exception);
        }
    }

    private List<Map<String, Object>> list(String json) throws SQLException {
        try {
            return objectMapper.readValue(json, LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new SQLException("无法读取学习计划JSON", exception);
        }
    }

    private UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private ServiceException notFound(String message) {
        return new ServiceException(ResultCode.RECORD_NOT_FOUND, message);
    }
}
