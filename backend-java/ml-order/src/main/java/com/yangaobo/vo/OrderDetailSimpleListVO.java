package com.yangaobo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** @author 杨奥博 */
@Schema(name = "订单明细全查VO")
@Data
public class OrderDetailSimpleListVO implements Serializable {
    @Schema(description = "主键")  
    private Long id;  
    @Schema(description = "课程标题")  
    private String courseTitle;  
}
