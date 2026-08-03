package com.yangaobo.ai.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.InternalAiResponse;
import com.yangaobo.ai.client.OrderAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.exception.BusinessOperationException;
import com.yangaobo.ai.exception.DownstreamServiceException;
import com.yangaobo.ai.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Service
public class AiBusinessGateway {

    private static final Logger log = LoggerFactory.getLogger(AiBusinessGateway.class);

    private final CourseAiClient courseClient;
    private final OrderAiClient orderClient;
    private final UserAiClient userClient;

    public AiBusinessGateway(
            CourseAiClient courseClient,
            OrderAiClient orderClient,
            UserAiClient userClient) {
        this.courseClient = courseClient;
        this.orderClient = orderClient;
        this.userClient = userClient;
    }

    public CourseAiClient.CourseKnowledge getCourse(Long courseId) {
        return call("course", () -> courseClient.getCourse(courseId));
    }

    public List<CourseAiClient.CourseSummary> searchCourses(String keyword, int limit) {
        String safeKeyword = safeCourseKeyword(keyword);
        if (safeKeyword.isBlank()) {
            return List.of();
        }
        return call("course", () -> courseClient.search(safeKeyword, limit));
    }

    public CourseAiClient.CursorPage<CourseAiClient.CourseKnowledge> courseKnowledge(
            Long cursor,
            int size) {
        return call("course", () -> courseClient.knowledge(cursor, size));
    }

    public List<CourseAiClient.Category> categories() {
        return call("course", courseClient::categories);
    }

    public List<OrderAiClient.OrderSummary> getMyOrders(int limit) {
        Long userId = currentUserId();
        return call("order", () -> orderClient.orders(userId, limit));
    }

    public List<OrderAiClient.CartItem> getMyCart() {
        Long userId = currentUserId();
        return call("order", () -> orderClient.cart(userId));
    }

    public UserAiClient.UserProfile getMyProfile() {
        Long userId = currentUserId();
        return call("user", () -> userClient.profile(userId));
    }

    public List<UserAiClient.UserRole> getMyRoles() {
        Long userId = currentUserId();
        return call("user", () -> userClient.roles(userId));
    }

    private Long currentUserId() {
        return UserContext.requireUser().id();
    }

    private <T> T call(String serviceName, Supplier<InternalAiResponse<T>> request) {
        try {
            InternalAiResponse<T> response = request.get();
            if (response == null) {
                log.warn(
                        "Internal AI call to {} returned no response",
                        serviceName);
                throw unavailable(serviceName);
            }
            if (!response.successful()) {
                log.warn(
                        "Internal AI call to {} failed with business code {}",
                        serviceName,
                        response.code());
                throw new BusinessOperationException(
                        response.code() == null
                                ? "UNKNOWN"
                                : String.valueOf(response.code()),
                        publicMessage(response.message()));
            }
            return response.data();
        } catch (BusinessOperationException exception) {
            throw exception;
        } catch (DownstreamServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                    "Internal AI call to {} failed: {}",
                    serviceName,
                    exception.getClass().getSimpleName());
            throw unavailable(serviceName);
        }
    }

    private DownstreamServiceException unavailable(String serviceName) {
        return new DownstreamServiceException(
                "The " + serviceName + " service is temporarily unavailable");
    }

    private String publicMessage(String message) {
        if (message == null || message.isBlank()) {
            return "业务操作未完成，请检查请求后重试";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120
                ? normalized
                : normalized.substring(0, 120);
    }

    private String safeCourseKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String normalized = keyword.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 42) {
            return normalized;
        }
        return normalized.substring(0, 42).trim();
    }
}
