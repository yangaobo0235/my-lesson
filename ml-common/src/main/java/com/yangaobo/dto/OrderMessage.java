package com.yangaobo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 杨奥博
 */
@Data
public class OrderMessage implements Serializable {
    private Long fkUserId;
    private Long fkCourseId;
    private Double skPrice;
    private Double price;
}
