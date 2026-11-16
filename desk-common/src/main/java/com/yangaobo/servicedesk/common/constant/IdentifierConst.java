package com.yangaobo.servicedesk.common.constant;

/**
 * 集中维护公开业务标识格式。
 *
 * @author yangaobo
 */
public final class IdentifierConst {
    /**
     * 业务标识格式。
     */
    public static final String PATTERN = "^[0-9a-f]{32}$";

    /**
     * 禁止实例化。
     */
    private IdentifierConst() {
    }
}
