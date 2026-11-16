package com.yangaobo.servicedesk.user.component;

import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.user.error.AuthenticationErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 登录凭据的业务边界。
 *
 * @author yangaobo
 */
@Component
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class LoginAttemptLimiter {
    private static final DefaultRedisScript<Long> COUNT =
            new DefaultRedisScript<>(
                    "local n=redis.call('INCR',KEYS[1]); if n==1 then"
                            + " redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n",
                    Long.class);
    private final StringRedisTemplate redis;
    private final String prefix;

    /**
     * 接入所属服务的Redis与隔离键前缀。
     */
    public LoginAttemptLimiter(
            StringRedisTemplate redis, @Value("${desk.redis-key-prefix}") String prefix) {
        this.redis = redis;
        this.prefix = prefix;
    }

    /**
     * 同时限制来源地址与规范化账号，Redis故障明确拒绝新登录。
     */
    public void acquire(String username, String address) {
        try {
            check("account:" + digest(username), 10);
            check("address:" + digest(address), 100);
        } catch (DataAccessException failure) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 原子设置过期，不因重复请求延长窗口。
     */
    private void check(String key, int limit) {
        Long count = redis.execute(COUNT, List.of(prefix + "login:" + key), "900");
        if (count == null) throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        if (count > limit) throw new BusinessException(AuthenticationErrorCode.TOO_MANY_ATTEMPTS);
    }

    /**
     * 键不包含明文账号或网络地址。
     */
    private String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
