"""编排AI协议生成、wheel构建与制品合同完整性检查。"""

import argparse
import subprocess
import zipfile
from pathlib import Path

from generate_contracts import generate_contracts


def main() -> None:
    """从仓库唯一源生成后构建wheel，拒绝遗漏协议的制品。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--uv", default="uv")
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    generate_contracts()
    output = arguments.output.resolve()
    subprocess.run(
        [arguments.uv, "build", "--wheel", "--out-dir", str(output)],
        cwd=root / "desk-ai",
        check=True,
    )
    wheels = sorted(output.glob("servicedesk_ai-*.whl"), key=lambda path: path.stat().st_mtime)
    if not wheels:
        raise RuntimeError("未生成AI wheel")
    with zipfile.ZipFile(wheels[-1]) as archive:
        names = set(archive.namelist())
        required = {
            "servicedesk_ai/main.py",
            "desk_contracts/common/v1/common_pb2.py",
            "desk_contracts/common/v1/common_pb2_grpc.py",
            "desk_contracts/common/v1/common_pb2.pyi",
        }
        if not required <= names:
            raise RuntimeError("AI制品未包含完整生成合同")
    print(wheels[-1])


if __name__ == "__main__":
    main()
