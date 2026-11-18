"""评测通过正式HTTP合同驱动生产服务，不直接修改业务数据库。作者：yangaobo。"""

import json
import time
from pathlib import Path
from uuid import uuid4

import httpx


class ApiFailure(RuntimeError):
    """只暴露安全错误编号，不把认证或原始响应写入测试报告。"""

    def __init__(self, code: int, path: str) -> None:
        super().__init__(f"{path}: code={code}")
        self.code = code


class Client:
    """每个操作者拥有独立Cookie及CSRF，凭据仅从指定原生配置文件读取。"""

    def __init__(self, base_url: str, accounts_file: Path, role: str) -> None:
        account = json.loads(accounts_file.read_text(encoding="utf-8"))[role]
        self.http = httpx.Client(base_url=base_url, timeout=15, trust_env=False)
        self.csrf = ""
        try:
            self.csrf = self.call("users/auth/csrf")["token"]
            self.identity = self.call(
                "users/auth/login",
                {
                    "username": account["username"],
                    "password": account["password"],
                },
            )
            self.csrf = self.call("users/auth/csrf")["token"]
        except BaseException:
            self.http.close()
            raise

    def call(self, path: str, body: dict | None = None) -> dict:
        """不自动重试未知写结果；原动作键由场景驱动器持久保存。"""
        response = self.http.request(
            "GET" if body is None else "POST",
            "/api/v1/" + path,
            json=body,
            headers={"X-CSRF-TOKEN": self.csrf},
        )
        value = response.json()
        if value.get("code") != 0 or not response.is_success:
            raise ApiFailure(value.get("code", -1), path.split("?")[0])
        return value["data"]

    def upload(self, metadata: dict, content: bytes) -> dict:
        """只有原创合成规程上传，不读取任意设备或客户文件。"""
        response = self.http.post(
            "/api/v1/admin/document/upload",
            files={
                "metadata": (None, json.dumps(metadata, ensure_ascii=False), "application/json"),
                "file": ("evaluation-guide.md", content, "text/markdown"),
            },
            headers={"X-CSRF-TOKEN": self.csrf},
            timeout=60,
        )
        value = response.json()
        if value.get("code") != 0 or not response.is_success:
            raise ApiFailure(value.get("code", -1), "admin/document/upload")
        return value["data"]

    def close(self) -> None:
        """只释放本评测连接，不注销其他会话或修改账号。"""
        self.http.close()


def key() -> str:
    """一次新业务意图对应一个请求身份。"""
    return uuid4().hex


def write_json(path: Path, value: object) -> None:
    """安全产物原子替换；Windows短暂占用只重试落盘，不重发外部请求或隐藏永久失败。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for attempt in range(5):
        try:
            temporary.replace(path)
            return
        except PermissionError as error:
            if getattr(error, "winerror", None) not in {5, 32, 33} or attempt == 4:
                raise
            time.sleep(0.025 * 2**attempt)
