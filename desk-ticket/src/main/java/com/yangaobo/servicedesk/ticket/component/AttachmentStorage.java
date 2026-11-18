package com.yangaobo.servicedesk.ticket.component;

import com.yangaobo.servicedesk.ticket.model.entity.AttachmentEntity;

/**
 * 附件私有存储边界，任何外写错误都不能推断为对象不存在。
 *
 * @author yangaobo
 */
public interface AttachmentStorage {
    /**
     * 为新意图确定本服务桶，不接受用户路径。
     */
    String bucket();

    /**
     * 按本服务前缀及新附件身份分配不可复用对象键。
     */
    String key(String attachmentId);

    /**
     * 仅新意图调用一次禁止覆盖 PUT，成功须返回精确版本。
     */
    String put(AttachmentEntity intent, byte[] bytes);

    /**
     * 对原键查证版本；不存在返回空，但不授权自动重传。
     */
    String locate(AttachmentEntity intent);

    /**
     * 有界读取完整精确版本，核对长度和摘要后返回。
     */
    byte[] read(AttachmentEntity attachment);
}
