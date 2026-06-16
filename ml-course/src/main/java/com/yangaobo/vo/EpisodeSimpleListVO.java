package com.yangaobo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @author 杨奥博
 */
@Schema(name = "集次全查VO")
@Data
public class EpisodeSimpleListVO implements Serializable {
    @Schema(description = "主键")  
    private Long id;  
    @Schema(description = "标题")  
    private String title;  
}
