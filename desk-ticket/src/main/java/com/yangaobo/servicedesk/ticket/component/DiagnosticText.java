package com.yangaobo.servicedesk.ticket.component;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.model.enums.AttachmentFormatEnum;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 有界诊断文本校验，不执行日志、JSON 或其中的指令。
 *
 * @author yangaobo
 */
public final class DiagnosticText {
    /**
     * 原件和脱敏文本共同的字节上限。
     */
    public static final int MAX_BYTES = 1048576;
    /**
     * 严格限制 JSON 深度及标量大小的专属解析器，不修改应用全局设置。
     */
    private static final JsonFactory JSON = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                    .maxStringLength(MAX_BYTES).maxNumberLength(1000).build()).build();

    /**
     * 禁止实例化无状态校验器。
     */
    private DiagnosticText() {
    }

    /**
     * 严格 UTF-8 编码，拒绝孤立代理字符，不允许摘要对替换后的字节计算。
     */
    public static byte[] encode(String text) {
        if (text == null || text.isBlank()) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        try {
            ByteBuffer value = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(text));
            if (value.remaining() > MAX_BYTES) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
            byte[] bytes = new byte[value.remaining()];
            value.get(bytes);
            return bytes;
        } catch (CharacterCodingException failure) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 校验完整单一 JSON 值、深度及文本编码；不接受尾随第二份载荷。
     */
    public static String validate(byte[] bytes, String format) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        try {
            AttachmentFormatEnum.valueOf(format);
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.indexOf('\0') >= 0 || text.isBlank()) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
            if (AttachmentFormatEnum.JSON.getValue().equals(format)) {
                try (var parser = JSON.createParser(text)) {
                    if (parser.nextToken() == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                    parser.skipChildren();
                    if (parser.nextToken() != null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                }
            }
            return text;
        } catch (IOException | IllegalArgumentException | NullPointerException failure) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 摘要绑定精确字节，不进行换行或空白归一化。
     */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
