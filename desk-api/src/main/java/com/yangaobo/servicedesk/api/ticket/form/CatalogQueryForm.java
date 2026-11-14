package com.yangaobo.servicedesk.api.ticket.form;

/**
 * 知识规则的有界目录核验。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
public class CatalogQueryForm extends com.yangaobo.servicedesk.api.user.form.VerifySessionForm implements java.io.Serializable {
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 至多50个项目或型号。 */
    private java.util.Set<String> ids;
}
