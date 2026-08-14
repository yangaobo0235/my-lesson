from mylesson_agent.knowledge.service import KnowledgeIndexer


class NoModel:
    pass


class NoSearch:
    pass


def indexer(chunk_size: int = 20) -> KnowledgeIndexer:
    return KnowledgeIndexer(
        NoModel(),  # type: ignore[arg-type]
        NoSearch(),  # type: ignore[arg-type]
        chunk_size=chunk_size,
        overlap=5,
    )


def test_markdown_sections_keep_parent_context_and_heading_path() -> None:
    chunks = indexer()._chunks(
        "# Java\n## 集合\nArrayList 适合随机访问，LinkedList 适合双端操作。"
    )

    assert len(chunks) > 1
    assert {chunk.section_path for chunk in chunks} == {"Java > 集合"}
    assert all("Java / 集合" in chunk.parent_content for chunk in chunks)
    assert all(len(chunk.content) <= 20 for chunk in chunks)


def test_empty_document_produces_traceable_placeholder_chunk() -> None:
    chunks = indexer()._chunks(" \n ")

    assert chunks[0].content == "空内容"
    assert chunks[0].parent_content == "空内容"
