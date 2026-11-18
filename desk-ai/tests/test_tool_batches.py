"""只读工具并发、付费串行和失败取消的行为验证。作者：yangaobo。"""

import asyncio
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from servicedesk_ai.diagnosis.execution import RunWorker, ToolRound
from servicedesk_ai.errors.common import BusinessError, CommonErrorCode
from servicedesk_ai.infrastructure.models import ModelMessage, ModelTool, ModelToolCall
from servicedesk_ai.schemas.runs import RunResult


@pytest.mark.asyncio
@pytest.mark.parametrize("no_progress", [False, True])
async def test_stalled_round_finishes_without_another_discovery_call(monkeypatch, no_progress):
    """普通咨询的重复请求和连续无进展均关闭工具，再通过正常最终回答及提交边界。"""

    @asynccontextmanager
    async def connection():
        yield object()

    worker = RunWorker.__new__(RunWorker)
    run = SimpleNamespace(
        diagnosis_context_version=None,
        id="a" * 32,
        task_id=None,
        context_manifest=SimpleNamespace(support_context=None),
        pipeline_config=SimpleNamespace(max_model_calls=4, max_input_tokens=12000),
    )
    worker.engine = SimpleNamespace(connect=connection)
    worker.verify = AsyncMock()
    worker.messages = AsyncMock(return_value=(run, [ModelMessage(role="user", content="当前问题")]))
    worker.evidence = SimpleNamespace(list_items=AsyncMock(return_value=[]))
    worker.tools = SimpleNamespace(
        no_progress=AsyncMock(return_value=no_progress),
        definitions=lambda run: [
            ModelTool(name="load_procedure", description="读取", parameters={"type": "object"})
        ],
    )
    output = RunResult(answer_type="INSUFFICIENT_EVIDENCE", text="没有取得新的依据。")
    tool_message = ModelMessage(
        role="assistant",
        tool_calls=(ModelToolCall(id="repeated", name="load_procedure", arguments={}),),
    )
    worker.models = SimpleNamespace(
        chat=AsyncMock(
            side_effect=[
                SimpleNamespace(
                    call_id="c" * 32,
                    response=SimpleNamespace(
                        message=tool_message,
                        usage=SimpleNamespace(input_tokens=10, output_tokens=10),
                    ),
                ),
                SimpleNamespace(
                    response=SimpleNamespace(
                        message=ModelMessage(
                            role="assistant", content=output.model_dump_json(by_alias=True)
                        )
                    )
                ),
            ]
        )
    )
    worker.tool_messages = AsyncMock(
        return_value=ToolRound(
            [
                ModelMessage(
                    role="tool", tool_call_id="repeated", content='[{"status":"DUPLICATE_CALL"}]'
                )
            ],
            not no_progress,
        )
    )
    worker.finish = AsyncMock()
    monkeypatch.setattr(
        "servicedesk_ai.diagnosis.execution.usage", AsyncMock(return_value=SimpleNamespace(calls=0))
    )
    await worker.execute_bounded(run)
    assert worker.models.chat.await_count == 2
    assert not worker.models.chat.await_args_list[-1].args[1].tools
    worker.finish.assert_awaited_once_with(run, output)


@pytest.mark.asyncio
@pytest.mark.parametrize("duplicate", [False, True])
async def test_only_host_duplicate_receipt_requests_finalization(duplicate):
    """资料正文中的相似标记不控制执行；宿主重复回执则保留配对并停止下一轮取证。"""
    worker = RunWorker.__new__(RunWorker)
    worker.verify = AsyncMock()
    value = (
        {"status": "DUPLICATE_CALL"} if duplicate else {"content": '{"status":"DUPLICATE_CALL"}'}
    )
    worker.tools = SimpleNamespace(execute=AsyncMock(return_value=[value]))
    run = SimpleNamespace(
        diagnosis_context_version=None,
        pipeline_config=SimpleNamespace(readonly_tool_concurrency=2, tool_timeout_seconds=1),
    )
    message = ModelMessage(
        role="assistant", tool_calls=(ModelToolCall(id="one", name="load_procedure", arguments={}),)
    )
    result = await worker.tool_messages(run, "model", message)
    assert result.finalize is duplicate
    assert result.messages[0].tool_call_id == "one"


@pytest.mark.asyncio
async def test_readonly_batch_is_bounded_and_preserves_call_order():
    """多个只读外调实际重叠但不超额度，付费搜索不与其他调用重叠。"""
    worker = RunWorker.__new__(RunWorker)
    worker.verify = AsyncMock()
    active = 0
    peak = 0
    two_started = asyncio.Event()

    async def execute(run, model_id, call):
        nonlocal active, peak
        active += 1
        peak = max(peak, active)
        if active == 2:
            two_started.set()
        if call.name == "search_knowledge":
            assert active == 1
        elif call.id in {"0", "1"}:
            await asyncio.wait_for(two_started.wait(), 1)
        await asyncio.sleep(0)
        active -= 1
        return [{"id": call.id}]

    worker.tools = SimpleNamespace(execute=execute)
    run = SimpleNamespace(
        diagnosis_context_version=None,
        pipeline_config=SimpleNamespace(readonly_tool_concurrency=2, tool_timeout_seconds=1),
    )
    names = ["read_evidence"] * 3 + ["search_knowledge"] * 2 + ["load_procedure"]
    message = ModelMessage(
        role="assistant",
        tool_calls=tuple(
            ModelToolCall(id=str(i), name=name, arguments={}) for i, name in enumerate(names)
        ),
    )
    result = await worker.tool_messages(run, "model", message)
    assert peak == 2 and active == 0
    assert [item.tool_call_id for item in result.messages] == [str(i) for i in range(len(names))]
    assert not result.finalize
    assert worker.verify.await_count == len(names)


@pytest.mark.asyncio
async def test_permission_failure_cancels_pending_tools_and_skips_paid_search():
    """同批失败不遗留后台任务，也不能继续执行后面的付费工具。"""
    worker = RunWorker.__new__(RunWorker)
    worker.verify = AsyncMock()
    started = asyncio.Event()
    cancelled = asyncio.Event()
    failure = BusinessError(CommonErrorCode.FORBIDDEN)

    async def execute(run, model_id, call):
        if call.id == "fail":
            await started.wait()
            raise failure
        assert call.id == "pending"
        started.set()
        try:
            await asyncio.Event().wait()
        finally:
            cancelled.set()

    worker.tools = SimpleNamespace(execute=execute)
    run = SimpleNamespace(
        diagnosis_context_version=None,
        pipeline_config=SimpleNamespace(readonly_tool_concurrency=2, tool_timeout_seconds=1),
    )
    message = ModelMessage(
        role="assistant",
        tool_calls=(
            ModelToolCall(id="fail", name="read_evidence", arguments={}),
            ModelToolCall(id="pending", name="load_procedure", arguments={}),
            ModelToolCall(id="paid", name="search_knowledge", arguments={}),
        ),
    )
    with pytest.raises(BusinessError) as raised:
        await worker.tool_messages(run, "model", message)
    assert raised.value is failure and cancelled.is_set()
