from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SEED_SQL = REPOSITORY_ROOT / "demo-data" / "sql" / "01-mylesson-demo-data.sql"


@dataclass(frozen=True)
class Course:
    course_id: int
    title: str
    author: str
    category: str
    price: float
    info: str
    episodes: tuple[str, ...]


@dataclass(frozen=True)
class TextSource:
    source_id: int
    title: str
    content: str


@dataclass(frozen=True)
class BenchmarkFacts:
    courses: tuple[Course, ...]
    articles: tuple[TextSource, ...]
    notices: tuple[TextSource, ...]


def _insert_rows(sql: str, schema: str, table: str) -> list[list[Any]]:
    marker = f"INSERT INTO `{schema}`.`{table}`"
    start = sql.find(marker)
    if start < 0:
        raise ValueError(f"Seed table not found: {schema}.{table}")
    values_start = sql.find("VALUES", start)
    statement_end = sql.find(";", values_start)
    if values_start < 0 or statement_end < 0:
        raise ValueError(f"Malformed seed statement: {schema}.{table}")
    body = sql[values_start + len("VALUES") : statement_end]
    return [_parse_tuple(row) for row in _tuple_rows(body)]


def _tuple_rows(body: str) -> list[str]:
    rows: list[str] = []
    start: int | None = None
    depth = 0
    quoted = False
    index = 0
    while index < len(body):
        char = body[index]
        if quoted:
            if char == "'" and index + 1 < len(body) and body[index + 1] == "'":
                index += 2
                continue
            if char == "'":
                quoted = False
        elif char == "'":
            quoted = True
        elif char == "(":
            if depth == 0:
                start = index
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0 and start is not None:
                rows.append(body[start : index + 1])
                start = None
        index += 1
    if quoted or depth != 0:
        raise ValueError("Unbalanced SQL seed values")
    return rows


def _parse_tuple(row: str) -> list[Any]:
    values: list[str] = []
    token: list[str] = []
    quoted = False
    depth = 0
    index = 1
    while index < len(row) - 1:
        char = row[index]
        if quoted:
            token.append(char)
            if char == "'" and index + 1 < len(row) - 1 and row[index + 1] == "'":
                token.append("'")
                index += 2
                continue
            if char == "'":
                quoted = False
        elif char == "'":
            quoted = True
            token.append(char)
        elif char == "(":
            depth += 1
            token.append(char)
        elif char == ")":
            depth -= 1
            token.append(char)
        elif char == "," and depth == 0:
            values.append("".join(token).strip())
            token = []
        else:
            token.append(char)
        index += 1
    values.append("".join(token).strip())
    return [_literal(value) for value in values]


def _literal(value: str) -> Any:
    if len(value) >= 2 and value[0] == value[-1] == "'":
        return value[1:-1].replace("''", "'")
    if re.fullmatch(r"-?\d+", value):
        return int(value)
    if re.fullmatch(r"-?\d+\.\d+", value):
        return float(value)
    if value.upper() == "NULL":
        return None
    return value


def load_benchmark_facts(seed_sql: Path = DEFAULT_SEED_SQL) -> BenchmarkFacts:
    sql = seed_sql.read_text(encoding="utf-8")
    categories = {int(row[0]): str(row[1]) for row in _insert_rows(sql, "ml_cms", "category")}
    seasons = {
        int(row[0]): (int(row[3]), int(row[4])) for row in _insert_rows(sql, "ml_cms", "season")
    }
    episode_rows = _insert_rows(sql, "ml_cms", "episode")
    episodes_by_course: dict[int, list[tuple[int, int, str]]] = {}
    for row in episode_rows:
        season_id = int(row[5])
        course_id, season_index = seasons[season_id]
        episodes_by_course.setdefault(course_id, []).append(
            (season_index, int(row[6]), str(row[1]))
        )

    courses: list[Course] = []
    for row in _insert_rows(sql, "ml_cms", "course"):
        course_id = int(row[0])
        ordered_episodes = tuple(
            title for _, _, title in sorted(episodes_by_course.get(course_id, []))
        )
        courses.append(
            Course(
                course_id=course_id,
                title=str(row[1]),
                author=str(row[2]),
                category=categories[int(row[3])],
                price=float(row[6]),
                info=str(row[8]),
                episodes=ordered_episodes,
            )
        )

    articles = tuple(
        TextSource(int(row[0]), str(row[1]), str(row[2]))
        for row in _insert_rows(sql, "ml_sms", "article")
    )
    notices = tuple(
        TextSource(int(row[0]), f"平台通知 {row[0]}", str(row[1]))
        for row in _insert_rows(sql, "ml_sms", "notice")
    )
    return BenchmarkFacts(tuple(courses), articles, notices)


def build_corpus(facts: BenchmarkFacts) -> list[dict[str, Any]]:
    documents: list[dict[str, Any]] = []
    for course in facts.courses:
        documents.append(
            {
                "ref": f"COURSE:{course.course_id}",
                "sourceType": "COURSE",
                "sourceId": str(course.course_id),
                "title": course.title,
                "content": "\n".join(
                    (
                        f"课程：{course.title}",
                        f"讲师：{course.author}",
                        f"分类：{course.category}",
                        f"价格：{course.price:.1f} 元",
                        course.info,
                    )
                ),
            }
        )
        documents.append(
            {
                "ref": f"COURSE_EPISODES:{course.course_id}",
                "sourceType": "COURSE_EPISODES",
                "sourceId": str(course.course_id),
                "title": f"{course.title} - 分集",
                "content": "课程分集列表\n"
                + "\n".join(
                    f"{index}. {title}" for index, title in enumerate(course.episodes, start=1)
                ),
            }
        )
    for article in facts.articles:
        documents.append(
            {
                "ref": f"ARTICLE:{article.source_id}",
                "sourceType": "ARTICLE",
                "sourceId": str(article.source_id),
                "title": article.title,
                "content": article.content,
            }
        )
    for notice in facts.notices:
        documents.append(
            {
                "ref": f"NOTICE:{notice.source_id}",
                "sourceType": "NOTICE",
                "sourceId": str(notice.source_id),
                "title": notice.title,
                "content": notice.content,
            }
        )
    return documents
