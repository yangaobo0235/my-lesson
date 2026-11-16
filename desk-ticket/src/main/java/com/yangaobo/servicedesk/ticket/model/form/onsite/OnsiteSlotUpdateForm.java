package com.yangaobo.servicedesk.ticket.model.form.onsite;

import com.yangaobo.servicedesk.common.validator.Identifier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.io.Serial;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 草稿时段修改；已发布窗口不可变。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OnsiteSlotUpdateForm extends OnsiteSlotForm {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草稿时段标识。
     */
    @NotNull
    @Identifier
    private String id;

    /**
     * 读取时的版本。
     */
    @NotNull
    @PositiveOrZero
    private Long rowVersion;
}
