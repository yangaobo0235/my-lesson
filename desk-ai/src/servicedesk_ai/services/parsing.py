"""从已核验的原件字节确定性生成片段及真实定位，不执行文档指令。作者：yangaobo。"""

from __future__ import annotations

import hashlib
import re
import unicodedata
from collections import Counter

from servicedesk_ai.diagnosis.budget import estimate_text_tokens
from servicedesk_ai.schemas.indexing import (
    MarkdownSegment,
    ParsedChunk,
    ProcessingConfig,
    SourceLocations,
)


def error_codes(content: str) -> list[str]:
    """仅抽取完整字面错误码，不由模型补全或推断。"""
    return sorted(set(re.findall(r"\b(?:0x[0-9a-fA-F]{4,16}|[A-Z]{1,6}[-_]\d{3,8})\b", content)))


def chunk_identifier(job_id: str, number: int) -> str:
    """任务内稳定ID，用于MySQL、ES及无损UUID向量点。"""
    return hashlib.sha256(f"{job_id}:{number}".encode()).hexdigest()[:32]


def build_manifest(
    chunks: list[ParsedChunk], job_id: str, config: ProcessingConfig
) -> list[ParsedChunk]:
    """按相邻真实章节合并父正文；超预算显式分段，父先子后且叶片ID不变。"""
    manifest: list[ParsedChunk] = []
    pending: list[ParsedChunk] = []

    def flush() -> None:
        """章节只复制实际叶片正文及定位，不补写推断内容。"""
        if not pending:
            return
        content = "\n".join(chunk.content for chunk in pending)
        identifier = hashlib.sha256(f"{job_id}:section:{pending[0].id}".encode()).hexdigest()[:32]
        pages = {page.page_number: page for c in pending for page in c.source_locations.pages}
        locations = SourceLocations(
            kind=pending[0].source_locations.kind,
            pages=[pages[number] for number in sorted(pages)],
            segments=[s for c in pending for s in c.source_locations.segments],
        )
        manifest.append(
            ParsedChunk(
                id=identifier,
                section_path=pending[0].section_path,
                content=content,
                source_locations=locations,
                node_kind="SECTION",
                token_estimate=estimate_text_tokens(content),
                error_codes=error_codes(content),
            )
        )
        manifest.extend(
            c.model_copy(
                update={
                    "parent_chunk_id": identifier,
                    "token_estimate": estimate_text_tokens(c.content),
                }
            )
            for c in pending
        )
        pending.clear()

    for chunk in chunks:
        if chunk.node_kind != "LEAF" or chunk.parent_chunk_id is not None:
            raise ValueError("解析输入不能携带预建章节关系")
        size = sum(len(c.content.encode()) + 1 for c in pending) + len(chunk.content.encode())
        changed_anchor = bool(
            pending
            and pending[-1].source_locations.segments
            and chunk.source_locations.segments
            and pending[-1].source_locations.segments[0].anchor
            != chunk.source_locations.segments[0].anchor
        )
        if pending and (
            pending[-1].section_path != chunk.section_path or changed_anchor or size > 48 * 1024
        ):
            flush()
        pending.append(chunk)
    flush()
    if not manifest or len(manifest) > config.max_chunks:
        raise ValueError("包含父章节的完整清单超过片段预算")
    return manifest


def parse_markdown(data: bytes, job_id: str, config: ProcessingConfig) -> list[ParsedChunk]:
    """按原UTF-8行切片，标题锚点重复时追加序号；代码块内井号不作标题。

    远程图片、原始HTML和控制字符明确拒绝，不隐式改写原件或虚构页码。
    """
    if not data or len(data) > 1048576:
        raise ValueError("Markdown大小不合法")
    text = data.decode("utf-8-sig", errors="strict")
    if "\x00" in text or re.search(r"<\s*(?:/?[a-zA-Z]|!--)", text):
        raise ValueError("Markdown不允许原始HTML")
    if re.search(r"!\[[^\]]*\]\(\s*(?:https?:|//|data:)", text, re.I):
        raise ValueError("Markdown不允许远程资源")
    lines = text.splitlines()
    chunks: list[ParsedChunk] = []
    anchor, start = "document", 1
    section: list[str] = []
    pending: list[str] = []
    counts: Counter[str] = Counter()
    fence: str | None = None

    def flush(end: int) -> None:
        """按原行界输出；空白段不生成伪正文。"""
        nonlocal pending, start
        content = "\n".join(pending)
        if content.strip():
            chunks.append(
                ParsedChunk(
                    id=chunk_identifier(job_id, len(chunks) + 1),
                    section_path=" / ".join(section) or None,
                    content=content,
                    source_locations=SourceLocations(
                        kind="MARKDOWN",
                        segments=[MarkdownSegment(anchor=anchor, line_start=start, line_end=end)],
                    ),
                    error_codes=error_codes(content),
                )
            )
        pending = []
        start = end + 1

    for number, line in enumerate(lines, 1):
        if len(line) > 4000:
            raise ValueError("单行超过定位切片预算")
        heading = re.match(r"^(#{1,6})\s+(.+?)\s*#*\s*$", line) if fence is None else None
        if heading:
            if pending:
                flush(number - 1)
            level, title = len(heading[1]), heading[2]
            section = section[: level - 1] + [title]
            slug = re.sub(r"[^\w\- ]", "", unicodedata.normalize("NFKC", title)).strip().lower()
            slug = re.sub(r"\s+", "-", slug)[:180] or "section"
            counts[slug] += 1
            anchor = slug if counts[slug] == 1 else f"{slug}-{counts[slug] - 1}"
        if pending and sum(len(value) + 1 for value in pending) + len(line) > config.chunk_chars:
            flush(number - 1)
        if not pending:
            start = number
        pending.append(line)
        marker = re.match(r"^\s*(`{3,}|~{3,})", line)
        if marker:
            if fence is None:
                fence = marker[1][0]
            elif marker[1][0] == fence:
                fence = None
        if len(chunks) >= config.max_chunks:
            raise ValueError("片段清单超过预算")
    if pending:
        flush(len(lines))
    if not chunks or len(chunks) > config.max_chunks:
        raise ValueError("Markdown无可用正文或超预算")
    return chunks
