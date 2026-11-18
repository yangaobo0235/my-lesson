"""验证持久阶段和任务更新的 SSE 交付及来源隐藏。作者：yangaobo。"""

import asyncio
import json
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
import pytest

from desk_contracts.user.v1.identity_pb2 import VerifiedIdentity
from servicedesk_ai.api import runs
from servicedesk_ai.api.dependencies import identity
from servicedesk_ai.bootstrap.settings import Settings
from servicedesk_ai.main import create_app
from servicedesk_ai.schemas.run_views import RunTaskUpdate

ID = "a" * 32


@pytest.mark.asyncio
@pytest.mark.parametrize("hidden", [False, True])
async def test_subscription_reports_persisted_phases_and_only_visible_result(monkeypatch, hidden):
    """同一 RUNNING 状态的不同阶段也交付，隐藏来源省略派生内容及检查。"""
    monkeypatch.setattr(
        runs,
        "asyncio",
        SimpleNamespace(sleep=AsyncMock(), CancelledError=asyncio.CancelledError),
    )
    model = SimpleNamespace(status="RUNNING", execution_phase="MODEL", iteration_number=1)
    tool = SimpleNamespace(status="RUNNING", execution_phase="TOOL", iteration_number=1)
    outcome = SimpleNamespace(
        status="SUCCEEDED",
        execution_phase="COMMIT",
        iteration_number=2,
        sources_hidden=hidden,
        answer="已保存正文",
        answer_type="CLARIFICATION",
        citations=[],
    )
    task = RunTaskUpdate(
        task_id=ID, status="WAITING_USER", task_state_version="2", pending_check_ids=[ID]
    )
    reads = SimpleNamespace(
        get=AsyncMock(return_value=model),
        subscription_snapshot=AsyncMock(
            side_effect=[(model, [], None), (tool, [], None), (outcome, [], task)]
        ),
    )
    actor = VerifiedIdentity()
    verify_ref = AsyncMock(return_value=actor)
    app = create_app(Settings(infrastructure_enabled=False))
    app.dependency_overrides[identity] = lambda: actor
    app.state.services = SimpleNamespace(
        run_reads=reads, rpc=SimpleNamespace(identity=SimpleNamespace(verify_ref=verify_ref))
    )
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.post(f"/api/v1/ai/runs/{ID}/stream")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    events = {
        json.loads(payload.removeprefix("data: "))["sequence"]: (
            kind.removeprefix("event: "),
            json.loads(payload.removeprefix("data: "))["data"],
        )
        for kind, payload in (
            frame.splitlines()
            for frame in response.text.strip().split("\n\n")
            if frame.startswith("event:")
        )
    }
    assert [data["executionPhase"] for kind, data in events.values() if kind == "stage"] == [
        "MODEL",
        "TOOL",
        "COMMIT",
    ]
    assert events[2][1]["iterationNumber"] == 1
    assert list(events.values())[-1] == ("done", {"status": "SUCCEEDED", "sourcesHidden": hidden})
    if hidden:
        assert list(events) == [1, 2, 3, 4]
    else:
        assert events[4] == ("task", task.model_dump(mode="json", by_alias=True))
        assert events[5][0] == "answer"
        assert events[6][0] == "citations"
    assert verify_ref.await_count == reads.subscription_snapshot.await_count == 3
    reads.get.assert_awaited_once_with(actor, ID)
