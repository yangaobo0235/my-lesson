package com.yangaobo.servicedesk.ticket.exception;

import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.web.HttpErrorMapping;
import com.yangaobo.servicedesk.ticket.component.web.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 在MVC边界统一安全错误，保留框架真实状态与必要响应头。
 *
 * @author yangaobo
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 当前错误边界的安全运行日志。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 将业务、框架及未知错误转换为统一结果，不回显外部异常消息。
     *
     * @param exception 当前失败
     * @param request 原始请求
     * @return 真实HTTP状态及无数据错误
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handle(Exception exception, HttpServletRequest request) {
        String traceId = RequestTraceFilter.traceId(request);
        if (exception instanceof BusinessException business) {
            CommonErrorCode error = HttpErrorMapping.registered(business.getErrorCode());
            return ResponseEntity.status(HttpErrorMapping.statusOf(error))
                    .body(Result.failure(error, traceId));
        }
        if (exception instanceof ErrorResponse framework) {
            int status = framework.getStatusCode().value();
            return ResponseEntity.status(status).headers(framework.getHeaders())
                    .body(Result.failure(HttpErrorMapping.fromStatus(status), traceId));
        }
        if (exception instanceof HttpMessageNotReadableException
                || exception instanceof TypeMismatchException) {
            return ResponseEntity.badRequest()
                    .body(Result.failure(CommonErrorCode.INVALID_REQUEST, traceId));
        }
        // 不记录异常message中的正文、SQL参数或凭据；定位保留类型及请求标识。
        LOGGER.error("请求处理异常 traceId={} exceptionType={}", traceId, exception.getClass().getName());
        return ResponseEntity.internalServerError()
                .body(Result.failure(CommonErrorCode.INTERNAL_ERROR, traceId));
    }
}
