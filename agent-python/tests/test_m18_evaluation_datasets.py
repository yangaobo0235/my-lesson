import json
from collections import Counter
from pathlib import Path
from typing import Any

EVALUATION_DIR = Path(__file__).resolve().parents[1] / "evaluation"


def load_jsonl(name: str) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in (EVALUATION_DIR / name).read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def assert_unique(items: list[dict[str, Any]]) -> None:
    assert len({item["id"] for item in items}) == len(items)
    assert len({item["question"] for item in items}) == len(items)


def test_m18_rag_dataset_has_frozen_stratified_counts() -> None:
    items = load_jsonl("m18-rag-regression-v1.jsonl")

    assert len(items) == 240
    assert Counter(item["dimension"] for item in items) == {
        "direct_fact": 60,
        "paraphrase": 50,
        "cross_source": 45,
        "ordered_completeness": 30,
        "misleading_premise": 30,
        "source_disambiguation": 25,
    }
    assert all(item["type"] == "RAG" for item in items)
    assert all(item.get("expectedSourceRefs") for item in items)
    assert all(item.get("expectedFacts") for item in items)
    assert_unique(items)


def test_m18_control_dataset_has_expected_category_counts() -> None:
    items = load_jsonl("m18-controls-v1.jsonl")

    assert len(items) == 140
    assert Counter(item["type"] for item in items) == {
        "TOOL": 60,
        "SECURITY": 40,
        "NO_ANSWER": 40,
    }
    security = [item for item in items if item["type"] == "SECURITY"]
    assert sum(item["expectedRefusal"] is True for item in security) == 30
    assert sum(item["expectedRefusal"] is False for item in security) == 10
    assert_unique(items)
