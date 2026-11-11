package com.yangaobo.servicedesk.common;

import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.env.SystemEnvironment;
import com.yangaobo.servicedesk.common.env.SystemEnvironmentEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证跨应用基础合同及环境歧义拒绝，不连接外部资源。
 *
 * @author yangaobo
 */
class FoundationContractTest {

    /**
     * 成功与错误均保持稳定业务码、空值及不可变请求标识。
     */
    @Test
    void preservesResultAndDoesNotMutateOriginal() {
        Result<String> original = Result.success("payload");
        Result<String> traced = original.withTraceId("trusted-trace");
        assertEquals(0, traced.getCode());
        assertEquals("payload", traced.getData());
        assertEquals("trusted-trace", traced.getTraceId());
        assertNull(original.getTraceId());
        assertNull(Result.success().getData());
        Result<Void> failure = Result.failure(CommonErrorCode.INTERNAL_ERROR, "trace");
        assertEquals(1, failure.getCode());
        assertNull(failure.getData());
    }

    /**
     * 功能Profile不改变基础环境，缺失或冲突时拒绝启动。
     */
    @Test
    void rejectsMissingAndConflictingBaseEnvironments() {
        assertEquals(SystemEnvironmentEnum.DEV,
                SystemEnvironment.fromProfiles("dev", "feature").getBaseEnvironment());
        assertEquals(SystemEnvironmentEnum.PROD,
                SystemEnvironment.fromProfiles("prod").getBaseEnvironment());
        assertThrows(IllegalArgumentException.class, () -> SystemEnvironment.fromProfiles("feature"));
        assertThrows(IllegalArgumentException.class, () -> SystemEnvironment.fromProfiles("dev", "prod"));
    }
}
