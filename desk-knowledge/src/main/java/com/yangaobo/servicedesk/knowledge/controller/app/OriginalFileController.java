package com.yangaobo.servicedesk.knowledge.controller.app;

import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.knowledge.component.OssOriginalStorage;
import com.yangaobo.servicedesk.knowledge.model.entity.DocumentEntity;
import com.yangaobo.servicedesk.knowledge.model.enums.ContentFormatEnum;
import com.yangaobo.servicedesk.knowledge.model.form.KnowledgeReadForm;
import com.yangaobo.servicedesk.knowledge.security.CurrentActor;
import com.yangaobo.servicedesk.knowledge.service.KnowledgeReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 同源代理固定OSS版本，Range和流保持独立协议，不重定向外部对象。
 *
 * @author yangaobo
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "知识原件读取")
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class OriginalFileController {
    private final KnowledgeReadService knowledgeReadService;
    private final OssOriginalStorage storage;
    private final CurrentActor currentActor;

    /**
     * 浏览器每次读取均按当前知识授权。
     */
    @ApiResponse(
            responseCode = "200",
            description = "固定版本完整原件",
            content = {
                    @Content(
                            mediaType = "application/pdf",
                            schema = @Schema(type = "string", format = "binary")),
                    @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", format = "binary"))
            },
            headers = {
                    @Header(
                            name = "Accept-Ranges",
                            description = "bytes",
                            schema = @Schema(type = "string")),
                    @Header(
                            name = "Content-Length",
                            description = "本次响应字节数",
                            schema = @Schema(type = "integer", format = "int64")),
                    @Header(
                            name = "Content-Disposition",
                            description = "attachment及UTF-8文件名",
                            schema = @Schema(type = "string")),
                    @Header(
                            name = "Cache-Control",
                            description = "private, no-store",
                            schema = @Schema(type = "string"))
            })
    @ApiResponse(
            responseCode = "206",
            description = "单个字节范围原件",
            content = {
                    @Content(
                            mediaType = "application/pdf",
                            schema = @Schema(type = "string", format = "binary")),
                    @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", format = "binary"))
            },
            headers = {
                    @Header(
                            name = "Accept-Ranges",
                            description = "bytes",
                            schema = @Schema(type = "string")),
                    @Header(
                            name = "Content-Length",
                            description = "本次响应字节数",
                            schema = @Schema(type = "integer", format = "int64")),
                    @Header(
                            name = "Content-Disposition",
                            description = "attachment及UTF-8文件名",
                            schema = @Schema(type = "string")),
                    @Header(
                            name = "Cache-Control",
                            description = "private, no-store",
                            schema = @Schema(type = "string")),
                    @Header(
                            name = "Content-Range",
                            description = "bytes start-end/total",
                            schema = @Schema(type = "string"))
            })
    @ApiResponse(
            responseCode = "416",
            description = "范围不满足，JSON错误码1009",
            content =
            @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Result.class)),
            headers =
            @Header(
                    name = "Content-Range",
                    description = "bytes */total",
                    schema = @Schema(type = "string")))
    @GetMapping("/api/v1/knowledge/documents/{id}/file")
    @Operation(summary = "读取当前有权原件版本")
    public ResponseEntity<StreamingResponseBody> read(
            @PathVariable String id,
            @Valid KnowledgeReadForm form,
            @RequestHeader(value = "Range", required = false) String range) {
        return response(
                knowledgeReadService.readable(currentActor.requireIdentity(), id, form), range);
    }

    /**
     * 知识管理员可查看未发布的精确原件，不读取任何私有会话。
     */
    @ApiResponse(
            responseCode = "200",
            description = "固定版本完整原件",
            content = {
                    @Content(
                            mediaType = "application/pdf",
                            schema = @Schema(type = "string", format = "binary")),
                    @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", format = "binary"))
            },
            headers = {
                    @Header(
                            name = "Accept-Ranges",
                            description = "bytes",
                            schema = @Schema(type = "string")),
                    @Header(
                            name = "Content-Length",
                            description = "本次响应字节数",
                            schema = @Schema(type = "integer", format = "int64")),
                    @Header(
                            name = "Content-Disposition",
                            description = "attachment及UTF-8文件名",
                            schema = @Schema(type = "string")),
                    @Header(
                            name = "Cache-Control",
                            description = "private, no-store",
                            schema = @Schema(type = "string"))
            })
    @ApiResponse(
            responseCode = "206",
            description = "单个字节范围原件",
            content = {
                    @Content(
                            mediaType = "application/pdf",
                            schema = @Schema(type = "string", format = "binary")),
                    @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", format = "binary"))
            },
            headers = {
                    @Header(
                            name = "Accept-Ranges",
                            description = "bytes",
                            schema = @Schema(type = "string")),
                    @Header(
                            name = "Content-Length",
                            description = "本次响应字节数",
                            schema = @Schema(type = "integer", format = "int64")),
                    @Header(
                            name = "Content-Disposition",
                            description = "attachment及UTF-8文件名",
                            schema = @Schema(type = "string")),
                    @Header(
                            name = "Cache-Control",
                            description = "private, no-store",
                            schema = @Schema(type = "string")),
                    @Header(
                            name = "Content-Range",
                            description = "bytes start-end/total",
                            schema = @Schema(type = "string"))
            })
    @ApiResponse(
            responseCode = "416",
            description = "范围不满足，JSON错误码1009",
            content =
            @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Result.class)),
            headers =
            @Header(
                    name = "Content-Range",
                    description = "bytes */total",
                    schema = @Schema(type = "string")))
    @GetMapping("/api/v1/admin/document/{id}/file")
    @Operation(summary = "预览管理范围原件")
    public ResponseEntity<StreamingResponseBody> preview(
            @PathVariable String id,
            @RequestHeader(value = "Range", required = false) String range) {
        return response(knowledgeReadService.manageable(currentActor.requireIdentity(), id), range);
    }

    /**
     * 流提交前解析单范围及打开固定版本；提交后故障中断传输。
     */
    private ResponseEntity<StreamingResponseBody> response(DocumentEntity document, String range) {
        long total = document.getFileSizeBytes(), start = 0, end = total - 1;
        boolean partial = range != null;
        if (partial) {
            var matcher = Pattern.compile("bytes=(\\d*)-(\\d*)").matcher(range);
            try {
                if (!matcher.matches()
                        || (matcher.group(1).isEmpty() && matcher.group(2).isEmpty()))
                    throw new IllegalArgumentException();
                if (matcher.group(1).isEmpty()) {
                    long suffix = Long.parseLong(matcher.group(2));
                    if (suffix < 1) throw new IllegalArgumentException();
                    start = Math.max(0, total - suffix);
                } else {
                    start = Long.parseLong(matcher.group(1));
                    if (!matcher.group(2).isEmpty())
                        end = Math.min(end, Long.parseLong(matcher.group(2)));
                }
                if (start < 0 || start >= total || end < start)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) {
                var failure =
                        new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
                failure.getHeaders().add("Content-Range", "bytes */" + total);
                throw failure;
            }
        }
        var original = storage.open(document, partial ? "bytes=" + start + "-" + end : null);
        long length = end - start + 1;
        if (original.contentLength() == null
                || original.contentLength() != length
                || !document.getStorageVersionId().equals(original.versionId())) {
            try {
                original.close();
            } catch (Exception ignored) {
                original.abort();
            }
            throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
        }
        StreamingResponseBody body =
                output -> {
                    try (original) {
                        byte[] buffer = new byte[65536];
                        long sent = 0;
                        int count;
                        while ((count = original.body().read(buffer)) != -1) {
                            if (sent + count > length) throw new IOException("原件总量不一致");
                            output.write(buffer, 0, count);
                            sent += count;
                        }
                        if (sent != length) throw new IOException("原件流未完整结束");
                    } catch (Exception failure) {
                        throw new IOException("原件传输中断", failure);
                    }
                };
        var builder =
                ResponseEntity.status(partial ? 206 : 200)
                        .header("Accept-Ranges", "bytes")
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "sandbox")
                        .header("Cache-Control", "private, no-store")
                        .header(
                                "Content-Disposition",
                                ContentDisposition.attachment()
                                        .filename(
                                                document.getOriginalFileName(),
                                                StandardCharsets.UTF_8)
                                        .build()
                                        .toString())
                        .header(
                                "Content-Type",
                                ContentFormatEnum.PDF.getValue().equals(document.getContentFormat())
                                        ? "application/pdf"
                                        : "text/plain; charset=utf-8")
                        .contentLength(length);
        if (partial) builder.header("Content-Range", "bytes " + start + "-" + end + "/" + total);
        return builder.body(body);
    }
}
