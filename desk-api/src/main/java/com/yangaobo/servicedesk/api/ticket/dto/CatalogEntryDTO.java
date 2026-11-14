package com.yangaobo.servicedesk.api.ticket.dto;

/**
 * 目录新增关联资格。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class CatalogEntryDTO implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 资源标识。 */
    private String id;

    /** 业务类别。 */
    private String category;

    /** 可用于新增知识关联。 */
    private boolean available;
}
