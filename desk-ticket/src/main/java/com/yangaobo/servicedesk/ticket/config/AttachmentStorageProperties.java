package com.yangaobo.servicedesk.ticket.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * ticket 独占的附件桶与最小权限凭据，不复用知识存储身份。
 *
 * @author yangaobo
 */
@Data
@Validated
@ConfigurationProperties("desk.attachment-storage")
public class AttachmentStorageProperties implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * OSS 区域。
     */
    @NotBlank
    private String region;
    /**
     * 无凭据的 HTTPS 根地址。
     */
    @NotBlank
    private String endpoint;
    /**
     * 本服务私有且开启版本管理的桶。
     */
    @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
    @NotBlank
    private String bucket;
    /**
     * 访问标识。
     */
    @NotBlank
    @ToString.Exclude
    private String accessKeyId;
    /**
     * 访问密钥。
     */
    @NotBlank
    @ToString.Exclude
    private String accessKeySecret;
    /**
     * ticket 环境专属对象前缀。
     */
    @NotBlank
    @Pattern(regexp = "ticket/(test|ci|prod)/attachments/")
    private String objectPrefix;
}
