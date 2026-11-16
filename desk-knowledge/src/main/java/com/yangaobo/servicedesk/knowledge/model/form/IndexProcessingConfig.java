package com.yangaobo.servicedesk.knowledge.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 受限解析与向量空间配置。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class IndexProcessingConfig implements Serializable {
    /**
     * 已按真实服务结果核验的固定解析合同。
     */
    @Pattern(regexp = "mineru-tasks-v1-docvortex-middle-2.0")
    private String parserContract = "mineru-tasks-v1-docvortex-middle-2.0";

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 处理配置版本1。
     */
    @Schema(description = "处理配置版本1")
    @Min(1)
    @Max(1)
    private Integer schemaVersion = 1;

    /**
     * MinerU档位。
     */
    @Schema(description = "MinerU档位")
    @Pattern(regexp = "flash|basic|standard|advanced")
    private String parserTier = "basic";

    /**
     * 固定向量空间。
     */
    @Schema(description = "固定向量空间")
    @Pattern(regexp = "te4_1024_v1")
    private String vectorSpace = "te4_1024_v1";

    /**
     * 固定向量模型。
     */
    @Schema(description = "固定向量模型")
    @Pattern(regexp = "text-embedding-v4")
    private String embeddingModel = "text-embedding-v4";

    /**
     * 切片字符预算。
     */
    @Schema(description = "切片字符预算")
    @Min(200)
    @Max(2000)
    private Integer chunkChars = 1200;

    /**
     * 物理页预算。
     */
    @Schema(description = "物理页预算")
    @Min(1)
    @Max(200)
    private Integer maxPages = 200;

    /**
     * 片段数量预算。
     */
    @Schema(description = "片段数量预算")
    @Min(1)
    @Max(500)
    private Integer maxChunks = 500;

    /**
     * 总执行秒数预算。
     */
    @Schema(description = "总执行秒数预算")
    @Min(30)
    @Max(600)
    private Integer totalSeconds = 600;
}
