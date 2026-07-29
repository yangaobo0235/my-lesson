package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.agent.model.AgentAnswer;
import com.yangaobo.ai.agent.model.AgentRoute;
import com.yangaobo.ai.agent.model.AgentSelection;
import com.yangaobo.ai.agent.model.IntentDecision;
import com.yangaobo.ai.agent.model.UserIntent;
import com.yangaobo.ai.agent.service.AgentOrchestrator;
import com.yangaobo.ai.agent.service.MyLessonReactAgentService;
import com.yangaobo.ai.approval.model.ApprovalRequestResult;
import com.yangaobo.ai.approval.service.ApprovalService;
import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.conversation.model.ConversationEventType;
import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.AiAnswer;
import com.yangaobo.ai.rag.model.Citation;
import com.yangaobo.ai.rag.model.HybridSearchResult;
import com.yangaobo.ai.rag.model.SearchHit;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.dto.CreateLearningPlanRequest;
import com.yangaobo.ai.tool.dto.NoArgsRequest;
import com.yangaobo.ai.tool.model.BusinessToolSpec;
import com.yangaobo.ai.tool.model.ToolRunContext;
import com.yangaobo.ai.tool.model.ToolResult;
import com.yangaobo.ai.tool.service.BusinessToolExecutor;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class KnowledgeAnswerService {

    public static final String NO_ANSWER =
            "当前知识库中没有足够信息回答这个问题。"
                    + "你可以换一种描述，或查看相关课程列表。";
    private static final String CAPABILITY_ANSWER = """
            你好，我是 MyLesson AI 学习助手，可以帮你完成这些事情：

            1. 课程问答：基于平台课程、文章和公告回答问题，并尽量给出引用依据。
            2. 课程推荐：根据你的目标、时间和偏好推荐合适课程。
            3. 学习计划：为你生成阶段学习计划，并支持查看和更新进度。
            4. 个人查询：查询你的资料、购物车、订单和已有学习计划。
            5. 待确认操作：加入或移除购物车这类写操作会先进入确认流程。
            6. 知识库与评测：管理员可以查看知识来源、向量索引、工具审计和评测结果。

            你可以直接告诉我学习目标，例如“每天 30 分钟提升表达能力”，也可以问“睡眠不好想低强度运动，有哪些课程适合我？”。
            """;

    private static final String SYSTEM_PROMPT = """
            你是 MyLesson 的知识库问答助手。
            只能依据用户消息中提供的“知识资料”回答，不得使用外部知识补充。
            每个事实性结论后必须使用 [1]、[2] 形式标注支持该结论的资料编号。
            不得引用不存在的编号，不得编造课程、文章、公告或链接。
            如果资料不足以回答，只能回复：
            当前知识库中没有足够信息回答这个问题。你可以换一种描述，或查看相关课程列表。
            """;

    private final HybridKnowledgeSearchService searchService;
    private final KnowledgeRelevanceEvaluator relevanceEvaluator;
    private final CitationService citationService;
    private final RagProperties properties;
    private final ChatClient chatClient;
    private final AgentOrchestrator agentOrchestrator;
    private final MyLessonReactAgentService reactAgentService;
    private final AiBusinessGateway businessGateway;
    private final BusinessToolExecutor toolExecutor;

    public KnowledgeAnswerService(
            HybridKnowledgeSearchService searchService,
            KnowledgeRelevanceEvaluator relevanceEvaluator,
            CitationService citationService,
            RagProperties properties,
            ChatClient.Builder chatClientBuilder,
            AgentOrchestrator agentOrchestrator,
            MyLessonReactAgentService reactAgentService,
            AiBusinessGateway businessGateway,
            BusinessToolExecutor toolExecutor) {
        this.searchService = searchService;
        this.relevanceEvaluator = relevanceEvaluator;
        this.citationService = citationService;
        this.properties = properties;
        this.chatClient = chatClientBuilder.build();
        this.agentOrchestrator = agentOrchestrator;
        this.reactAgentService = reactAgentService;
        this.businessGateway = businessGateway;
        this.toolExecutor = toolExecutor;
    }

    public AiAnswer answer(String question) {
        return answer(
                question,
                question,
                "",
                ignored -> {
                },
                null);
    }

    public AiAnswer answer(
            String question,
            String retrievalQuery,
            String conversationContext,
            Consumer<HybridSearchResult> retrievalObserver) {
        return answer(
                question,
                retrievalQuery,
                conversationContext,
                retrievalObserver,
                null);
    }

    public AiAnswer answer(
            String question,
            String retrievalQuery,
            String conversationContext,
            Consumer<HybridSearchResult> retrievalObserver,
            ToolRunContext toolRunContext) {
        return answer(
                question,
                retrievalQuery,
                conversationContext,
                retrievalObserver,
                toolRunContext,
                new IntentDecision(
                        UserIntent.KNOWLEDGE_QA,
                        1.0,
                        "默认知识问答路由"));
    }

    public AiAnswer answer(
            String question,
            String retrievalQuery,
            String conversationContext,
            Consumer<HybridSearchResult> retrievalObserver,
            ToolRunContext toolRunContext,
            IntentDecision intentDecision) {
        String traceId = traceId();
        if (isCapabilityQuestion(question)) {
            retrievalObserver.accept(
                    new HybridSearchResult(List.of(), 0.0, false));
            return new AiAnswer(CAPABILITY_ANSWER, List.of(), traceId);
        }
        AgentSelection selection = toolRunContext == null
                ? null
                : agentOrchestrator.select(intentDecision);
        AgentRoute route = selection == null ? null : selection.route();
        publishAgentSelected(toolRunContext, selection, intentDecision);
        publishAgentStarted(toolRunContext, selection);
        boolean agentCompleted = false;
        try {
        AiAnswer courseRecommendation =
                deterministicCourseRecommendation(question, intentDecision, traceId);
        if (courseRecommendation != null) {
            retrievalObserver.accept(
                    new HybridSearchResult(List.of(), 0.0, false));
            publishAgentCompleted(toolRunContext, selection, true);
            agentCompleted = true;
            return courseRecommendation;
        }
        AiAnswer learningPlanApproval =
                deterministicLearningPlanApproval(
                        question,
                        toolRunContext,
                        intentDecision,
                        traceId);
        if (learningPlanApproval != null) {
            retrievalObserver.accept(
                    new HybridSearchResult(List.of(), 0.0, false));
            publishAgentCompleted(toolRunContext, selection, true);
            agentCompleted = true;
            return learningPlanApproval;
        }
        AiAnswer knowledgeRebuildApproval =
                deterministicKnowledgeRebuildApproval(
                        toolRunContext,
                        intentDecision,
                        traceId);
        if (knowledgeRebuildApproval != null) {
            retrievalObserver.accept(
                    new HybridSearchResult(List.of(), 0.0, false));
            publishAgentCompleted(toolRunContext, selection, true);
            agentCompleted = true;
            return knowledgeRebuildApproval;
        }
        if (route != null
                && !route.conservative()
                && intentDecision.intent() == UserIntent.OUT_OF_SCOPE) {
            retrievalObserver.accept(
                    new HybridSearchResult(List.of(), 0.0, false));
            publishAgentCompleted(toolRunContext, selection, true);
            agentCompleted = true;
            return new AiAnswer(
                    "这个问题不属于 MyLesson 的学习或业务范围。"
                            + "你可以询问课程、知识点、订单、购物车或学习计划。",
                    List.of(),
                    traceId);
        }
        HybridSearchResult searchResult = route == null
                || route.retrievalEnabled()
                ? searchService.search(retrievalQuery)
                : new HybridSearchResult(List.of(), 0.0, false);
        retrievalObserver.accept(searchResult);
        boolean knowledgeAnswerable = relevanceEvaluator.isAnswerable(
                retrievalQuery,
                searchResult);
        if (!knowledgeAnswerable) {
            searchService.markNoAnswer(
                    searchResult,
                    searchResult.hits().isEmpty()
                            ? "NO_CANDIDATES"
                            : "BELOW_RELEVANCE_THRESHOLD");
        }
        if (!knowledgeAnswerable && toolRunContext == null) {
            return new AiAnswer(NO_ANSWER, List.of(), traceId);
        }

        List<SearchHit> contextHits = knowledgeAnswerable
                ? searchResult.hits().stream()
                        .limit(properties.getAnswerTopN())
                        .toList()
                : List.of();
        List<Citation> availableCitations =
                citationService.create(contextHits);
        String userPrompt = buildUserPrompt(
                question,
                conversationContext,
                availableCitations);
        AgentAnswer agentAnswer = null;
        String rawAnswer;
        if (toolRunContext == null) {
            rawAnswer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
        } else {
            agentAnswer = reactAgentService.answer(
                    toolRunContext.run().conversationId(),
                    userPrompt,
                    toolRunContext,
                    route,
                    selection.profile());
            rawAnswer = agentAnswer.content();
        }
        publishAgentCompleted(toolRunContext, selection, true);
        agentCompleted = true;
        CitationService.ValidatedAnswer validated =
                citationService.validate(rawAnswer, availableCitations);
        if (validated.answer().isBlank()) {
            return new AiAnswer(NO_ANSWER, List.of(), traceId);
        }
        if (toolRunContext != null && toolRunContext.hasToolCalls()) {
            List<Citation> citations = validated.citations().isEmpty()
                    && knowledgeAnswerable
                    ? availableCitations
                    : validated.citations();
            return new AiAnswer(
                    validated.answer(),
                    citations,
                    traceId);
        }
        if (agentAnswer != null
                && agentAnswer.modelCallLimitReached()) {
            return new AiAnswer(
                    validated.answer(),
                    validated.citations(),
                    traceId);
        }
        if (validated.citations().isEmpty()
                || NO_ANSWER.equals(validated.answer())) {
            return new AiAnswer(NO_ANSWER, List.of(), traceId);
        }
        return new AiAnswer(
                validated.answer(),
                validated.citations(),
                traceId);
        } catch (RuntimeException exception) {
            if (!agentCompleted) {
                publishAgentCompleted(toolRunContext, selection, false);
            }
            throw exception;
        }
    }

    private void publishAgentSelected(
            ToolRunContext toolRunContext,
            AgentSelection selection,
            IntentDecision decision) {
        if (toolRunContext == null || selection == null) {
            return;
        }
        toolRunContext.routeSelected(
                selection.profile().name(),
                selection.profile().version(),
                decision.intent().name(),
                decision.confidence(),
                selection.route().conservative());
        toolRunContext.publish(
                ConversationEventType.AGENT_SELECTED,
                Map.of(
                        "agentName", selection.profile().name(),
                        "profileName", selection.profile().name(),
                        "profileVersion", selection.profile().version(),
                        "displayName", selection.profile().displayName(),
                        "description", selection.profile().description(),
                        "intent", decision.intent().name(),
                        "conservative", selection.route().conservative()));
    }

    private void publishAgentStarted(
            ToolRunContext toolRunContext,
            AgentSelection selection) {
        if (toolRunContext == null || selection == null) {
            return;
        }
        toolRunContext.publish(
                ConversationEventType.AGENT_STARTED,
                Map.of(
                        "agentName", selection.profile().name(),
                        "profileName", selection.profile().name(),
                        "profileVersion", selection.profile().version(),
                        "displayName", selection.profile().displayName()));
    }

    private void publishAgentCompleted(
            ToolRunContext toolRunContext,
            AgentSelection selection,
            boolean success) {
        if (toolRunContext == null || selection == null) {
            return;
        }
        toolRunContext.publish(
                ConversationEventType.AGENT_COMPLETED,
                Map.of(
                        "agentName", selection.profile().name(),
                        "profileName", selection.profile().name(),
                        "profileVersion", selection.profile().version(),
                        "displayName", selection.profile().displayName(),
                        "success", success));
    }

    private String buildUserPrompt(
            String question,
            String conversationContext,
            List<Citation> citations) {
        StringBuilder prompt = new StringBuilder()
                .append("问题：")
                .append(question);
        if (conversationContext != null
                && !conversationContext.isBlank()) {
            prompt.append(
                            "\n\n对话上下文（只用于理解代词、偏好和连续话题，"
                                    + "不能作为事实依据）：\n")
                    .append(conversationContext);
        }
        prompt.append("\n\n知识资料：\n");
        if (citations.isEmpty()) {
            prompt.append("当前没有足够的知识资料；如问题属于业务数据，请使用工具。\n");
        }
        for (Citation citation : citations) {
            prompt.append('[')
                    .append(citation.index())
                    .append("] ")
                    .append(citation.title())
                    .append('\n')
                    .append(citation.excerpt())
                    .append("\n来源：")
                    .append(citation.sourceUrl())
                    .append("\n\n");
        }
        return prompt.toString();
    }

    private String traceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString()
                : traceId;
    }

    private boolean isCapabilityQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question
                .replaceAll("\\s+", "")
                .toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("你能做什么")
                || normalized.contains("能帮我做什么")
                || normalized.contains("可以做什么")
                || normalized.contains("有什么功能")
                || normalized.contains("介绍一下你")
                || normalized.contains("介绍下你")
                || normalized.contains("你是谁")
                || normalized.matches(".*你好[，,。!！]*$");
    }

    private AiAnswer deterministicCourseRecommendation(
            String question,
            IntentDecision intentDecision,
            String traceId) {
        if (intentDecision == null
                || intentDecision.intent() != UserIntent.COURSE_SEARCH) {
            return null;
        }
        Set<Long> courseIds = recommendedCourseIds(question);
        if (courseIds.isEmpty()) {
            return null;
        }

        List<CourseAiClient.CourseKnowledge> courses = new ArrayList<>();
        for (Long courseId : courseIds) {
            try {
                courses.add(businessGateway.getCourse(courseId));
            } catch (RuntimeException ignored) {
                // Keep the deterministic path best-effort and fall back to RAG.
            }
        }
        if (courses.isEmpty()) {
            return null;
        }

        List<Citation> citations = new ArrayList<>();
        StringBuilder answer = new StringBuilder()
                .append("可以。基于平台已有课程，我建议你优先考虑：\n\n");
        int index = 1;
        for (CourseAiClient.CourseKnowledge course : courses) {
            Citation citation = new Citation(
                    index,
                    "COURSE",
                    course.id().toString(),
                    course.title(),
                    "/course-server/api/v1/course/select/" + course.id(),
                    excerpt(course));
            citations.add(citation);
            answer.append(index)
                    .append(". 《")
                    .append(course.title())
                    .append("》：")
                    .append(recommendationReason(course))
                    .append(" [")
                    .append(index)
                    .append("]\n");
            index++;
        }
        answer.append("\n建议顺序：先用睡眠和放松类课程稳定状态，")
                .append("再加入低强度运动，最后补充临场稳定或表达训练。");
        return new AiAnswer(answer.toString(), List.copyOf(citations), traceId);
    }

    private Set<Long> recommendedCourseIds(String question) {
        String text = question == null ? "" : question;
        Set<Long> result = new LinkedHashSet<>();
        if (containsAny(text, "睡眠", "睡觉", "失眠", "冥想", "睡前")) {
            result.add(11L);
        }
        if (containsAny(text, "运动", "低强度", "跑步", "慢跑", "拉伸", "启动")) {
            result.add(10L);
        }
        if (containsAny(text, "呼吸", "放松", "紧张", "临场", "稳定", "自信")) {
            result.add(9L);
        }
        if (containsAny(text, "表达", "沟通", "公开分享", "汇报")) {
            result.add(7L);
        }
        if (containsAny(text, "学习", "效率", "时间", "计划")) {
            result.add(8L);
        }
        return result;
    }

    private AiAnswer deterministicLearningPlanApproval(
            String question,
            ToolRunContext toolRunContext,
            IntentDecision intentDecision,
            String traceId) {
        if (toolRunContext == null
                || intentDecision == null
                || intentDecision.intent() != UserIntent.LEARNING_PLAN
                || !isLearningPlanCreationRequest(question)) {
            return null;
        }
        Integer minutesPerDay = extractMinutesPerDay(question);
        CreateLearningPlanRequest request =
                new CreateLearningPlanRequest(
                        cleanGoal(question),
                        minutesPerDay);
        String toolName = ApprovalService.CREATE_PLAN;
        ToolResult<ApprovalRequestResult> result = toolExecutor.execute(
                new BusinessToolSpec<>(
                        toolName,
                        "根据当前登录用户明确提供的学习目标创建学习计划草案，并创建审批任务。",
                        CreateLearningPlanRequest.class,
                        true,
                        Set.of(),
                        ignored -> null),
                request,
                new ToolContext(Map.of(
                        ToolRunContext.CONTEXT_KEY,
                        toolRunContext)));
        if (!result.success()) {
            return new AiAnswer(
                    result.message() == null || result.message().isBlank()
                            ? "学习计划草案生成失败，请稍后重试。"
                            : result.message(),
                    List.of(),
                    traceId);
        }
        ApprovalRequestResult approval = result.data();
        if (approval != null) {
            return new AiAnswer(
                    "已为你生成学习计划草案："
                            + approval.reason()
                            + "。请在当前对话的确认提示，或“待确认操作”页面点击确认；确认后可到“学习计划”页面查看课程顺序、每日安排和进度。",
                    List.of(),
                    traceId);
        }
        return new AiAnswer(
                "学习计划草案已提交确认，请到“待确认操作”页面处理。",
                List.of(),
                traceId);
    }

    private AiAnswer deterministicKnowledgeRebuildApproval(
            ToolRunContext toolRunContext,
            IntentDecision intentDecision,
            String traceId) {
        if (toolRunContext == null
                || intentDecision == null
                || intentDecision.intent() != UserIntent.ADMIN_OPERATION) {
            return null;
        }
        String toolName = ApprovalService.REBUILD_INDEX;
        ToolResult<ApprovalRequestResult> result = toolExecutor.execute(
                new BusinessToolSpec<>(
                        toolName,
                        "为管理员创建知识索引重建审批任务。",
                        NoArgsRequest.class,
                        true,
                        Set.of("ADMIN"),
                        ignored -> null),
                new NoArgsRequest(),
                new ToolContext(Map.of(
                        ToolRunContext.CONTEXT_KEY,
                        toolRunContext)));
        if (!result.success()) {
            return new AiAnswer(
                    result.message() == null || result.message().isBlank()
                            ? "知识索引重建审批创建失败，请稍后重试。"
                            : result.message(),
                    List.of(),
                    traceId);
        }
        ApprovalRequestResult approval = result.data();
        return new AiAnswer(
                approval == null
                        ? "知识索引重建已提交审批，请到“待确认操作”页面处理。"
                        : "已创建知识索引重建审批："
                                + approval.reason()
                                + "。确认后系统将异步重建索引。",
                List.of(),
                traceId);
    }

    private boolean isLearningPlanCreationRequest(String question) {
        String text = question == null ? "" : question;
        if (containsAny(text, "查看", "查询", "已有", "当前", "进度")) {
            return false;
        }
        return containsAny(
                text,
                "制定",
                "生成",
                "创建",
                "安排",
                "规划",
                "做一个",
                "来一个")
                && containsAny(text, "学习计划", "学习规划", "计划");
    }

    private Integer extractMinutesPerDay(String question) {
        if (question == null) {
            return null;
        }
        Matcher matcher = Pattern
                .compile("(\\d{1,3})\\s*(分钟|min)")
                .matcher(question);
        if (!matcher.find()) {
            return null;
        }
        int minutes = Integer.parseInt(matcher.group(1));
        if (minutes < 10 || minutes > 480) {
            return null;
        }
        return minutes;
    }

    private String cleanGoal(String question) {
        String goal = question == null ? "" : question.trim();
        return goal.length() <= 500
                ? goal
                : goal.substring(0, 500);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String recommendationReason(CourseAiClient.CourseKnowledge course) {
        if (course.id() == 11L) {
            return "适合先处理睡前节律、放松和作息调整";
        }
        if (course.id() == 10L) {
            return "适合从低门槛运动开始建立身体节奏";
        }
        if (course.id() == 9L) {
            return "适合练习呼吸、放松和临场稳定";
        }
        if (course.id() == 7L) {
            return "适合提升表达结构和公开分享信心";
        }
        if (course.id() == 8L) {
            return "适合把每天的学习时间安排得更轻盈";
        }
        return "与当前学习目标匹配";
    }

    private String excerpt(CourseAiClient.CourseKnowledge course) {
        if (course.description() != null && !course.description().isBlank()) {
            return course.description();
        }
        if (course.detail() != null && !course.detail().isBlank()) {
            return course.detail();
        }
        if (course.episodeTitles() != null && !course.episodeTitles().isEmpty()) {
            return "包含章节：" + String.join(
                    "、",
                    course.episodeTitles().stream().limit(3).toList());
        }
        return course.title();
    }
}
