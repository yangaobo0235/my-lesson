package com.yangaobo.servicedesk.ticket.component.web;

import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.ErrorCode;
import com.yangaobo.servicedesk.ticket.component.RemoteDraftError;
import com.yangaobo.servicedesk.ticket.component.RemoteIdentityError;
import com.yangaobo.servicedesk.ticket.error.BookingErrorCode;
import com.yangaobo.servicedesk.ticket.error.DeviceErrorCode;
import com.yangaobo.servicedesk.ticket.error.SupportErrorCode;
import com.yangaobo.servicedesk.ticket.error.TicketErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 在所属应用显式转换公共错误与HTTP状态，不按编号推断状态。
 *
 * @author yangaobo
 */
public final class HttpErrorMapping {

    /**
     * 禁止创建无状态映射工具实例。
     */
    private HttpErrorMapping() {
    }

    /**
     * 确认错误类型已登记，未知类型安全回退系统异常。
     *
     * @param error 业务错误
     * @return 可公开展示的已登记错误
     */
    public static ErrorCode registered(ErrorCode error) {
        return error instanceof CommonErrorCode
                || error instanceof RemoteIdentityError
                || error instanceof SupportErrorCode
                || error instanceof DeviceErrorCode
                || error instanceof BookingErrorCode
                || error instanceof TicketErrorCode
                || error instanceof RemoteDraftError
                ? error
                : CommonErrorCode.INTERNAL_ERROR;
    }

    /**
     * 从已登记错误选择HTTP状态。
     *
     * @param error 业务错误
     * @return 明确的HTTP状态
     */
    public static HttpStatus statusOf(ErrorCode error) {
        if (error instanceof RemoteIdentityError remote)
            return remote == RemoteIdentityError.PASSWORD_CHANGE_REQUIRED
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.UNAUTHORIZED;
        if (error instanceof SupportErrorCode
                || error instanceof DeviceErrorCode
                || error instanceof BookingErrorCode
                || error instanceof TicketErrorCode
                || error instanceof RemoteDraftError) return HttpStatus.CONFLICT;
        return switch ((CommonErrorCode) registered(error)) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT, DUPLICATE_VALUE -> HttpStatus.CONFLICT;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case RANGE_NOT_SATISFIABLE -> HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case UPSTREAM_PROTOCOL_ERROR -> HttpStatus.BAD_GATEWAY;
            case DEPENDENCY_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 为框架拒绝选择安全码，调用方继续保留框架原状态及必要头。
     *
     * @param status 框架HTTP状态
     * @return 已登记的公共错误
     */
    public static CommonErrorCode fromStatus(int status) {
        return switch (status) {
            case 403 -> CommonErrorCode.FORBIDDEN;
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> CommonErrorCode.CONFLICT;
            case 413 -> CommonErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 416 -> CommonErrorCode.RANGE_NOT_SATISFIABLE;
            case 429 -> CommonErrorCode.TOO_MANY_REQUESTS;
            case 502 -> CommonErrorCode.UPSTREAM_PROTOCOL_ERROR;
            case 503 -> CommonErrorCode.SERVICE_UNAVAILABLE;
            case 504 -> CommonErrorCode.DEPENDENCY_TIMEOUT;
            default -> status >= 400 && status < 500
                    ? CommonErrorCode.INVALID_REQUEST
                    : CommonErrorCode.INTERNAL_ERROR;
        };
    }
}
