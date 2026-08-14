package com.yangaobo.service;

import com.yangaobo.component.MyRedis;
import com.yangaobo.constant.ML;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SeckillStockReservationService {

    private static final String RESERVE_SCRIPT = """
            local identity = ARGV[3]
            local request = redis.call('GET', KEYS[3])
            if request then
                if string.sub(request, 1, string.len(identity) + 1) == identity .. '|' then
                    return 2
                end
                return -4
            end
            local existing = redis.call('GET', KEYS[2])
            if existing then
                if existing == ARGV[1] then return 2 end
                return -2
            end
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if not stock then return -3 end
            if stock <= 0 then return -1 end
            redis.call('DECR', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
            redis.call('SET', KEYS[3], identity .. '|' .. ARGV[4], 'EX', ARGV[2])
            redis.call('ZADD', KEYS[4], ARGV[4], ARGV[1])
            redis.call('INCR', KEYS[5])
            redis.call('EXPIRE', KEYS[5], ARGV[2])
            return 1
            """;

    private static final String RELEASE_SCRIPT = """
            local existing = redis.call('GET', KEYS[2])
            if existing ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[2])
            redis.call('DEL', KEYS[3])
            redis.call('ZREM', KEYS[4], ARGV[1])
            redis.call('INCR', KEYS[1])
            local reserved = tonumber(redis.call('GET', KEYS[5]) or '0')
            if reserved > 0 then redis.call('DECR', KEYS[5]) end
            return 1
            """;

    private static final String RECONCILE_STOCK_SCRIPT = """
            local initial = tonumber(redis.call('GET', KEYS[1]))
            local reserved = tonumber(redis.call('GET', KEYS[2]) or '0')
            if not initial then return -1 end
            local expected = initial - reserved
            if expected < 0 then return -2 end
            local current = tonumber(redis.call('GET', KEYS[3]))
            if current == expected then return 0 end
            redis.call('SET', KEYS[3], tostring(expected), 'KEEPTTL')
            return 1
            """;

    private static final long QUALIFICATION_TTL_SECONDS = 24 * 60 * 60;

    private final MyRedis redis;

    public SeckillStockReservationService(MyRedis redis) {
        this.redis = redis;
    }

    public ReservationResult reserve(
            Long seckillId,
            Long courseId,
            Long userId,
            UUID requestId) {
        return reserve(seckillId, courseId, userId, requestId, 0D, 0D);
    }

    public ReservationResult reserve(
            Long seckillId,
            Long courseId,
            Long userId,
            UUID requestId,
            double price,
            double seckillPrice) {
        long reservedAt = System.currentTimeMillis();
        String identity = String.join("|",
                seckillId.toString(),
                courseId.toString(),
                userId.toString(),
                Double.toString(price),
                Double.toString(seckillPrice));
        Long result = redis.lua(
                RESERVE_SCRIPT,
                keys(seckillId, courseId, userId, requestId),
                requestId.toString(),
                String.valueOf(QUALIFICATION_TTL_SECONDS),
                identity,
                String.valueOf(reservedAt));
        return switch (result == null ? -3 : result.intValue()) {
            case 1 -> ReservationResult.RESERVED;
            case 2 -> ReservationResult.IDEMPOTENT_REPLAY;
            case -1 -> ReservationResult.OUT_OF_STOCK;
            case -2 -> ReservationResult.ALREADY_QUALIFIED;
            case -4 -> ReservationResult.REQUEST_CONFLICT;
            default -> ReservationResult.STOCK_NOT_READY;
        };
    }

    public boolean release(
            Long seckillId,
            Long courseId,
            Long userId,
            UUID requestId) {
        Long result = redis.lua(
                RELEASE_SCRIPT,
                keys(seckillId, courseId, userId, requestId),
                requestId.toString());
        return result != null && result == 1L;
    }

    private List<String> keys(
            Long seckillId,
            Long courseId,
            Long userId,
            UUID requestId) {
        String stockKey = ML.Redis.SECKILL_COURSE_COUNT_PREFIX + courseId;
        String qualificationKey = ML.Redis.SECKILL_QUALIFICATION_PREFIX
                + seckillId + ':' + courseId + ':' + userId;
        String requestKey = ML.Redis.SECKILL_REQUEST_PREFIX + requestId;
        String reservedCountKey = ML.Redis.SECKILL_COURSE_RESERVED_COUNT_PREFIX + courseId;
        return List.of(
                stockKey,
                qualificationKey,
                requestKey,
                ML.Redis.SECKILL_RECONCILE_PENDING_KEY,
                reservedCountKey);
    }

    public List<Reservation> pendingBefore(long cutoffEpochMillis, int limit) {
        if (limit < 1) {
            return List.of();
        }
        Set<String> requestIds = redis.zRangeByScore(
                ML.Redis.SECKILL_RECONCILE_PENDING_KEY,
                0,
                cutoffEpochMillis);
        if (requestIds == null || requestIds.isEmpty()) {
            return List.of();
        }
        return requestIds.stream()
                .limit(limit)
                .map(this::findReservation)
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<Reservation> findReservation(String requestId) {
        String payload = redis.get(ML.Redis.SECKILL_REQUEST_PREFIX + requestId);
        if (payload == null || payload.isBlank()) {
            redis.zRem(ML.Redis.SECKILL_RECONCILE_PENDING_KEY, requestId);
            return Optional.empty();
        }
        String[] values = payload.split("\\|", -1);
        if (values.length != 6) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Reservation(
                    UUID.fromString(requestId),
                    Long.parseLong(values[0]),
                    Long.parseLong(values[1]),
                    Long.parseLong(values[2]),
                    Double.parseDouble(values[3]),
                    Double.parseDouble(values[4]),
                    Long.parseLong(values[5])));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean reconcileStock(Long courseId) {
        Long result = redis.lua(
                RECONCILE_STOCK_SCRIPT,
                List.of(
                        ML.Redis.SECKILL_COURSE_INITIAL_COUNT_PREFIX + courseId,
                        ML.Redis.SECKILL_COURSE_RESERVED_COUNT_PREFIX + courseId,
                        ML.Redis.SECKILL_COURSE_COUNT_PREFIX + courseId));
        return result != null && result == 1L;
    }

    public Set<String> activeCourseIds() {
        Set<String> ids = redis.sMembers(ML.Redis.SECKILL_ACTIVE_COURSES_KEY);
        return ids == null ? Set.of() : ids;
    }

    public record Reservation(
            UUID requestId,
            Long seckillId,
            Long courseId,
            Long userId,
            double price,
            double seckillPrice,
            long reservedAtEpochMillis) {
    }


    public enum ReservationResult {
        RESERVED,
        IDEMPOTENT_REPLAY,
        OUT_OF_STOCK,
        ALREADY_QUALIFIED,
        REQUEST_CONFLICT,
        STOCK_NOT_READY
    }
}
