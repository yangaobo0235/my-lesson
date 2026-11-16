package com.yangaobo.servicedesk.api.rpc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.yangaobo.servicedesk.api.common.v1.RpcErrorDetail;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.ErrorCode;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

import java.util.Map;

/**
 * 核验远端详情与传输状态，只返回调用用例允许的安全本地语义。
 *
 * @author yangaobo
 */
public final class RemoteRpcErrors {
    /**
     * 不创建无状态转换实例。
     */
    private RemoteRpcErrors() {
    }

    /**
     * 未登记载荷为协议错误；服务凭据失败不转换为浏览器退出。
     */
    public static ErrorCode translate(
            StatusRuntimeException failure, Map<Integer, ErrorCode> permitted) {
        var transport = failure.getStatus().getCode();
        com.google.rpc.Status status;
        try {
            status = StatusProto.fromThrowable(failure);
        } catch (RuntimeException malformed) {
            return CommonErrorCode.UPSTREAM_PROTOCOL_ERROR;
        }
        if (status != null && status.getDetailsCount() > 0) {
            if (status.getCode() != transport.value())
                return CommonErrorCode.UPSTREAM_PROTOCOL_ERROR;
            for (var packed : status.getDetailsList()) {
                try {
                    if (!packed.is(RpcErrorDetail.class)) continue;
                    var detail = packed.unpack(RpcErrorDetail.class);
                    if (RpcContract.status(detail.getCode()).getCode() != transport)
                        return CommonErrorCode.UPSTREAM_PROTOCOL_ERROR;
                    var allowed = permitted.get(detail.getCode());
                    if (allowed != null) return allowed;
                    return switch (detail.getCode()) {
                        case 1, 1011 -> CommonErrorCode.SERVICE_UNAVAILABLE;
                        case 1013 -> CommonErrorCode.DEPENDENCY_TIMEOUT;
                        default -> CommonErrorCode.UPSTREAM_PROTOCOL_ERROR;
                    };
                } catch (InvalidProtocolBufferException malformed) {
                    return CommonErrorCode.UPSTREAM_PROTOCOL_ERROR;
                }
            }
            return CommonErrorCode.UPSTREAM_PROTOCOL_ERROR;
        }
        return switch (transport) {
            case DEADLINE_EXCEEDED -> CommonErrorCode.DEPENDENCY_TIMEOUT;
            case UNAVAILABLE, UNAUTHENTICATED, PERMISSION_DENIED, CANCELLED -> CommonErrorCode.SERVICE_UNAVAILABLE;
            default -> CommonErrorCode.UPSTREAM_PROTOCOL_ERROR;
        };
    }
}
