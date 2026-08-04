package com.yangaobo.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author 杨奥博
 */
@Data
public class OrderMessage implements Serializable {
    private UUID requestId;
    private UUID qualificationId;
    private Long fkSeckillId;
    private Long fkUserId;
    private Long fkCourseId;
    private Double skPrice;
    private Double price;
}
