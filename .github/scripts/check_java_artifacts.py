"""检查真实Java制品的库/启动应用边界与本机配置排除。"""

import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def main() -> None:
    """在Maven构建后检查实际JAR，不能以Git忽略代替打包排除。"""
    project = ET.parse(ROOT / "pom.xml").getroot()
    version = project.findtext("m:version", namespaces=NS)
    for module in project.findall("m:modules/m:module", NS):
        name = module.text
        if name is None:
            raise RuntimeError("模块名不能为空")
        jar = ROOT / name / "target" / f"{name}-{version}.jar"
        with zipfile.ZipFile(jar) as archive:
            files = archive.namelist()
            boot = any(path.startswith("BOOT-INF/") for path in files)
            if boot != (name not in {"desk-common", "desk-api"}):
                raise RuntimeError(f"普通库/启动制品边界不正确：{name}")
            if any(Path(path).name.startswith("application-local") for path in files):
                raise RuntimeError(f"制品包含本机配置或模板：{name}")
            if any(
                Path(path).name in {"application-test.yml", "application-connected.yml"}
                for path in files
            ):
                raise RuntimeError(f"制品包含测试配置或已移除的功能配置：{name}")
            if name == "desk-api":
                for model in ["ActorRef", "RpcErrorDetail"]:
                    expected = f"com/yangaobo/servicedesk/api/common/v1/{model}.class"
                    if expected not in files:
                        raise RuntimeError(f"api JAR未包含生成合同：{model}")
            if name in {"desk-user", "desk-ticket", "desk-knowledge"}:
                if "BOOT-INF/classes/db/migration/V1__initial_schema.sql" not in files:
                    raise RuntimeError(f"应用制品未包含数据库初始迁移：{name}")
            if name in {"desk-ticket", "desk-knowledge"}:
                service = name.removeprefix("desk-")
                expected = {
                    "BOOT-INF/classes/db/upgrade/V2__office_it_support.sql",
                    f"BOOT-INF/classes/com/yangaobo/servicedesk/{service}/migration/"
                    "V2__office_it_support.class",
                }
                if not expected <= set(files):
                    raise RuntimeError(f"应用制品未包含受控增量迁移：{name}")
            if (
                name == "desk-knowledge"
                and (
                    "BOOT-INF/classes/com/yangaobo/servicedesk/knowledge/migration/"
                    "V3__restore_original_digest.class"
                )
                not in files
            ):
                raise RuntimeError("knowledge制品未包含原件摘要修复迁移")
            print(f"制品边界通过：{jar.name}")


if __name__ == "__main__":
    main()
