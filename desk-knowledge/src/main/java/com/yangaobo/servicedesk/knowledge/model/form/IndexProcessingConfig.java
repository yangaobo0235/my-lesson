package com.yangaobo.servicedesk.knowledge.model.form;

/**
 * 受限解析与向量空间配置。
 *
 * @author yangaobo
 */
@lombok.Data
@lombok.NoArgsConstructor
public class IndexProcessingConfig implements java.io.Serializable {
    /** 已按真实服务结果核验的固定解析合同。 */
    @jakarta.validation.constraints.Pattern(regexp = "mineru-tasks-v1-docvortex-middle-2.0")
    private String parserContract = "mineru-tasks-v1-docvortex-middle-2.0";
    @java.io.Serial private static final long serialVersionUID = 1L;

    /** 处理配置版本1。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "处理配置版本1")
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(1)
    private Integer schemaVersion = 1;

    /** MinerU档位。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "MinerU档位")
    @jakarta.validation.constraints.Pattern(regexp = "flash|basic|standard|advanced")
    private String parserTier = "basic";

    /** 固定向量空间。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "固定向量空间")
    @jakarta.validation.constraints.Pattern(regexp = "te4_1024_v1")
    private String vectorSpace = "te4_1024_v1";

    /** 固定向量模型。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "固定向量模型")
    @jakarta.validation.constraints.Pattern(regexp = "text-embedding-v4")
    private String embeddingModel = "text-embedding-v4";

    /** 切片字符预算。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "切片字符预算")
    @jakarta.validation.constraints.Min(200) @jakarta.validation.constraints.Max(2000)
    private Integer chunkChars = 1200;

    /** 物理页预算。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "物理页预算")
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(200)
    private Integer maxPages = 200;

    /** 片段数量预算。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "片段数量预算")
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(500)
    private Integer maxChunks = 500;

    /** 总执行秒数预算。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "总执行秒数预算")
    @jakarta.validation.constraints.Min(30) @jakarta.validation.constraints.Max(600)
    private Integer totalSeconds = 600;
}
