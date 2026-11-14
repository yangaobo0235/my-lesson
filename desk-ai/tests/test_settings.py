"""验证原生dotenv加载、环境优先级及生产隔离，不读取真实本机凭据。"""

import json
import os
from collections.abc import Iterator
from pathlib import Path

import pytest
from pydantic import ValidationError

from servicedesk_ai.bootstrap.settings import Settings, get_settings


@pytest.fixture(autouse=True)
def isolated_settings(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> Iterator[None]:
    """每例使用专用目录和干净AI环境，避免读取开发机或CI注入的凭据。"""
    for key in tuple(os.environ):
        if key.startswith("DESK_AI_"):
            monkeypatch.delenv(key)
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("DESK_AI_INFRASTRUCTURE_ENABLED", "false")
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


def test_development_dotenv_uses_native_nested_fields_and_process_overrides(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    """含#、空格和美元符的值正确读取，进程变量优先且不清除同组其他字段。"""
    password = "p#$ (literal)\\value"
    (tmp_path / ".env").write_text(
        f"DESK_AI_MYSQL__PASSWORD={json.dumps(password)}\n"
        "DESK_AI_MODEL__API_KEY='file-key'\n"
        "DESK_AI_MODEL__TEMPERATURE=0.3\n"
        "DESK_AI_MODEL__DIMENSIONS=1024\n",
        encoding="utf-8",
    )
    monkeypatch.setenv("DESK_AI_MODEL__API_KEY", "process-key")
    settings = get_settings()
    assert settings.mysql.password.get_secret_value() == password
    assert settings.model.api_key.get_secret_value() == "process-key"
    assert settings.model.temperature == 0.3
    assert settings.model.dimensions == 1024
    assert "process-key" not in repr(settings)
    # 直接构造不隐式读取文件，测试与显式装配可独立选择配置源。
    assert Settings(infrastructure_enabled=False).mysql.password.get_secret_value() == ""


def test_production_never_reads_development_dotenv(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    """生产忽略整个开发文件，未知字段及本机密码都不会被加载。"""
    (tmp_path / ".env").write_text(
        "UNKNOWN_KEY=private-value\nDESK_AI_MYSQL__PASSWORD=private-value\n",
        encoding="utf-8",
    )
    monkeypatch.setenv("DESK_AI_ENVIRONMENT", "prod")
    settings = get_settings()
    assert settings.environment == "prod"
    assert settings.mysql.password.get_secret_value() == ""


def test_development_file_cannot_select_production(tmp_path: Path) -> None:
    """生产环境选择必须来自进程，开发文件不能改变入口的环境边界。"""
    (tmp_path / ".env").write_text("DESK_AI_ENVIRONMENT=prod\n", encoding="utf-8")
    with pytest.raises(ValueError, match="不能通过开发.env切换生产"):
        get_settings()


def test_dotenv_typos_and_missing_credentials_fail_safely(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """拼错字段和空凭据保持原有失败关闭，错误中不回显输入值。"""
    path = tmp_path / ".env"
    path.write_text("DESK_AI_MODEL__TYPO=private-value\n", encoding="utf-8")
    with pytest.raises(ValidationError) as error:
        get_settings()
    assert "private-value" not in str(error.value)
    monkeypatch.delenv("DESK_AI_INFRASTRUCTURE_ENABLED")
    path.write_text("DESK_AI_INFRASTRUCTURE_ENABLED=true\n", encoding="utf-8")
    with pytest.raises(ValidationError, match="mysql.password"):
        get_settings()


def test_published_dotenv_template_uses_valid_native_fields() -> None:
    """直接解析正式模板，保证填写入口与类型配置一致且凭据保持空白。"""
    template = Path(__file__).resolve().parents[1] / ".env.example"
    settings = Settings(_env_file=template, infrastructure_enabled=False)
    assert settings.mysql.host == "192.168.23.66"
    assert settings.model.dimensions == 1024
    assert settings.mysql.password.get_secret_value() == ""
    assert settings.model.api_key.get_secret_value() == ""
    assert settings.rocketmq.secret_key.get_secret_value() == ""


def test_normal_startup_without_credentials_fails_closed(monkeypatch: pytest.MonkeyPatch) -> None:
    """未复制开发文件或忘记填写凭据时不能悄悄启动离线服务。"""
    monkeypatch.delenv("DESK_AI_INFRASTRUCTURE_ENABLED")
    with pytest.raises(ValidationError, match="mysql.password"):
        get_settings()
