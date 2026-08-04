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
