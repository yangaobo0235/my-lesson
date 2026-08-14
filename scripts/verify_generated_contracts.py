from __future__ import annotations

from uuid import uuid4

from mylesson_internal_tools_client import ApiClient, Configuration
from mylesson_internal_tools_client.models.adjustment_request import AdjustmentRequest
from mylesson_internal_tools_client.models.confirm_draft_request import ConfirmDraftRequest
from mylesson_internal_tools_client.models.create_draft_request import CreateDraftRequest
from mylesson_internal_tools_client.models.error_response import ErrorResponse


def serialized(value: object) -> dict[str, object]:
    payload = ApiClient().sanitize_for_serialization(value)
    if not isinstance(payload, dict):
        raise AssertionError(f"Expected object serialization, got {type(payload)!r}")
    return payload


def require(payload: dict[str, object], camel: str, snake: str) -> object:
    if camel in payload:
        return payload[camel]
    if snake in payload:
        return payload[snake]
    raise AssertionError(f"Generated payload is missing {camel}/{snake}: {payload}")


def verify_models() -> None:
    request_id = uuid4()
    draft = serialized(
        CreateDraftRequest(
            request_id=request_id,
            goal="掌握 Java 并发",
            minutes_per_day=45,
            courses=[{"courseId": 7}],
            daily_routine=[{"activity": "练习", "minutes": 45}],
        )
    )
    assert str(require(draft, "requestId", "request_id")) == str(request_id)
    assert require(draft, "minutesPerDay", "minutes_per_day") == 45

    adjustment = serialized(
        AdjustmentRequest(
            request_id=request_id,
            expected_version=2,
            adjustment="每天增加十五分钟练习",
        )
    )
    assert require(adjustment, "expectedVersion", "expected_version") == 2

    confirmation = serialized(
        ConfirmDraftRequest(
            request_id=request_id,
            expected_version=3,
            payload_hash="a" * 64,
        )
    )
    assert require(confirmation, "payloadHash", "payload_hash") == "a" * 64

    error = serialized(ErrorResponse(code=409, message="version conflict", retryable=False))
    assert error["code"] == 409
    assert error["message"] == "version conflict"
    assert error["retryable"] is False


def verify_authentication_headers() -> None:
    configuration = Configuration()
    configuration.api_key["serviceToken"] = "service-token-for-contract-test"
    configuration.api_key["delegation"] = "delegation-for-contract-test"
    auth = configuration.auth_settings()
    assert auth["serviceToken"]["key"] == "X-Internal-Token"
    assert auth["serviceToken"]["value"] == "service-token-for-contract-test"
    assert auth["delegation"]["key"] == "X-ML-Delegation"
    assert auth["delegation"]["value"] == "delegation-for-contract-test"


if __name__ == "__main__":
    verify_models()
    verify_authentication_headers()
    print("Generated Java/Python contract clients passed regression checks.")
