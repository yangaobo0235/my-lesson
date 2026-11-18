package com.yangaobo.servicedesk.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.ai.v1.ActionDraftServiceGrpc;
import com.yangaobo.servicedesk.api.ai.v1.ValidateDraftRequest;
import com.yangaobo.servicedesk.api.ai.v1.ValidatedDraft;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.ActionDraftClient;
import com.yangaobo.servicedesk.ticket.model.dto.support.DraftValidation;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 浏览器确认目标冲突与上游校验证明损坏分别映射，不连接真实AI服务。
 *
 * @author yangaobo
 */
class ActionDraftClientTest {
    /**
     * 合法草稿与校验根的固定测试身份。
     */
    private static final String DRAFT = "a".repeat(32);
    /**
     * 内容摘要不由根标识替换改变。
     */
    private static final String DIGEST = "b".repeat(64);
    /**
     * 仅监听回环的合同替身。
     */
    private Server server;
    /**
     * 当前测试专用通道。
     */
    private ManagedChannel channel;
    /**
     * 真实调用适配器。
     */
    private ActionDraftClient client;
    /**
     * 替身返回的证明，按测试控制。
     */
    private ValidatedDraft response;

    /**
     * 建立真实gRPC线传输，绕过的只有测试TLS装配。
     */
    @BeforeEach
    void start() throws Exception {
        long now = Instant.now().toEpochMilli();
        response = ValidatedDraft.newBuilder().setId(DRAFT).setDraftRootId(DRAFT)
                .setTaskId("c".repeat(32)).setPurpose("CREATE_TICKET").setContentSha256(DIGEST)
                .setValidationId("d".repeat(32)).setValidatedAtEpochMs(now).setValidUntilEpochMs(now + 30000)
                .setTitle("合成标题").setSymptom("未恢复").build();
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(new ActionDraftServiceGrpc.ActionDraftServiceImplBase() {
                    /** 返回本测试声明的最小内部证明。 */
                    @Override
                    public void validateDraft(ValidateDraftRequest request, StreamObserver<ValidatedDraft> observer) {
                        observer.onNext(response);
                        observer.onCompleted();
                    }
                }).build().start();
        channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().build();
        client = new ActionDraftClient(new ObjectMapper(), "unused", "", "", "", "fixture-token");
        ReflectionTestUtils.setField(client, "channel", channel);
    }

    /**
     * 有界释放本测试所有网络资源。
     */
    @AfterEach
    void stop() throws Exception {
        if (channel != null) channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        if (server != null) server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * 合法但与证明不同的浏览器根标识应返回冲突。
     */
    @Test
    void replacedRootIsBusinessConflict() {
        var error = assertThrows(BusinessException.class, () -> validate("e".repeat(32)));
        assertEquals(CommonErrorCode.CONFLICT, error.getErrorCode());
    }

    /**
     * 上游根身份不符合合同仍须作为协议错误，不能放宽为正常业务拒绝。
     */
    @Test
    void malformedUpstreamRootIsProtocolError() {
        response = response.toBuilder().setDraftRootId("broken").build();
        var error = assertThrows(BusinessException.class, () -> validate(DRAFT));
        assertEquals(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR, error.getErrorCode());
    }

    /**
     * 相同根与内容仍能生成正式用例需要的校验证明。
     */
    @Test
    void matchingRootKeepsValidProof() {
        assertEquals(DRAFT, validate(DRAFT).getDraftRootId());
    }

    /**
     * 调用真实适配器，人工输入与内部证明分别建立。
     */
    private DraftValidation validate(String root) {
        var actor = new IdentityDTO();
        actor.setOwnerId("f".repeat(32));
        actor.setSessionRef("fixture-session");
        actor.setAuthVersion(0L);
        var input = new ObjectMapper().createObjectNode().put("sourceDraftRootId", root)
                .put("contentSha256", DIGEST).put("title", "合成标题").put("symptom", "未恢复");
        return client.validate(actor, DRAFT, "CREATE_TICKET", "1".repeat(32), "2".repeat(32), null, null, null, input);
    }
}
