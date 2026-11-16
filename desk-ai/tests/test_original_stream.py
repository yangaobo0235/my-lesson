"""精确原件流的完整性、断流与临时文件回收回归。作者：yangaobo。"""

import asyncio
import hashlib
from types import SimpleNamespace

import pytest

from desk_contracts.knowledge.v1.source_pb2 import OriginalPart
from servicedesk_ai.infrastructure.domain_rpc import DomainRpc


class Stream:
    """受控帧源可在最后一帧后失败，避免把EOF误当RPC成功。"""

    def __init__(self, frames, error=None):
        self.frames, self.error, self.cancelled = frames, error, False

    async def __aiter__(self):
        for frame in self.frames:
            yield frame
        if self.error:
            raise self.error

    def cancel(self):
        self.cancelled = True


def client(tmp_path, stream):
    """测试只替换传输，保留真实原件校验与文件生命周期。"""
    rpc = object.__new__(DomainRpc)
    rpc.token = "token"
    rpc.original_directory = tmp_path
    rpc.original = SimpleNamespace(ReadOriginal=lambda *_args, **_kwargs: stream)
    return rpc


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "fault", [None, "offset", "sha", "partial", "repeat", "oversized", "cancel"]
)
async def test_original_is_delivered_only_after_complete_validation(tmp_path, fault):
    """半文件、错序、重复元信息、超大帧与取消均不留下可用原件。"""
    content = b"original"
    digest = hashlib.sha256(content).hexdigest()
    frames = [
        OriginalPart(total_bytes=len(content), sha256=digest, content_format="MARKDOWN"),
        OriginalPart(offset=0, data=content),
    ]
    if fault == "offset":
        frames[1].offset = 1
    elif fault == "sha":
        frames[1].data = b"modified"
    elif fault == "partial":
        frames.pop()
    elif fault == "repeat":
        frames.append(frames[0])
    elif fault == "oversized":
        frames[1].data = b"x" * 65537
    stream = Stream(frames, asyncio.CancelledError() if fault == "cancel" else None)
    rpc = client(tmp_path, stream)
    if fault:
        with pytest.raises(asyncio.CancelledError if fault == "cancel" else ValueError):
            await rpc._read_original_once("doc", "job", digest, "MARKDOWN", 1)
    else:
        assert await rpc._read_original_once("doc", "job", digest, "MARKDOWN", 1) == content
    assert stream.cancelled
    assert list(tmp_path.iterdir()) == []
