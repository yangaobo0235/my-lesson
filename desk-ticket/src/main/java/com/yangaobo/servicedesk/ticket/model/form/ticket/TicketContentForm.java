package com.yangaobo.servicedesk.ticket.model.form.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户反馈或处理说明的输入约束。
 *
 * @author yangaobo
 */
@Data
@lombok.EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketContentForm extends TicketActionForm implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 公开动作说明。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "公开动作说明。")
    @NotBlank
    @Size(max = 5000)
    private String content;
}
