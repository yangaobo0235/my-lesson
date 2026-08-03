package com.yangaobo.ai.recommendation.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.OrderAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.recommendation.model.CourseRecommendationRequest;
import com.yangaobo.ai.recommendation.model.CourseRecommendationResponse;
import com.yangaobo.ai.service.AiBusinessGateway;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseRecommendationServiceTest {

    private final AiBusinessGateway gateway = mock(AiBusinessGateway.class);
    private final CourseRecommendationService service =
            new CourseRecommendationService(gateway);

    @Test
    void shouldReturnStructuredRecommendationsWithUserStateAndCitations() {
        when(gateway.searchCourses("Java 后端面试", 5)).thenReturn(List.of(
                new CourseAiClient.CourseSummary(
                        1L,
                        "Spring Boot 实战",
                        "林老师",
                        "Java 后端",
                        99D,
                        "cover-1",
                        LocalDateTime.now()),
                new CourseAiClient.CourseSummary(
                        2L,
                        "Redis 与 MQ 面试专题",
                        "周老师",
                        "中间件",
                        129D,
                        "cover-2",
                        LocalDateTime.now())));
        when(gateway.getMyOrders(20)).thenReturn(List.of(
                new OrderAiClient.OrderSummary(
                        11L,
                        "ORDER-1",
                        99D,
                        99D,
                        1,
                        1,
                        null,
                        LocalDateTime.now(),
                        List.of(new OrderAiClient.OrderItem(
                                1L,
                                "Spring Boot 实战",
                                "cover-1",
                                99D)))));
        when(gateway.getMyCart()).thenReturn(List.of(
                new OrderAiClient.CartItem(
                        21L,
                        2L,
                        "Redis 与 MQ 面试专题",
                        "cover-2",
                        129D,
                        LocalDateTime.now())));

        CourseRecommendationResponse response = service.recommend(
                new CourseRecommendationRequest("Java 后端面试", 5));

        assertThat(response.goal()).isEqualTo("Java 后端面试");
        assertThat(response.nextAction()).isEqualTo("REVIEW_RECOMMENDATIONS");
        assertThat(response.recommendedCourses()).hasSize(2);
        assertThat(response.recommendedCourses().get(0).owned()).isFalse();
        assertThat(response.recommendedCourses().get(0).inCart()).isTrue();
        assertThat(response.recommendedCourses().get(1).owned()).isTrue();
        assertThat(response.recommendedCourses())
                .allSatisfy(course -> assertThat(course.citations())
                        .isNotEmpty()
                        .allSatisfy(citation ->
                                assertThat(citation.sourceType())
                                        .isEqualTo("COURSE")));
    }

    @Test
    void shouldUseOnlyCourseKnowledgeAndProfileContext() {
        when(gateway.searchCourses("微服务学习", 3)).thenReturn(List.of(
                new CourseAiClient.CourseSummary(
                        3L,
                        "Spring Cloud 微服务",
                        "陈老师",
                        "微服务",
                        159D,
                        "cover-3",
                        LocalDateTime.now())));
        when(gateway.getMyOrders(20)).thenReturn(List.of());
        when(gateway.getMyCart()).thenReturn(List.of());
        when(gateway.getMyProfile()).thenReturn(
                new UserAiClient.UserProfile(
                        41L,
                        "alice",
                        "Alice",
                        "alice@example.com",
                        "浙江",
                        "avatar",
                        "射手座",
                        "138****0000",
                        2,
                        22,
                        "想系统学习 Java 后端"));
        when(gateway.getCourse(3L)).thenReturn(
                new CourseAiClient.CourseKnowledge(
                        3L,
                        "Spring Cloud 微服务",
                        "陈老师",
                        "微服务",
                        "覆盖服务注册、配置中心和远程调用",
                        "detail",
                        List.of("Nacos 注册发现", "OpenFeign 调用"),
                        LocalDateTime.now()));
        CourseRecommendationResponse response = service.recommend(
                new CourseRecommendationRequest("微服务学习", 3));

        CourseRecommendationResponse.RecommendedCourse course =
                response.recommendedCourses().get(0);
        assertThat(course.reason()).contains("已结合你的学习资料说明");
        assertThat(course.citations())
                .extracting(
                        CourseRecommendationResponse
                                .RecommendationCitation::sourceType)
                .containsExactly("COURSE");
        assertThat(course.citations().get(0).snippet())
                .contains("服务注册");
    }
}
