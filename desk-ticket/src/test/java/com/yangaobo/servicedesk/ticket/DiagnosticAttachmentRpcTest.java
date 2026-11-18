package com.yangaobo.servicedesk.ticket;

import com.yangaobo.servicedesk.api.common.v1.ActorRef;
import com.yangaobo.servicedesk.api.ticket.v1.DiagnosticAttachmentServiceGrpc;
import com.yangaobo.servicedesk.api.ticket.v1.RedactedRequest;
import com.yangaobo.servicedesk.api.ticket.v1.AttachmentListRequest;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.DiagnosticText;
import com.yangaobo.servicedesk.ticket.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.ticket.model.dto.attachment.RedactedAttachment;
import com.yangaobo.servicedesk.ticket.model.vo.attachment.AttachmentVO;
import com.yangaobo.servicedesk.ticket.rpc.DiagnosticAttachmentRpcService;
import com.yangaobo.servicedesk.ticket.service.IAttachmentService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 真实本机gRPC流验证分帧和迟到撤权；身份、附件用例替身不代表生产mTLS或MySQL验收。
 *
 * @author yangaobo
 */
class DiagnosticAttachmentRpcTest {
    private final IdentityVerificationClient identities = mock(IdentityVerificationClient.class);
    private final IAttachmentService attachments = mock(IAttachmentService.class);
    private final IdentityDTO actor = new IdentityDTO();
    private Server server;
    private ManagedChannel channel;

    /**
     * 只监听回环随机端口，生产监听仍强制mTLS。
     */
    @BeforeEach
    void start() throws Exception {
        when(identities.verifySession(any())).thenReturn(actor);
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(new DiagnosticAttachmentRpcService(identities, attachments)).build().start();
        channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().build();
    }

    /**
     * 每个测试有界释放监听与通道。
     */
    @AfterEach
    void close() throws Exception {
        if (channel != null) channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        if (server != null) server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * 流首帧只有元信息，内容分帧连续且最终再次核验身份。
     */
    @Test
    void transfersCompleteFixedVersion() {
        byte[] bytes = DiagnosticText.encode("脱敏日志\n".repeat(18000));
        var value = new RedactedAttachment(3, "LOG", DiagnosticText.sha256(bytes), bytes);
        when(attachments.readRedacted(same(actor), anyString(), anyString(), eq(3L))).thenReturn(value);
        var stream = DiagnosticAttachmentServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS).readRedacted(request());
        var header = stream.next();
        assertEquals(bytes.length, header.getTotalBytes());
        assertEquals(3, header.getRedactionVersion());
        assertTrue(header.getData().isEmpty());
        var received = new ByteArrayOutputStream();
        while (stream.hasNext()) {
            var part = stream.next();
            assertFalse(part.hasTotalBytes());
            assertFalse(part.hasRedactionVersion());
            assertEquals(received.size(), part.getOffset());
            assertTrue(part.getData().size() > 0 && part.getData().size() <= 65536);
            received.writeBytes(part.getData().toByteArray());
        }
        assertArrayEquals(bytes, received.toByteArray());
        assertEquals(header.getSha256(), DiagnosticText.sha256(received.toByteArray()));
        verify(identities, times(2)).verifySession(any());
    }

    /**
     * 已发出部分或全部正文都不能掩盖最终撤权。
     */
    @Test
    void revokedBeforeCompletionFailsStream() {
        byte[] bytes = DiagnosticText.encode("脱敏日志");
        when(attachments.readRedacted(same(actor), anyString(), anyString(), eq(3L)))
                .thenReturn(new RedactedAttachment(3, "LOG", DiagnosticText.sha256(bytes), bytes))
                .thenThrow(new BusinessException(CommonErrorCode.FORBIDDEN));
        var stream = DiagnosticAttachmentServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS).readRedacted(request());
        var error = assertThrows(StatusRuntimeException.class, () -> {
            while (stream.hasNext()) stream.next();
        });
        assertEquals(Status.Code.PERMISSION_DENIED, error.getStatus().getCode());
        verify(identities, times(2)).verifySession(any());
    }

    /**
     * 原用户会话失效不交付正文，也不能仅凭服务认证继续读取。
     */
    @Test
    void identityFailureDoesNotReadAttachment() {
        when(identities.verifySession(any())).thenThrow(new BusinessException(CommonErrorCode.FORBIDDEN));
        var stream = DiagnosticAttachmentServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS).readRedacted(request());
        assertThrows(StatusRuntimeException.class, stream::hasNext);
        verifyNoInteractions(attachments);
    }

    /**
     * 候选分页只传固定脱敏元信息，续页游标不得跳过第21项。
     */
    @Test
    void discoversBoundedRedactedMetadata() {
        var values = java.util.stream.IntStream.rangeClosed(1, 21).mapToObj(index -> {
            var value = new AttachmentVO();
            value.setId(String.format("%032x", index));
            value.setOriginalFileName("diagnostic.log");
            value.setContentFormat("LOG");
            value.setRedactionVersion(3L);
            value.setRedactedSha256("e".repeat(64));
            return value;
        }).toList();
        when(attachments.listRedacted(same(actor), eq("b".repeat(32)), isNull())).thenReturn(values);
        var query = AttachmentListRequest.newBuilder().setActor(request().getActor())
                .setTicketId("b".repeat(32)).build();
        var response = DiagnosticAttachmentServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(5, TimeUnit.SECONDS).listRedacted(query);
        assertEquals(20, response.getItemsCount());
        assertEquals(values.get(19).getId(), response.getNextAfterId());
        assertEquals(3, response.getItems(0).getRedactionVersion());
        verify(attachments, never()).readRedacted(any(), anyString(), anyString(), anyLong());
    }

    /**
     * 固定身份与目标，不包含客户端声明的范围或角色。
     */
    private RedactedRequest request() {
        return RedactedRequest.newBuilder().setActor(ActorRef.newBuilder().setOwnerId("a".repeat(32))
                        .setSessionRef("00000000-0000-0000-0000-000000000001").setAuthVersion(1))
                .setTicketId("b".repeat(32)).setAttachmentId("c".repeat(32)).setRedactionVersion(3).build();
    }
}
