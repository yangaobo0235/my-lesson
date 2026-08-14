import json
from datetime import UTC, datetime, timedelta
from uuid import uuid4

import jwt
import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi import HTTPException
from pydantic import SecretStr

from mylesson_agent.config import get_settings
from mylesson_agent.security.delegation import decode_delegation, ensure_admin

PRIVATE_KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)
PRIVATE_PEM = PRIVATE_KEY.private_bytes(
    serialization.Encoding.PEM,
    serialization.PrivateFormat.PKCS8,
    serialization.NoEncryption(),
)
PUBLIC_PEM = PRIVATE_KEY.public_key().public_bytes(
    serialization.Encoding.PEM,
    serialization.PublicFormat.SubjectPublicKeyInfo,
).decode()


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
    return jwt.encode(claims, PRIVATE_PEM, algorithm="RS256", headers={"kid": "test"})


def test_delegation_validates_signature_and_claims(monkeypatch: pytest.MonkeyPatch) -> None:
    settings = get_settings()
    monkeypatch.setattr(
        settings, "delegation_public_keys", SecretStr(json.dumps({"test": PUBLIC_PEM}))
    )
    user = decode_delegation(token())
    assert user.id == 42
    assert user.username == "learner"


def test_delegation_rejects_wrong_audience(monkeypatch: pytest.MonkeyPatch) -> None:
    settings = get_settings()
    monkeypatch.setattr(
        settings, "delegation_public_keys", SecretStr(json.dumps({"test": PUBLIC_PEM}))
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
