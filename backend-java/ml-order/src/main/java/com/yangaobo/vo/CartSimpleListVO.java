package com.yangaobo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** @author 杨奥博 */
@Schema(name = "购物车全查VO")
@Data
public class CartSimpleListVO implements Serializable {
    @Schema(description = "主键")  
    private Long id;  
    @Schema(description = "用户账号")  
    private String username;  
    @Schema(description = "课程标题")  
    private String courseTitle;  
}
