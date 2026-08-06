from __future__ import annotations

import argparse
import asyncio
import json
import math
import os
import sys
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.engine import make_url

PROJECT_ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = PROJECT_ROOT / "src"
sys.path.insert(0, str(SOURCE_ROOT))

from mylesson_agent.agents.routing import IntentRouter  # noqa: E402
from mylesson_agent.agents.runtime import AgentRuntime  # noqa: E402
from mylesson_agent.config import Settings  # noqa: E402
from mylesson_agent.domain.api_models import UserIntent  # noqa: E402
from mylesson_agent.infrastructure.database import Database  # noqa: E402
from mylesson_agent.infrastructure.orm import (  # noqa: E402
    KnowledgeChunkRow,
    KnowledgeSourceRow,
)
from mylesson_agent.llm.client import ModelClient  # noqa: E402
from mylesson_agent.rag.search_client import JavaKnowledgeSearchClient  # noqa: E402
from mylesson_agent.rag.service import HybridRetriever  # noqa: E402

CASE_TYPES = ("RAG", "TOOL", "SECURITY", "NO_ANSWER")
TOOL_ALIASES = {"get_learning_plan": "get_learning_plans"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the MyLesson 240-case Agent quality evaluation.",
    )
    parser.add_argument(
        "--dataset",
        type=Path,
        default=PROJECT_ROOT / "evaluation" / "m15-evaluation-v3.jsonl",
    )
    parser.add_argument(
        "--thresholds",
        type=Path,
        default=PROJECT_ROOT / "evaluation" / "regression-thresholds.json",
    )
    parser.add_argument("--output", type=Path, help="Write the complete JSON report to this path.")
    parser.add_argument(
        "--types",
        nargs="+",
        choices=CASE_TYPES,
        default=list(CASE_TYPES),
        help="Only run selected case types.",
    )
    parser.add_argument("--case-id", action="append", help="Only run the specified case ID.")
    parser.add_argument("--limit", type=int, help="Limit the number of selected cases.")
    parser.add_argument(
        "--fail-on-gate",
        action="store_true",
        help="Exit with status 2 when a configured regression gate fails.",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError(f"Expected a JSON object in {path}")
    return payload


def load_cases(path: Path) -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw_line.strip():
            continue
        payload = json.loads(raw_line)
        if not isinstance(payload, dict):
            raise ValueError(f"Case on line {line_number} is not a JSON object")
        missing = {"id", "type", "question"} - payload.keys()
        if missing:
            raise ValueError(f"Case on line {line_number} is missing {sorted(missing)}")
        if payload["type"] not in CASE_TYPES:
            raise ValueError(f"Unsupported case type on line {line_number}: {payload['type']}")
        cases.append(payload)
    return cases


def p95(values: list[int]) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * 0.95) - 1)]


def normalize_tool(name: str) -> str:
    return TOOL_ALIASES.get(name, name)


def lower_camel(value: str) -> str:
    head, *tail = value.lower().split("_")
    return head + "".join(part.title() for part in tail)


async def evaluate_case(
    item: dict[str, Any],
    *,
    database: Database,
    router: IntentRouter,
    retriever: HybridRetriever,
) -> dict[str, Any]:
    started = perf_counter()
    case_id = str(item["id"])
    case_type = str(item["type"])
    question = str(item["question"])
    result: dict[str, Any] = {
        "caseId": case_id,
        "caseType": case_type,
        "question": question,
        "passed": False,
        "metrics": {},
    }
    try:
        if case_type in {"RAG", "NO_ANSWER"}:
            async with database.sessions() as session:
                retrieval = await retriever.search(session, question)
            actual_sources = sorted({hit.source_type for hit in retrieval.hits})
            combined_content = "\n".join(hit.content for hit in retrieval.hits)
            if case_type == "RAG":
                expected_sources = {str(value) for value in item.get("expectedSources", [])}
                must_contain = [str(value) for value in item.get("mustContain", [])]
                source_hit = bool(expected_sources.intersection(actual_sources))
                content_hit = all(value in combined_content for value in must_contain)
                citation_hit = bool(retrieval.citations(6))
                result["passed"] = source_hit and content_hit
                result["metrics"] = {
                    "sourceHit": source_hit,
                    "contentHit": content_hit,
                    "citationHit": citation_hit,
                    "rerankApplied": retrieval.reranked,
                    "hitCount": len(retrieval.hits),
                }
                result["expectedSources"] = sorted(expected_sources)
                result["actualSources"] = actual_sources
            else:
                no_answer = not retrieval.hits
                expected = bool(item.get("expectedNoAnswer", True))
                result["passed"] = no_answer == expected
                result["metrics"] = {
                    "noAnswer": no_answer,
                    "rerankApplied": retrieval.reranked,
                    "hitCount": len(retrieval.hits),
                }
                result["actualSources"] = actual_sources
        elif case_type == "TOOL":
            selection = await router.select(question, "")
            calls = AgentRuntime._select_tools(selection, question)
            actual_tools = list(dict.fromkeys(name for name, _ in calls))
            expected_tools = [normalize_tool(str(value)) for value in item.get("expectedTools", [])]
            result["passed"] = set(actual_tools) == set(expected_tools)
            result["metrics"] = {
                "toolSelection": bool(result["passed"]),
                "intent": selection.decision.intent.value,
                "routeConfidence": selection.decision.confidence,
            }
            result["expectedTools"] = expected_tools
            result["actualTools"] = actual_tools
        elif case_type == "SECURITY":
            selection = await router.select(question, "")
            refused = (
                selection.decision.intent == UserIntent.OUT_OF_SCOPE
                and not selection.route.conservative
            )
            expected = bool(item.get("expectedRefusal", True))
            result["passed"] = refused == expected
            result["metrics"] = {
                "authorizationDecision": bool(result["passed"]),
                "authorizationBlocked": refused,
                "intent": selection.decision.intent.value,
                "routeConfidence": selection.decision.confidence,
            }
    except Exception as exception:
        result["error"] = f"{type(exception).__name__}: {exception}"
    finally:
        result["latencyMs"] = max(0, round((perf_counter() - started) * 1000))
    return result


def summarize(results: list[dict[str, Any]]) -> dict[str, Any]:
    categories: dict[str, dict[str, Any]] = {}
    for case_type in CASE_TYPES:
        selected = [item for item in results if item["caseType"] == case_type]
        if not selected:
            continue
        passed = sum(bool(item["passed"]) for item in selected)
        category: dict[str, Any] = {
            "total": len(selected),
            "passed": passed,
            "failed": len(selected) - passed,
            "passRate": passed / len(selected),
            "p95LatencyMs": p95([int(item["latencyMs"]) for item in selected]),
        }
        if case_type == "RAG":
            for output_name, metric_name in (
                ("sourceHitRate", "sourceHit"),
                ("contentHitRate", "contentHit"),
                ("citationHitRate", "citationHit"),
                ("rerankAppliedRate", "rerankApplied"),
            ):
                category[output_name] = sum(
                    bool(item["metrics"].get(metric_name)) for item in selected
                ) / len(selected)
        categories[case_type] = category
    total = len(results)
    passed = sum(bool(item["passed"]) for item in results)
    return {
        "total": total,
        "passed": passed,
        "failed": total - passed,
        "passRate": passed / total if total else 0.0,
        "categories": categories,
    }


def evaluate_gates(summary: dict[str, Any], thresholds: dict[str, Any]) -> dict[str, Any]:
    categories = summary["categories"]
    checks: dict[str, dict[str, Any]] = {}

    def add_rate(name: str, actual: float, required: float) -> None:
        checks[name] = {"actual": actual, "required": required, "passed": actual >= required}

    if "RAG" in categories:
        add_rate(
            "citationHitRate",
            float(categories["RAG"]["citationHitRate"]),
            float(thresholds.get("citationHitRate", 0.0)),
        )
    if "TOOL" in categories:
        add_rate(
            "toolSelectionAccuracy",
            float(categories["TOOL"]["passRate"]),
            float(thresholds.get("toolSelectionAccuracy", 0.0)),
        )
    if "SECURITY" in categories:
        add_rate(
            "authorizationBlockRate",
            float(categories["SECURITY"]["passRate"]),
            float(thresholds.get("authorizationBlockRate", 0.0)),
        )
    if "NO_ANSWER" in categories:
        add_rate(
            "noAnswerAccuracy",
            float(categories["NO_ANSWER"]["passRate"]),
            float(thresholds.get("noAnswerAccuracy", 0.0)),
        )
    latency_limit = int(thresholds.get("localP95LatencyMillis", 0))
    if latency_limit:
        for case_type, category in categories.items():
            actual = int(category["p95LatencyMs"])
            checks[f"{lower_camel(case_type)}P95LatencyMs"] = {
                "actual": actual,
                "requiredMax": latency_limit,
                "passed": actual <= latency_limit,
            }
    return {"passed": all(item["passed"] for item in checks.values()), "checks": checks}


async def run(args: argparse.Namespace) -> tuple[dict[str, Any], bool]:
    os.chdir(PROJECT_ROOT)
    cases = load_cases(args.dataset.resolve())
    selected_types = set(args.types)
    selected_ids = set(args.case_id or [])
    cases = [
        item
        for item in cases
        if item["type"] in selected_types and (not selected_ids or item["id"] in selected_ids)
    ]
    if args.limit is not None:
        if args.limit < 1:
            raise ValueError("--limit must be greater than zero")
        cases = cases[: args.limit]
    if not cases:
        raise ValueError("No evaluation cases matched the selection")

    settings = Settings()
    database = Database(settings)
    model = ModelClient(settings)
    keyword_search = JavaKnowledgeSearchClient(settings)
    router = IntentRouter(model, settings.intent_confidence_threshold)
    retriever = HybridRetriever(settings, model, keyword_search)
    try:
        async with database.sessions() as session:
            source_count = int(
                await session.scalar(select(func.count()).select_from(KnowledgeSourceRow)) or 0
            )
            chunk_count = int(
                await session.scalar(select(func.count()).select_from(KnowledgeChunkRow)) or 0
            )
        results = [
            await evaluate_case(
                item,
                database=database,
                router=router,
                retriever=retriever,
            )
            for item in cases
        ]
    finally:
        await keyword_search.close()
        await model.close()
        await database.close()

    summary = summarize(results)
    thresholds = load_json(args.thresholds.resolve())
    gates = evaluate_gates(summary, thresholds)
    report = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(UTC).isoformat(),
        "dataset": {
            "path": str(args.dataset.resolve()),
            "selectedCases": len(cases),
            "caseCounts": dict(Counter(str(item["type"]) for item in cases)),
        },
        "environment": {
            "databaseHost": make_url(settings.database_url).host,
            "knowledgeSources": source_count,
            "knowledgeChunks": chunk_count,
            "chatModel": settings.chat_model,
            "routerModel": settings.router_model,
            "embeddingModel": settings.embedding_model,
            "rerankModel": settings.rerank_model,
            "vectorMinimumScore": settings.vector_minimum_score,
            "rerankMinimumScore": settings.rerank_minimum_score,
        },
        "summary": summary,
        "gates": gates,
        "results": results,
    }
    return report, bool(gates["passed"])


def main() -> int:
    args = parse_args()
    report, gates_passed = asyncio.run(run(args))
    payload = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        output = args.output.resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(payload + "\n", encoding="utf-8")
        print(
            json.dumps(
                {"output": str(output), "summary": report["summary"], "gates": report["gates"]},
                ensure_ascii=False,
            )
        )
    else:
        print(payload)
    return 2 if args.fail_on_gate and not gates_passed else 0


if __name__ == "__main__":
    raise SystemExit(main())
