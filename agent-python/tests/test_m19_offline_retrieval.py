from scripts.run_m19_offline_retrieval import (
    Bm25Index,
    ndcg_at,
    recall_at,
    reciprocal_rank,
    rrf_indices,
    tokenize,
)


def test_chinese_tokenizer_keeps_local_context() -> None:
    tokens = tokenize("课程《把日常拍成电影》price 79.0")

    assert "课程" in tokens
    assert "电影" in tokens
    assert "price" in tokens
    assert "79.0" in tokens


def test_bm25_prefers_exact_course_title() -> None:
    index = Bm25Index(
        [
            "把日常拍成电影 课程 光线 构图",
            "镜头里的青春纪念册 课程 朋友 社团",
        ]
    )

    scores = index.scores("把日常拍成电影的讲师")

    assert scores[0] > scores[1]


def test_rrf_and_metrics_handle_multiple_expected_sources() -> None:
    assert rrf_indices([[0, 1], [1, 0]], 2) == [0, 1]
    actual = ["COURSE:1", "COURSE:2", "ARTICLE:1"]
    expected = {"COURSE:1", "COURSE:2"}

    assert recall_at(actual, expected, 2) == 1.0
    assert reciprocal_rank(actual, expected, 2) == 1.0
    assert ndcg_at(actual, expected, 2) == 1.0
