"""验证真实Java服务与Python客户端的双向TLS、会话引用及错误边界。"""

import asyncio
import os
from pathlib import Path

import grpc
from pydantic import SecretStr
from verify_manual_workflow import Browser

from desk_contracts.common.v1.common_pb2 import ActorRef
from desk_contracts.user.v1.identity_pb2_grpc import IdentityServiceStub
from servicedesk_ai.bootstrap.components import RpcSettings
from servicedesk_ai.infrastructure.identity import (
    IdentityClient,
    IdentityFailure,
    open_identity,
)


async def verify(settings: RpcSettings, cookie: str, csrf: str) -> ActorRef:
    """持有原浏览器会话，经TLS核验后再使用不可登录会话引用。"""
    async with open_identity(settings) as client:
        identity = await client.verify_browser(cookie, "GET", None)
        repeated = await client.verify_ref(identity.actor)
        assert repeated == identity
        await client.verify_browser(cookie, "POST", csrf)
        try:
            await client.verify_browser(cookie, "POST", "invalid")
        except IdentityFailure as failure:
            assert failure.code == 1003
        else:
            raise AssertionError("RPC未拒绝错误CSRF")
        try:
            await client.verify_ref(identity.actor, remaining_seconds=0)
        except IdentityFailure as failure:
            assert failure.code == 1013
        else:
            raise AssertionError("RPC未应用父预算")
        changed = ActorRef()
        changed.CopyFrom(identity.actor)
        changed.auth_version += 1
        try:
            await client.verify_ref(changed)
        except IdentityFailure as failure:
            assert failure.code == 2002
        else:
            raise AssertionError("RPC未拒绝身份版本不符")
    credentials = grpc.ssl_channel_credentials(
        Path(settings.ca_file).read_bytes(),
        Path(settings.private_key_file).read_bytes(),
        Path(settings.certificate_file).read_bytes(),
    )
    async with grpc.aio.secure_channel(settings.user_target, credentials) as channel:
        wrong = IdentityClient(channel, "wrong-service-token")
        try:
            await wrong.verify_ref(identity.actor)
        except IdentityFailure as failure:
            assert failure.code == 1011, "服务凭据失败不能注销用户"
        else:
            raise AssertionError("RPC未拒绝错误服务凭据")
        stub = IdentityServiceStub(channel)
        try:
            await stub.VerifySessionRef(
                identity.actor,
                timeout=5,
                metadata=(
                    (
                        "authorization",
                        "Bearer " + settings.service_token.get_secret_value(),
                    ),
                ),
            )
        except grpc.aio.AioRpcError as failure:
            assert failure.code() == grpc.StatusCode.INVALID_ARGUMENT
        else:
            raise AssertionError("RPC未限制身份Deadline")
    async with grpc.aio.secure_channel(
        settings.user_target,
        credentials,
        options=(("grpc.ssl_target_name_override", "untrusted-name.invalid"),),
    ) as channel:
        try:
            await IdentityClient(channel, settings.service_token.get_secret_value()).verify_ref(
                identity.actor
            )
        except IdentityFailure as failure:
            assert failure.code == 1011
        else:
            raise AssertionError("RPC未验证服务端名称")
    print("PASS TLS跨语言身份、原会话引用、CSRF、版本、服务凭据与Deadline", flush=True)
    return identity.actor


async def revoked(settings: RpcSettings, actor: ActorRef) -> None:
    """用户退出后后台原会话引用不能继续使用。"""
    async with open_identity(settings) as client:
        try:
            await client.verify_ref(actor)
        except IdentityFailure as failure:
            assert failure.code == 2002
        else:
            raise AssertionError("退出后的RPC会话引用仍可使用")
    print("PASS 退出后RPC会话引用即时失效", flush=True)


def main() -> None:
    """只对显式隔离服务运行，不输出Cookie和私钥。"""
    if os.environ.get("DESK_VERIFY_ISOLATED") != "true":
        raise RuntimeError("必须显式声明独占环境")
    directory = Path(os.environ["DESK_VERIFY_TLS_DIR"])
    settings = RpcSettings(
        user_target=os.environ["DESK_VERIFY_RPC_TARGET"],
        ca_file=str(directory / "ca.pem"),
        certificate_file=str(directory / "ai.pem"),
        private_key_file=str(directory / "ai-key.pem"),
        service_token=SecretStr(os.environ["DESK_VERIFY_AI_TOKEN"]),
    )
    browser = Browser(os.environ["DESK_VERIFY_USER_URL"], os.environ["DESK_VERIFY_TICKET_URL"])
    try:
        browser.login(os.environ["DESK_VERIFY_ADMIN"], os.environ["DESK_VERIFY_ADMIN_PASSWORD"])
        cookie = "DESK_SESSION=" + browser.client.cookies["DESK_SESSION"]
        actor = asyncio.run(verify(settings, cookie, browser.csrf))
        browser.call("users/auth/logout", {})
        asyncio.run(revoked(settings, actor))
    finally:
        browser.client.close()


if __name__ == "__main__":
    main()
