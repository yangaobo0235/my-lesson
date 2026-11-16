"""离线盘点按精确版本保留引用，缺失引用或范围错误时拒绝回收判断。作者：yangaobo。"""

import csv
import json
import subprocess
import sys
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parents[2] / ".github/scripts/inventory_oss_originals.py"


@pytest.mark.parametrize("fault", [None, "missing", "scope", "marker"])
def test_inventory_never_confuses_object_key_with_exact_version(tmp_path, fault):
    """同键不同版本独立判断，新对象和删除标记始终留给人工复核。"""
    inventory = [
        {
            "bucket": "private",
            "key": "test/file",
            "version_id": version,
            "last_modified": modified,
            "delete_marker": marker,
        }
        for version, modified, marker in [
            ("used", "2026-11-01T00:00:00Z", "false"),
            ("orphan", "2026-11-01T00:00:00Z", "false"),
            ("new", "2026-11-14T00:00:00Z", "false"),
            ("marker", "2026-11-01T00:00:00Z", "true"),
        ]
    ]
    reference = {"bucket": "private", "key": "test/file", "version_id": "used"}
    if fault == "missing":
        reference["version_id"] = "missing-version"
    elif fault == "scope":
        inventory[0]["key"] = "prod/file"
    elif fault == "marker":
        reference["version_id"] = "marker"
    for filename, rows in [("inventory.csv", inventory), ("references.csv", [reference])]:
        with (tmp_path / filename).open("w", encoding="utf-8", newline="") as target:
            writer = csv.DictWriter(target, fieldnames=list(rows[0]))
            writer.writeheader()
            writer.writerows(rows)
    output = tmp_path / "report.json"
    result = subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--inventory",
            str(tmp_path / "inventory.csv"),
            "--references",
            str(tmp_path / "references.csv"),
            "--bucket",
            "private",
            "--prefix",
            "test/",
            "--freeze-at",
            "2026-11-14T12:00:00Z",
            "--output",
            str(output),
        ],
        capture_output=True,
        timeout=10,
    )
    if fault in {"scope", "marker"}:
        assert result.returncode != 0 and not output.exists()
    else:
        report = json.loads(output.read_text(encoding="utf-8"))
        assert report["mode"] == "READ_ONLY_MANUAL_REVIEW"
        if fault == "missing":
            assert result.returncode == 2 and report["reclamation_blocked"]
            assert report["missing_references"][0]["version_id"] == "missing-version"
        else:
            assert result.returncode == 0
            assert [item["version_id"] for item in report["candidates"]] == ["orphan"]
            assert not report["reclamation_blocked"]
