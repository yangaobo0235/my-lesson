package com.yangaobo.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** @author 杨奥博 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Schema(name = "购物车分页DTO")
@Data
public class CartPageDTO extends PageDTO {
    @Schema(description = "用户ID，用户表外键")
    private Long fkUserId;
    @Schema(description = "课程ID，课程表外键")
    private Long fkCourseId;
}