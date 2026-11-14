package com.yangaobo.servicedesk.ticket.model.vo.ticket;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 受理时资产与型号的安全历史投影，不含序列号。
 *
 * @author yangaobo
 */
@Data
public class DeviceSnapshotVO implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** 客户资产编号。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "客户资产编号。")
    private String assetCode;

    /** 产品型号标识。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "产品型号标识。")
    private String productModelId;

    /** 品牌。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "品牌。")
    private String brand;

    /** 准确型号。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "准确型号。")
    private String modelName;
}
