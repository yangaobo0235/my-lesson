package com.yangaobo.component;

import com.yangaobo.dto.OrderMessage;
import com.yangaobo.feign.UserFeign;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(
        named = "RUN_EXTERNAL_INTEGRATION_TESTS", matches = "true")
class OrderMessageListenerExternalIntegrationTest {

    private JdbcTemplate jdbcTemplate;
    private OrderMessageListener listener;
    private DriverManagerDataSource dataSource;
    private final List<UUID> requestIds = new ArrayList<>();

    @BeforeEach
    void connectToMySql() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(environment(
                "ORDER_DATASOURCE_URL",
                "jdbc:mysql://127.0.0.1:3306/ml_oms"
                        + "?serverTimezone=Asia/Shanghai"
                        + "&useUnicode=true&characterEncoding=utf-8"));
        dataSource.setUsername(environment("MYSQL_USERNAME", "root"));
        dataSource.setPassword(environment("MYSQL_PASSWORD", "root"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        listener = new OrderMessageListener();
        ReflectionTestUtils.setField(listener, "jdbcTemplate", jdbcTemplate);
    }

    @AfterEach
    void cleanUp() {
        for (UUID requestId : requestIds) {
            jdbcTemplate.update(
                    "DELETE FROM seckill_order_consume WHERE request_id = ?",
                    requestId.toString());
        }
    }

    @Test
    void shouldReserveOnlyOneConsumerForConcurrentDuplicateMessages()
            throws Exception {
        int deliveries = 1_000;
        UUID requestId = UUID.randomUUID();
        requestIds.add(requestId);
        OrderMessage message = message(requestId, requestId);

        List<Boolean> results = runConcurrently(deliveries,
                ignored -> ReflectionTestUtils.invokeMethod(
                        listener, "reserve", message));

        assertThat(results).containsExactlyInAnyOrderElementsOf(
                expectedResults(deliveries));
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seckill_order_consume"
                        + " WHERE request_id = ?",
                Integer.class,
                requestId.toString());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void shouldReserveOneRequestForConcurrentQualificationCollision()
            throws Exception {
        int deliveries = 500;
        UUID qualificationId = UUID.randomUUID();
        List<OrderMessage> messages = new ArrayList<>(deliveries);
        for (int i = 0; i < deliveries; i++) {
            UUID requestId = UUID.randomUUID();
            requestIds.add(requestId);
            messages.add(message(requestId, qualificationId));
        }

        List<Boolean> results = runConcurrently(deliveries,
                index -> ReflectionTestUtils.invokeMethod(
                        listener, "reserve", messages.get(index)));

        assertThat(results).containsExactlyInAnyOrderElementsOf(
                expectedResults(deliveries));
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seckill_order_consume"
                        + " WHERE qualification_id = ?",
                Integer.class,
                qualificationId.toString());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void shouldRejectDifferentRequestsForSameQualification() {
        UUID qualificationId = UUID.randomUUID();
        UUID firstRequestId = UUID.randomUUID();
        UUID secondRequestId = UUID.randomUUID();
        requestIds.add(firstRequestId);
        requestIds.add(secondRequestId);

        Boolean first = ReflectionTestUtils.invokeMethod(
                listener, "reserve", message(firstRequestId, qualificationId));
        Boolean second = ReflectionTestUtils.invokeMethod(
                listener, "reserve", message(secondRequestId, qualificationId));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void consumerCrashRollsBackProcessingMarkerForBrokerRedelivery() {
        UUID requestId = UUID.randomUUID();
        requestIds.add(requestId);
        OrderMessage message = message(requestId, requestId);
        message.setPrice(100.0);
        message.setSkPrice(50.0);
        UserFeign userFeign = mock(UserFeign.class);
        when(userFeign.select(41L)).thenThrow(new IllegalStateException("consumer crash"));
        ReflectionTestUtils.setField(listener, "userFeign", userFeign);
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(
                ignored -> listener.onMessage(message)))
                .isInstanceOf(IllegalStateException.class);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seckill_order_consume WHERE request_id = ?",
                Integer.class,
                requestId.toString());
        assertThat(rows).isZero();
    }

    private OrderMessage message(UUID requestId, UUID qualificationId) {
        OrderMessage message = new OrderMessage();
        message.setRequestId(requestId);
        message.setQualificationId(qualificationId);
        message.setFkSeckillId(3L);
        message.setFkUserId(41L);
        message.setFkCourseId(7L);
        return message;
    }

    private List<Boolean> runConcurrently(
            int taskCount, ThrowingTask<Boolean> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(64, taskCount));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>(taskCount);
        try {
            for (int i = 0; i < taskCount; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.run(index);
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>(taskCount);
            for (Future<Boolean> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private List<Boolean> expectedResults(int deliveries) {
        List<Boolean> results = new ArrayList<>(deliveries);
        results.add(true);
        for (int i = 1; i < deliveries; i++) {
            results.add(false);
        }
        return results;
    }

    private String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @FunctionalInterface
    private interface ThrowingTask<T> {
        T run(int index) throws Exception;
    }
}
