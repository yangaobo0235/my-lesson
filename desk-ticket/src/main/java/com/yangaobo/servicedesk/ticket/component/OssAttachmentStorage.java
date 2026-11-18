package com.yangaobo.servicedesk.ticket.component;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.HeadObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.config.AttachmentStorageProperties;
import com.yangaobo.servicedesk.ticket.model.entity.AttachmentEntity;

import java.io.ByteArrayInputStream;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ticket 独立凭据的 OSS 适配，供应商对象及异常不泄露到业务层。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class OssAttachmentStorage implements AttachmentStorage {
    private final OSSClient client;
    private final AttachmentStorageProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public String bucket() {
        return properties.getBucket();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String key(String attachmentId) {
        return properties.getObjectPrefix() + attachmentId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String put(AttachmentEntity intent, byte[] bytes) {
        checkLocation(intent);
        try {
            var result = client.putObject(PutObjectRequest.newBuilder().bucket(intent.getStorageBucket())
                    .key(intent.getStorageKey()).forbidOverwrite(true).contentType("application/octet-stream")
                    .contentLength(bytes.length).body(BinaryData.fromStream(new ByteArrayInputStream(bytes), (long) bytes.length)).build());
            return version(result.versionId());
        } catch (Exception failure) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String locate(AttachmentEntity intent) {
        checkLocation(intent);
        try {
            return version(client.headObject(HeadObjectRequest.newBuilder().bucket(intent.getStorageBucket())
                    .key(intent.getStorageKey()).build()).versionId());
        } catch (Exception failure) {
            var remote = ServiceException.asCause(failure);
            if (remote != null && remote.statusCode() == 404 && "NoSuchKey".equals(remote.errorCode())) return null;
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] read(AttachmentEntity attachment) {
        checkLocation(attachment);
        String selectedVersion = version(attachment.getStorageVersionId());
        try (var result = client.getObject(GetObjectRequest.newBuilder().bucket(attachment.getStorageBucket())
                .key(attachment.getStorageKey()).versionId(selectedVersion).build());
             var input = result.body()) {
            byte[] bytes = input.readNBytes(DiagnosticText.MAX_BYTES + 1);
            if (!selectedVersion.equals(result.versionId()) || bytes.length != attachment.getFileSizeBytes()
                    || !DiagnosticText.sha256(bytes).equals(attachment.getFileSha256()))
                throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
            return bytes;
        } catch (BusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 防止错误配置或持久定位越过当前服务存储边界。
     */
    private void checkLocation(AttachmentEntity value) {
        if (!bucket().equals(value.getStorageBucket()) || !key(value.getId()).equals(value.getStorageKey()))
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    /**
     * 未开启版本管理的 null 版本不具备可审计的原件身份。
     */
    private String version(String value) {
        if (value == null || value.isBlank() || "null".equals(value) || value.length() > 1024)
            throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
        return value;
    }
}
