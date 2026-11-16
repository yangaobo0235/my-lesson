"""核对全部所有者错误编号、业务子段和公共跨语言镜像。作者：yangaobo。"""

import ast
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def check_error_contracts() -> None:
    """从正式登记逐项比较定义，发现新枚举遗漏和已登记项缺失。"""
    contract = (ROOT / "docs/设计/接口约定.md").read_text(encoding="utf-8")
    entries = re.findall(r"^\| (\d+) \| (\w+ErrorCode)\.(\w+) \|", contract, re.M)
    declared = {(kind, name): int(code) for code, kind, name in entries}
    codes = [int(code) for code, _, _ in entries]
    if len(set(codes)) != len(codes) or len(declared) != len(entries):
        raise ValueError("正式错误登记存在重复编号或重复语义")
    domains = {
        "CommonErrorCode": (1, 1999),
        "AuthenticationErrorCode": (2001, 2099),
        "AccountErrorCode": (2100, 2199),
        "OrganizationErrorCode": (2200, 2299),
        "SupportErrorCode": (3000, 3099),
        "DeviceErrorCode": (3100, 3199),
        "TicketErrorCode": (3200, 3299),
        "BookingErrorCode": (3300, 3399),
        "DocumentErrorCode": (4000, 4299),
        "GenerationErrorCode": (5000, 5099),
        "RetrievalErrorCode": (5100, 5199),
        "DraftErrorCode": (5200, 5299),
    }
    actual: dict[tuple[str, str], int] = {}
    java_common: dict[str, int] = {}
    for module in ("desk-common", "desk-user", "desk-ticket", "desk-knowledge"):
        for path in (ROOT / module / "src/main/java").rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            if not re.search(r"implements\s+(?:[\w.]+\.)?ErrorCode\b", source):
                continue
            if path.stem.startswith("Remote"):
                for name, number in re.findall(r'\b([A-Z][A-Z_0-9]*)\s*\(\s*(\d+)\s*,\s*"', source):
                    if not any(
                        int(number) == int(code) and name == owner_name
                        for code, _, owner_name in entries
                    ):
                        raise ValueError(
                            f"远端镜像重复编号表达了不同语义：{path.stem}.{name}={number}"
                        )
                continue
            if path.stem not in domains:
                raise ValueError(f"未登记错误定义：{path}")
            for name, code in re.findall(r'\b([A-Z][A-Z_0-9]*)\s*\(\s*(\d+)\s*,\s*"', source):
                if (path.stem, name) in actual:
                    raise ValueError(f"所有者定义被重复声明：{path.stem}.{name}")
                if module != "desk-common" and path.parent.name != "error":
                    raise ValueError(f"业务错误必须归所有者error包：{path}")
                actual[path.stem, name] = int(code)
                if path.stem == "CommonErrorCode":
                    java_common[name] = int(code)
    for path in (ROOT / "desk-ai/src/servicedesk_ai/errors").glob("*.py"):
        for node in ast.parse(path.read_text(encoding="utf-8")).body:
            if not isinstance(node, ast.ClassDef) or not any(
                isinstance(base, ast.Name) and base.id == "Enum" for base in node.bases
            ):
                continue
            if node.name not in domains:
                raise ValueError(f"未登记错误定义：{node.name}")
            values = {
                item.targets[0].id: item.value.elts[0].value
                for item in node.body
                if isinstance(item, ast.Assign)
                and isinstance(item.targets[0], ast.Name)
                and isinstance(item.value, ast.Tuple)
                and isinstance(item.value.elts[0], ast.Constant)
            }
            if node.name == "CommonErrorCode":
                if values != java_common:
                    raise ValueError("Java/Python公共错误镜像不一致")
            else:
                actual.update({(node.name, name): code for name, code in values.items()})
    if actual != declared:
        raise ValueError(
            f"错误定义与正式登记不一致：缺失={declared.items() - actual.items()}，"
            f"多余={actual.items() - declared.items()}"
        )
    for (kind, name), code in actual.items():
        minimum, maximum = domains[kind]
        if not minimum <= code <= maximum or (
            kind == "CommonErrorCode" and code != 1 and code < 1000
        ):
            raise ValueError(f"错误编号超出所属子段：{kind}.{name}={code}")


if __name__ == "__main__":
    check_error_contracts()
    print("通过全部所有者错误与跨语言公共镜像检查")
