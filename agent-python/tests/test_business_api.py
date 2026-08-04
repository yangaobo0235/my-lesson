import json
from types import SimpleNamespace

import httpx
import pytest
import respx
from fastapi import HTTPException

from mylesson_agent.api.business import CreateLearningPlanDraftRequest, _proxy, create_draft


def _app() -> SimpleNamespace:
    return SimpleNamespace(settings=SimpleNamespace(service_token=None))


def _user() -> SimpleNamespace:
    return SimpleNamespace(delegation_token="delegation")


@respx.mock
async def test_proxy_unwraps_successful_java_result() -> None:
    respx.get("http://course.test/resource").mock(
        return_value=httpx.Response(200, json={"code": 1000, "data": {"id": "1"}})
    )

    result = await _proxy("GET", "http://course.test/resource", _app(), _user())

    assert result == {"id": "1"}


@respx.mock
async def test_proxy_rejects_java_business_error_with_http_200() -> None:
    respx.post("http://course.test/resource").mock(
        return_value=httpx.Response(
            200,
            json={"code": 9000, "message": "server error", "data": None},
        )
    )

    with pytest.raises(HTTPException) as error:
        await _proxy("POST", "http://course.test/resource", _app(), _user())

    assert error.value.status_code == 502


@respx.mock
async def test_proxy_rejects_java_http_error() -> None:
    respx.get("http://course.test/resource").mock(return_value=httpx.Response(503))

    with pytest.raises(HTTPException) as error:
        await _proxy("GET", "http://course.test/resource", _app(), _user())

    assert error.value.status_code == 503


@respx.mock
async def test_create_draft_preserves_client_request_id() -> None:
    request_id = "12345678-1234-5678-1234-567812345678"
    route = respx.post("http://course.test/internal/v1/agent/me/learning-plan-drafts").mock(
        return_value=httpx.Response(200, json={"code": 1000, "data": {"id": "draft-1"}})
    )
    app = SimpleNamespace(
        settings=SimpleNamespace(service_token=None, course_service_url="http://course.test")
    )
    request = CreateLearningPlanDraftRequest.model_validate(
        {
            "requestId": request_id,
            "goal": "Learn Java",
            "minutesPerDay": 30,
            "courses": [{"courseId": 1}],
            "dailyRoutine": [{"minutes": 30}],
        }
    )

    result = await create_draft(request, _user(), app)

    assert result == {"id": "draft-1"}
    body = json.loads(route.calls.last.request.content)
    assert body["requestId"] == request_id
