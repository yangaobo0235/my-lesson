from datetime import UTC, datetime, timedelta
from uuid import uuid4

import jwt
import pytest
from fastapi import HTTPException
from pydantic import SecretStr

from mylesson_agent.config import get_settings
from mylesson_agent.security.delegation import decode_delegation, ensure_admin


def token(**overrides: object) -> str:
    now = datetime.now(UTC)
    claims: dict[str, object] = {
        "iss": "ml-gateway",
        "aud": "ml-agent",
        "sub": "42",
        "username": "learner",
        "roles": ["STUDENT"],
        "iat": now,
        "exp": now + timedelta(seconds=60),
        "jti": str(uuid4()),
    }
    claims.update(overrides)
    return jwt.encode(claims, "test-secret-with-at-least-32-bytes", algorithm="HS256")


def test_delegation_validates_signature_and_claims(monkeypatch: pytest.MonkeyPatch) -> None:
    settings = get_settings()
    monkeypatch.setattr(
        settings, "delegation_secret", SecretStr("test-secret-with-at-least-32-bytes")
    )
    user = decode_delegation(token())
    assert user.id == 42
    assert user.username == "learner"


def test_delegation_rejects_wrong_audience(monkeypatch: pytest.MonkeyPatch) -> None:
    settings = get_settings()
    monkeypatch.setattr(
        settings, "delegation_secret", SecretStr("test-secret-with-at-least-32-bytes")
    )
    with pytest.raises(HTTPException) as error:
        decode_delegation(token(aud="another-service"))
    assert error.value.status_code == 401


def test_admin_check_accepts_only_admin_role() -> None:
    from mylesson_agent.domain.api_models import AuthenticatedUser

    student = AuthenticatedUser(
        id=1, username="student", roles=["STUDENT"], delegation_token="token"
    )
    with pytest.raises(HTTPException) as error:
        ensure_admin(student)
    assert error.value.status_code == 403
