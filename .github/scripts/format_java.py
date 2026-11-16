"""使用 IDEA 原生格式器统一手写 Java，隔离正在运行的 IDE 和本机配置。"""

import argparse
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULES = (
    "desk-common",
    "desk-api",
    "desk-gateway",
    "desk-user",
    "desk-ticket",
    "desk-knowledge",
)


def main() -> int:
    """格式化已登记源码；检查模式将原生格式器退出码交给调用方。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--idea-home", default=os.environ.get("DESK_IDEA_HOME"))
    parser.add_argument("--check", action="store_true", help="只检查，不写入源码")
    args = parser.parse_args()
    if not args.idea_home:
        parser.error("通过 --idea-home 或 DESK_IDEA_HOME 指定 IntelliJ IDEA 安装目录")
    launcher = (
        Path(args.idea_home).resolve() / "bin" / ("format.bat" if os.name == "nt" else "format.sh")
    )
    if not launcher.is_file():
        parser.error(f"找不到 IDEA 原生格式器：{launcher}")

    work = ROOT / ".local/idea-format"
    work.mkdir(parents=True, exist_ok=True)
    properties = work / "idea.properties"
    properties.write_text(
        "".join(
            f"idea.{name}.path={(work / name).as_posix()}\n"
            for name in ("config", "system", "log", "plugins")
        ),
        encoding="utf-8",
        newline="\n",
    )
    vmoptions = work / "formatter.vmoptions"
    vmoptions.write_text(
        "-Xms128m\n-Xmx2048m\n-Djava.awt.headless=true\n",
        encoding="utf-8",
        newline="\n",
    )
    temp = work / "tmp"
    temp.mkdir(exist_ok=True)
    env = os.environ | {
        "IDEA_PROPERTIES": str(properties),
        "IDEA_VM_OPTIONS": str(vmoptions),
        "TEMP": str(temp),
        "TMP": str(temp),
        "TMPDIR": str(temp),
    }
    paths = [
        str(path)
        for module in MODULES
        for kind in ("main", "test")
        if (path := ROOT / module / "src" / kind / "java").is_dir()
    ]
    paths.extend(str(path) for path in sorted((ROOT / ".github/scripts").glob("*.java")))
    command = [
        str(launcher),
        "-s",
        str(ROOT / ".github/format/intellij-java.xml"),
        "-charset",
        "UTF-8",
        "-r",
        "-m",
        "*.java",
    ]
    if args.check:
        command.append("-dry")
    return subprocess.run([*command, *paths], cwd=ROOT, env=env, check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
