import json
from hmac import compare_digest
from typing import Any

import jwt
from fastapi import Header, HTTPException, status

from mylesson_agent.config import get_settings
from mylesson_agent.domain.api_models import AuthenticatedUser


def decode_delegation(token: str) -> AuthenticatedUser:
    settings = get_settings()
    if settings.delegation_public_keys is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Delegation verification is not configured",
        )
    try:
        header = jwt.get_unverified_header(token)
        if header.get("alg") != "RS256" or not header.get("kid"):
            raise jwt.InvalidAlgorithmError("Invalid delegation algorithm")
        public_keys = json.loads(settings.delegation_public_keys.get_secret_value())
        public_key = public_keys.get(str(header["kid"]))
        if not public_key:
            raise jwt.InvalidKeyError("Unknown delegation key")
        claims: dict[str, Any] = jwt.decode(
            token,
            public_key.replace("\\n", "\n"),
            algorithms=["RS256"],
            issuer=settings.delegation_issuer,
            audience=settings.delegation_audience,
            options={"require": ["exp", "iat", "iss", "aud", "sub", "jti"]},
        )
        user_id = int(claims["sub"])
        username = str(claims.get("username") or "")
        roles_value = claims.get("roles") or []
        roles = [str(role) for role in roles_value] if isinstance(roles_value, list) else []
        if not username or not roles:
            raise ValueError("Missing username or roles")
        return AuthenticatedUser(
            id=user_id,
            username=username,
            roles=roles,
            delegation_token=token,
        )
    except (jwt.PyJWTError, KeyError, TypeError, ValueError) as exception:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid user delegation",
        ) from exception


async def require_user(
    x_ml_delegation: str | None = Header(default=None),
) -> AuthenticatedUser:
    if not x_ml_delegation:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing user delegation",
        )
    return decode_delegation(x_ml_delegation)


async def require_service(
    x_internal_token: str | None = Header(default=None),
) -> None:
    settings = get_settings()
    if settings.service_token is None or not x_internal_token:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Invalid service token")
    expected = settings.service_token.get_secret_value()
    if not expected or not compare_digest(expected, x_internal_token):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Invalid service token")


def ensure_admin(user: AuthenticatedUser) -> None:
    admin_roles = {"ADMIN", "ROLE_ADMIN", "管理员", "超级管理员"}
    if not any(role.upper() in admin_roles for role in user.roles):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin role required")
