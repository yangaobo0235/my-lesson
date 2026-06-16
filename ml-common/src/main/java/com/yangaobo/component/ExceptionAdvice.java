package com.yangaobo.component;

import com.yangaobo.exception.ServiceException;
import com.yangaobo.result.Result;
import com.yangaobo.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author 杨奥博
 */
@Slf4j
@RestControllerAdvice(basePackages = {"com.yangaobo"})
public class ExceptionAdvice {

    @ExceptionHandler(ServiceException.class)
    public Object serviceException(ServiceException e) {
        String coderMessage = e.getMessage();
        log.warn("业务异常: code={}, detail={}", e.getResultCode(), coderMessage);
        Result<Object> body = new Result<>(e.getResultCode(), e.getResultCode().getMESSAGE());
        if (e.getResultCode() == ResultCode.UNAUTHORIZED || e.getResultCode() == ResultCode.Token_EXPIRED) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
        if (e.getResultCode() == ResultCode.FORBIDDEN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        return body;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Object hibernateValidatorException(BindException e) {
        // 获取BindingResult
        BindingResult bindingResult = e.getBindingResult();
        // 获取BindingResult中所有属性错误信息集合中的第一个属性错误
        FieldError firstFieldError = bindingResult.getFieldErrors().get(0);
        // 异常信息 : "xxx实例的xxx属性校验失败: xxx异常信息"
        String coderMessage = String.format("%s实例的%s属性校验失败: %s",
                firstFieldError.getObjectName(),
                firstFieldError.getField(),
                firstFieldError.getDefaultMessage());
        // 记录日志
        log.warn(coderMessage);
        // 响应
        return new Result<>(ResultCode.ILLEGAL_PARAM, firstFieldError.getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public Object exception(Exception e) {
        log.error("未处理的服务器异常", e);
        return new Result<>(ResultCode.SERVER_ERROR, ResultCode.SERVER_ERROR.getMESSAGE());
    }
}
