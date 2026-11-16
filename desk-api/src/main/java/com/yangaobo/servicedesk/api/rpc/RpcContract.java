package com.yangaobo.servicedesk.api.rpc;

import io.grpc.Context;
import io.grpc.Status;

import java.util.concurrent.TimeUnit;

/**
 * 提供内部RPC的显式状态与方法预算合同，不注册客户端或运行配置。
 *
 * @author yangaobo
 */
public final class RpcContract {
    /**
     * 不创建无状态合同实例。
     */
    private RpcContract() {
    }

    /**
     * 返回方法允许的最大预算，单位毫秒；身份、状态与原件分别管理。
     */
    public static long maximumMillis(String method) {
        String name = method.substring(method.lastIndexOf('/') + 1);
        return switch (name) {
            case "VerifyBrowserSession", "VerifySessionRef" -> 3000;
            case "GetStatus" -> 15000;
            case "ReadOriginal" -> 120000;
            default -> 5000;
        };
    }

    /**
     * 子调用为父请求预留交付时间，预算耗尽不再发起外部操作。
     */
    public static long remainingMillis(String method) {
        long budget = maximumMillis(method);
        var parent = Context.current().getDeadline();
        if (parent != null) budget = Math.min(budget, parent.timeRemaining(TimeUnit.MILLISECONDS));
        if (budget <= 100) throw Status.DEADLINE_EXCEEDED.asRuntimeException();
        return budget - 100;
    }

    /**
     * 按已登记语义转换，未知编号安全回退INTERNAL，不按号段计算。
     */
    public static Status status(int code) {
        return switch (code) {
            case 1001, 1006, 1007, 1008, 1009, 2005, 4002, 4003 -> Status.INVALID_ARGUMENT;
            case 1002 -> Status.NOT_FOUND;
            case 1003, 2004 -> Status.PERMISSION_DENIED;
            case 2001, 2002, 2003 -> Status.UNAUTHENTICATED;
            case 1004, 1005, 2102, 2201, 3001, 3101, 3102, 3201, 3301, 4001, 4201, 5201 -> Status.FAILED_PRECONDITION;
            case 1010, 2006, 5001, 5002 -> Status.RESOURCE_EXHAUSTED;
            case 1011, 5101, 5102 -> Status.UNAVAILABLE;
            case 1013 -> Status.DEADLINE_EXCEEDED;
            default -> Status.INTERNAL;
        };
    }
}
