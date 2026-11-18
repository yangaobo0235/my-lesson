"""脱敏附件的完整传输、分页和依赖撤权回归，不替代真实mTLS联调。作者：yangaobo。"""

import hashlib
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from pydantic import ValidationError

from desk_contracts.common.v1.common_pb2 import ActorRef
from desk_contracts.ticket.v1.support_pb2 import RedactedPart, RedactedRequest
from servicedesk_ai.errors.common import BusinessError
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc, RedactedText
from servicedesk_ai.schemas.diagnosis import InspectDiagnosticText
from servicedesk_ai.services.evidence import Evidence, EvidenceService


class Stream:
    """可在最后一帧后失败，模拟服务端完成前撤权。"""

    def __init__(self, parts, failure=None):
        self.parts, self.failure, self.cancelled = parts, failure, False

    async def __aiter__(self):
        for part in self.parts:
            yield part
        if self.failure:
            raise self.failure

    def cancel(self):
        self.cancelled = True


def frames(body=b"diagnostic text"):
    """构造真实protobuf元信息和连续正文帧。"""
    return [
        RedactedPart(
            total_bytes=len(body),
            sha256=hashlib.sha256(body).hexdigest(),
            content_format="LOG",
            redaction_version=3,
        ),
        RedactedPart(data=body),
    ]


async def read(stream):
    """仅替换gRPC传输，执行生产客户端的全部校验。"""
    rpc = object.__new__(DomainRpc)
    rpc.token = "test-only"
    rpc.attachments = SimpleNamespace(ReadRedacted=lambda *args, **kwargs: stream)
    return await rpc.read_redacted(RedactedRequest(redaction_version=3))


@pytest.mark.asyncio
async def test_complete_unicode_stream_returns_only_after_success():
    """UTF-8跨帧仍正确，结束后释放流。"""
    body = "诊断🙂".encode()
    parts = frames(body)
    parts[1:] = [RedactedPart(data=body[:2]), RedactedPart(offset=2, data=body[2:])]
    stream = Stream(parts)
    result = await read(stream)
    assert result.content == "诊断🙂" and result.redaction_version == 3
    assert stream.cancelled


@pytest.mark.parametrize("damage", ["truncated", "offset", "version", "digest", "mixed", "repeat"])
@pytest.mark.asyncio
async def test_malformed_stream_never_delivers_partial_text(damage):
    """缺帧、乱序、换版及元信息混入正文均失败。"""
    parts = frames()
    if damage == "truncated":
        parts.pop()
    elif damage == "offset":
        parts[1].offset = 1
    elif damage == "version":
        parts[0].redaction_version = 4
    elif damage == "digest":
        parts[0].sha256 = "0" * 64
    elif damage == "mixed":
        parts[0].data = b"x"
    else:
        parts[1].sha256 = parts[0].sha256
    stream = Stream(parts)
    with pytest.raises(ValueError):
        await read(stream)
    assert stream.cancelled


@pytest.mark.asyncio
async def test_late_permission_failure_does_not_return_buffered_text():
    """收到全部正文但终态失败仍不可引用。"""
    stream = Stream(frames(), PermissionError("revoked"))
    with pytest.raises(PermissionError):
        await read(stream)
    assert stream.cancelled


class Engine:
    """只为证据登记用例提供事务范围。"""

    @asynccontextmanager
    async def begin(self):
        yield object()


def lease():
    """固定宿主工单与原身份，不允许模型传入范围。"""
    return SimpleNamespace(
        id="a" * 32,
        owner_id="b" * 32,
        session_ref="c" * 32,
        auth_version=1,
        context_manifest=SimpleNamespace(ticket_id="d" * 32),
        ticket_row_version=None,
        pipeline_config=SimpleNamespace(source_limit=64),
    )


@pytest.mark.asyncio
async def test_excerpt_has_exact_version_and_character_cursor():
    """大文本原样分页，既不执行嵌入命令，也不把首段标为完整。"""
    content = "中文🙂" * 2000 + "$(ignored)"
    rpc = SimpleNamespace(
        read_redacted=AsyncMock(return_value=RedactedText(content, "e" * 64, "TEXT", 3))
    )
    service = EvidenceService(Engine(), rpc)
    service.add = AsyncMock()
    await service.diagnostic_text(lease(), "f" * 32, 3, 0)
    added = service.add.call_args.kwargs
    assert added["excerpt"] == content[:4000]
    assert added["source_version"]["next_offset"] == 4000
    assert added["source_version"]["content_complete"] is False
    assert added["locator"]["redaction_version"] == 3
    assert rpc.read_redacted.call_args.args[0].ticket_id == "d" * 32
    with pytest.raises(BusinessError):
        await service.diagnostic_text(lease(), "f" * 32, 3, len(content))


@pytest.mark.asyncio
async def test_history_verification_rechecks_exact_attachment_version():
    """历史来源不可因已保存摘录而绕过固定版本授权。"""
    rpc = SimpleNamespace(
        read_redacted=AsyncMock(return_value=RedactedText("changed", "0" * 64, "TEXT", 3))
    )
    item = Evidence(
        "f" * 32,
        1,
        "ATTACHMENT",
        "诊断",
        "text",
        {},
        {"redaction_version": 3, "sha256": "e" * 64},
        attachment_id="e" * 32,
    )
    service = EvidenceService(Engine(), rpc)
    with pytest.raises(BusinessError):
        await service.verify_items(lease(), [item], ActorRef(owner_id="b" * 32), set())
    request = rpc.read_redacted.call_args.args[0]
    assert request.attachment_id == "e" * 32 and request.redaction_version == 3


def test_inspection_cannot_accept_arbitrary_location_or_unconfirmed_version():
    """输入仅允许附件身份和固定脱敏版本，不接受路径或URL。"""
    for extras in ({"url": "https://example.com"}, {"offset": 1.5}, {"redactionVersion": "0"}):
        with pytest.raises(ValidationError):
            InspectDiagnosticText.model_validate(
                {"attachmentId": "e" * 32, "redactionVersion": "3", **extras}
            )
