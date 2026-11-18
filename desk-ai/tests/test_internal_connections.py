"""用真实本机监听验证内部连接不继承系统代理，且保留TLS与资源关闭。作者：yangaobo。"""

import asyncio
import ipaddress
import logging
from datetime import UTC, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

import grpc
import pytest
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

from desk_contracts.common.v1.common_pb2 import ActorRef
from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from desk_contracts.user.v1.identity_pb2_grpc import (
    IdentityServiceServicer,
    add_IdentityServiceServicer_to_server,
)
from servicedesk_ai.bootstrap.components import RpcSettings
from servicedesk_ai.bootstrap.observability import open_observability
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.infrastructure.domain_rpc import open_domain_rpc
from servicedesk_ai.infrastructure.identity import open_identity
from servicedesk_ai.receiver import configure_direct_transport


def reject_implicit_proxy(monkeypatch):
    """所有隐式代理指向拒绝连接的地址，不能依赖开发机已有NO_PROXY例外。"""
    for name in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy", "grpc_proxy"):
        monkeypatch.setenv(name, "http://127.0.0.1:9")
    for name in ("NO_PROXY", "no_proxy", "no_grpc_proxy"):
        monkeypatch.setenv(name, "")


@pytest.mark.asyncio
@pytest.mark.parametrize("configured", [False, True])
async def test_rocketmq_sdk_transport_bypasses_proxy_only_when_configured(monkeypatch, configured):
    """官方SDK真实发出Heartbeat；未装配时被坏代理阻断，装配后直达本机监听。"""
    from rocketmq import ClientConfiguration, Credentials
    from rocketmq.grpc_protocol import Code, HeartbeatRequest, Status
    from rocketmq.grpc_protocol.service_pb2 import HeartbeatResponse
    from rocketmq.v5.client.connection import RpcChannel

    reject_implicit_proxy(monkeypatch)
    if configured:
        configure_direct_transport()
    server = grpc.aio.server()
    server.add_generic_rpc_handlers(
        (
            grpc.method_handlers_generic_handler(
                "apache.rocketmq.v2.MessagingService",
                {
                    "Heartbeat": grpc.unary_unary_rpc_method_handler(
                        lambda request, context: HeartbeatResponse(status=Status(code=Code.OK)),
                        request_deserializer=HeartbeatRequest.FromString,
                        response_serializer=HeartbeatResponse.SerializeToString,
                    )
                },
            ),
        )
    )
    port = server.add_insecure_port("127.0.0.1:0")
    await server.start()
    channel = RpcChannel(ClientConfiguration(f"127.0.0.1:{port}", Credentials()).rpc_endpoints)
    loop = asyncio.get_running_loop()
    channel.create_channel(loop)
    try:
        if configured:
            response = await channel.async_stub.Heartbeat(HeartbeatRequest(), timeout=2)
            assert response.status.code == Code.OK
        else:
            with pytest.raises(grpc.aio.AioRpcError):
                await channel.async_stub.Heartbeat(HeartbeatRequest(), timeout=2)
    finally:
        channel.close_channel(loop)
        await asyncio.sleep(0)
        await server.stop(grace=0)


@pytest.mark.asyncio
@pytest.mark.parametrize("domain", [False, True])
async def test_internal_rpc_uses_direct_mutual_tls_with_hostile_proxy(
    tmp_path, monkeypatch, domain
):
    """两个真实装配入口都能直连双向TLS服务，身份结果保持原合同。"""
    reject_implicit_proxy(monkeypatch)
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "local-proxy-regression")])
    now = datetime.now(UTC)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(subject)
        .public_key(private.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=1))
        .not_valid_after(now + timedelta(days=1))
        .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
        .add_extension(
            x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address("127.0.0.1"))]),
            critical=False,
        )
        .sign(private, hashes.SHA256())
    )
    key_bytes = private.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    cert_bytes = cert.public_bytes(serialization.Encoding.PEM)
    cert_path, key_path = tmp_path / "certificate.pem", tmp_path / "key.pem"
    cert_path.write_bytes(cert_bytes)
    key_path.write_bytes(key_bytes)

    class Identity(IdentityServiceServicer):
        """只提供本测试需要的身份回执。"""

        async def VerifySessionRef(self, request, context):
            """实际TLS握手后回传请求身份，不保存认证元信息。"""
            return VerifiedIdentity(actor=request, display_name="本机测试")

    server = grpc.aio.server()
    add_IdentityServiceServicer_to_server(Identity(), server)
    port = server.add_secure_port(
        "127.0.0.1:0",
        grpc.ssl_server_credentials(
            [(key_bytes, cert_bytes)], root_certificates=cert_bytes, require_client_auth=True
        ),
    )
    await server.start()
    target = f"127.0.0.1:{port}"
    settings = RpcSettings(
        user_target=target,
        ticket_target=target,
        knowledge_target=target,
        service_token="k" * 32,
        ca_file=str(cert_path),
        certificate_file=str(cert_path),
        private_key_file=str(key_path),
        original_directory=str(tmp_path),
    )
    try:
        async with open_domain_rpc(settings) if domain else open_identity(settings) as client:
            identity = client.identity if domain else client
            actor = ActorRef(owner_id="a" * 32, session_ref="test-reference", auth_version=1)
            assert (await identity.verify_ref(actor)).actor == actor
    finally:
        await server.stop(grace=0)


def test_zipkin_delivery_uses_configured_collector_without_system_proxy(tmp_path, monkeypatch):
    """真实HTTP接收一批span，失败代理不应截获内部追踪。"""
    reject_implicit_proxy(monkeypatch)
    received = []

    class Collector(BaseHTTPRequestHandler):
        """接收固定测试span，不打印HTTP正文。"""

        def do_POST(self):
            """记录实际端点和长度，返回Zipkin接受状态。"""
            received.append((self.path, len(self.rfile.read(int(self.headers["Content-Length"])))))
            self.send_response(202)
            self.end_headers()

        def log_message(self, *args):
            """测试服务器不写默认标准错误日志。"""

    server = ThreadingHTTPServer(("127.0.0.1", 0), Collector)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    settings = Settings(infrastructure_enabled=False)
    settings.observability.log_directory = str(tmp_path / "logs")
    settings.observability.url = f"http://127.0.0.1:{server.server_port}"
    settings.observability.sample_ratio = 1
    provider, handler = open_observability(settings)
    try:
        with provider.get_tracer("proxy-regression").start_as_current_span("test"):
            pass
        assert provider.force_flush(timeout_millis=3000)
        assert len(received) == 1 and received[0][0] == "/api/v2/spans" and received[0][1] > 0
    finally:
        logging.getLogger("servicedesk_ai").removeHandler(handler)
        provider.shutdown()
        handler.close()
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
