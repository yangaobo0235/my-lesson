"""从desk-api唯一协议源构建Python合同，不在服务启动时执行。"""

import shutil
import tempfile
from pathlib import Path

from grpc_tools import protoc


def generate_contracts() -> Path:
    """验证协议归属并替换本仓库内明确归生成器所有的输出目录。"""
    root = Path(__file__).resolve().parents[2]
    source = root / "desk-api" / "src" / "main" / "proto"
    destination = root / "desk-ai" / "src" / "desk_contracts"
    protos = sorted(source.rglob("*.proto"))
    if not protos or any(path.relative_to(source).parts[0] != "desk_contracts" for path in protos):
        raise RuntimeError("必须从desk-api的desk_contracts命名空间生成协议")
    # 递归删除前验证绝对目标及所有者，拒绝符号链接或意外手写内容。
    expected = root / "desk-ai" / "src" / "desk_contracts"
    if (
        destination.is_symlink()
        or destination.resolve() != expected
        or root not in destination.parents
    ):
        raise RuntimeError("生成目标必须是本仓库desk-ai/src/desk_contracts")
    if destination.exists():
        for path in destination.rglob("*"):
            if path.is_symlink():
                raise RuntimeError("生成目录不允许符号链接")
            if path.is_file() and not (
                path.name.endswith(("_pb2.py", "_pb2_grpc.py", "_pb2.pyi", ".pyc"))
                or path.name == "__init__.py"
            ):
                raise RuntimeError(f"生成目录中存在非生成文件：{path.name}")
    with tempfile.TemporaryDirectory(prefix="desk-contracts-") as temporary:
        arguments = [
            "protoc",
            f"--proto_path={source}",
            f"--python_out={temporary}",
            f"--grpc_python_out={temporary}",
            f"--pyi_out={temporary}",
            *(str(path.relative_to(source).as_posix()) for path in protos),
        ]
        if protoc.main(arguments) != 0:
            raise RuntimeError("Protobuf生成失败，保留原输出")
        generated = Path(temporary) / "desk_contracts"
        if destination.exists():
            shutil.rmtree(destination)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(generated, destination)
    return destination


if __name__ == "__main__":
    print(generate_contracts())
