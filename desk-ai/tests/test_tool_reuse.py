"""跨轮知识回执复用必须完整、同范围、当前有权，不能重用未知或已撤回来源。"""

import json
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock

import pytest

from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.infrastructure.models import ModelToolCall
from servicedesk_ai.repositories import runs
from servicedesk_ai.schemas.indexing import canonical_hash
from servicedesk_ai.services.evidence import Evidence
from servicedesk_ai.services.run_tools import PreparedTool, RunTools


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "sequence,expected",
    [
        (["empty", "empty"], False),
        (["empty", "empty", "empty"], True),
        (["invalid", "invalid", "invalid"], True),
        (["new", "new", "new"], False),
        (["new", "new", "new", "new"], True),
        (["initial", "initial", "initial"], True),
        (["empty", "empty", "new"], False),
        (["empty", "empty", "empty", "new"], False),
        (["attachment-a", "attachment-b", "attachment-c"], False),
        (["attachment-a"] * 4, True),
        (["check-initial"] * 3, True),
        (["check-new"] * 3, False),
        (["check-new"] * 4, True),
    ],
)
async def test_no_progress_uses_receipt_identity_not_success_or_model_prose(
    monkeypatch, sequence, expected
):
    """空结果、拒绝及已知证据不算进展；新来源、附件定位和检查版本可重置连续次数。"""
    rows = []
    for item in sequence:
        payload = []
        refs = []
        name = "search_knowledge"
        status = "OK"
        if item == "empty":
            status = "EMPTY"
        elif item == "invalid":
            status = "INVALID_ARGUMENT"
            payload = [{"status": "INVALID_ARGUMENT", "notice": "new evidence"}]
        elif item.startswith("attachment-"):
            name = "list_diagnostic_attachments"
            payload = [{"items": [{"attachmentId": item, "redactionVersion": "1"}]}]
        elif item.startswith("check-"):
            name = "read_task_context"
            payload = [{"section": "CHECKS", "records": [{"checkId": item, "rowVersion": "2"}]}]
        else:
            refs = [item]
            payload = [{"sourceNumber": 1, "content": "资料正文不能决定进展状态"}]
        rows.append(
            {
                "tool_name": name,
                "status": status,
                "result_payload": json.dumps({"schema_version": 1, "items": payload}),
                "result_refs": json.dumps({"schema_version": 1, "source_usage_ids": refs}),
            }
        )

    @asynccontextmanager
    async def begin():
        yield SimpleNamespace(
            execute=AsyncMock(return_value=SimpleNamespace(mappings=lambda: rows))
        )

    monkeypatch.setattr(runs, "owned", AsyncMock())
    tool = RunTools(SimpleNamespace(begin=begin), None, None, None, None)
    run = SimpleNamespace(
        id="a" * 32,
        pipeline_config=SimpleNamespace(max_tool_calls=12),
        context_manifest=SimpleNamespace(
            source_usages=["initial"],
            checks=[SimpleNamespace(id="check-initial", row_version=2)],
        ),
    )
    assert await tool.no_progress(run) is expected
    runs.owned.assert_awaited_once()


@pytest.mark.asyncio
async def test_invalid_tool_arguments_have_receipt_and_can_be_corrected(monkeypatch):
    """越界参数落拒绝账目且不外调；同参重复阻断，合法纠正仍走正常读取。"""
    intentions = []
    receipts = []

    async def execute(statement, values):
        sql = str(statement)
        if "SELECT COUNT(*)" in sql:
            return SimpleNamespace(one=lambda: (len(intentions), len(intentions)))
        if "SELECT tool_number" in sql:
            duplicate = next(
                (item["number"] for item in intentions if item["hash"] == values["hash"]), None
            )
            return SimpleNamespace(scalar_one_or_none=lambda: duplicate)
        if "INSERT INTO ai_tool_call" in sql:
            intentions.append(values)
        if "result_payload=:payload" in sql:
            receipts.append(values)

    @asynccontextmanager
    async def begin():
        yield SimpleNamespace(execute=execute)

    tool = RunTools(SimpleNamespace(begin=begin), None, None, None, None)
    tool.task_context = AsyncMock(return_value=([], [{"section": "CHECKS", "records": []}]))
    tool.read = AsyncMock()
    tool.reuse_knowledge = AsyncMock()
    monkeypatch.setattr(runs, "owned", AsyncMock())
    run = SimpleNamespace(
        id="b" * 32,
        task_id="c" * 32,
        claim_token="lease",
        diagnosis_context_version=1,
        pipeline_config=SimpleNamespace(max_tool_calls=3),
        context_manifest=SimpleNamespace(service_item_id="d" * 32, ticket_id=None),
    )
    call = ModelToolCall(
        id="bad", name="read_task_context", arguments={"section": "CHECKS", "limit": 5}
    )
    reply = await tool.execute(run, "model", call)
    assert reply[0]["status"] == "INVALID_ARGUMENT"
    assert receipts[-1]["status"] == "INVALID_ARGUMENT"
    assert json.loads(intentions[-1]["arguments"])["invalid_arguments"] == call.arguments
    assert json.loads(receipts[-1]["refs"])["source_usage_ids"] == []
    assert json.loads(receipts[-1]["payload"])["items"] == reply
    tool.task_context.assert_not_awaited()
    tool.read.assert_not_awaited()
    tool.reuse_knowledge.assert_not_awaited()
    duplicate = await tool.execute(run, "model", call.model_copy(update={"id": "repeat"}))
    assert duplicate[0]["status"] == "DUPLICATE_CALL"
    assert duplicate[0]["previousToolNumber"] == 1
    corrected = call.model_copy(
        update={"id": "corrected", "arguments": {"section": "CHECKS", "limit": 3}}
    )
    await tool.execute(run, "model", corrected)
    tool.task_context.assert_awaited_once()
    assert receipts[-1]["status"] == "OK"
    from servicedesk_ai.diagnosis.budget import ExecutionBudgetExceeded

    with pytest.raises(ExecutionBudgetExceeded):
        await tool.execute(run, "model", call)
    assert len(intentions) == 3


@pytest.mark.asyncio
@pytest.mark.parametrize("unknown_tool", [False, True])
async def test_invalid_arguments_never_bypass_tool_permission_or_execution_ownership(
    monkeypatch, unknown_tool
):
    """参数错误不能掩盖未知工具或失效执行权，拒绝后不写伪回执。"""
    db = SimpleNamespace(execute=AsyncMock())

    @asynccontextmanager
    async def begin():
        yield db

    tool = RunTools(SimpleNamespace(begin=begin), None, None, None, None)
    monkeypatch.setattr(
        runs, "owned", AsyncMock(side_effect=BusinessError(CommonErrorCode.CONFLICT))
    )
    run = SimpleNamespace(
        task_id="c" * 32,
        diagnosis_context_version=1,
        context_manifest=SimpleNamespace(service_item_id="d" * 32, ticket_id=None),
    )
    call = ModelToolCall(
        id="bad",
        name="execute_shell" if unknown_tool else "read_task_context",
        arguments={"limit": 5},
    )
    with pytest.raises(BusinessError) as raised:
        await tool.execute(run, "model", call)
    assert raised.value.error == (
        CommonErrorCode.FORBIDDEN if unknown_tool else CommonErrorCode.CONFLICT
    )
    db.execute.assert_not_awaited()


@pytest.mark.asyncio
async def test_duplicate_tool_is_recorded_without_another_external_read(monkeypatch):
    """同轮重复提议有独立拒绝回执，不重读、不伪造新证据，也不误报环境改变。"""
    db = SimpleNamespace(execute=AsyncMock())

    @asynccontextmanager
    async def begin():
        yield db

    tool = RunTools(SimpleNamespace(begin=begin), None, None, None, None)
    tool.prepare = AsyncMock(return_value=PreparedTool("a" * 32, 1))
    tool.read = AsyncMock()
    tool.reuse_knowledge = AsyncMock()
    monkeypatch.setattr(runs, "owned", AsyncMock())
    run = SimpleNamespace(id="b" * 32, claim_token="lease")
    call = ModelToolCall(id="duplicate", name="load_procedure", arguments={})
    reply = await tool.execute(run, "model", call)
    assert reply[0]["status"] == "DUPLICATE_CALL" and reply[0]["previousToolNumber"] == 1
    tool.read.assert_not_awaited()
    tool.reuse_knowledge.assert_not_awaited()
    receipt = db.execute.call_args.args[1]
    assert receipt["status"] == "INVALID_ARGUMENT"
    assert json.loads(receipt["refs"])["source_usage_ids"] == []
    assert json.loads(receipt["payload"])["items"] == reply


@pytest.fixture
def reuse(monkeypatch):
    """仅替换外部授权和持久资源，复用合同校验由实际用例执行。"""
    source = Evidence(
        id="a" * 32,
        number=7,
        source_type="CHUNK",
        title="完整规程章节",
        excerpt="只记录网络状态；异常发热停止排查。",
        locator={"kind": "MARKDOWN"},
        source_version={"knowledge_source": {"index_job_id": "b" * 32}, "content_complete": True},
        chunk_id="c" * 32,
    )
    parent = SimpleNamespace(id="d" * 32)
    run = SimpleNamespace(
        id="e" * 32,
        task_id="f" * 32,
        conversation_id="1" * 32,
        owner_id="2" * 32,
        diagnosis_context_version=1,
        pipeline_config=SimpleNamespace(tool_version="v1"),
    )
    payload = {"schema_version": 1, "items": [RunTools.evidence_message(source)]}
    row = {
        "id": "3" * 32,
        "run_id": parent.id,
        "result_payload": json.dumps(payload),
        "result_refs": json.dumps({"source_usage_ids": [source.id]}),
        "result_fingerprint": canonical_hash(payload),
    }

    class Engine:
        """只模拟一个候选读取，不模拟数据库并发。"""

        @asynccontextmanager
        async def connect(self):
            yield SimpleNamespace(
                execute=AsyncMock(
                    return_value=SimpleNamespace(
                        mappings=lambda: SimpleNamespace(first=lambda: row)
                    )
                )
            )

        begin = connect

    evidence = SimpleNamespace(
        same_scope=Mock(),
        list_items=AsyncMock(return_value=[source]),
        verify_items=AsyncMock(),
        add=AsyncMock(return_value=source),
    )
    rpc = SimpleNamespace(
        sources=AsyncMock(
            return_value=SimpleNamespace(sources=[SimpleNamespace(index_job_id="b" * 32)])
        )
    )
    tool = RunTools(Engine(), rpc, None, evidence, None)
    monkeypatch.setattr(runs, "run", AsyncMock(return_value=parent))
    monkeypatch.setattr("servicedesk_ai.services.run_tools.actor_ref", lambda value: None)
    monkeypatch.setattr("servicedesk_ai.services.run_tools.source_query", lambda value: None)
    call = ModelToolCall(
        id="provider-call", name="read_evidence", arguments={"chunkId": source.chunk_id}
    )
    return tool, run, call, row, source


@pytest.mark.asyncio
async def test_completed_read_is_rebound_to_current_source_numbers(reuse):
    """完整旧回执重新授权并登记本轮来源，保存原调用身份而不再次执行工具。"""
    tool, run, call, _, source = reuse
    items, reference = await tool.reuse_knowledge(run, call)
    assert items == [source] and reference.call_id == "3" * 32 and reference.run_id == "d" * 32
    tool.evidence.verify_items.assert_awaited_once()
    tool.evidence.add.assert_awaited_once()


@pytest.mark.asyncio
async def test_tampered_payload_never_reaches_model(reuse):
    """回执正文摘要失配不能当作缓存命中或普通空结果。"""
    tool, run, call, row, _ = reuse
    row["result_payload"] = '{"schema_version":1,"items":[]}'
    with pytest.raises(BusinessError):
        await tool.reuse_knowledge(run, call)
    tool.evidence.add.assert_not_awaited()


@pytest.mark.asyncio
async def test_withdrawn_source_blocks_cached_content(reuse):
    """原调用已成功也不能绕过当前撤权。"""
    tool, run, call, _, _ = reuse
    tool.evidence.verify_items.side_effect = BusinessError(CommonErrorCode.FORBIDDEN)
    with pytest.raises(BusinessError):
        await tool.reuse_knowledge(run, call)
    tool.evidence.add.assert_not_awaited()


@pytest.mark.asyncio
async def test_retired_revision_is_not_reused_for_new_read(reuse):
    """历史可读不等于新取证可用，普通换版走当前授权读取。"""
    tool, run, call, _, _ = reuse
    tool.rpc.sources.return_value = SimpleNamespace(sources=[])
    assert await tool.reuse_knowledge(run, call) is None
    tool.evidence.add.assert_not_awaited()


@pytest.mark.asyncio
async def test_volatile_business_discovery_is_always_fresh(reuse):
    """发现列表和工单事件必须重新读取，不能借旧回执提供新业务权限。"""
    tool, run, call, _, _ = reuse
    call = call.model_copy(update={"name": "list_diagnostic_attachments"})
    assert await tool.reuse_knowledge(run, call) is None
    tool.rpc.sources.assert_not_awaited()
