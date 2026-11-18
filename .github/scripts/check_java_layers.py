"""核对正式文档的实体各层对应、同名XML及手写SQL归属。"""

import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULES = ("user", "ticket", "knowledge")


def check_java_layers() -> list[str]:
    """允许有效空层，拒绝缺失对应、重复statement和Java内嵌SQL。"""
    errors: list[str] = []
    structure = (ROOT / "docs/设计/项目结构.md").read_text(encoding="utf-8")
    table = re.search(
        r"^\| 服务 \| Entity / Mapper / XML \| Service / ServiceImpl \| Controller \|\r?\n"
        r"(?:\|[^\n]*\n)+",
        structure,
        re.M,
    )
    if table is None:
        return ["项目结构缺少实体分层登记表，无法核对文件对应"]
    section = table[0]
    registered: dict[str, set[str]] = {module: set() for module in MODULES}
    for line in section.splitlines():
        columns = [item.strip() for item in line.split("|")]
        if len(columns) != 6 or columns[1] not in MODULES:
            continue
        module, entity = columns[1:3]
        service = re.match(r"[A-Za-z]+", columns[3])
        controller = re.match(r"[A-Za-z]+", columns[4])
        if service is None or controller is None:
            errors.append(f"实体分层登记不完整：{module}/{entity}")
            continue
        registered[module].add(entity)
        base = ROOT / f"desk-{module}/src/main/java/com/yangaobo/servicedesk/{module}"
        expected = (
            base / f"model/entity/{entity}Entity.java",
            base / f"mapper/{entity}Mapper.java",
            base / f"service/I{service[0]}Service.java",
            base / f"service/impl/{service[0]}ServiceImpl.java",
        )
        for path in expected:
            if not path.is_file():
                errors.append(f"实体对应文件缺失：{path.relative_to(ROOT)}")
        if not list((base / "controller").glob(f"**/{controller[0]}Controller.java")):
            errors.append(f"实体对应Controller缺失：{module}/{entity}/{controller[0]}")

    for module in MODULES:
        base = ROOT / f"desk-{module}/src/main/java/com/yangaobo/servicedesk/{module}"
        entities = {p.stem.removesuffix("Entity") for p in (base / "model/entity").glob("*.java")}
        if entities != registered[module]:
            errors.append(f"实体与分层登记不一致：{module}/{entities ^ registered[module]}")
        mappers = {p.stem: p for p in (base / "mapper").glob("*Mapper.java")}
        xmls = {
            p.stem: p for p in (ROOT / f"desk-{module}/src/main/resources/mapper").glob("*.xml")
        }
        if mappers.keys() != xmls.keys():
            errors.append(f"Mapper和同名XML不齐备：{module}/{mappers.keys() ^ xmls.keys()}")
        for name in mappers.keys() & xmls.keys():
            path = xmls[name]
            try:
                mapper = ET.parse(path).getroot()
            except ET.ParseError as failure:
                errors.append(f"Mapper XML语法无效：{path.relative_to(ROOT)}：{failure}")
                continue
            namespace = f"com.yangaobo.servicedesk.{module}.mapper.{name}"
            if mapper.tag != "mapper" or mapper.get("namespace") != namespace:
                errors.append(f"Mapper XML namespace不匹配：{path.relative_to(ROOT)}")
            statements = [
                node.get("id")
                for node in mapper
                if node.tag in {"select", "insert", "update", "delete"}
            ]
            source = mappers[name].read_text(encoding="utf-8")
            methods = re.findall(r"\b(\w+)\s*\([^;{}]*\)\s*;", source)
            if len(set(statements)) != len(statements) or set(methods) != set(statements):
                errors.append(f"自定义方法和XML statement不匹配或重复：{namespace}")
            raw = path.read_text(encoding="utf-8")
            if "${" in raw or "<script" in raw or re.search(r"\bSELECT\s+(?:\w+\.)?\*", raw, re.I):
                errors.append(f"XML存在非约定SQL拼接、script或SELECT *：{path.relative_to(ROOT)}")
        for path in base.rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            if re.search(r"@(Select|Insert|Update|Delete)(?:Provider)?\s*\(", source):
                errors.append(f"自定义SQL必须归Mapper XML：{path.relative_to(ROOT)}")
            if re.search(
                r"\.(?:last|inSql|notInSql|setSql|eqSql|gtSql|geSql|ltSql|leSql)\s*\(", source
            ) or re.search(r'\.apply\s*\(\s*"', source):
                errors.append(f"Java中残留Wrapper SQL片段：{path.relative_to(ROOT)}")
    return errors
