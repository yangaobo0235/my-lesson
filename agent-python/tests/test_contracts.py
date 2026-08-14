from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4

from mylesson_agent.domain.api_models import ConversationView, StreamEvent

CONTRACT = Path(__file__).resolve().parents[2] / "contracts" / "internal-tools.openapi.yaml"


def test_api_models_serialize_as_camel_case() -> None:
    now = datetime.now(UTC)
    view = ConversationView(
        id=uuid4(), user_id=8, title="新对话", status="ACTIVE", created_at=now, updated_at=now
    )
    payload = view.model_dump(mode="json")
    assert payload["userId"] == 8
    assert "user_id" not in payload


def test_sse_wrapper_contains_compatibility_fields() -> None:
    event = StreamEvent(
        event_id=1,
        sequence=1,
        type="run_started",
        conversation_id=uuid4(),
        run_id=uuid4(),
        request_id=uuid4(),
        trace_id="trace",
        timestamp=datetime.now(UTC),
        data={"status": "RUNNING"},
    ).model_dump(mode="json")
    assert set(event) == {
        "eventId",
        "sequence",
        "type",
        "conversationId",
        "runId",
        "requestId",
        "traceId",
        "timestamp",
        "data",
    }


def test_internal_tool_contract_covers_runtime_routes_and_security_headers() -> None:
    contract = CONTRACT.read_text(encoding="utf-8")
    expected_paths = {
        "/internal/v1/agent/me/profile",
        "/internal/v1/agent/me/orders",
        "/internal/v1/agent/me/cart",
        "/internal/v1/agent/courses/search",
        "/internal/v1/agent/courses/{courseId}",
        "/internal/v1/agent/me/learning-plan-drafts",
        "/internal/v1/agent/me/learning-plan-drafts/by-request/{requestId}",
    }

    assert all(f"  {path}:" in contract for path in expected_paths)
    assert "name: X-Internal-Token" in contract
    assert "name: X-ML-Delegation" in contract
    assert "#/components/schemas/ErrorResponse" in contract
    assert "version: 1.1.0" in contract
