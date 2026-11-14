package com.yangaobo.servicedesk.ticket.model.vo.support;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前有效服务项目的公开输出。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class EntitledServiceVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 当前项目条件。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "当前项目条件。")
    private ServiceItemVO item;

    /** 资格首日，含当天。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "资格首日，含当天。")
    private java.time.LocalDate startsOn;

    /** 资格末日，含当天。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "资格末日，含当天。")
    private java.time.LocalDate endsOn;
}
