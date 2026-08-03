package com.yangaobo.ai.workflow.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.yangaobo.ai.agent.config.AgentProperties;
import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.conversation.model.ConversationRun;
import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.security.AuthenticatedUser;
import com.yangaobo.ai.security.UserContext;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.dto.CreateLearningPlanRequest;
import com.yangaobo.ai.tool.model.ToolRunContext;
import com.yangaobo.ai.tool.repository.LearningPlanRepository;
import com.yangaobo.ai.workflow.model.LearningPlanDraft;
import com.yangaobo.ai.workflow.model.LearningPlanDraftRecord;
import com.yangaobo.ai.workflow.model.LearningPlanAdjustmentRequest;
import com.yangaobo.ai.workflow.model.LearningPlanReview;
import com.yangaobo.ai.workflow.model.LearningPlanState;
import com.yangaobo.ai.workflow.repository.LearningPlanDraftRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningPlanWorkflowServiceTest {

    private AiBusinessGateway gateway;
    private LearningPlanDraftGenerator generator;
    private LearningPlanDraftRepository draftRepository;
    private LearningPlanRepository planRepository;
    private MemorySaver checkpointSaver;
    private LearningPlanWorkflowService service;

    @BeforeEach
    void setUp() {
        gateway = mock(AiBusinessGateway.class);
        generator = mock(LearningPlanDraftGenerator.class);
        draftRepository = mock(LearningPlanDraftRepository.class);
        planRepository = mock(LearningPlanRepository.class);
        checkpointSaver = MemorySaver.builder().build();
        service = new LearningPlanWorkflowService(
                gateway,
                generator,
                new LearningPlanValidator(),
                draftRepository,
                planRepository,
                checkpointSaver);
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
    void shouldRepairHallucinatedCourseWithDeterministicFallback() {
        UUID runId = UUID.randomUUID();
        CourseAiClient.CourseSummary candidate = course(7L);
        when(gateway.getMyProfile()).thenReturn(profile());
        when(gateway.searchCourses("Java", 6))
                .thenReturn(List.of(candidate));
        when(gateway.getCourse(7L)).thenReturn(
                new CourseAiClient.CourseKnowledge(
                        7L,
                        "Java",
                        "老师",
                        "编程",
                        "",
                        "",
                        List.of(),
                        LocalDateTime.now()));
        when(generator.generate(
                eq("Java"),
                eq(30),
                any(),
                any()))
                .thenReturn(new LearningPlanDraft(
                        14,
                        List.of(new LearningPlanDraft.DraftCourse(
                                1,
                                999L,
                                "虚构课程")),
                        List.of(new LearningPlanDraft.DraftRoutine(
                                "学习",
                                30)),
                        "test"));
        when(generator.repair(
                eq("Java"),
                eq(30),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(new LearningPlanDraft(
                        14,
                        List.of(new LearningPlanDraft.DraftCourse(
                                1, 999L, "仍是虚构课程")),
                        List.of(new LearningPlanDraft.DraftRoutine(
                                "学习", 30)),
                        "test"));
        when(generator.deterministicFallback(
                eq("Java"), eq(30), any()))
                .thenReturn(new LearningPlanDraft(
                        14,
                        List.of(new LearningPlanDraft.DraftCourse(
                                1, 7L, "学习 Java")),
                        List.of(new LearningPlanDraft.DraftRoutine(
                                "学习", 30)),
                        "fallback"));
        AtomicReference<LearningPlanDraftRecord> saved =
                new AtomicReference<>();
        when(draftRepository.save(eq(runId), any()))
                .thenAnswer(invocation -> {
                    LearningPlanState state = invocation.getArgument(1);
                    LearningPlanDraftRecord record =
                            new LearningPlanDraftRecord(
                            UUID.randomUUID(),
                            runId,
                            state,
                            null,
                            null);
                    saved.set(record);
                    return record;
                });
        when(draftRepository.findByRun(runId))
                .thenAnswer(invocation ->
                        java.util.Optional.ofNullable(saved.get()));

        LearningPlanDraftRecord result = service.prepare(
                runId,
                new CreateLearningPlanRequest("Java", 30));

        verify(draftRepository).save(eq(runId), any());
        assertThat(result.state().status()).isEqualTo("WAITING_CONFIRMATION");
        assertThat(result.state().terminationReason())
                .isEqualTo("DETERMINISTIC_FALLBACK");
        assertThat(result.state().repairAttempts()).isEqualTo(2);
        assertThat(result.state().draft().courses())
                .extracting(LearningPlanDraft.DraftCourse::courseId)
                .containsExactly(7L);
        assertThat(checkpointSaver.list(RunnableConfig.builder()
                .threadId(runId.toString())
                .build())).isNotEmpty();
    }

    @Test
    void shouldRewriteSearchOnceThenGenerateReviewedDraft() {
        PlanDesignerAgent designer = mock(PlanDesignerAgent.class);
        LearningPlanQueryRewriter rewriter =
                mock(LearningPlanQueryRewriter.class);
        LearningPlanReviewService reviewer =
                mock(LearningPlanReviewService.class);
        service = fullWorkflow(
                designer,
                rewriter,
                reviewer,
                mock(LearningPlanRepairService.class));
        UUID runId = UUID.randomUUID();
        CourseAiClient.CourseSummary candidate = course(7L);
        when(gateway.getMyProfile()).thenReturn(profile());
        when(gateway.searchCourses("Java", 6)).thenReturn(List.of());
        when(rewriter.rewrite("Java", profile())).thenReturn("Spring");
        when(gateway.searchCourses("Spring", 6))
                .thenReturn(List.of(candidate));
        stubVerifiedCourse(candidate);
        when(designer.design(eq("Java"), eq(30), any(), any()))
                .thenReturn(validDraft(7L));
        when(reviewer.review(eq("Java"), eq(30), any(), any(), any()))
                .thenReturn(acceptedReview());
        stubDraftSave();

        LearningPlanDraftRecord result = service.prepare(
                runId,
                new CreateLearningPlanRequest("Java", 30));

        assertThat(result.state().searchAttempts()).isEqualTo(1);
        assertThat(result.state().reviewAttempts()).isEqualTo(1);
        assertThat(result.state().status()).isEqualTo("WAITING_CONFIRMATION");
        verify(rewriter).rewrite("Java", profile());
    }

    @Test
    void shouldHonorExplicitPlanDaysFromUserGoal() {
        UUID runId = UUID.randomUUID();
        CourseAiClient.CourseSummary candidate = course(7L);
        when(gateway.getMyProfile()).thenReturn(profile());
        when(gateway.searchCourses("7天学习Java", 6))
                .thenReturn(List.of(candidate));
        stubVerifiedCourse(candidate);
        when(generator.generate(
                eq("7天学习Java"), eq(30), any(), any()))
                .thenReturn(validDraft(7L));
        stubDraftSave();

        LearningPlanDraftRecord result = service.prepare(
                runId,
                new CreateLearningPlanRequest("7天学习Java", 30));

        assertThat(result.state().draft().planDays()).isEqualTo(7);
        assertThat(result.state().status()).isEqualTo("WAITING_CONFIRMATION");
    }

    @Test
    void shouldFallbackAfterSingleRewriteStillHasNoCandidates() {
        PlanDesignerAgent designer = mock(PlanDesignerAgent.class);
        LearningPlanQueryRewriter rewriter =
                mock(LearningPlanQueryRewriter.class);
        service = fullWorkflow(
                designer,
                rewriter,
                mock(LearningPlanReviewService.class),
                mock(LearningPlanRepairService.class));
        UUID runId = UUID.randomUUID();
        when(gateway.getMyProfile()).thenReturn(profile());
        when(gateway.searchCourses(anyString(), anyInt()))
                .thenReturn(List.of());
        when(rewriter.rewrite("Java", profile())).thenReturn("Spring");
        when(designer.deterministicFallback("Java", 30, List.of()))
                .thenReturn(emptyDraft());
        stubDraftSave();

        LearningPlanDraftRecord result = service.prepare(
                runId,
                new CreateLearningPlanRequest("Java", 30));

        assertThat(result.state().searchAttempts()).isEqualTo(1);
        assertThat(result.state().terminationReason())
                .isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.state().status()).isEqualTo("INSUFFICIENT_DATA");
        verify(rewriter, times(1)).rewrite("Java", profile());
    }

    @Test
    void shouldUseRunContextIdentityAcrossGraphThreads() {
        UUID runId = UUID.randomUUID();
        when(gateway.getMyProfile()).thenReturn(profile());
        when(gateway.searchCourses(anyString(), anyInt()))
                .thenReturn(List.of());
        when(generator.deterministicFallback(
                eq("Java"), eq(30), eq(List.of())))
                .thenReturn(emptyDraft());
        stubDraftSave();
        ToolRunContext runContext = new ToolRunContext(
                new AuthenticatedUser(41L, "alice", List.of("student")),
                new ConversationRun(
                        runId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "RUNNING",
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID().toString(),
                        null),
                (type, data) -> { });
        UserContext.clear();

        LearningPlanDraftRecord result = service.prepare(
                runId,
                new CreateLearningPlanRequest("Java", 30),
                runContext);

        assertThat(result.state().userId()).isEqualTo(41L);
        assertThat(result.state().status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(UserContext.get()).isNull();
    }

    @Test
    void shouldRepairReviewerIssueAndAcceptSecondReview() {
        PlanDesignerAgent designer = mock(PlanDesignerAgent.class);
        LearningPlanReviewService reviewer =
                mock(LearningPlanReviewService.class);
        LearningPlanRepairService repair =
                mock(LearningPlanRepairService.class);
        service = fullWorkflow(
                designer,
                mock(LearningPlanQueryRewriter.class),
                reviewer,
                repair);
        UUID runId = UUID.randomUUID();
        CourseAiClient.CourseSummary candidate = course(7L);
        when(gateway.getMyProfile()).thenReturn(profile());
        when(gateway.searchCourses("Java", 6))
                .thenReturn(List.of(candidate));
        stubVerifiedCourse(candidate);
        when(designer.design(eq("Java"), eq(30), any(), any()))
                .thenReturn(validDraft(7L));
        LearningPlanReview rejected = new LearningPlanReview(
                false,
                List.of(new LearningPlanReview.Issue(
                        "PACE", "节奏需要调整", List.of(7L), List.of(1))),
                "缩短连续学习时间");
        when(reviewer.review(eq("Java"), eq(30), any(), any(), any()))
                .thenReturn(rejected, acceptedReview());
        when(repair.repair(eq("Java"), eq(30), any(), any(), any(),
                any(), any())).thenReturn(validDraft(7L));
        stubDraftSave();

        LearningPlanDraftRecord result = service.prepare(
                runId,
                new CreateLearningPlanRequest("Java", 30));

        assertThat(result.state().repairAttempts()).isEqualTo(1);
        assertThat(result.state().reviewAttempts()).isEqualTo(2);
        assertThat(result.state().reviewResult().accepted()).isTrue();
        assertThat(result.state().status()).isEqualTo("WAITING_CONFIRMATION");
    }

    @Test
    void shouldCreateVersionTwoFromUserAdjustment() {
        PlanDesignerAgent designer = mock(PlanDesignerAgent.class);
        LearningPlanReviewService reviewer =
                mock(LearningPlanReviewService.class);
        service = fullWorkflow(
                designer,
                mock(LearningPlanQueryRewriter.class),
                reviewer,
                mock(LearningPlanRepairService.class));
        UUID previousId = UUID.randomUUID();
        UUID adjustmentRunId = UUID.randomUUID();
        LearningPlanState previousState = state(
                List.of(course(7L)), validDraft(7L), "WAITING_CONFIRMATION");
        LearningPlanDraftRecord previous = new LearningPlanDraftRecord(
                previousId, UUID.randomUUID(), previousState, null, null);
        when(draftRepository.findOwned(previousId, 41L))
                .thenReturn(java.util.Optional.of(previous));
        when(draftRepository.createAdjustmentRun(eq(41L), any()))
                .thenReturn(adjustmentRunId);
        when(draftRepository.findByRun(adjustmentRunId))
                .thenReturn(java.util.Optional.empty());
        when(draftRepository.updateStatus(
                previousId,
                41L,
                "WAITING_CONFIRMATION",
                "SUPERSEDED"))
                .thenReturn(true);
        when(gateway.getMyProfile()).thenReturn(profile());
        when(gateway.searchCourses(anyString(), eq(6)))
                .thenReturn(List.of(course(7L)));
        stubVerifiedCourse(course(7L));
        when(designer.design(anyString(), eq(30), any(), any()))
                .thenReturn(validDraft(7L));
        when(reviewer.review(anyString(), eq(30), any(), any(), any()))
                .thenReturn(acceptedReview());
        stubDraftSave();
        UUID requestId = UUID.randomUUID();

        LearningPlanDraftRecord result = service.adjust(
                previousId,
                new LearningPlanAdjustmentRequest("减少每日任务", requestId));

        assertThat(result.state().version()).isEqualTo(2);
        assertThat(result.state().previousDraftId()).isEqualTo(previousId);
        assertThat(result.state().adjustmentRequest()).isEqualTo("减少每日任务");
        verify(draftRepository).updateStatus(
                previousId,
                41L,
                "WAITING_CONFIRMATION",
                "SUPERSEDED");
        verify(draftRepository).completeAdjustmentRun(
                adjustmentRunId, result.state());
    }

    @Test
    void shouldRejectRepeatedDraftConfirmationWithCas() {
        UUID draftId = UUID.randomUUID();
        CourseAiClient.CourseSummary candidate = course(7L);
        LearningPlanState state = state(
                List.of(candidate), validDraft(7L), "WAITING_CONFIRMATION");
        LearningPlanDraftRecord record = new LearningPlanDraftRecord(
                draftId, UUID.randomUUID(), state, null, null);
        when(draftRepository.findOwned(draftId, 41L))
                .thenReturn(java.util.Optional.of(record));
        stubVerifiedCourse(candidate);
        when(draftRepository.updateStatus(
                draftId, 41L, "WAITING_CONFIRMATION", "CONFIRMING"))
                .thenReturn(true, false);
        when(draftRepository.updateStatus(
                draftId, 41L, "CONFIRMING", "CONFIRMED"))
                .thenReturn(true);
        when(planRepository.insert(eq(41L), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        service.confirm(draftId);

        assertThatThrownBy(() -> service.confirm(draftId))
                .isInstanceOf(BusinessOperationException.class)
                .hasMessageContaining("状态已变化");
        verify(planRepository, times(1)).insert(eq(41L), any());
    }

    private LearningPlanWorkflowService fullWorkflow(
            PlanDesignerAgent designer,
            LearningPlanQueryRewriter rewriter,
            LearningPlanReviewService reviewer,
            LearningPlanRepairService repair) {
        return new LearningPlanWorkflowService(
                gateway,
                generator,
                designer,
                new CandidateQualityGate(),
                rewriter,
                reviewer,
                repair,
                new AgentProperties(),
                new LearningPlanValidator(),
                draftRepository,
                planRepository,
                checkpointSaver);
    }

    private void stubDraftSave() {
        AtomicReference<LearningPlanDraftRecord> saved =
                new AtomicReference<>();
        when(draftRepository.save(any(), any()))
                .thenAnswer(invocation -> {
                    UUID runId = invocation.getArgument(0);
                    LearningPlanState state = invocation.getArgument(1);
                    LearningPlanDraftRecord record =
                            new LearningPlanDraftRecord(
                            UUID.randomUUID(), runId, state, null, null);
                    saved.set(record);
                    return record;
                });
        when(draftRepository.findByRun(any()))
                .thenAnswer(invocation ->
                        java.util.Optional.ofNullable(saved.get()));
    }

    private void stubVerifiedCourse(CourseAiClient.CourseSummary candidate) {
        when(gateway.getCourse(candidate.id())).thenReturn(
                new CourseAiClient.CourseKnowledge(
                        candidate.id(), candidate.title(), candidate.author(),
                        candidate.category(), "", "", List.of(),
                        LocalDateTime.now()));
    }

    private LearningPlanState state(
            List<CourseAiClient.CourseSummary> candidates,
            LearningPlanDraft draft,
            String status) {
        return new LearningPlanState(
                41L, "Java", 30, candidates, draft, List.of(), status);
    }

    private LearningPlanReview acceptedReview() {
        return new LearningPlanReview(true, List.of(), "");
    }

    private LearningPlanDraft validDraft(Long courseId) {
        return new LearningPlanDraft(
                14,
                List.of(new LearningPlanDraft.DraftCourse(
                        1, courseId, "学习 Java")),
                List.of(new LearningPlanDraft.DraftRoutine("学习", 30)),
                "计划草案");
    }

    private LearningPlanDraft emptyDraft() {
        return new LearningPlanDraft(
                7,
                List.of(),
                List.of(new LearningPlanDraft.DraftRoutine("复习", 30)),
                "资料不足");
    }

    private CourseAiClient.CourseSummary course(Long id) {
        return new CourseAiClient.CourseSummary(
                id,
                "Java",
                "老师",
                "编程",
                0.0,
                "",
                LocalDateTime.now());
    }

    private UserAiClient.UserProfile profile() {
        return new UserAiClient.UserProfile(
                41L,
                "alice",
                "Alice",
                null,
                null,
                null,
                null,
                null,
                null,
                20,
                "入门");
    }
}
