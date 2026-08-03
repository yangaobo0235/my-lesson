package com.yangaobo.service;

import com.yangaobo.component.MyRedis;
import com.yangaobo.constant.ML;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SeckillStockReservationService {

    private static final String RESERVE_SCRIPT = """
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
            return 1
            """;

    private static final String RELEASE_SCRIPT = """
            local existing = redis.call('GET', KEYS[2])
            if existing ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[2])
            redis.call('INCR', KEYS[1])
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
        Long result = redis.lua(
                RESERVE_SCRIPT,
                keys(seckillId, courseId, userId),
                requestId.toString(),
                String.valueOf(QUALIFICATION_TTL_SECONDS));
        return switch (result == null ? -3 : result.intValue()) {
            case 1 -> ReservationResult.RESERVED;
            case 2 -> ReservationResult.IDEMPOTENT_REPLAY;
            case -1 -> ReservationResult.OUT_OF_STOCK;
            case -2 -> ReservationResult.ALREADY_QUALIFIED;
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
                keys(seckillId, courseId, userId),
                requestId.toString());
        return result != null && result == 1L;
    }

    private List<String> keys(
            Long seckillId,
            Long courseId,
            Long userId) {
        String stockKey = ML.Redis.SECKILL_COURSE_COUNT_PREFIX + courseId;
        String qualificationKey = ML.Redis.SECKILL_QUALIFICATION_PREFIX
                + seckillId + ':' + courseId + ':' + userId;
        return List.of(stockKey, qualificationKey);
    }

    public enum ReservationResult {
        RESERVED,
        IDEMPOTENT_REPLAY,
        OUT_OF_STOCK,
        ALREADY_QUALIFIED,
        STOCK_NOT_READY
    }
}
