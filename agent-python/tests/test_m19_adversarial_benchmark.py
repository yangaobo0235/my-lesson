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


def test_m19_has_600_unique_stratified_cases() -> None:
    rag = load_jsonl("m19-adversarial-rag-v1.jsonl")
    agent = load_jsonl("m19-adversarial-agent-v1.jsonl")
    all_cases = rag + agent

    assert len(rag) == 288
    assert len(agent) == 312
    assert len(all_cases) == 600
    assert len({item["id"] for item in all_cases}) == 600
    assert len({item["question"] for item in all_cases}) == 600
    assert Counter(item["difficulty"] for item in rag) == {
        "hard": 70,
        "very_hard": 144,
        "adversarial": 74,
    }
    assert Counter(item["expectedIntent"] for item in agent) == {
        "COURSE_SEARCH": 48,
        "KNOWLEDGE_QA": 60,
        "PERSONAL_QUERY": 48,
        "LEARNING_PLAN": 48,
        "OUT_OF_SCOPE": 108,
    }


def test_m19_rag_labels_resolve_to_frozen_corpus() -> None:
    rag = load_jsonl("m19-adversarial-rag-v1.jsonl")
    corpus = load_jsonl("m19-corpus-v1.jsonl")
    refs = {item["ref"] for item in corpus}

    assert len(corpus) == 52
    assert len(refs) == 52
    assert all(set(item["expectedSourceRefs"]) <= refs for item in rag)
    assert all(item["expectedFacts"] for item in rag)
    assert sum(len(item["expectedSourceRefs"]) > 1 for item in rag) == 128
    assert sum(bool(item["forbiddenFacts"]) for item in rag) == 36


def test_m19_adversarial_controls_include_safe_and_unsafe_requests() -> None:
    agent = load_jsonl("m19-adversarial-agent-v1.jsonl")
    dimensions = Counter(item["dimension"] for item in agent)

    assert dimensions["adversarial_boundary/identity_boundary"] == 24
    assert dimensions["adversarial_boundary/write_boundary"] == 24
    assert dimensions["adversarial_boundary/prompt_injection"] == 12
    assert dimensions["adversarial_boundary/safe_security_control"] == 12
