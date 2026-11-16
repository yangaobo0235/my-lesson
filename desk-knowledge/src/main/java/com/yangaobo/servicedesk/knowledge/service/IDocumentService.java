package com.yangaobo.servicedesk.knowledge.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.IdVersionForm;
import com.yangaobo.servicedesk.common.base.PageVO;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentPublishForm;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentUpdateForm;
import com.yangaobo.servicedesk.knowledge.model.form.DocumentUploadForm;
import com.yangaobo.servicedesk.knowledge.model.form.KnowledgeQueryForm;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentEventVO;
import com.yangaobo.servicedesk.knowledge.model.vo.DocumentVO;

import java.io.IOException;
import java.io.InputStream;

/**
 * 原件上传、草稿、发布替换及授权管理的完整用例。
 *
 * @author yangaobo
 */
public interface IDocumentService extends IService<DocumentEntity> {
    /**
     * 原键先查回执，文件校验与OSS上传在数据库事务外。
     */
    DocumentVO upload(
            IdentityDTO actor,
            DocumentUploadForm form,
            String filename,
            String contentType,
            InputStream input)
            throws IOException;

    /**
     * 管理查询或按当前上传者原键查询。
     */
    DocumentVO query(IdentityDTO actor, KnowledgeQueryForm form);

    /**
     * 管理员分页查看修订，不暴露OSS定位。
     */
    PageVO<DocumentVO> list(IdentityDTO actor, KnowledgeQueryForm form);

    /**
     * 只在草稿修改元数据及适用规则。
     */
    DocumentVO update(IdentityDTO actor, DocumentUpdateForm form);

    /**
     * 首次发布或同事务替换当前修订。
     */
    DocumentVO publish(IdentityDTO actor, DocumentPublishForm form);

    /**
     * 停用立即撤销召回，恢复重新校验成功索引及来源。
     */
    DocumentVO availability(IdentityDTO actor, IdVersionForm form, boolean restore);

    /**
     * 仅从未发布且所有索引和解析已安全收敛的草稿可逻辑删除。
     */
    void deleteDraft(IdentityDTO actor, IdVersionForm form);

    /**
     * 查询不可变动作审计。
     */
    PageVO<DocumentEventVO> events(IdentityDTO actor, KnowledgeQueryForm form);
}
