package com.yangaobo.servicedesk.knowledge.model.dto;

import com.yangaobo.servicedesk.knowledge.model.form.IndexProcessingConfig;

/**
 * 索引v2完整信封，SUBMIT和CANCEL共享不可变输入摘要。
 *
 * @author yangaobo
 */
@lombok.Data
public class IndexCommand implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;
    /** 线信封版本，固定2。 */
    private int schemaVersion = 2;
    /** SUBMIT或CANCEL。 */
    private String command;
    /** 本次指令操作ID。 */
    private String operationId;
    /** 固定尝试身份。 */
    private String jobId;
    /** 固定原件修订。 */
    private String documentId;
    /** 文档内正序尝试。 */
    private int attemptNumber;
    /** PDF或MARKDOWN。 */
    private String contentFormat;
    /** 原件字节摘要。 */
    private String fileSha256;
    /** 原发起管理员，仅审计。 */
    private String requestedBy;
    /** 受限处理配置。 */
    private IndexProcessingConfig processingConfig;
    /** 不含指令类型、操作ID及追踪的不可变输入摘要。 */
    private String requestHash;
}
