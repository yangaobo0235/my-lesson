import json
from datetime import UTC, datetime
from types import SimpleNamespace
from uuid import uuid4

from mylesson_agent.conversation.events import EventPublisher
from mylesson_agent.domain.api_models import StreamEvent


def test_sse_contains_persistent_event_id() -> None:
    event = StreamEvent(
        event_id=42,
        sequence=3,
        type="answer_delta",
        conversation_id=uuid4(),
        run_id=uuid4(),
        request_id=uuid4(),
        trace_id="trace",
        timestamp=datetime.now(UTC),
        data={"delta": "answer"},
    )
    text = EventPublisher._format(event)
    assert text.startswith("id: 42\nevent: answer_delta\ndata: ")
    payload = json.loads(text.split("data: ", 1)[1])
    assert payload["eventId"] == 42
    assert payload["sequence"] == 3


def test_database_event_rebuilds_public_stream_contract() -> None:
    conversation_id = uuid4()
    run_id = uuid4()
    request_id = uuid4()
    event = EventPublisher._from_row(
        SimpleNamespace(
            id=7,
            sequence=2,
            event_type="run_completed",
            conversation_id=conversation_id,
            run_id=run_id,
            created_at=datetime.now(UTC),
            event_data={"status": "SUCCEEDED"},
        ),
        SimpleNamespace(request_id=request_id, trace_id="trace"),
    )
    assert event.event_id == 7
    assert event.conversation_id == conversation_id
    assert event.request_id == request_id
