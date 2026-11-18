package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.validator.Identifier;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * 请求客户补充信息，可采用经人工确认的不可变草稿。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketInformationForm extends TicketWaitForm {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 可选的已确认草稿修订。
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
    @Pattern(regexp = "[0-9a-f]{64}")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String contentSha256;
}
