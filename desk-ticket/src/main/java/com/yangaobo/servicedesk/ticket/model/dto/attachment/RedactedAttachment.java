package com.yangaobo.servicedesk.ticket.model.dto.attachment;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 已校验的固定脱敏版本，仅用于内部流传输，不含原件存储身份。
 *
 * @author yangaobo
 */
@Getter
@AllArgsConstructor
public class RedactedAttachment implements Serializable {
    /**
     * 本地传输投影序列化版本。
     */
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 明确确认的脱敏版本。
     */
    private final long redactionVersion;
    /**
     * 上传者确认后的正文格式。
     */
    private final String contentFormat;
    /**
     * UTF-8正文摘要。
     */
    private final String sha256;
    /**
     * 有界UTF-8字节，仅交内部传输，不进入日志。
     */
    private final byte[] bytes;
}
