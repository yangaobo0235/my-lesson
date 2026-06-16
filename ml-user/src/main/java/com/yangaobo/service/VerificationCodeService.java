package com.yangaobo.service;

import com.yangaobo.component.MyRedis;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.result.ResultCode;
import com.yangaobo.sms.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VerificationCodeService {
    private static final int MAX_DAILY_SENDS = 10;
    private static final int MAX_ATTEMPTS = 5;

    private final MyRedis redis;
    private final SmsSender smsSender;

    public VerificationCodeService(MyRedis redis, SmsSender smsSender) {
        this.redis = redis;
        this.smsSender = smsSender;
    }

    public void send(String purpose, String phone, String clientIp) {
        String cooldownKey = "vcode:cooldown:" + purpose + ":" + phone;
        if (!redis.setNxEx(cooldownKey, "1", 60, TimeUnit.SECONDS)) {
            throw new ServiceException(ResultCode.VCODE_ILLEGAL, "验证码发送过于频繁");
        }

        enforceDailyLimit("phone", phone);
        enforceDailyLimit("ip", normalizeIp(clientIp));

        try {
            smsSender.sendVerificationCode(phone);
            redis.setEx(codeKey(purpose, phone), "sent", 5, TimeUnit.MINUTES);
            redis.del(attemptKey(purpose, phone));
            log.info("验证码已提交发送: purpose={}, phone={}", purpose, maskPhone(phone));
        } catch (RuntimeException exception) {
            redis.del(cooldownKey);
            throw exception;
        }
    }

    public void verify(String purpose, String phone, String submittedCode) {
        String key = codeKey(purpose, phone);
        if (redis.get(key) == null) {
            throw new ServiceException(ResultCode.VCODE_ILLEGAL, "验证码已失效");
        }
        if (!smsSender.checkVerificationCode(phone, submittedCode)) {
            long attempts = redis.incr(attemptKey(purpose, phone), 1);
            redis.expire(attemptKey(purpose, phone), 5, TimeUnit.MINUTES);
            if (attempts >= MAX_ATTEMPTS) {
                redis.del(key);
                throw new ServiceException(ResultCode.VCODE_ILLEGAL, "验证码错误次数过多，请重新获取");
            }
            throw new ServiceException(ResultCode.VCODE_ILLEGAL, "验证码错误");
        }
        redis.del(key);
        redis.del(attemptKey(purpose, phone));
    }

    private void enforceDailyLimit(String dimension, String value) {
        String key = "vcode:daily:" + dimension + ":" + value;
        long count = redis.incr(key, 1);
        if (count == 1) {
            redis.expire(key, 1, TimeUnit.DAYS);
        }
        if (count > MAX_DAILY_SENDS) {
            throw new ServiceException(ResultCode.VCODE_ILLEGAL, "今日验证码发送次数已达上限");
        }
    }

    private String codeKey(String purpose, String phone) {
        return "vcode:code:" + purpose + ":" + phone;
    }

    private String attemptKey(String purpose, String phone) {
        return "vcode:attempt:" + purpose + ":" + phone;
    }

    private String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.replace(':', '_');
    }

    private String maskPhone(String phone) {
        return phone.length() < 7 ? "***" : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
