package com.yangaobo.servicedesk.api;

import com.yangaobo.servicedesk.api.common.v1.ActorRef;
import com.yangaobo.servicedesk.api.common.v1.RpcErrorDetail;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用固定线向量验证Java生成物，Python独立验证同一向量。
 *
 * @author yangaobo
 */
class ContractWireTest {

    /**
     * 超出JavaScript安全整数的认证版本保持int64及原字段编号。
     *
     * @throws InvalidProtocolBufferException 固定向量解析失败
     */
    @Test
    void preservesActorWireContract() throws InvalidProtocolBufferException {
        String session = "12345678-1234-1234-1234-123456789012";
        String wire = "0a20" + "61".repeat(32) + "1224"
                + HexFormat.of().formatHex(session.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "188180808080808010";
        ActorRef actor = ActorRef.newBuilder().setOwnerId("a".repeat(32))
                .setSessionRef(session).setAuthVersion(9007199254740993L).build();
        assertEquals(wire, HexFormat.of().formatHex(actor.toByteArray()));
        assertEquals(actor, ActorRef.parseFrom(HexFormat.of().parseHex(wire)));
    }

    /**
     * optional退避明确区分不存在与零值。
     *
     * @throws InvalidProtocolBufferException 序列化结果解析失败
     */
    @Test
    void preservesOptionalRetryPresence() throws InvalidProtocolBufferException {
        RpcErrorDetail absent = RpcErrorDetail.newBuilder().setCode(1004).build();
        RpcErrorDetail zero = absent.toBuilder().setRetryAfterMs(0).build();
        assertFalse(absent.hasRetryAfterMs());
        assertTrue(RpcErrorDetail.parseFrom(zero.toByteArray()).hasRetryAfterMs());
        assertEquals("08ec072000", HexFormat.of().formatHex(zero.toByteArray()));
    }
}
