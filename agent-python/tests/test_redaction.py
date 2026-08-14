from mylesson_agent.security.redaction import sanitize_audit_payload


def test_audit_payload_masks_secrets_and_phone_numbers_recursively() -> None:
    payload = {
        "token": "secret-token",
        "profile": {
            "phone": "13800138000",
            "display": "联系 13900139000 处理",
            "courseId": 7,
        },
    }

    sanitized = sanitize_audit_payload(payload)

    assert sanitized == {
        "token": "[REDACTED]",
        "profile": {
            "phone": "138****8000",
            "display": "联系 139****9000 处理",
            "courseId": 7,
        },
    }
