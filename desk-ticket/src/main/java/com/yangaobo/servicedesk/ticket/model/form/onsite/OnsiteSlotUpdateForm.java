package com.yangaobo.servicedesk.ticket.model.form.onsite;

/**
 * 草稿时段修改；已发布窗口不可变。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.EqualsAndHashCode(callSuper = true)
public class OnsiteSlotUpdateForm extends OnsiteSlotForm {
    @java.io.Serial private static final long serialVersionUID = 1L;
    /** 草稿时段标识。 */
    @jakarta.validation.constraints.NotNull
    @com.yangaobo.servicedesk.common.validator.Identifier
    private String id;
    /** 读取时的版本。 */
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.PositiveOrZero
    private Long rowVersion;
}
