package com.yangaobo.servicedesk.ticket.model.vo.support;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前有效服务项目的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class EntitledServiceVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前项目条件。
     */
    @Schema(description = "当前项目条件。")
    private ServiceItemVO item;

    /**
     * 资格首日，含当天。
     */
    @Schema(description = "资格首日，含当天。")
    private LocalDate startsOn;

    /**
     * 资格末日，含当天。
     */
    @Schema(description = "资格末日，含当天。")
    private LocalDate endsOn;
}
