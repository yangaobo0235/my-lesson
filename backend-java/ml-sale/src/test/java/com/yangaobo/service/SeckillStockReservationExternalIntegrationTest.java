package com.yangaobo.service;

import com.yangaobo.component.MyRedis;
import com.yangaobo.constant.ML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
        named = "RUN_EXTERNAL_INTEGRATION_TESTS", matches = "true")
class SeckillStockReservationExternalIntegrationTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private SeckillStockReservationService reservationService;
    private final List<String> keysToDelete =
            Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void connectToRedis() {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(
                        environment("REDIS_HOST", "127.0.0.1"),
                        Integer.parseInt(environment("REDIS_PORT", "6379")));
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        reservationService = new SeckillStockReservationService(
                new MyRedis(redisTemplate));
    }

    @AfterEach
    void cleanUp() {
        if (redisTemplate != null && !keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldNotOversellAcrossStockAndConcurrencyMatrix() throws Exception {
        List<StockScenario> scenarios = List.of(
                new StockScenario(1, 64),
                new StockScenario(10, 250),
                new StockScenario(50, 1_000),
                new StockScenario(200, 2_000),
                new StockScenario(500, 5_000));
        int totalRequests = 0;
        int totalReserved = 0;
        long startedAt = System.nanoTime();

        for (StockScenario scenario : scenarios) {
            long token = positiveToken();
            long seckillId = token;
            long courseId = token;
            String stockKey = ML.Redis.SECKILL_COURSE_COUNT_PREFIX + courseId;
            keysToDelete.add(stockKey);
            redisTemplate.opsForValue().set(
                    stockKey, String.valueOf(scenario.stock()));

            List<SeckillStockReservationService.ReservationResult> results =
                    runConcurrently(scenario.requests(), index -> {
                        long userId = token + index + 1;
                        keysToDelete.add(qualificationKey(
                                seckillId, courseId, userId));
                        return reservationService.reserve(
                                seckillId, courseId, userId, UUID.randomUUID());
                    });

            assertThat(results).filteredOn(result -> result ==
                            SeckillStockReservationService.ReservationResult.RESERVED)
                    .hasSize(scenario.stock());
            assertThat(results).filteredOn(result -> result ==
                            SeckillStockReservationService.ReservationResult.OUT_OF_STOCK)
                    .hasSize(scenario.requests() - scenario.stock());
            assertThat(redisTemplate.opsForValue().get(stockKey)).isEqualTo("0");
            totalRequests += scenario.requests();
            totalReserved += scenario.stock();
        }

        System.out.printf(
                "seckill-stock scenarios=%d requests=%d reserved=%d elapsedMs=%d%n",
                scenarios.size(),
                totalRequests,
                totalReserved,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }

    @Test
    void shouldDeductOnlyOnceWhenSameRequestIsReplayedConcurrently()
            throws Exception {
        int requests = 2_000;
        long token = positiveToken();
        long seckillId = token;
        long courseId = token;
        long userId = token;
        UUID requestId = UUID.randomUUID();
        String stockKey = ML.Redis.SECKILL_COURSE_COUNT_PREFIX + courseId;
        keysToDelete.add(stockKey);
        keysToDelete.add(qualificationKey(seckillId, courseId, userId));
        redisTemplate.opsForValue().set(stockKey, "10");

        List<SeckillStockReservationService.ReservationResult> results =
                runConcurrently(requests, ignored -> reservationService.reserve(
                        seckillId, courseId, userId, requestId));

        assertThat(results).filteredOn(result -> result ==
                        SeckillStockReservationService.ReservationResult.RESERVED)
                .hasSize(1);
        assertThat(results).filteredOn(result -> result ==
                        SeckillStockReservationService.ReservationResult.IDEMPOTENT_REPLAY)
                .hasSize(requests - 1);
        assertThat(redisTemplate.opsForValue().get(stockKey)).isEqualTo("9");
    }

    @Test
    void shouldGrantOneQualificationWhenSameUserUsesDifferentRequests()
            throws Exception {
        int requests = 1_000;
        long token = positiveToken();
        long seckillId = token;
        long courseId = token;
        long userId = token;
        String stockKey = ML.Redis.SECKILL_COURSE_COUNT_PREFIX + courseId;
        keysToDelete.add(stockKey);
        keysToDelete.add(qualificationKey(seckillId, courseId, userId));
        redisTemplate.opsForValue().set(stockKey, "100");

        List<SeckillStockReservationService.ReservationResult> results =
                runConcurrently(requests, ignored -> reservationService.reserve(
                        seckillId, courseId, userId, UUID.randomUUID()));

        assertThat(results).filteredOn(result -> result ==
                        SeckillStockReservationService.ReservationResult.RESERVED)
                .hasSize(1);
        assertThat(results).filteredOn(result -> result ==
                        SeckillStockReservationService.ReservationResult.ALREADY_QUALIFIED)
                .hasSize(requests - 1);
        assertThat(redisTemplate.opsForValue().get(stockKey)).isEqualTo("99");
    }

    private <T> List<T> runConcurrently(
            int taskCount, ThrowingTask<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(64, taskCount));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>(taskCount);
        try {
            for (int i = 0; i < taskCount; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.run(index);
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>(taskCount);
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private String qualificationKey(
            long seckillId, long courseId, long userId) {
        return ML.Redis.SECKILL_QUALIFICATION_PREFIX
                + seckillId + ':' + courseId + ':' + userId;
    }

    private long positiveToken() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    private String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @FunctionalInterface
    private interface ThrowingTask<T> {
        T run(int index) throws Exception;
    }

    private record StockScenario(int stock, int requests) {
    }
}
