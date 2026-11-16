"""检查纳入版本管理的文件边界、Java依赖及网关资源白名单。"""

import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
JAVA_MODULES = {
    "desk-common",
    "desk-api",
    "desk-gateway",
    "desk-user",
    "desk-ticket",
    "desk-knowledge",
}
ROOT_FILES = {
    "AGENTS.md",
    "README.md",
    "pom.xml",
    ".gitignore",
    ".gitattributes",
    ".editorconfig",
}
ROOT_DIRECTORIES = JAVA_MODULES | {".github", "docs", "desk-ai", "desk-web"}
AI_ROOT = {
    ".python-version",
    ".env.example",
    "pyproject.toml",
    "uv.lock",
    "alembic.ini",
    "src",
    "tests",
}
WEB_ROOT = {
    "tests",
    "package.json",
    "package-lock.json",
    "index.html",
    "vite.config.ts",
    "tsconfig.json",
    "eslint.config.js",
    ".prettierignore",
    "src",
}


def main() -> None:
    """只检查Git实际纳管候选，保留用户忽略的IDE文件和构建缓存。"""
    output = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT
    )
    files = sorted(
        {name for name in output.decode("utf-8").split("\0") if name and (ROOT / name).is_file()}
    )
    errors: list[str] = []
    for directory in [ROOT, *(ROOT / name for name in JAVA_MODULES | {"desk-ai", "desk-web"})]:
        for name in (
            ".local",
            "logs",
            "log",
            "tmp",
            "temp",
            ".ruff_cache",
            ".pytest_cache",
            ".mypy_cache",
        ):
            if directory == ROOT and name == ".local":
                continue
            if (directory / name).exists():
                errors.append(f"本机产物必须归仓库根.local：{(directory / name).relative_to(ROOT)}")
    for name in files:
        if name.startswith("docs/开发计划/"):
            errors.append(f"本机开发计划不得纳入Git：{name}")
        parts = Path(name).parts
        top = parts[0]
        if (len(parts) == 1 and top not in ROOT_FILES) or (
            len(parts) > 1 and top not in ROOT_DIRECTORIES
        ):
            errors.append(f"根目录未登记文件：{name}")
        if top in JAVA_MODULES and len(parts) > 1 and parts[1] not in {"pom.xml", "src"}:
            errors.append(f"Java模块根只能放pom.xml与src：{name}")
        if top == "desk-ai" and len(parts) > 1 and parts[1] not in AI_ROOT:
            errors.append(f"Python模块根未登记：{name}")
        if top == "desk-web" and len(parts) > 1 and parts[1] not in WEB_ROOT:
            errors.append(f"前端模块根未登记：{name}")
        if "desk_contracts" in parts and top == "desk-ai":
            errors.append(f"生成的Python合同不得提交：{name}")
        if name.endswith(".proto") and not name.startswith(
            "desk-api/src/main/proto/desk_contracts/"
        ):
            errors.append(f"协议必须归desk-api唯一源：{name}")
        raw = (ROOT / name).read_bytes()
        if b"\x00" in raw:
            errors.append(f"源码中存在二进制文件：{name}")
            continue
        try:
            raw.decode("utf-8")
        except UnicodeDecodeError:
            errors.append(f"文件不是UTF-8：{name}")
        if b"\r" in raw or raw.startswith(b"\xef\xbb\xbf") or (raw and not raw.endswith(b"\n")):
            errors.append(f"文件必须为无BOM的UTF-8/LF并以换行结束：{name}")

    project = ET.parse(ROOT / "pom.xml").getroot()
    modules = {item.text for item in project.findall("m:modules/m:module", NS)}
    if modules != JAVA_MODULES:
        errors.append("根POM必须聚合六个已登记Java模块")
    allowed = {
        "desk-common": set(),
        "desk-api": {"desk-common"},
        "desk-gateway": {"desk-common"},
        "desk-user": {"desk-common", "desk-api"},
        "desk-ticket": {"desk-common", "desk-api"},
        "desk-knowledge": {"desk-common", "desk-api"},
    }
    for module in JAVA_MODULES:
        pom = ET.parse(ROOT / module / "pom.xml").getroot()
        for dependency in pom.findall("m:dependencies/m:dependency", NS):
            group = dependency.findtext("m:groupId", namespaces=NS) or ""
            artifact = dependency.findtext("m:artifactId", namespaces=NS) or ""
            scope = dependency.findtext("m:scope", namespaces=NS)
            if artifact in JAVA_MODULES and artifact not in allowed[module]:
                errors.append(f"禁止模块依赖：{module} -> {artifact}")
            if module in {"desk-common", "desk-api"} and scope != "test":
                contract_dependency = module == "desk-api" and artifact in {
                    "spring-web",
                    "spring-cloud-openfeign-core",
                }
                if (
                    group.startswith("org.springframework") or "starter" in artifact
                ) and not contract_dependency:
                    errors.append(f"基础库不得包含Spring装配依赖：{module} -> {artifact}")
            if module == "desk-gateway" and any(
                word in artifact for word in ("webmvc", "mybatis", "jdbc", "mysql", "flyway")
            ):
                errors.append(f"网关不得引入MVC或数据库依赖：{artifact}")
        if module in {"desk-common", "desk-api"}:
            if pom.findall(".//m:plugin[m:artifactId='spring-boot-maven-plugin']", NS):
                errors.append(f"基础库不得执行Boot重打包：{module}")

    contract = (ROOT / "docs/设计/接口约定.md").read_text(encoding="utf-8")
    section = contract.split("## 5. 管理动作与网关归属", 1)[1].split("## 6.", 1)[0]
    expected = set(re.findall(r"^\| ([a-z-]+) \| (?:user|ticket|knowledge|AI) \|", section, re.M))
    routes = (ROOT / "desk-gateway/src/main/resources/application.yml").read_text(encoding="utf-8")
    registered = set(re.findall(r"/api/v1/admin/([a-z-]+)/\*\*", routes))
    if expected != registered or "/api/v1/admin/**" in routes:
        errors.append("网关管理资源白名单与接口约定不一致")
    from check_error_contracts import check_error_contracts

    try:
        check_error_contracts()
    except ValueError as failure:
        errors.append(str(failure))
    from check_java_layers import check_java_layers

    errors.extend(check_java_layers())
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"通过文件/依赖边界检查：{len(files)}个文件、6个Java模块、{len(registered)}个管理资源")


if __name__ == "__main__":
    main()
