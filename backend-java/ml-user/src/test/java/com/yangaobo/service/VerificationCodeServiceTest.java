package com.yangaobo.service;

import com.yangaobo.component.MyRedis;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.sms.SmsSender;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeServiceTest {

    @Test
    void storesOnlySentMarkerWithoutVerificationCode() {
        MyRedis redis = mock(MyRedis.class);
        SmsSender smsSender = mock(SmsSender.class);
        when(redis.setNxEx(anyString(), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(redis.incr(anyString(), eq(1L))).thenReturn(1L);
        VerificationCodeService service = new VerificationCodeService(redis, smsSender);
        service.send("login", "13800138000", "127.0.0.1");

        verify(smsSender).sendVerificationCode("13800138000");
        verify(redis).setEx(
                org.mockito.ArgumentMatchers.startsWith("vcode:code:login:"),
                eq("sent"),
                eq(5L),
                eq(TimeUnit.MINUTES));
    }

    @Test
    void rejectsSendingDuringCooldown() {
        MyRedis redis = mock(MyRedis.class);
        SmsSender smsSender = mock(SmsSender.class);
        when(redis.setNxEx(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);
        VerificationCodeService service = new VerificationCodeService(redis, smsSender);

        assertThrows(ServiceException.class,
                () -> service.send("login", "13800138000", "127.0.0.1"));
    }

    @Test
    void deletesCodeAfterTooManyWrongAttempts() {
        MyRedis redis = mock(MyRedis.class);
        SmsSender smsSender = mock(SmsSender.class);
        when(redis.get("vcode:code:login:13800138000")).thenReturn("sent");
        when(smsSender.checkVerificationCode("13800138000", "000000")).thenReturn(false);
        when(redis.incr("vcode:attempt:login:13800138000", 1)).thenReturn(5L);
        VerificationCodeService service = new VerificationCodeService(redis, smsSender);

        assertThrows(ServiceException.class,
                () -> service.verify("login", "13800138000", "000000"));
        verify(redis).del("vcode:code:login:13800138000");
    }

    @Test
    void releasesCooldownWhenSmsProviderFails() {
        MyRedis redis = mock(MyRedis.class);
        SmsSender smsSender = mock(SmsSender.class);
        when(redis.setNxEx(anyString(), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(redis.incr(anyString(), eq(1L))).thenReturn(1L);
        doThrow(new ServiceException(
                com.yangaobo.result.ResultCode.SMS_SEND_FAILED,
                "短信服务暂时不可用"))
                .when(smsSender)
                .sendVerificationCode("13800138000");
        VerificationCodeService service = new VerificationCodeService(redis, smsSender);

        assertThrows(ServiceException.class,
                () -> service.send("login", "13800138000", "127.0.0.1"));

        verify(redis).del("vcode:cooldown:login:13800138000");
    }

    @Test
    void deletesSentMarkerAfterCloudVerificationPasses() {
        MyRedis redis = mock(MyRedis.class);
        SmsSender smsSender = mock(SmsSender.class);
        when(redis.get("vcode:code:login:13800138000")).thenReturn("sent");
        when(smsSender.checkVerificationCode("13800138000", "123456")).thenReturn(true);
        VerificationCodeService service = new VerificationCodeService(redis, smsSender);

        service.verify("login", "13800138000", "123456");

        verify(redis).del("vcode:code:login:13800138000");
        verify(redis).del("vcode:attempt:login:13800138000");
    }
}
