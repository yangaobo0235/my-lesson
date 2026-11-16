package com.yangaobo.servicedesk.ticket.model.form.ticket;

import com.yangaobo.servicedesk.common.validator.Identifier;
import com.yangaobo.servicedesk.ticket.model.form.support.SupportContextForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户直接提单的输入约束。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class TicketCreateForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本次建单原请求键。
     */
    @Schema(description = "本次建单原请求键。")
    @NotBlank
    @Identifier
    private String clientRequestId;

    /**
     * 可空表示待分类，仅承诺分流。
     */
    @Schema(description = "可空表示待分类，仅承诺分流。")
    @Identifier
    private String serviceItemId;

    /**
     * 可选同客户设备。
     */
    @Schema(description = "可选同客户设备。")
    @Identifier
    private String deviceId;

    /**
     * 问题标题。
     */
    @Schema(description = "问题标题。")
    @NotBlank
    @Size(max = 200)
    private String title;

    /**
     * 故障现象。
     */
    @Schema(description = "故障现象。")
    @NotBlank
    @Size(max = 5000)
    private String symptom;

    /**
     * 已尝试操作，可空。
     */
    @Schema(description = "已尝试操作，可空。")
    @Size(max = 5000)
    private String attemptedActions;

    /**
     * 固定环境，由后端标注来源。
     */
    @Schema(description = "固定环境，由后端标注来源。")
    @Valid
    @NotNull
    private SupportContextForm supportContext;

    /**
     * 可选现场区域。
     */
    @Schema(description = "可选现场区域。")
    @Pattern(regexp = "[A-Z0-9_-]{1,32}")
    private String serviceAreaCode;

    /**
     * 可选现场地点。
     */
    @Schema(description = "可选现场地点。")
    @Size(max = 300)
    private String serviceLocation;

    /**
     * 可选AI草稿，真实草稿消费者接入前不接受非空。
     */
    @Schema(description = "可选AI草稿，真实草稿消费者接入前不接受非空。")
    @Identifier
    private String sourceDraftId;
}
