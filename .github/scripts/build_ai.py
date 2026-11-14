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
            "servicedesk_ai/migrations/env.py",
            "servicedesk_ai/migrations/script.py.mako",
            "servicedesk_ai/migrations/versions/0001_initial_schema.py",
            "servicedesk_ai/migrations/versions/0002_office_it_support.py",
            "servicedesk_ai/migrations/versions/0003_restore_original_digest.py",
            "servicedesk_ai/migrations/sql/0002_office_it_support.sql",
            "servicedesk_ai/worker.py",
            "servicedesk_ai/receiver.py",
            "servicedesk_ai/reconcile_parser.py",
            "desk_contracts/common/v1/common_pb2.py",
            "desk_contracts/common/v1/common_pb2_grpc.py",
            "desk_contracts/common/v1/common_pb2.pyi",
            "desk_contracts/ticket/v1/support_pb2.py",
            "desk_contracts/knowledge/v1/source_pb2.py",
            "desk_contracts/ai/v1/execution_pb2.py",
        }
        if not required <= names:
            raise RuntimeError("AI制品未包含完整生成合同")
        if any(Path(name).name.startswith(".env") for name in names):
            raise RuntimeError("AI制品不得包含本机dotenv配置或模板")
    print(wheels[-1])


if __name__ == "__main__":
    main()
