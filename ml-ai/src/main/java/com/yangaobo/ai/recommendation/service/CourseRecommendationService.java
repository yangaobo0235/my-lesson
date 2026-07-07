package com.yangaobo.ai.recommendation.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.OrderAiClient;
import com.yangaobo.ai.client.SaleAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.recommendation.model.CourseRecommendationRequest;
import com.yangaobo.ai.recommendation.model.CourseRecommendationResponse;
import com.yangaobo.ai.recommendation.model.CourseRecommendationResponse.RecommendationCitation;
import com.yangaobo.ai.recommendation.model.CourseRecommendationResponse.RecommendedCourse;
import com.yangaobo.ai.service.AiBusinessGateway;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CourseRecommendationService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 8;

    private final AiBusinessGateway businessGateway;

    public CourseRecommendationService(AiBusinessGateway businessGateway) {
        this.businessGateway = businessGateway;
    }

    public CourseRecommendationResponse recommend(
            CourseRecommendationRequest request) {
        String goal = request.goal().trim();
        int limit = request.limit() == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(MAX_SEARCH_LIMIT, request.limit()));
        List<CourseAiClient.CourseSummary> candidates = search(goal, limit);
        Set<Long> owned = ownedCourseIds();
        Set<Long> cart = cartCourseIds();
        UserAiClient.UserProfile profile = profile();
        List<SaleAiClient.SaleSearchHit> materials = saleMaterials(goal);

        List<RecommendedCourse> courses = new ArrayList<>();
        int priority = 1;
        for (CourseAiClient.CourseSummary candidate : candidates) {
            CourseAiClient.CourseKnowledge knowledge =
                    courseKnowledge(candidate.id());
            courses.add(toRecommendation(
                    goal,
                    candidate,
                    knowledge,
                    materials,
                    profile,
                    priority++,
                    owned.contains(candidate.id()),
                    cart.contains(candidate.id())));
        }
        courses.sort(Comparator
                .comparing(RecommendedCourse::owned)
                .thenComparing(RecommendedCourse::priority));
        return new CourseRecommendationResponse(
                goal,
                summary(goal, courses),
                List.copyOf(courses),
                "ASK_USER_CONFIRMATION");
    }

    private List<CourseAiClient.CourseSummary> search(String goal, int limit) {
        List<CourseAiClient.CourseSummary> direct =
                businessGateway.searchCourses(goal, limit);
        if (!direct.isEmpty()) {
            return direct;
        }
        List<CourseAiClient.CourseSummary> mapped =
                mappedSearch(goal, limit);
        if (!mapped.isEmpty()) {
            return mapped;
        }
        String simplified = goal
                .replaceAll(
                        "两个月|一个月|三个月|学完|学习|准备|面试|需要|哪些|课程|推荐",
                        " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (simplified.isBlank() || simplified.equals(goal)) {
            return direct;
        }
        return businessGateway.searchCourses(simplified, limit);
    }

    private List<CourseAiClient.CourseSummary> mappedSearch(
            String goal,
            int limit) {
        Map<Long, CourseAiClient.CourseSummary> result = new LinkedHashMap<>();
        for (String keyword : mappedKeywords(goal)) {
            List<CourseAiClient.CourseSummary> courses =
                    businessGateway.searchCourses(keyword, limit);
            for (CourseAiClient.CourseSummary course : courses) {
                result.putIfAbsent(course.id(), course);
                if (result.size() >= limit) {
                    return List.copyOf(result.values());
                }
            }
        }
        return List.copyOf(result.values());
    }

    private List<String> mappedKeywords(String goal) {
        List<String> keywords = new ArrayList<>();
        if (containsAny(goal, "睡眠", "睡觉", "失眠", "冥想", "睡前")) {
            keywords.add("睡眠");
        }
        if (containsAny(goal, "放松", "呼吸", "紧张", "临场", "自信")) {
            keywords.add("放松");
        }
        if (containsAny(goal, "低强度", "运动", "跑步", "慢跑", "拉伸")) {
            keywords.add("运动");
        }
        if (containsAny(goal, "表达", "沟通", "公开分享", "汇报")) {
            keywords.add("表达");
        }
        if (containsAny(goal, "学习", "效率", "时间", "计划")) {
            keywords.add("学习");
        }
        return keywords;
    }

    private RecommendedCourse toRecommendation(
            String goal,
            CourseAiClient.CourseSummary course,
            CourseAiClient.CourseKnowledge knowledge,
            List<SaleAiClient.SaleSearchHit> materials,
            UserAiClient.UserProfile profile,
            int priority,
            boolean owned,
            boolean inCart) {
        List<RecommendationCitation> citations =
                citations(course, knowledge, materials);
        return new RecommendedCourse(
                course.id(),
                course.title(),
                course.author(),
                course.category(),
                course.price(),
                course.cover(),
                reason(goal, course, profile, knowledge, owned, inCart),
                priority,
                estimatedHours(priority),
                owned,
                inCart,
                citations);
    }

    private String reason(
            String goal,
            CourseAiClient.CourseSummary course,
            UserAiClient.UserProfile profile,
            CourseAiClient.CourseKnowledge knowledge,
            boolean owned,
            boolean inCart) {
        StringBuilder reason = new StringBuilder()
                .append("匹配学习目标“")
                .append(goal)
                .append("”，可作为优先级 ")
                .append(priorityLabel(course))
                .append(" 的学习内容");
        if (knowledge != null
                && knowledge.episodeTitles() != null
                && !knowledge.episodeTitles().isEmpty()) {
            reason.append("；课程包含 ")
                    .append(Math.min(knowledge.episodeTitles().size(), 3))
                    .append(" 个可参考章节");
        }
        if (profile != null && hasText(profile.info())) {
            reason.append("；已结合你的学习资料说明");
        }
        if (owned) {
            reason.append("；你已购买，可优先复习或继续学习");
        } else if (inCart) {
            reason.append("；当前已在购物车，确认后可继续结算");
        } else {
            reason.append("；如认可推荐，可发起加入购物车确认");
        }
        return reason.toString();
    }

    private String priorityLabel(CourseAiClient.CourseSummary course) {
        if (course.category() == null || course.category().isBlank()) {
            return "靠前";
        }
        return "靠前的“" + course.category() + "”方向";
    }

    private String summary(String goal, List<RecommendedCourse> courses) {
        if (courses.isEmpty()) {
            return "当前课程库没有检索到足够匹配“" + goal
                    + "”的课程，建议换一个更具体的关键词。";
        }
        return "根据“" + goal + "”检索到 " + courses.size()
                + " 门候选课程，建议按优先级学习；已购买课程会标记为已拥有。";
    }

    private List<RecommendationCitation> citations(
            CourseAiClient.CourseSummary course,
            CourseAiClient.CourseKnowledge knowledge,
            List<SaleAiClient.SaleSearchHit> materials) {
        List<RecommendationCitation> citations = new ArrayList<>();
        citations.add(new RecommendationCitation(
                "COURSE",
                String.valueOf(course.id()),
                course.title(),
                courseSnippet(course, knowledge)));
        for (SaleAiClient.SaleSearchHit material : materials) {
            if (material == null || !hasText(material.sourceType())
                    || material.id() == null) {
                continue;
            }
            citations.add(new RecommendationCitation(
                    material.sourceType(),
                    String.valueOf(material.id()),
                    hasText(material.title()) ? material.title()
                            : material.sourceType() + "-" + material.id(),
                    hasText(material.snippet())
                            ? material.snippet()
                            : "与学习目标相关的站内资料"));
            if (citations.size() >= 3) {
                break;
            }
        }
        return List.copyOf(citations);
    }

    private String courseSnippet(
            CourseAiClient.CourseSummary course,
            CourseAiClient.CourseKnowledge knowledge) {
        if (knowledge != null && hasText(knowledge.description())) {
            return trim(knowledge.description());
        }
        if (knowledge != null && knowledge.episodeTitles() != null
                && !knowledge.episodeTitles().isEmpty()) {
            return "包含章节：" + String.join(
                    "、",
                    knowledge.episodeTitles().stream()
                            .limit(3)
                            .toList());
        }
        if (course.category() == null || course.category().isBlank()) {
            return "课程与学习目标相关，可作为阶段学习内容";
        }
        return "课程分类为 " + course.category()
                + "，适合纳入该目标的学习路径";
    }

    private int estimatedHours(int priority) {
        return Math.max(4, 14 - priority * 2);
    }

    private Set<Long> ownedCourseIds() {
        Set<Long> result = new HashSet<>();
        List<OrderAiClient.OrderSummary> orders;
        try {
            orders = businessGateway.getMyOrders(20);
        } catch (RuntimeException exception) {
            return Set.of();
        }
        if (orders == null) {
            return Set.of();
        }
        for (OrderAiClient.OrderSummary order : orders) {
            if (order.items() == null) {
                continue;
            }
            for (OrderAiClient.OrderItem item : order.items()) {
                if (item.courseId() != null) {
                    result.add(item.courseId());
                }
            }
        }
        return result;
    }

    private Set<Long> cartCourseIds() {
        Set<Long> result = new HashSet<>();
        List<OrderAiClient.CartItem> items;
        try {
            items = businessGateway.getMyCart();
        } catch (RuntimeException exception) {
            return Set.of();
        }
        if (items == null) {
            return Set.of();
        }
        for (OrderAiClient.CartItem item : items) {
            if (item.courseId() != null) {
                result.add(item.courseId());
            }
        }
        return result;
    }

    private UserAiClient.UserProfile profile() {
        try {
            return businessGateway.getMyProfile();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private CourseAiClient.CourseKnowledge courseKnowledge(Long courseId) {
        if (courseId == null) {
            return null;
        }
        try {
            return businessGateway.getCourse(courseId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private List<SaleAiClient.SaleSearchHit> saleMaterials(String goal) {
        try {
            List<SaleAiClient.SaleSearchHit> hits =
                    businessGateway.searchSale(goal, 3);
            return hits == null ? List.of() : hits;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String trim(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120
                ? normalized
                : normalized.substring(0, 120);
    }
}
