package com.yangaobo.servicedesk.knowledge.component;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * OSS不可变版本回执，只在知识服务内部使用。
 *
 * @author yangaobo
 */
@Data
public class StoredOriginal implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 私有桶。
     */
    private String bucket;

    /**
     * 后端生成的不复用对象键。
     */
    private String key;

    /**
     * 版本桶实际返回的精确版本。
     */
    private String versionId;
}
