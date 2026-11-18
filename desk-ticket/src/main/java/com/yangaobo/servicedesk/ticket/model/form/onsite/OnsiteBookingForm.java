package com.yangaobo.servicedesk.ticket.model.form.onsite;

import com.yangaobo.servicedesk.common.validator.Identifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户原键预约输入。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
public class OnsiteBookingForm extends com.yangaobo.servicedesk.common.base.VersionForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 时段标识。
     */
    @Schema(description = "时段标识")
    @NotNull
    @Identifier
    private String slotId;

    /**
     * 原请求键。
     */
    @Schema(description = "原请求键")
    @NotNull
    @Identifier
    private String clientRequestId;

    /**
     * 可选的已确认预约草稿修订。
     */
    @Identifier
    private String sourceDraftId;

    /**
     * 草稿根用于正式回执唯一采用。
     */
    @Identifier
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String sourceDraftRootId;

    /**
     * 人工确认内容的SHA256。
     */
    @jakarta.validation.constraints.Pattern(regexp = "[0-9a-f]{64}")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String contentSha256;
}
