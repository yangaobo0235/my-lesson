import json
from pathlib import Path

from pydantic import SecretStr

from mylesson_agent.config import Settings


def test_readiness_rejects_placeholder_and_short_secrets() -> None:
    placeholders = Settings(
        delegation_public_keys=SecretStr("not-json"),
        service_token=SecretStr("generate-an-independent-service-token"),
        dashscope_api_key=SecretStr("change-me"),
    )
    assert placeholders.security_configured is False
    assert placeholders.model_configured is False
    assert Settings(
        delegation_public_keys=SecretStr("{}"),
        service_token=SecretStr("also-short"),
    ).security_configured is False
    assert Settings(
        delegation_public_keys=SecretStr("{}"),
        service_token=SecretStr("s" * 32),
    ).security_configured is False


def test_readiness_accepts_independent_production_secrets() -> None:
    public_keys = {
        "v1": "-----BEGIN PUBLIC KEY-----\\nabc\\n-----END PUBLIC KEY-----"
    }
    settings = Settings(
        delegation_public_keys=SecretStr(json.dumps(public_keys)),
        service_token=SecretStr("s" * 32),
        dashscope_api_key=SecretStr("sk-valid"),
    )
    assert settings.security_configured is True
    assert settings.model_configured is True


def test_settings_load_shared_monorepo_env(
    tmp_path: Path,
    monkeypatch,
) -> None:
    service_directory = tmp_path / "agent-python"
    service_directory.mkdir()
    public_keys = (
        "AI_DELEGATION_PUBLIC_KEYS='{\"v1\":\"-----BEGIN PUBLIC KEY-----"
        "\\\\nabc\\\\n-----END PUBLIC KEY-----\"}'\n"
    )
    (tmp_path / ".env").write_text(
        public_keys + "AI_INTERNAL_TOKEN=" + "s" * 32 + "\n",
        encoding="utf-8",
    )
    monkeypatch.chdir(service_directory)
    monkeypatch.delenv("AI_DELEGATION_PUBLIC_KEYS", raising=False)
    monkeypatch.delenv("AI_INTERNAL_TOKEN", raising=False)

    settings = Settings()

    assert settings.security_configured is True
