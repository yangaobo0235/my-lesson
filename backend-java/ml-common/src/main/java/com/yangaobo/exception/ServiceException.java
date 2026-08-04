package com.yangaobo.exception;

import com.yangaobo.result.ResultCode;
import lombok.Getter;

/**
 * @author 杨奥博
 */
@Getter
public class ServiceException extends RuntimeException {

    private final ResultCode resultCode;

    public ServiceException(ResultCode resultCode, String coderMessage) {
        super(coderMessage);
        this.resultCode = resultCode;
    }
}
