import httpx
from pydantic import SecretStr

from mylesson_agent.config import Settings
from mylesson_agent.domain.api_models import AuthenticatedUser
from mylesson_agent.tools.client import BusinessToolClient


async def test_tool_rejects_user_identity_argument() -> None:
    client = BusinessToolClient(Settings(service_token=SecretStr("service")))
    user = AuthenticatedUser(
        id=7, username="learner", roles=["STUDENT"], delegation_token="delegation"
    )
    try:
        result = await client.execute("get_my_profile", {"userId": 99}, user, "trace")
    finally:
        await client.close()
    assert result.success is False
    assert result.arguments == {}
    assert "identity" in (result.error or "").lower()


def test_learning_plan_draft_is_post_body() -> None:
    client = BusinessToolClient(Settings())
    payload = {
        "requestId": "00000000-0000-0000-0000-000000000001",
        "goal": "学习Java",
        "minutesPerDay": 30,
        "courses": [{"courseId": 1}],
        "dailyRoutine": [{"minutes": 30}],
    }
    method, url, params, body = client._request("create_learning_plan_draft", payload)
    assert method == "POST"
    assert url.endswith("/internal/v1/agent/me/learning-plan-drafts")
    assert params == {}
    assert body == payload


async def test_read_tool_retries_transient_network_failures() -> None:
    attempts = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        if attempts < 3:
            raise httpx.ReadTimeout("temporary", request=request)
        return httpx.Response(200, json={"data": {"id": 7}})

    client = BusinessToolClient(
        Settings(tool_read_max_attempts=3, tool_retry_base_seconds=0)
    )
    await client._client.aclose()
    client._client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    try:
        result = await client.execute(
            "get_my_profile",
            {},
            AuthenticatedUser(
                id=7, username="learner", roles=["STUDENT"], delegation_token="delegation"
            ),
            "trace",
        )
    finally:
        await client.close()

    assert result.success is True
    assert attempts == 3


async def test_write_timeout_queries_idempotent_result_without_reposting() -> None:
    methods: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        methods.append(request.method)
        if request.method == "POST":
            raise httpx.ReadTimeout("unknown result", request=request)
        return httpx.Response(200, json={"id": "draft-1", "status": "WAITING_CONFIRMATION"})

    client = BusinessToolClient(Settings(tool_retry_base_seconds=0))
    await client._client.aclose()
    client._client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    try:
        result = await client.execute(
            "create_learning_plan_draft",
            {"requestId": "00000000-0000-0000-0000-000000000001", "goal": "Java"},
            AuthenticatedUser(
                id=7, username="learner", roles=["STUDENT"], delegation_token="delegation"
            ),
            "trace",
        )
    finally:
        await client.close()

    assert result.success is True
    assert result.data["id"] == "draft-1"
    assert methods == ["POST", "GET"]
