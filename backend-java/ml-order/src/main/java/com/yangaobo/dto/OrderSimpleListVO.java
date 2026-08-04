package com.yangaobo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** @author 杨奥博 */
@Schema(name = "订单全查VO")
@Data
public class OrderSimpleListVO implements Serializable {
    @Schema(description = "主键")  
    private Long id;  
    @Schema(description = "订单编号")  
    private String sn;  
}
