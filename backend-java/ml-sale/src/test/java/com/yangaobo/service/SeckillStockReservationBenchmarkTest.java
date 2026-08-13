package com.yangaobo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
        named = "RUN_SECKILL_BENCHMARK", matches = "true")
class SeckillStockReservationBenchmarkTest {

    private static final int CONCURRENCY = Integer.getInteger(
            "seckill.benchmark.concurrency", 128);
    private static final int WARMUP_REQUESTS = Integer.getInteger(
            "seckill.benchmark.warmup-requests", 2_000);
    private static final int MEASURED_REQUESTS = Integer.getInteger(
            "seckill.benchmark.measured-requests", 30_000);
    private static final int MEASURED_STOCK = Integer.getInteger(
            "seckill.benchmark.measured-stock", 5_000);
    private static final int REPLAY_REQUESTS = Integer.getInteger(
            "seckill.benchmark.replay-requests", 5_000);
    private static final int SAME_USER_REQUESTS = Integer.getInteger(
            "seckill.benchmark.same-user-requests", 5_000);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private SeckillStockReservationService reservationService;
    private final List<String> keyPatterns = new ArrayList<>();

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
        if (redisTemplate != null) {
            for (String pattern : keyPatterns) {
                var keys = redisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            }
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void benchmarkAtomicStockReservationUnderAdversarialContention()
            throws Exception {
        runUniqueUsers(WARMUP_REQUESTS, WARMUP_REQUESTS, 32, false);

        MeasuredRun measured = runUniqueUsers(
                MEASURED_REQUESTS,
                MEASURED_STOCK,
                CONCURRENCY,
                true);
        ContentionRun replay = runSameRequestReplay(REPLAY_REQUESTS);
        ContentionRun sameUser = runSameUserDifferentRequests(
                SAME_USER_REQUESTS);

        assertThat(measured.reserved()).isEqualTo(MEASURED_STOCK);
        assertThat(measured.outOfStock())
                .isEqualTo(MEASURED_REQUESTS - MEASURED_STOCK);
        assertThat(measured.finalStock()).isZero();
        assertThat(measured.errors()).isZero();
        assertThat(replay.reserved()).isEqualTo(1);
        assertThat(replay.expectedConflict()).isEqualTo(REPLAY_REQUESTS - 1);
        assertThat(replay.finalStock()).isEqualTo(9);
        assertThat(sameUser.reserved()).isEqualTo(1);
        assertThat(sameUser.expectedConflict())
                .isEqualTo(SAME_USER_REQUESTS - 1);
        assertThat(sameUser.finalStock()).isEqualTo(99);

        Map<String, Object> report = report(measured, replay, sameUser);
        Path output = Path.of(System.getProperty(
                "seckill.benchmark.output",
                "target/seckill-stock-reservation-benchmark.json"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), report);
        System.out.println("SECKILL_BENCHMARK="
                + new ObjectMapper().writeValueAsString(report));
    }

    private MeasuredRun runUniqueUsers(
            int requests,
            int stock,
            int concurrency,
            boolean measured) throws Exception {
        long token = positiveToken();
        long seckillId = token;
        long courseId = token;
        registerKeys(seckillId, courseId);
        String stockKey = stockKey(courseId);
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        long[] latencies = new long[requests];
        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger outOfStock = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        long started = System.nanoTime();
        runConcurrently(requests, concurrency, index -> {
            long requestStarted = System.nanoTime();
            try {
                var result = reservationService.reserve(
                        seckillId,
                        courseId,
                        token + index + 1,
                        UUID.randomUUID());
                if (result == SeckillStockReservationService
                        .ReservationResult.RESERVED) {
                    reserved.incrementAndGet();
                } else if (result == SeckillStockReservationService
                        .ReservationResult.OUT_OF_STOCK) {
                    outOfStock.incrementAndGet();
                } else {
                    errors.incrementAndGet();
                }
            } catch (RuntimeException exception) {
                errors.incrementAndGet();
            } finally {
                latencies[index] = System.nanoTime() - requestStarted;
            }
            return null;
        });
        long elapsed = System.nanoTime() - started;
        int finalStock = Integer.parseInt(redisTemplate.opsForValue()
                .get(stockKey));
        if (!measured) {
            return new MeasuredRun(
                    requests, stock, concurrency, reserved.get(),
                    outOfStock.get(), errors.get(), finalStock,
                    elapsed, new long[0]);
        }
        return new MeasuredRun(
                requests, stock, concurrency, reserved.get(),
                outOfStock.get(), errors.get(), finalStock,
                elapsed, latencies);
    }

    private ContentionRun runSameRequestReplay(int requests)
            throws Exception {
        long token = positiveToken();
        registerKeys(token, token);
        String stockKey = stockKey(token);
        redisTemplate.opsForValue().set(stockKey, "10");
        UUID requestId = UUID.randomUUID();
        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger replayed = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        runConcurrently(requests, CONCURRENCY, ignored -> {
            var result = reservationService.reserve(
                    token, token, token, requestId);
            if (result == SeckillStockReservationService
                    .ReservationResult.RESERVED) {
                reserved.incrementAndGet();
            } else if (result == SeckillStockReservationService
                    .ReservationResult.IDEMPOTENT_REPLAY) {
                replayed.incrementAndGet();
            } else {
                unexpected.incrementAndGet();
            }
            return null;
        });
        return new ContentionRun(
                requests,
                reserved.get(),
                replayed.get(),
                unexpected.get(),
                Integer.parseInt(redisTemplate.opsForValue().get(stockKey)));
    }

    private ContentionRun runSameUserDifferentRequests(int requests)
            throws Exception {
        long token = positiveToken();
        registerKeys(token, token);
        String stockKey = stockKey(token);
        redisTemplate.opsForValue().set(stockKey, "100");
        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        runConcurrently(requests, CONCURRENCY, ignored -> {
            var result = reservationService.reserve(
                    token, token, token, UUID.randomUUID());
            if (result == SeckillStockReservationService
                    .ReservationResult.RESERVED) {
                reserved.incrementAndGet();
            } else if (result == SeckillStockReservationService
                    .ReservationResult.ALREADY_QUALIFIED) {
                rejected.incrementAndGet();
            } else {
                unexpected.incrementAndGet();
            }
            return null;
        });
        return new ContentionRun(
                requests,
                reserved.get(),
                rejected.get(),
                unexpected.get(),
                Integer.parseInt(redisTemplate.opsForValue().get(stockKey)));
    }

    private <T> void runConcurrently(
            int taskCount,
            int concurrency,
            ThrowingTask<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>(taskCount);
        try {
            for (int index = 0; index < taskCount; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.run(taskIndex);
                }));
            }
            start.countDown();
            for (Future<T> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Map<String, Object> report(
            MeasuredRun measured,
            ContentionRun replay,
            ContentionRun sameUser) {
        long[] sorted = measured.latencies().clone();
        Arrays.sort(sorted);
        double elapsedSeconds = measured.elapsedNanos() / 1_000_000_000.0;
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("os", System.getProperty("os.name")
                + " " + System.getProperty("os.version"));
        environment.put("java", System.getProperty("java.version"));
        environment.put("processors", Runtime.getRuntime()
                .availableProcessors());
        environment.put("redis", environment("REDIS_VERSION", "unknown"));
        environment.put("client", "Java 17 + Spring Data Redis/Lettuce");

        Map<String, Object> throughput = new LinkedHashMap<>();
        throughput.put("requests", measured.requests());
        throughput.put("stock", measured.stock());
        throughput.put("concurrency", measured.concurrency());
        throughput.put("warmupRequests", WARMUP_REQUESTS);
        throughput.put("durationMs", Math.round(elapsedSeconds * 1000));
        throughput.put("requestsPerSecond",
                Math.round(measured.requests() / elapsedSeconds));
        throughput.put("p50Ms", nanosToMillis(percentile(sorted, 0.50)));
        throughput.put("p95Ms", nanosToMillis(percentile(sorted, 0.95)));
        throughput.put("p99Ms", nanosToMillis(percentile(sorted, 0.99)));
        throughput.put("reserved", measured.reserved());
        throughput.put("outOfStock", measured.outOfStock());
        throughput.put("errors", measured.errors());
        throughput.put("finalStock", measured.finalStock());
        throughput.put("oversell", Math.max(
                0, measured.reserved() - measured.stock()));

        return Map.of(
                "scope", "Redis Lua stock reservation core; "
                        + "excludes HTTP, Redisson lock, RocketMQ and order DB.",
                "environment", environment,
                "throughput", throughput,
                "sameRequestReplay", contentionMap(replay),
                "sameUserDifferentRequests", contentionMap(sameUser));
    }

    private Map<String, Object> contentionMap(ContentionRun run) {
        return Map.of(
                "requests", run.requests(),
                "reserved", run.reserved(),
                "expectedConflict", run.expectedConflict(),
                "unexpected", run.unexpected(),
                "finalStock", run.finalStock());
    }

    private long percentile(long[] sorted, double quantile) {
        int index = Math.max(
                0,
                (int) Math.ceil(sorted.length * quantile) - 1);
        return sorted[index];
    }

    private double nanosToMillis(long nanos) {
        return Math.round(nanos / 10_000.0) / 100.0;
    }

    private void registerKeys(long seckillId, long courseId) {
        keyPatterns.add(stockKey(courseId));
        keyPatterns.add(ML.Redis.SECKILL_QUALIFICATION_PREFIX
                + seckillId + ':' + courseId + ":*");
    }

    private String stockKey(long courseId) {
        return ML.Redis.SECKILL_COURSE_COUNT_PREFIX + courseId;
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

    private record MeasuredRun(
            int requests,
            int stock,
            int concurrency,
            int reserved,
            int outOfStock,
            int errors,
            int finalStock,
            long elapsedNanos,
            long[] latencies) {
    }

    private record ContentionRun(
            int requests,
            int reserved,
            int expectedConflict,
            int unexpected,
            int finalStock) {
    }
}
