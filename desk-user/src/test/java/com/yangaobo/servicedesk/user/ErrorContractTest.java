package com.yangaobo.servicedesk.user;

import static org.junit.jupiter.api.Assertions.*;

import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.ErrorCode;
import com.yangaobo.servicedesk.user.component.web.HttpErrorMapping;
import com.yangaobo.servicedesk.user.error.AccountErrorCode;
import com.yangaobo.servicedesk.user.error.AuthenticationErrorCode;
import com.yangaobo.servicedesk.user.error.OrganizationErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 验证所有本地公共/业务错误与未知类型的实际边界合同。
 *
 * @author yangaobo
 */
class ErrorContractTest {
    /**
     * 持续登记全部本地错误，新增语义必须明确其HTTP状态。
     */
    private java.util.ArrayList<ErrorCode> errors() {
        var errors =
                new java.util.ArrayList<ErrorCode>(java.util.List.of(CommonErrorCode.values()));
        errors.addAll(java.util.List.of(AccountErrorCode.values()));
        errors.addAll(java.util.List.of(AuthenticationErrorCode.values()));
        errors.addAll(java.util.List.of(OrganizationErrorCode.values()));
        return errors;
    }

    /**
     * 伪造同号错误仍不能获得已登记类型的公开消息资格。
     */
    private ErrorCode unsafe() {
        return new ErrorCode() {
            public int code() {
                return 1003;
            }

            public String message() {
                return "secret";
            }
        };
    }

    /**
     * 验证每项拥有者错误，不能只覆盖公共码或依赖默认409。
     */
    @Test
    void allLocalErrorsHaveExplicitHttpContract() {
        var expected =
                java.util.Map.ofEntries(
                        java.util.Map.entry(1, 500),
                        java.util.Map.entry(1001, 400),
                        java.util.Map.entry(1002, 404),
                        java.util.Map.entry(1003, 403),
                        java.util.Map.entry(1004, 409),
                        java.util.Map.entry(1005, 409),
                        java.util.Map.entry(1006, 405),
                        java.util.Map.entry(1007, 413),
                        java.util.Map.entry(1008, 415),
                        java.util.Map.entry(1009, 416),
                        java.util.Map.entry(1010, 429),
                        java.util.Map.entry(1011, 503),
                        java.util.Map.entry(1012, 502),
                        java.util.Map.entry(1013, 504),
                        java.util.Map.entry(2001, 401),
                        java.util.Map.entry(2002, 401),
                        java.util.Map.entry(2003, 401),
                        java.util.Map.entry(2004, 403),
                        java.util.Map.entry(2005, 400),
                        java.util.Map.entry(2006, 429),
                        java.util.Map.entry(2102, 409),
                        java.util.Map.entry(2201, 409));
        assertEquals(expected.size(), errors().size());
        for (var error : errors()) {
            assertSame(error, HttpErrorMapping.registered(error));
            assertEquals(expected.get(error.code()), HttpErrorMapping.statusOf(error).value());
        }
        assertSame(CommonErrorCode.INTERNAL_ERROR, HttpErrorMapping.registered(unsafe()));
        assertEquals(500, HttpErrorMapping.statusOf(unsafe()).value());
    }
}
