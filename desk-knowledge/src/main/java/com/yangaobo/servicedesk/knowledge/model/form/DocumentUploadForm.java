package com.yangaobo.servicedesk.knowledge.model.form;

import com.yangaobo.servicedesk.common.annotation.SchemaEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.yangaobo.servicedesk.common.validator.Identifier;
import com.yangaobo.servicedesk.knowledge.model.enums.ContentFormatEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 校验后的原件元数据。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class DocumentUploadForm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前上传者的原请求键。
     */
    @Schema(description = "当前上传者的原请求键")
    @NotNull
    @Identifier
    private String clientUploadId;

    /**
     * 所属系列。
     */
    @Schema(description = "所属系列")
    @NotNull
    @Identifier
    private String seriesId;

    /**
     * PDF或MARKDOWN。
     */
    @Schema(description = "PDF或MARKDOWN")
    @NotNull
    @SchemaEnum(ContentFormatEnum.class)
    @Pattern(regexp = "PDF|MARKDOWN")
    private String contentFormat;

    /**
     * 资料标题。
     */
    @Schema(description = "资料标题")
    @NotBlank
    @Size(max = 255)
    private String title;

    /**
     * 人工登记的来源说明链接，不自动抓取。
     */
    @Schema(description = "人工登记的来源说明链接，不自动抓取")
    @Size(max = 2000)
    private String sourceUrl;

    /**
     * 来源与使用依据复核记录，发布必填。
     */
    @Schema(description = "来源与使用依据复核记录，发布必填")
    @Size(max = 1000)
    private String sourceReviewNote;

    /**
     * 原件修订标签。
     */
    @Schema(description = "原件修订标签")
    @Size(max = 100)
    private String revisionLabel;

    /**
     * 原件发布日期，可空。
     */
    @Schema(description = "原件发布日期，可空")
    private LocalDate issuedOn;

    /**
     * 人可读的适用说明。
     */
    @Schema(description = "人可读的适用说明")
    @Size(max = 500)
    private String applicabilityNote;

    /**
     * 可空人工规程；上传后固定，内容由有限白名单合同校验。
     */
    @Schema(description = "修订内人工规程，最多20项检查及64KiB，创建后不可修改")
    private JsonNode procedureDefinition;

    /**
     * 显式JSON null与未提供规程含义一致，不把NullNode当作待验证规程。
     *
     * @param procedureDefinition 可空修订规程
     */
    public void setProcedureDefinition(JsonNode procedureDefinition) {
        this.procedureDefinition = procedureDefinition == null || procedureDefinition.isNull()
                ? null : procedureDefinition;
    }

    /**
     * 一条内AND，多条间OR。
     */
    @Schema(description = "一条内AND，多条间OR")
    @Valid
    @NotEmpty
    @Size(max = 50)
    private List<DocumentRuleForm> rules;
}
