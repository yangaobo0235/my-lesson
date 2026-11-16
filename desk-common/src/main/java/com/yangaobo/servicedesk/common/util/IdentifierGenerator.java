package com.yangaobo.servicedesk.common.util;

import java.util.UUID;

/**
 * 生成32位小写十六进制业务标识，不用于转换远端标识或执行令牌。
 *
 * @author yangaobo
 */
public final class IdentifierGenerator {
    /**
     * 不创建无状态工具实例。
     */
    private IdentifierGenerator() {
    }

    /**
     * 生成新的业务标识；原请求重试仍须复用已经保存的请求键。
     */
    public static String next() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
