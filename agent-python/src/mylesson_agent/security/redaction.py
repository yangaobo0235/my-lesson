import re
from typing import Any

SENSITIVE_KEYS = {
    "authorization",
    "cookie",
    "delegationtoken",
    "password",
    "secret",
    "servicetoken",
    "token",
}
PHONE_PATTERN = re.compile(r"(?<!\d)(1\d{10})(?!\d)")


def sanitize_audit_payload(value: Any) -> Any:
    if isinstance(value, dict):
        sanitized: dict[str, Any] = {}
        for key, item in value.items():
            normalized = re.sub(r"[^a-z0-9]", "", str(key).lower())
            sanitized[str(key)] = (
                "[REDACTED]"
                if normalized in SENSITIVE_KEYS or normalized.endswith("token")
                else sanitize_audit_payload(item)
            )
        return sanitized
    if isinstance(value, list):
        return [sanitize_audit_payload(item) for item in value]
    if isinstance(value, tuple):
        return [sanitize_audit_payload(item) for item in value]
    if isinstance(value, str):
        return PHONE_PATTERN.sub(
            lambda match: f"{match.group(1)[:3]}****{match.group(1)[7:]}", value
        )
    return value
