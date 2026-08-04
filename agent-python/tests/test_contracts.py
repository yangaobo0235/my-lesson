from datetime import UTC, datetime
from uuid import uuid4

from mylesson_agent.domain.api_models import ConversationView, StreamEvent


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
