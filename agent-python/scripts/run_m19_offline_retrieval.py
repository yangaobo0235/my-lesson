from __future__ import annotations

import argparse
import asyncio
import json
import math
import re
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter
from typing import Any

from mylesson_agent.config import Settings
from mylesson_agent.llm.client import ModelClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
EVALUATION_DIR = PROJECT_ROOT / "evaluation"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the M19 offline retrieval replay.")
    parser.add_argument(
        "--dataset",
        type=Path,
        default=EVALUATION_DIR / "m19-adversarial-rag-v1.jsonl",
    )
    parser.add_argument(
        "--corpus",
        type=Path,
        default=EVALUATION_DIR / "m19-corpus-v1.jsonl",
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--concurrency", type=int, default=4)
    parser.add_argument("--skip-query-rewrite", action="store_true")
    return parser.parse_args()


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()
    ]


def tokenize(text: str) -> list[str]:
    normalized = text.lower()
    tokens = re.findall(r"[a-z0-9]+(?:[._+-][a-z0-9]+)*", normalized)
    for segment in re.findall(r"[\u3400-\u9fff]+", normalized):
        tokens.extend(segment)
        tokens.extend(segment[index : index + 2] for index in range(len(segment) - 1))
        tokens.extend(segment[index : index + 3] for index in range(len(segment) - 2))
    return tokens


class Bm25Index:
    def __init__(self, documents: list[str], *, k1: float = 1.5, b: float = 0.75) -> None:
        self._k1 = k1
        self._b = b
        self._terms = [Counter(tokenize(document)) for document in documents]
        self._lengths = [sum(terms.values()) for terms in self._terms]
        self._average_length = sum(self._lengths) / max(1, len(self._lengths))
        frequencies: Counter[str] = Counter()
        for terms in self._terms:
            frequencies.update(terms.keys())
        count = len(documents)
        self._idf = {
            term: math.log(1 + (count - frequency + 0.5) / (frequency + 0.5))
            for term, frequency in frequencies.items()
        }

    def scores(self, query: str) -> list[float]:
        query_terms = Counter(tokenize(query))
        scores: list[float] = []
        for terms, length in zip(self._terms, self._lengths, strict=True):
            score = 0.0
            length_factor = 1 - self._b + self._b * length / self._average_length
            for term, query_frequency in query_terms.items():
                frequency = terms.get(term, 0)
                if frequency == 0:
                    continue
                score += (
                    self._idf.get(term, 0.0)
                    * frequency
                    * (self._k1 + 1)
                    / (frequency + self._k1 * length_factor)
                    * min(query_frequency, 2)
                )
            scores.append(score)
        return scores


def cosine(left: list[float], right: list[float]) -> float:
    numerator = sum(a * b for a, b in zip(left, right, strict=True))
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    return numerator / (left_norm * right_norm) if left_norm and right_norm else 0.0


def top_indices(scores: list[float], limit: int) -> list[int]:
    return sorted(range(len(scores)), key=scores.__getitem__, reverse=True)[:limit]


def rrf_indices(rankings: list[list[int]], limit: int, *, k: int = 60) -> list[int]:
    scores: Counter[int] = Counter()
    for ranking in rankings:
        for rank, index in enumerate(ranking, start=1):
            scores[index] += 1 / (k + rank)
    return [index for index, _ in scores.most_common(limit)]


def recall_at(actual: list[str], expected: set[str], limit: int) -> float:
    if not expected:
        return 1.0
    return len(set(actual[:limit]) & expected) / len(expected)


def reciprocal_rank(actual: list[str], expected: set[str], limit: int) -> float:
    for rank, ref in enumerate(actual[:limit], start=1):
        if ref in expected:
            return 1 / rank
    return 0.0


def ndcg_at(actual: list[str], expected: set[str], limit: int) -> float:
    gains = [1.0 if ref in expected else 0.0 for ref in actual[:limit]]
    dcg = sum(gain / math.log2(rank + 1) for rank, gain in enumerate(gains, start=1))
    ideal_count = min(len(expected), limit)
    ideal = sum(1 / math.log2(rank + 1) for rank in range(1, ideal_count + 1))
    return dcg / ideal if ideal else 1.0


def p95(values: list[int]) -> int:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * 0.95) - 1)] if ordered else 0


async def embed_batches(
    model: ModelClient, texts: list[str], batch_size: int = 10
) -> list[list[float]]:
    embeddings: list[list[float]] = []
    for offset in range(0, len(texts), batch_size):
        embeddings.extend(await model.embed(texts[offset : offset + batch_size]))
    return embeddings


async def rewrite_queries(
    model: ModelClient,
    questions: list[str],
    concurrency: int,
) -> list[str]:
    semaphore = asyncio.Semaphore(concurrency)

    async def rewrite(question: str) -> str:
        async with semaphore:
            try:
                return await model.rewrite_query(question)
            except Exception:
                return question

    return list(await asyncio.gather(*(rewrite(question) for question in questions)))


async def rerank_case(
    model: ModelClient,
    question: str,
    candidate_indices: list[int],
    corpus: list[dict[str, Any]],
    semaphore: asyncio.Semaphore,
    minimum_score: float,
) -> tuple[list[int], int, str | None]:
    started = perf_counter()
    try:
        async with semaphore:
            ranked = await model.rerank(
                question,
                [str(corpus[index]["content"]) for index in candidate_indices],
                len(candidate_indices),
            )
        selected = [
            candidate_indices[index]
            for index, score in ranked
            if 0 <= index < len(candidate_indices) and score >= minimum_score
        ]
        return selected, round((perf_counter() - started) * 1000), None
    except Exception as exception:
        return candidate_indices, round((perf_counter() - started) * 1000), type(exception).__name__


def stage_metrics(refs: list[str], expected: set[str], limit: int = 6) -> dict[str, Any]:
    recall = recall_at(refs, expected, limit)
    return {
        "recallAt6": recall,
        "allExpectedAt6": recall == 1.0,
        "mrrAt6": reciprocal_rank(refs, expected, limit),
        "ndcgAt6": ndcg_at(refs, expected, limit),
        "top6": refs[:limit],
    }


def average(results: list[dict[str, Any]], stage: str, metric: str) -> float:
    return sum(float(item["stages"][stage][metric]) for item in results) / len(results)


def summarize(results: list[dict[str, Any]], stage: str) -> dict[str, Any]:
    return {
        "cases": len(results),
        "recallAt6": average(results, stage, "recallAt6"),
        "allExpectedAt6Rate": average(results, stage, "allExpectedAt6"),
        "mrrAt6": average(results, stage, "mrrAt6"),
        "ndcgAt6": average(results, stage, "ndcgAt6"),
    }


async def run(args: argparse.Namespace) -> dict[str, Any]:
    cases = load_jsonl(args.dataset.resolve())
    if args.limit is not None:
        if args.limit < 1:
            raise ValueError("--limit must be positive")
        cases = cases[: args.limit]
    corpus = load_jsonl(args.corpus.resolve())
    if not cases or not corpus:
        raise ValueError("Dataset and corpus must not be empty")
    settings = Settings()
    if not settings.model_configured:
        raise RuntimeError("DashScope is not configured")
    model = ModelClient(settings)
    questions = [str(item["question"]) for item in cases]
    try:
        rewritten = (
            questions
            if args.skip_query_rewrite
            else await rewrite_queries(model, questions, args.concurrency)
        )
        corpus_embeddings = await embed_batches(
            model,
            [f"{item['title']}\n{item['content']}" for item in corpus],
        )
        query_embeddings = await embed_batches(model, rewritten)
        bm25 = Bm25Index([f"{item['title']} {item['title']} {item['content']}" for item in corpus])
        preliminary: list[dict[str, Any]] = []
        for item, query, embedding in zip(cases, rewritten, query_embeddings, strict=True):
            sparse_scores = bm25.scores(query)
            vector_scores = [cosine(embedding, document) for document in corpus_embeddings]
            sparse = top_indices(sparse_scores, 20)
            vector = top_indices(vector_scores, 20)
            fused = rrf_indices([sparse, vector], 20, k=settings.rrf_k)
            preliminary.append(
                {
                    "case": item,
                    "rewritten": query,
                    "sparse": sparse,
                    "vector": vector,
                    "fused": fused,
                }
            )
        semaphore = asyncio.Semaphore(args.concurrency)
        reranked = await asyncio.gather(
            *(
                rerank_case(
                    model,
                    str(item["case"]["question"]),
                    item["fused"],
                    corpus,
                    semaphore,
                    settings.rerank_minimum_score,
                )
                for item in preliminary
            )
        )
    finally:
        await model.close()

    results: list[dict[str, Any]] = []
    for item, (final_indices, latency_ms, error) in zip(preliminary, reranked, strict=True):
        expected = {str(value) for value in item["case"]["expectedSourceRefs"]}

        def refs(indices: list[int]) -> list[str]:
            return [str(corpus[index]["ref"]) for index in indices]

        stages = {
            "bm25": stage_metrics(refs(item["sparse"]), expected),
            "vector": stage_metrics(refs(item["vector"]), expected),
            "rrf": stage_metrics(refs(item["fused"]), expected),
            "rerank": stage_metrics(refs(final_indices), expected),
        }
        result = {
            "caseId": item["case"]["id"],
            "dimension": item["case"]["dimension"],
            "difficulty": item["case"]["difficulty"],
            "question": item["case"]["question"],
            "rewrittenQuery": item["rewritten"],
            "expectedSourceRefs": sorted(expected),
            "stages": stages,
            "rerankLatencyMs": latency_ms,
        }
        if error:
            result["rerankFallback"] = error
        results.append(result)

    stages = {name: summarize(results, name) for name in ("bm25", "vector", "rrf", "rerank")}
    dimensions = {
        dimension: summarize(
            [item for item in results if item["dimension"] == dimension],
            "rerank",
        )
        for dimension in sorted({str(item["dimension"]) for item in results})
    }
    return {
        "schemaVersion": 1,
        "generatedAt": datetime.now(UTC).isoformat(),
        "methodology": {
            "scope": "Offline quality replay; not an HTTP or production benchmark.",
            "corpus": (
                "52 frozen sources reconstructed from demo-data/sql/01-mylesson-demo-data.sql."
            ),
            "sparse": "Local character unigram/bigram/trigram BM25 proxy, not Elasticsearch IK.",
            "vector": f"{settings.embedding_model} cosine similarity.",
            "fusion": f"RRF with k={settings.rrf_k}, top 20 from each retriever.",
            "rerank": f"{settings.rerank_model}, threshold={settings.rerank_minimum_score}, top 6.",
            "queryRewrite": not args.skip_query_rewrite,
        },
        "datasets": {
            "cases": str(args.dataset.resolve()),
            "corpus": str(args.corpus.resolve()),
            "caseCount": len(results),
            "corpusCount": len(corpus),
        },
        "summary": {
            "stages": stages,
            "dimensions": dimensions,
            "p95RerankLatencyMs": p95([int(item["rerankLatencyMs"]) for item in results]),
            "rerankFallbacks": sum("rerankFallback" in item for item in results),
        },
        "results": results,
    }


def main() -> int:
    args = parse_args()
    report = asyncio.run(run(args))
    args.output.resolve().parent.mkdir(parents=True, exist_ok=True)
    args.output.resolve().write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(report["summary"], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
