from pathlib import Path

from pydantic import SecretStr

from mylesson_agent.config import Settings


def test_readiness_rejects_placeholder_and_short_secrets() -> None:
    placeholders = Settings(
        delegation_secret=SecretStr("generate-at-least-32-random-bytes"),
        service_token=SecretStr("generate-an-independent-service-token"),
        dashscope_api_key=SecretStr("change-me"),
    )
    assert placeholders.security_configured is False
    assert placeholders.model_configured is False
    assert Settings(
        delegation_secret=SecretStr("short"),
        service_token=SecretStr("also-short"),
    ).security_configured is False
    assert Settings(
        delegation_secret=SecretStr(
            "generate-the-same-value-as-ai-identity-secret"
        ),
        service_token=SecretStr("s" * 32),
    ).security_configured is False


def test_readiness_accepts_independent_production_secrets() -> None:
    settings = Settings(
        delegation_secret=SecretStr("d" * 32),
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
    (tmp_path / ".env").write_text(
        "AI_DELEGATION_SECRET=" + "d" * 32 + "\n"
        "AI_INTERNAL_TOKEN=" + "s" * 32 + "\n",
        encoding="utf-8",
    )
    monkeypatch.chdir(service_directory)
    monkeypatch.delenv("AI_DELEGATION_SECRET", raising=False)
    monkeypatch.delenv("AI_INTERNAL_TOKEN", raising=False)

    settings = Settings()

    assert settings.security_configured is True
