package com.yangaobo.ai.service;

import com.yangaobo.ai.client.CourseAiClient;
import com.yangaobo.ai.client.InternalAiResponse;
import com.yangaobo.ai.client.OrderAiClient;
import com.yangaobo.ai.client.UserAiClient;
import com.yangaobo.ai.exception.DownstreamServiceException;
import com.yangaobo.ai.security.AuthenticatedUser;
import com.yangaobo.ai.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = AiBusinessGateway.class,
        properties = {
                "spring.cloud.bootstrap.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.config.import="
        })
class AiBusinessGatewayTest {

    @Autowired
    private AiBusinessGateway gateway;

    @MockitoBean
    private CourseAiClient courseClient;

    @MockitoBean
    private OrderAiClient orderClient;

    @MockitoBean
    private UserAiClient userClient;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void userBoundCallsAlwaysUseAuthenticatedUserId() {
        UserContext.set(new AuthenticatedUser(41L, "alice", List.of("student")));
        when(orderClient.orders(41L, 20))
                .thenReturn(new InternalAiResponse<>(1000, "ok", "ok", List.of()));

        assertEquals(List.of(), gateway.getMyOrders(20));
        verify(orderClient).orders(41L, 20);
    }

    @Test
    void downstreamFailureDoesNotExposeOriginalExceptionMessage() {
        UserContext.set(new AuthenticatedUser(41L, "alice", List.of("student")));
        when(orderClient.cart(41L))
                .thenThrow(new RuntimeException("Feign stack and internal address"));

        DownstreamServiceException exception = assertThrows(
                DownstreamServiceException.class,
                gateway::getMyCart);

        assertFalse(exception.getMessage().contains("Feign"));
        assertFalse(exception.getMessage().contains("internal address"));
    }

}
