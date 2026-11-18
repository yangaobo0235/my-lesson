"""父子证据清单与完整阅读边界的行为回归。作者：yangaobo。"""

import hashlib
import json
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from desk_contracts.knowledge.v1.source_pb2 import KnowledgeSource
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.schemas.indexing import ProcessingConfig
from servicedesk_ai.services.evidence import Evidence, EvidenceService, complete_knowledge_numbers
from servicedesk_ai.services.parsing import build_manifest, parse_markdown
from servicedesk_ai.services.run_tools import RunTools


@pytest.mark.asyncio
@pytest.mark.parametrize("content", ["短章节也必须显式阅读。", "正文与停止条件。" * 100])
async def test_search_preview_never_grants_full_read_or_continuation(monkeypatch, content):
    """搜索候选只用于发现；短预览不能取得操作依据，长预览不能提供全文续页游标。"""
    row = {
        "id": "a" * 32,
        "index_job_id": "b" * 32,
        "document_id": "c" * 32,
        "status": "SUCCEEDED",
        "last_command": "SUBMIT",
        "content": content,
        "content_sha256": hashlib.sha256(content.encode()).hexdigest(),
        "source_locations": '{"kind":"MARKDOWN","segments":[]}',
    }
    monkeypatch.setattr(
        "servicedesk_ai.services.evidence.queries.readable_chunk", AsyncMock(return_value=row)
    )
    service = EvidenceService(Engine(), SimpleNamespace())
    service.add = AsyncMock()
    await service.knowledge(
        SimpleNamespace(),
        [row["id"]],
        [KnowledgeSource(index_job_id=row["index_job_id"], document_id=row["document_id"])],
    )
    values = service.add.call_args.kwargs
    item = Evidence(id="d" * 32, number=1, **values)
    wire = RunTools.evidence_message(item)
    assert wire["usageKind"] == "METADATA"
    assert wire["complete"] is False
    assert wire["readFromOffset"] == 0
    assert "nextOffset" not in wire
    assert "endOffset" not in wire
    assert (
        complete_knowledge_numbers(
            [
                {
                    "source_number": 1,
                    "source_type": item.source_type,
                    "usage_kind": item.usage_kind,
                    "chunk_id": item.chunk_id,
                    "source_version": json.dumps(item.source_version),
                }
            ]
        )
        == set()
    )


def test_full_read_requires_contiguous_same_version_pages():
    """末尾页、断档页或不同摘要均不能伪装读完整章；相邻页可共同覆盖。"""

    def page(number, start, end, digest="same"):
        return {
            "source_number": number,
            "chunk_id": "a" * 32,
            "source_type": "CHUNK",
            "usage_kind": "CONTENT",
            "source_version": json.dumps(
                {
                    "content_sha256": digest,
                    "content_offset": start,
                    "content_end": end,
                    "content_length": 100,
                    "content_complete": False,
                }
            ),
        }

    assert complete_knowledge_numbers([page(1, 50, 100)]) == set()
    assert complete_knowledge_numbers([page(1, 0, 40), page(2, 50, 100)]) == set()
    assert complete_knowledge_numbers([page(1, 0, 50), page(2, 50, 100, "changed")]) == set()
    assert complete_knowledge_numbers([page(1, 0, 50), page(2, 50, 100)]) == {1, 2}


@pytest.mark.asyncio
async def test_read_page_respects_token_budget_without_losing_tail(monkeypatch):
    """长中文正文每页有界且字符连续，末尾停止条件不会因分页消失。"""
    from servicedesk_ai.diagnosis.budget import estimate_text_tokens

    content = "只读观察设备状态。" * 180 + "停止条件：出现焦味立即停止。"
    row = {
        "id": "a" * 32,
        "parent_chunk_id": None,
        "index_job_id": "b" * 32,
        "document_id": "c" * 32,
        "status": "SUCCEEDED",
        "last_command": "SUBMIT",
        "content": content,
        "node_kind": "LEAF",
        "content_sha256": hashlib.sha256(content.encode()).hexdigest(),
        "source_locations": '{"kind":"MARKDOWN","segments":[]}',
    }
    monkeypatch.setattr(
        "servicedesk_ai.services.evidence.queries.readable_chunk", AsyncMock(return_value=row)
    )
    service = EvidenceService(Engine(), SimpleNamespace())
    service.add = AsyncMock()
    offset, parts = 0, []
    while True:
        await service.knowledge(
            SimpleNamespace(),
            [row["id"]],
            [
                KnowledgeSource(
                    index_job_id=row["index_job_id"],
                    document_id=row["document_id"],
                    title="只读规程",
                )
            ],
            complete=True,
            offset=offset,
        )
        added = service.add.call_args.kwargs
        parts.append(added["excerpt"])
        assert estimate_text_tokens(added["excerpt"]) <= 1200
        assert added["source_version"]["content_offset"] == offset
        offset = added["source_version"]["next_offset"]
        if offset is None:
            break
    assert len(parts) > 1 and "".join(parts) == content


def test_section_restores_stop_condition_outside_retrieved_leaf():
    """同章节末尾的停止条件随完整父正文返回，保留原叶片身份和行定位。"""
    text = "# 打印检查\n" + "检查队列，不重复提交。\n" * 100 + "停止条件：出现焦味立即停止。\n"
    config = ProcessingConfig(chunk_chars=200)
    leaves = parse_markdown(text.encode(), "a" * 32, config)
    manifest = build_manifest(leaves, "a" * 32, config)
    parent = manifest[0]
    assert parent.node_kind == "SECTION"
    assert "停止条件" in parent.content
    assert "停止条件" not in leaves[0].content
    assert [c.id for c in manifest[1:]] == [c.id for c in leaves]
    assert all(c.parent_chunk_id == parent.id for c in manifest[1:])
    assert parent.content_sha256 == hashlib.sha256(parent.content.encode()).hexdigest()
    assert parent.source_locations.segments[-1].line_end == len(text.splitlines())
    assert build_manifest(leaves, "a" * 32, config) == manifest


def test_long_section_splits_without_dropping_content():
    """48KiB只决定章节分段，不能悄悄丢弃尾部风险说明。"""
    text = "# 长章节\n" + "逐项核对设备状态。\n" * 2500
    config = ProcessingConfig(chunk_chars=2000)
    leaves = parse_markdown(text.encode(), "a" * 32, config)
    manifest = build_manifest(leaves, "a" * 32, config)
    parents = [c for c in manifest if c.node_kind == "SECTION"]
    assert len(parents) > 1
    assert all(len(c.content.encode()) <= 49152 for c in parents)
    assert "\n".join(c.content for c in parents) == "\n".join(c.content for c in leaves)
    with pytest.raises(ValueError, match="完整清单"):
        build_manifest(leaves, "a" * 32, ProcessingConfig(max_chunks=len(leaves)))


class Engine:
    """隔离数据库连接，保留证据用例的真实授权与定位选择。"""

    @asynccontextmanager
    async def begin(self):
        yield object()


@pytest.mark.asyncio
@pytest.mark.parametrize("offset", [0, 4])
async def test_parent_read_keeps_exact_source_and_continuation(monkeypatch, offset):
    """授权父章节后，返回当前分段和下一段身份，不把首段当完整章节。"""
    parent = "d" * 32
    next_part = "e" * 32
    row = {
        "id": "a" * 32,
        "parent_chunk_id": parent,
        "index_job_id": "b" * 32,
        "document_id": "c" * 32,
        "status": "SUCCEEDED",
        "last_command": "SUBMIT",
        "content": "完整说明及停止条件",
        "node_kind": "SECTION",
        "source_locations": '{"schema_version":1,"kind":"MARKDOWN","segments":[]}',
    }
    monkeypatch.setattr(
        "servicedesk_ai.services.evidence.queries.readable_chunk",
        AsyncMock(side_effect=[row, {**row, "id": parent, "parent_chunk_id": None}]),
    )
    monkeypatch.setattr(
        "servicedesk_ai.services.evidence.queries.adjacent_sections",
        AsyncMock(return_value=[{"id": parent}, {"id": next_part}]),
    )
    service = EvidenceService(Engine(), SimpleNamespace())
    service.add = AsyncMock()

    async def read():
        return await service.knowledge(
            SimpleNamespace(),
            ["a" * 32],
            [KnowledgeSource(index_job_id="b" * 32, document_id="c" * 32, title="说明")],
            complete=True,
            expand_section=True,
            offset=offset,
        )

    if offset:
        with pytest.raises(BusinessError) as error:
            await read()
        assert error.value.error == CommonErrorCode.INVALID_REQUEST
        service.add.assert_not_awaited()
        return
    await read()
    added = service.add.call_args.kwargs
    assert added["chunk_id"] == parent
    assert added["source_version"]["section_parts"] == 2
    assert added["source_version"]["next_section_id"] == next_part
    assert added["excerpt"] == row["content"]
