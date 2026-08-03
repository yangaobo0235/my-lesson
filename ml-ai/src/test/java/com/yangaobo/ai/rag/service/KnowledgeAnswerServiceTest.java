package com.yangaobo.ai.rag.service;

import com.yangaobo.ai.agent.model.AgentAnswer;
import com.yangaobo.ai.agent.model.AgentProfile;
import com.yangaobo.ai.agent.model.AgentRoute;
import com.yangaobo.ai.agent.model.AgentSelection;
import com.yangaobo.ai.agent.model.IntentDecision;
import com.yangaobo.ai.agent.model.UserIntent;
import com.yangaobo.ai.agent.service.AgentOrchestrator;
import com.yangaobo.ai.agent.service.MyLessonReactAgentService;
import com.yangaobo.ai.conversation.model.ConversationRun;
import com.yangaobo.ai.rag.config.RagProperties;
import com.yangaobo.ai.rag.model.AiAnswer;
import com.yangaobo.ai.rag.model.HybridSearchResult;
import com.yangaobo.ai.rag.model.SearchHit;
import com.yangaobo.ai.security.AuthenticatedUser;
import com.yangaobo.ai.tool.model.ToolRunContext;
import com.yangaobo.ai.workflow.service.LearningPlanWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeAnswerServiceTest {

    @Test
    void shouldKeepRetrievedCitationsWhenAgentAlsoCallsTool() {
        HybridKnowledgeSearchService searchService =
                mock(HybridKnowledgeSearchService.class);
        KnowledgeRelevanceEvaluator relevanceEvaluator =
                mock(KnowledgeRelevanceEvaluator.class);
        AgentOrchestrator agentOrchestrator =
                mock(AgentOrchestrator.class);
        MyLessonReactAgentService agentService =
                mock(MyLessonReactAgentService.class);
        RagProperties properties = new RagProperties();
        SearchHit hit = new SearchHit(
                "COURSE",
                "1",
                "把日常拍成电影",
                "课程内容简介",
                "http://localhost/course/1",
                0.9);
        HybridSearchResult searchResult =
                new HybridSearchResult(List.of(hit), 0.9, true);
        when(searchService.search(any()))
                .thenReturn(searchResult);
        when(relevanceEvaluator.isAnswerable(any(), any()))
                .thenReturn(true);
        AgentRoute route = new AgentRoute(
                true,
                false,
                Set.of("get_course_detail"));
        when(agentOrchestrator.select(any()))
                .thenReturn(new AgentSelection(
                        new AgentProfile(
                                "course_recommendation_agent",
                                "课程推荐 Agent",
                                "课程推荐",
                                ""),
                        route));
        when(agentService.answer(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ToolRunContext context = invocation.getArgument(2);
                    context.toolCalled();
                    return new AgentAnswer(
                            "课程适合摄影新手。",
                            false);
                });
        KnowledgeAnswerService service = new KnowledgeAnswerService(
                searchService,
                relevanceEvaluator,
                new CitationService(properties),
                properties,
                mock(ChatClient.Builder.class),
                agentOrchestrator,
                agentService,
                mock(LearningPlanWorkflowService.class));
        UUID conversationId = UUID.randomUUID();
        ToolRunContext context = new ToolRunContext(
                new AuthenticatedUser(
                        1L,
                        "admin",
                        List.of("ADMIN")),
                new ConversationRun(
                        UUID.randomUUID(),
                        conversationId,
                        UUID.randomUUID(),
                        "RUNNING",
                        UUID.randomUUID(),
                        null,
                        "trace-1",
                        null),
                (type, data) -> {
                });

        AiAnswer answer = service.answer(
                "这门课程适合谁？",
                "把日常拍成电影",
                "",
                ignored -> {
                },
                context,
                new IntentDecision(
                        UserIntent.COURSE_SEARCH,
                        0.95,
                        "课程查询"));

        assertThat(answer.answer()).isEqualTo("课程适合摄影新手。");
        assertThat(answer.citations())
                .extracting(citation -> citation.sourceId())
                .containsExactly("1");
    }
}
