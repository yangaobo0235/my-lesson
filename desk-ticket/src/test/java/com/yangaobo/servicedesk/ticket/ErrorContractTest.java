package com.yangaobo.servicedesk.ticket;

import static org.junit.jupiter.api.Assertions.*;

import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.ErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.web.HttpErrorMapping;
import com.yangaobo.servicedesk.ticket.error.BookingErrorCode;
import com.yangaobo.servicedesk.ticket.error.DeviceErrorCode;
import com.yangaobo.servicedesk.ticket.error.SupportErrorCode;
import com.yangaobo.servicedesk.ticket.error.TicketErrorCode;
import com.yangaobo.servicedesk.ticket.rpc.RpcResponses;
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
        errors.addAll(java.util.List.of(BookingErrorCode.values()));
        errors.addAll(java.util.List.of(DeviceErrorCode.values()));
        errors.addAll(java.util.List.of(SupportErrorCode.values()));
        errors.addAll(java.util.List.of(TicketErrorCode.values()));
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
                        java.util.Map.entry(3001, 409),
                        java.util.Map.entry(3101, 409),
                        java.util.Map.entry(3102, 409),
                        java.util.Map.entry(3201, 409),
                        java.util.Map.entry(3301, 409));
        assertEquals(expected.size(), errors().size());
        for (var error : errors()) {
            assertSame(error, HttpErrorMapping.registered(error));
            assertEquals(expected.get(error.code()), HttpErrorMapping.statusOf(error).value());
        }
        assertSame(CommonErrorCode.INTERNAL_ERROR, HttpErrorMapping.registered(unsafe()));
        assertEquals(500, HttpErrorMapping.statusOf(unsafe()).value());
    }

    /**
     * 真正经过RPC响应边界验证数字、状态、安全消息及追踪号。
     */
    @Test
    void rpcDetailsPreserveRegisteredSemanticsAndFallback() throws Exception {
        var errors = errors();
        errors.add(unsafe());
        for (var error : errors) {
            var observed = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            RpcResponses.respond(
                    new io.grpc.stub.StreamObserver<Object>() {
                        public void onNext(Object value) {
                            throw new AssertionError("不能返回成功");
                        }

                        public void onCompleted() {
                            throw new AssertionError("不能返回成功");
                        }

                        public void onError(Throwable failure) {
                            observed.set(failure);
                        }
                    },
                    () -> {
                        throw new BusinessException(error);
                    });
            var status = io.grpc.protobuf.StatusProto.fromThrowable(observed.get());
            var detail =
                    status.getDetails(0)
                            .unpack(com.yangaobo.servicedesk.api.common.v1.RpcErrorDetail.class);
            var safe = HttpErrorMapping.registered(error);
            assertEquals(safe.code(), detail.getCode());
            assertEquals(
                    com.yangaobo.servicedesk.api.rpc.RpcContract.status(safe.code())
                            .getCode()
                            .value(),
                    status.getCode());
            assertFalse(detail.getTraceId().isBlank());
            assertFalse(detail.getMessage().contains("secret"));
        }
    }
}
