package com.yangaobo.servicedesk.api;

import static org.junit.jupiter.api.Assertions.*;

import com.yangaobo.servicedesk.api.rpc.RpcContract;
import io.grpc.Context;
import io.grpc.Status;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * 验证传输状态及父预算，不使用真实付费依赖。
 *
 * @author yangaobo
 */
class RpcContractTest {
    /**
     * 错误号逐项固定，系统异常不得回退UNAVAILABLE。
     */
    @Test
    void statusesFollowSemanticContract() {
        var groups =
                java.util.Map.of(
                        Status.Code.INTERNAL, new int[]{1, 1012, 99999},
                        Status.Code.INVALID_ARGUMENT,
                        new int[]{1001, 1006, 1007, 1008, 1009, 2005, 4002, 4003},
                        Status.Code.NOT_FOUND, new int[]{1002},
                        Status.Code.PERMISSION_DENIED, new int[]{1003, 2004},
                        Status.Code.UNAUTHENTICATED, new int[]{2001, 2002, 2003},
                        Status.Code.FAILED_PRECONDITION,
                        new int[]{
                                1004, 1005, 2102, 2201, 3001, 3101, 3102, 3201, 3301, 4001,
                                4201, 5201
                        },
                        Status.Code.RESOURCE_EXHAUSTED, new int[]{1010, 2006, 5001, 5002},
                        Status.Code.UNAVAILABLE, new int[]{1011, 5101, 5102},
                        Status.Code.DEADLINE_EXCEEDED, new int[]{1013});
        groups.forEach(
                (expected, codes) -> {
                    for (int code : codes)
                        assertEquals(expected, RpcContract.status(code).getCode(), "code=" + code);
                });
    }

    /**
     * 方法预算不同，子调用只能缩短父预算，耗尽不调用依赖。
     */
    @Test
    void methodBudgetAndParentDeadlineAreBounded() throws Exception {
        assertEquals(3000, RpcContract.maximumMillis("/identity/VerifySessionRef"));
        assertEquals(5000, RpcContract.maximumMillis("ValidateDraft"));
        assertEquals(15000, RpcContract.maximumMillis("GetStatus"));
        assertEquals(120000, RpcContract.maximumMillis("ReadOriginal"));
        try (var timer = Executors.newSingleThreadScheduledExecutor();
             var parent = Context.current().withDeadlineAfter(2, TimeUnit.SECONDS, timer)) {
            long remaining = parent.call(() -> RpcContract.remainingMillis("ReadOriginal"));
            assertTrue(remaining > 0 && remaining <= 1900);
        }
        try (var timer = Executors.newSingleThreadScheduledExecutor();
             var parent = Context.current().withDeadlineAfter(0, TimeUnit.MILLISECONDS, timer)) {
            assertThrows(
                    io.grpc.StatusRuntimeException.class,
                    () -> parent.call(() -> RpcContract.remainingMillis("GetStatus")));
        }
    }

    /**
     * 未知详情、错配状态和服务认证失败均不得伪装为用户退出。
     */
    @Test
    void remoteDetailsAreValidatedBeforeUse() {
        var permitted =
                java.util.Map.<Integer, com.yangaobo.servicedesk.common.base.ErrorCode>of(
                        1004, com.yangaobo.servicedesk.common.base.CommonErrorCode.CONFLICT);
        for (int code : new int[]{1004, 99999}) {
            var detail =
                    com.yangaobo.servicedesk.api.common.v1.RpcErrorDetail.newBuilder()
                            .setCode(code)
                            .setMessage("secret")
                            .build();
            var remote =
                    io.grpc.protobuf.StatusProto.toStatusRuntimeException(
                            com.google.rpc.Status.newBuilder()
                                    .setCode(Status.Code.FAILED_PRECONDITION.value())
                                    .addDetails(com.google.protobuf.Any.pack(detail))
                                    .build());
            var result =
                    com.yangaobo.servicedesk.api.rpc.RemoteRpcErrors.translate(remote, permitted);
            assertEquals(code == 1004 ? 1004 : 1012, result.code());
            assertFalse(result.message().contains("secret"));
        }
        assertEquals(
                1011,
                com.yangaobo.servicedesk.api.rpc.RemoteRpcErrors.translate(
                                Status.UNAUTHENTICATED.asRuntimeException(), permitted)
                        .code());
        assertEquals(
                1013,
                com.yangaobo.servicedesk.api.rpc.RemoteRpcErrors.translate(
                                Status.DEADLINE_EXCEEDED.asRuntimeException(), permitted)
                        .code());
        var malformed = new io.grpc.Metadata();
        malformed.put(
                io.grpc.Metadata.Key.of(
                        "grpc-status-details-bin", io.grpc.Metadata.BINARY_BYTE_MARSHALLER),
                new byte[]{-1});
        assertEquals(
                1012,
                com.yangaobo.servicedesk.api.rpc.RemoteRpcErrors.translate(
                                Status.INTERNAL.asRuntimeException(malformed), permitted)
                        .code());
    }
}
