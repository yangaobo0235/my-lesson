"""消融必须改变真实模型输入且不能影响普通worker或领取无关任务。作者：yangaobo。"""

import json
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from pydantic import ValidationError

from servicedesk_ai.diagnosis import run_context
from servicedesk_ai.diagnosis.budget import ContextGroup
from servicedesk_ai.infrastructure.models import ModelMessage, ModelRequest
from servicedesk_ai.services.run_tools import PreparedTool, RunTools
from tests.evaluation import worker
from tests.evaluation.ablations import ablate
from tests.evaluation.environment import Trial
from tests.evaluation.worker import batch_runs, claim_batch


@pytest.mark.parametrize(
    ("variant", "absent"),
    [
        ("full", set()),
        ("no_full_evidence", {"read_evidence", "load_procedure"}),
        ("no_on_demand_context", {"read_task_context"}),
        ("no_structured_state", {"read_task_context"}),
    ],
)
def test_tool_removal_keeps_permissions_and_restores_methods(variant, absent):
    """消费正式工具定义，只移除本项入口，离开测试作用域立即恢复。"""
    run = SimpleNamespace(
        task_id="task", context_manifest=SimpleNamespace(service_item_id="item", ticket_id=None)
    )
    tools = RunTools(None, None, None, None, None)
    original = {item.name for item in tools.definitions(run)}
    with ablate(variant):
        assert {item.name for item in tools.definitions(run)} == original - absent
        assert tools.definitions(SimpleNamespace(task_id=None)) == []
    assert {item.name for item in tools.definitions(run)} == original


def test_state_ablation_keeps_raw_feedback_and_current_request():
    """状态消融移除投影而非用户答案；预算处理仍使用正式函数。"""
    groups = [
        ContextGroup(
            name, (ModelMessage(role="user", content=name),), required=name == "message:current"
        )
        for name in ("message:feedback", "message:current", "fact:f1", "check:c1", "hypotheses")
    ]
    request = ModelRequest(messages=(ModelMessage(role="system", content="system"),))
    with ablate("no_structured_state"):
        _, selected = run_context.fit_context(request, groups, 10000)
        assert selected == {"message:feedback", "message:current"}
        assert run_context.represented_feedback([], [], "current") == set()
    _, selected = run_context.fit_context(request, groups, 10000)
    assert len(selected) == 5


@pytest.mark.asyncio
async def test_duplicate_ablation_preserves_argument_rejection_and_budget(monkeypatch):
    """仍调用正式prepare保留预算和意图，只取消重复短路，不吞原失败。"""
    prepare = AsyncMock(return_value=PreparedTool("intent", 2, True))
    monkeypatch.setattr(RunTools, "prepare", prepare)
    tools = RunTools(None, None, None, None, None)
    with ablate("no_duplicate_detection"):
        result = await tools.prepare("run", "model", "call")
        assert result == PreparedTool("intent", None, True)
        prepare.assert_awaited_once_with(tools, "run", "model", "call")
        prepare.side_effect = ValueError("budget")
        with pytest.raises(ValueError, match="budget"):
            await tools.prepare("run", "model", "call")


@pytest.mark.asyncio
async def test_empty_or_finished_batch_cannot_claim_any_global_queue(tmp_path):
    """无白名单时不执行SQL，已结束实例不会成为重新付费入口。"""
    db = SimpleNamespace(execute=AsyncMock())
    assert await claim_batch(db, tmp_path) is None
    trial = Trial(
        case_id="sample-1",
        family="sample",
        split="development",
        repeat=1,
        strategy="dynamic",
        expected="RESOLVED",
        status="COMPLETE",
        run_ids=["finished"],
    )
    path = tmp_path / "sample-1.dynamic.1.json"
    path.write_text(trial.model_dump_json(), encoding="utf-8")
    assert await claim_batch(db, tmp_path) is None
    db.execute.assert_not_awaited()
    value = json.loads(path.read_text())
    value.update(status="RUNNING", run_ids=["owned"])
    path.write_text(json.dumps(value), encoding="utf-8")
    assert batch_runs(tmp_path) == {"owned"}


@pytest.fixture
def running_trial_file(tmp_path):
    """建立一个有明确领取范围的真实试验文件。"""
    trial = Trial(
        case_id="sample-1",
        family="sample",
        split="development",
        repeat=1,
        strategy="dynamic",
        expected="RESOLVED",
        status="RUNNING",
        run_ids=["owned"],
    )
    path = tmp_path / "sample-1.dynamic.1.json"
    path.write_text(trial.model_dump_json(), encoding="utf-8")
    return path


@pytest.mark.parametrize("winerror", [5, 32, 33])
def test_transient_file_lock_reads_same_trial_without_widening_scope(
    monkeypatch, running_trial_file, winerror
):
    """临时占用解除后读取原文件，不忽略失败文件或退回全局队列。"""
    original = Path.read_text
    attempts = []
    pauses = []

    def read(path, *args, **kwargs):
        attempts.append(path)
        if len(attempts) < 3:
            error = PermissionError("locked")
            error.winerror = winerror
            raise error
        return original(path, *args, **kwargs)

    monkeypatch.setattr(Path, "read_text", read)
    monkeypatch.setattr(worker.time, "sleep", pauses.append)
    assert batch_runs(running_trial_file.parent) == {"owned"}
    assert attempts == [running_trial_file] * 3
    assert pauses == [0.025, 0.05]


@pytest.mark.asyncio
@pytest.mark.parametrize(("winerror", "expected_attempts"), [(None, 1), (13, 1), (32, 5)])
async def test_permanent_or_other_read_failure_never_claims(
    monkeypatch, running_trial_file, winerror, expected_attempts
):
    """其他权限错误立即失败，持续文件锁耗尽有限重试后失败，均不得执行领取SQL。"""
    error = PermissionError("unavailable")
    if winerror is not None:
        error.winerror = winerror
    attempts = []
    pauses = []

    def read(path, *args, **kwargs):
        attempts.append(path)
        raise error

    monkeypatch.setattr(Path, "read_text", read)
    monkeypatch.setattr(worker.time, "sleep", pauses.append)
    db = SimpleNamespace(execute=AsyncMock())
    with pytest.raises(PermissionError) as failure:
        await claim_batch(db, running_trial_file.parent)
    assert failure.value is error
    assert attempts == [running_trial_file] * expected_attempts
    assert sum(pauses) <= 0.375
    db.execute.assert_not_awaited()


@pytest.mark.asyncio
async def test_invalid_trial_is_not_retried_or_claimed(monkeypatch, running_trial_file):
    """JSON损坏不是暂时文件锁，明确失败且不扩大领取范围。"""
    running_trial_file.write_text("{broken", encoding="utf-8")
    pauses = []
    monkeypatch.setattr(worker.time, "sleep", pauses.append)
    db = SimpleNamespace(execute=AsyncMock())
    with pytest.raises(ValidationError):
        await claim_batch(db, running_trial_file.parent)
    assert pauses == []
    db.execute.assert_not_awaited()
