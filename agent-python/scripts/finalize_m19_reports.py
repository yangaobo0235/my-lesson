from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

from scripts.run_external_quality_evaluation import (  # noqa: E402
    failure_samples,
    file_sha256,
    quality_gates,
    tool_confusion_matrix,
)
from scripts.run_m19_offline_retrieval import (  # noqa: E402
    retrieval_failure_samples,
    retrieval_quality_gate,
)

EVALUATION_DIR = PROJECT_ROOT / "evaluation"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Finalize existing M19 reports.")
    parser.add_argument(
        "--agent-report",
        type=Path,
        default=EVALUATION_DIR / "m19-agent-routing-20260811.json",
    )
    parser.add_argument(
        "--retrieval-report",
        type=Path,
        default=EVALUATION_DIR / "m19-offline-retrieval-20260811.json",
    )
    return parser.parse_args()


def load(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Report must be a JSON object: {path}")
    return value


def write(path: Path, value: dict[str, Any]) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def finalize_agent(path: Path) -> None:
    report = load(path)
    results = report.get("results", [])
    summary = report["summary"]
    regression = Path(report["datasets"]["regression"])
    holdout = Path(report["datasets"]["holdout"])
    report["schemaVersion"] = 2
    report["datasets"]["regressionSha256"] = file_sha256(regression)
    report["datasets"]["holdoutSha256"] = file_sha256(holdout)
    summary["toolIntentConfusionMatrix"] = tool_confusion_matrix(results)
    summary["failureSamples"] = failure_samples(results)
    summary["qualityGate"] = quality_gates(summary)
    write(path, report)


def finalize_retrieval(path: Path) -> None:
    report = load(path)
    results = report.get("results", [])
    summary = report["summary"]
    cases = Path(report["datasets"]["cases"])
    corpus = Path(report["datasets"]["corpus"])
    report["schemaVersion"] = 2
    report["datasets"]["casesSha256"] = file_sha256(cases)
    report["datasets"]["corpusSha256"] = file_sha256(corpus)
    summary["failureSamples"] = retrieval_failure_samples(results)
    summary["qualityGate"] = retrieval_quality_gate(summary)
    write(path, report)


def main() -> int:
    args = parse_args()
    finalize_agent(args.agent_report.resolve())
    finalize_retrieval(args.retrieval_report.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
