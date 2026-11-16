package com.yangaobo.servicedesk.knowledge.controller.common;

import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.knowledge.component.web.HttpErrorMapping;
import com.yangaobo.servicedesk.knowledge.component.web.RequestTraceFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理Servlet容器错误分派，避免旁路返回默认HTML或不一致错误体。
 *
 * @author yangaobo
 */
@RestController
public class ApiErrorController implements ErrorController {

    /**
     * 保留容器失败状态并使用安全响应。
     *
     * @param request 容器错误分派请求
     * @return 无内部堆栈或请求数据的统一错误
     */
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<Result<Void>> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer code && code >= 400 && code <= 599 ? code : 500;
        return ResponseEntity.status(status)
                .body(
                        Result.failure(
                                HttpErrorMapping.fromStatus(status),
                                RequestTraceFilter.traceId(request)));
    }
}
