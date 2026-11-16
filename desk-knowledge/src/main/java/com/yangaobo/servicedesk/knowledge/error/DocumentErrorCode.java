package com.yangaobo.servicedesk.knowledge.error;

import com.yangaobo.servicedesk.common.base.ErrorCode;

/**
 * 原件上传的稳定业务拒绝合同。
 *
 * @author yangaobo
 */
public enum DocumentErrorCode implements ErrorCode {
    /**
     * 同上传请求仍在执行。
     */
    UPLOAD_IN_PROGRESS(4001, "原上传请求仍在处理，请沿原键查询或重试"),
    /**
     * PDF结构或类型不合法。
     */
    INVALID_PDF_TYPE(4002, "文件不符合PDF结构或类型要求"),
    /**
     * 原始PDF为空。
     */
    EMPTY_PDF(4003, "PDF内容为空"),
    /**
     * 索引或来源复核尚不满足发布条件。
     */
    PUBLICATION_NOT_READY(4201, "索引或来源复核尚不满足发布条件");

    /**
     * 稳定编号。
     */
    private final int code;

    /**
     * 可公开说明。
     */
    private final String message;

    /**
     * 固定已登记合同。
     */
    DocumentErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int code() {
        return code;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String message() {
        return message;
    }
}
