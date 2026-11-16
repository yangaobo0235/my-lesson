package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 受理时资产与型号的安全历史投影，不含序列号。
 *
 * @author yangaobo
 */
@Data
public class DeviceSnapshotVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 客户资产编号。
     */
    @Schema(description = "客户资产编号。")
    private String assetCode;

    /**
     * 产品型号标识。
     */
    @Schema(description = "产品型号标识。")
    private String productModelId;

    /**
     * 品牌。
     */
    @Schema(description = "品牌。")
    private String brand;

    /**
     * 准确型号。
     */
    @Schema(description = "准确型号。")
    private String modelName;
}
