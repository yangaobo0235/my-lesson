from dataclasses import dataclass
from time import monotonic
from typing import Any

import httpx

from mylesson_agent.config import Settings
from mylesson_agent.domain.api_models import AuthenticatedUser
from mylesson_agent.observability.metrics import TOOL_CALLS
from mylesson_agent.security.delegation import issue_internal_delegation


@dataclass(frozen=True)
class ToolResult:
    name: str
    arguments: dict[str, Any]
    data: Any
    success: bool
    latency_ms: int
    error: str | None = None


class BusinessToolClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client = httpx.AsyncClient(timeout=settings.tool_timeout_seconds)

    async def close(self) -> None:
        await self._client.aclose()

    async def execute(
        self,
        tool_name: str,
        arguments: dict[str, Any],
        user: AuthenticatedUser,
        trace_id: str,
    ) -> ToolResult:
        if "userId" in arguments or "user_id" in arguments:
            return ToolResult(
                tool_name, {}, None, False, 0, "User identity cannot be a tool argument"
            )
        headers = {
            "X-ML-Delegation": issue_internal_delegation(user),
            "X-Trace-Id": trace_id,
        }
        if self._settings.service_token:
            headers["X-Internal-Token"] = self._settings.service_token.get_secret_value()
        started = monotonic()
        try:
            method, url, params, body = self._request(tool_name, arguments)
            response = await self._client.request(
                method,
                url,
                params=params,
                json=body,
                headers=headers,
            )
            response.raise_for_status()
            payload = response.json()
            if isinstance(payload, dict) and payload.get("success") is False:
                raise RuntimeError(str(payload.get("message") or "Business tool failed"))
            data = (
                payload.get("data") if isinstance(payload, dict) and "data" in payload else payload
            )
            latency = int((monotonic() - started) * 1000)
            TOOL_CALLS.labels(tool_name, "success").inc()
            return ToolResult(tool_name, arguments, data, True, latency)
        except Exception as exception:
            latency = int((monotonic() - started) * 1000)
            TOOL_CALLS.labels(tool_name, "failed").inc()
            return ToolResult(tool_name, arguments, None, False, latency, str(exception))

    def _request(
        self, name: str, arguments: dict[str, Any]
    ) -> tuple[str, str, dict[str, Any], dict[str, Any] | None]:
        routes: dict[str, tuple[str, str]] = {
            "get_my_profile": (
                "GET",
                f"{self._settings.user_service_url}/internal/v1/agent/me/profile",
            ),
            "get_my_recent_orders": (
                "GET",
                f"{self._settings.order_service_url}/internal/v1/agent/me/orders",
            ),
            "get_my_cart": ("GET", f"{self._settings.order_service_url}/internal/v1/agent/me/cart"),
            "get_learning_plans": (
                "GET",
                f"{self._settings.course_service_url}/internal/v1/agent/me/learning-plans",
            ),
            "search_courses": (
                "GET",
                f"{self._settings.course_service_url}/internal/v1/agent/courses/search",
            ),
            "get_course_detail": (
                "GET",
                f"{self._settings.course_service_url}/internal/v1/agent/courses/{arguments.get('courseId')}",
            ),
            "create_learning_plan_draft": (
                "POST",
                f"{self._settings.course_service_url}/internal/v1/agent/me/learning-plan-drafts",
            ),
        }
        if name not in routes:
            raise ValueError(f"Unknown tool: {name}")
        method, url = routes[name]
        if name == "create_learning_plan_draft":
            return method, url, {}, arguments
        params = {
            key: value
            for key, value in arguments.items()
            if key != "courseId" and value is not None
        }
        return method, url, params, None
