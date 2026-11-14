package com.yangaobo.servicedesk.knowledge.component;

import com.yangaobo.servicedesk.knowledge.model.enums.ContentFormatEnum;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.knowledge.config.OssProperties;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;

/**
 * knowledge独占OSS凭据，所有读取固定对象版本且不提供签名URL。
 *
 * @author yangaobo
 */
@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class OssOriginalStorage {
    /** 只记录安全错误类别及HTTP状态，不记录桶、对象、请求或凭据。 */
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(OssOriginalStorage.class);
    private final OSSClient client;
    private final OssProperties properties;
    /** 外部上传发生在数据库事务外，未知结果不推断对象不存在。 */
    public StoredOriginal put(java.nio.file.Path file,String format,long size) {
        String key=properties.getObjectPrefix()+java.util.UUID.randomUUID().toString().replace("-","")+(ContentFormatEnum.PDF.getValue().equals(format)?".pdf":".md");
        try(var input=java.nio.file.Files.newInputStream(file)) {
            var result=client.putObject(PutObjectRequest.newBuilder().bucket(properties.getBucket()).key(key)
                .forbidOverwrite(true).contentType(ContentFormatEnum.PDF.getValue().equals(format)?"application/pdf":"text/markdown; charset=utf-8")
                .contentLength(Math.toIntExact(size)).body(BinaryData.fromStream(input,size)).build());
            if(result.versionId()==null || result.versionId().isBlank() || "null".equals(result.versionId()))
                throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
            var stored=new StoredOriginal();stored.setBucket(properties.getBucket());stored.setKey(key);stored.setVersionId(result.versionId());return stored;
        }catch(BusinessException failure){throw failure;}
        catch(Exception failure){
            var remote=com.aliyun.sdk.service.oss2.exceptions.ServiceException.asCause(failure);
            LOGGER.warn("原件写入未确认 errorType={} status={} remoteCode={}",failure.getClass().getSimpleName(),
                remote==null?null:remote.statusCode(),remote!=null && remote.errorCode()!=null && remote.errorCode().matches("[A-Za-z0-9]{1,80}")?remote.errorCode():"UNKNOWN");
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }
    /** 调用者拥有关闭结果及流的责任，Range由已校验边界提供。 */
    public GetObjectResult open(DocumentEntity document,String range) {
        if(!properties.getBucket().equals(document.getStorageBucket()) || !document.getStorageKey().startsWith(properties.getObjectPrefix()))
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        try {
            var builder=GetObjectRequest.newBuilder().bucket(document.getStorageBucket()).key(document.getStorageKey()).versionId(document.getStorageVersionId());
            if(range!=null)builder.range(range);
            return client.getObject(builder.build());
        }catch(Exception failure){throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);}
    }
}
