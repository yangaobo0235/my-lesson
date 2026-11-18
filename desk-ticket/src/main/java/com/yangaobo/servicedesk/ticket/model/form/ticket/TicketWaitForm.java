package com.yangaobo.servicedesk.ticket.model.form.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.time.Instant;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 进入或续期等待时明确原因和下一次跟进时刻，不提供默认期限。
 *
 * @author yangaobo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TicketWaitForm extends TicketActionForm {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 等待原因或本次跟进说明，同时进入公开时间线。
     */
    @NotBlank
    @Size(max = 1000)
    @Schema(description = "等待原因或本次跟进说明，最多1000字符")
    private String content;

    /**
     * 下一次跟进的绝对时刻，取得工单锁后与数据库时间比较。
     */
    @NotNull
    @Schema(description = "下一次跟进时刻，必须晚于本次动作决策时刻")
    private Instant followUpAt;
}
