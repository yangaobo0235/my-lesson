"""离线核对OSS全部版本与knowledge精确引用，只输出人工复核清单。作者：yangaobo。"""

import argparse
import csv
import json
from datetime import UTC, datetime, timedelta
from pathlib import Path


def instant(value: str) -> datetime:
    """盘点时刻必须声明时区，避免按维护机器本地时间猜测。"""
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if result.tzinfo is None:
        raise ValueError("盘点时刻和对象修改时间必须带时区")
    return result.astimezone(UTC)


def rows(path: Path, required: set[str]) -> list[dict[str, str]]:
    """只接受完整表头，空版本不冒充当前对象或精确原件。"""
    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        if not required <= set(reader.fieldnames or ()):
            raise ValueError(f"{path.name}缺少必需列")
        result = list(reader)
    for row in result:
        if any(row.get(name) in (None, "", "null") for name in required):
            raise ValueError(f"{path.name}存在缺失或空版本字段")
    return result


def reconcile(
    inventory: list[dict[str, str]],
    references: list[dict[str, str]],
    bucket: str,
    prefix: str,
    freeze_at: datetime,
    grace_hours: int,
) -> dict[str, object]:
    """按Bucket/Key/VersionId三元组比较；只产生候选，不执行网络或删除。"""
    if not bucket or not prefix or prefix.startswith("/") or grace_hours < 1:
        raise ValueError("必须限定所属桶、非空对象前缀和至少一小时保护窗口")

    def identity(item: dict[str, str]) -> tuple[str, str, str]:
        """业务引用以完整三元组标识，不能按对象键合并版本。"""
        return item["bucket"], item["key"], item["version_id"]

    for item in [*inventory, *references]:
        if item["bucket"] != bucket or not item["key"].startswith(prefix):
            raise ValueError("输入包含所声明桶/前缀之外的对象，拒绝部分范围盘点")
    keys = [identity(item) for item in inventory]
    if len(set(keys)) != len(keys):
        raise ValueError("库存存在重复精确版本，须核对导出分页")
    used = {identity(item) for item in references}
    missing = sorted(used - set(keys))
    cutoff = freeze_at - timedelta(hours=grace_hours)
    candidates = []
    deferred = 0
    for item in inventory:
        marker = item["delete_marker"].lower()
        if marker not in {"true", "false"}:
            raise ValueError("delete_marker只允许true/false")
        if identity(item) in used:
            if marker == "true":
                raise ValueError("业务原件引用指向删除标记，停止回收并核对恢复")
            continue
        if marker == "true" or instant(item["last_modified"]) >= cutoff:
            deferred += 1
            continue
        candidates.append(
            {name: item[name] for name in ("bucket", "key", "version_id", "last_modified")}
        )
    return {
        "mode": "READ_ONLY_MANUAL_REVIEW",
        "bucket": bucket,
        "prefix": prefix,
        "freeze_at": freeze_at.isoformat(),
        "grace_hours": grace_hours,
        "inventory_versions": len(inventory),
        "referenced_versions": len(used),
        "deferred_versions": deferred,
        "missing_references": [
            dict(zip(("bucket", "key", "version_id"), item, strict=True)) for item in missing
        ],
        "candidates": sorted(candidates, key=lambda item: (item["key"], item["version_id"])),
        "reclamation_blocked": bool(missing),
    }


def main() -> None:
    """读取受控窗口导出的CSV，结果留任务工作区，不读取应用凭据。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--references", type=Path, required=True)
    parser.add_argument("--bucket", required=True)
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--freeze-at", required=True)
    parser.add_argument("--grace-hours", type=int, default=24)
    parser.add_argument("--output", type=Path, required=True)
    options = parser.parse_args()
    result = reconcile(
        rows(options.inventory, {"bucket", "key", "version_id", "last_modified", "delete_marker"}),
        rows(options.references, {"bucket", "key", "version_id"}),
        options.bucket,
        options.prefix,
        instant(options.freeze_at),
        options.grace_hours,
    )
    # 不覆盖库存或引用源，保留盘点输入便于复核。
    if options.output.resolve() in {options.inventory.resolve(), options.references.resolve()}:
        raise ValueError("输出不能覆盖盘点输入")
    options.output.parent.mkdir(parents=True, exist_ok=True)
    options.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(
        json.dumps(
            {
                "candidates": len(result["candidates"]),
                "missing_references": len(result["missing_references"]),
            }
        )
    )
    if result["reclamation_blocked"]:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
