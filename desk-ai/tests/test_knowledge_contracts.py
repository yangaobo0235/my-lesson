"""验证真实定位、规范摘要与只读工具边界；不以替身证明MySQL或外部索引正确。"""

import io
import json
import stat
from zipfile import ZipFile, ZipInfo

import pytest
from pydantic import ValidationError

from servicedesk_ai.infrastructure.mineru import parse_artifact
from servicedesk_ai.schemas.diagnosis import (
    ConversationCreate,
    FactChange,
    FeedbackHandle,
    GenerationCreate,
    ReadTicketEvents,
    SearchKnowledge,
)
from servicedesk_ai.schemas.indexing import IndexCommand, ProcessingConfig, canonical_hash
from servicedesk_ai.services.parsing import parse_markdown


def envelope() -> dict:
    """与Java相同UTF-8规范JSON的独立输入向量，含中文以防编码漂移。"""
    value = {
        "schemaVersion": 2,
        "jobId": "a" * 32,
        "documentId": "b" * 32,
        "attemptNumber": 1,
        "contentFormat": "MARKDOWN",
        "fileSha256": "c" * 64,
        "requestedBy": "d" * 32,
        "processingConfig": ProcessingConfig().model_dump(by_alias=True),
    }
    return {
        **value,
        "requestHash": canonical_hash(value),
        "command": "SUBMIT",
        "operationId": "a" * 32,
    }


def test_cancel_and_submit_share_immutable_input_digest() -> None:
    """指令与操作ID改变不重算任务输入，同任务原身份始终固定。"""
    value = envelope()
    submit = IndexCommand.model_validate(value)
    cancel = IndexCommand.model_validate(value | {"command": "CANCEL", "operationId": "e" * 32})
    assert submit.request_hash == cancel.request_hash
    with pytest.raises(ValidationError, match="摘要"):
        IndexCommand.model_validate(value | {"contentFormat": "PDF"})
    with pytest.raises(ValidationError):
        IndexCommand.model_validate(value | {"url": "https://untrusted.invalid"})
    with pytest.raises(ValidationError, match="提交操作"):
        IndexCommand.model_validate(value | {"operationId": "e" * 32})


def test_markdown_locations_are_original_lines_and_code_fences_are_not_headings() -> None:
    """重复标题锚点、UTF-8 BOM与CRLF均保持真实原件行区间。"""
    lines = [
        "# 打印支持",
        "网络连接前置条件。",
        "```text",
        "# 不是标题",
        "```",
        "# 打印支持",
        "错误码 0x80070005，请转人工。",
    ]
    chunks = parse_markdown(("\ufeff" + "\r\n".join(lines)).encode(), "a" * 32, ProcessingConfig())
    assert len(chunks) == 2
    assert chunks[0].source_locations.segments[0].anchor == "打印支持"
    assert chunks[1].source_locations.segments[0].anchor == "打印支持-1"
    for chunk in chunks:
        location = chunk.source_locations
        assert location.pages == []
        segment = location.segments[0]
        assert chunk.content == "\n".join(lines[segment.line_start - 1 : segment.line_end])
    assert chunks[1].error_codes == ["0x80070005"]
    assert [item.id for item in chunks] == [
        item.id
        for item in parse_markdown(("\n".join(lines)).encode(), "a" * 32, ProcessingConfig())
    ]


@pytest.mark.parametrize(
    "body",
    [
        b"",
        b"\xff",
        b"<script>not executable</script>",
        b"![remote](https://invalid/a.png)",
        b"a\x00b",
    ],
)
def test_markdown_rejects_unsafe_or_invalid_original(body: bytes) -> None:
    """非法正文、控制字符、HTML及远程资源明确失败，不静默清洗原件。"""
    with pytest.raises((ValueError, UnicodeDecodeError)):
        parse_markdown(body, "a" * 32, ProcessingConfig())


def artifact(pages: list[dict], *, count: int = 2, extra: ZipInfo | None = None) -> bytes:
    """表达已观察到的解析器中间合同，包含真实空白物理页。"""
    buffer = io.BytesIO()
    with ZipFile(buffer, "w") as archive:
        archive.writestr(
            "middle_json.json",
            json.dumps(
                {
                    "schema": "docvortex.middle",
                    "schema_version": "2.0",
                    "is_full_document": True,
                    "metadata": {
                        "file_suffix": "pdf",
                        "document": {"page_count": count, "page_count_kind": "physical"},
                    },
                    "pages": pages,
                }
            ),
        )
        if extra:
            archive.writestr(extra, "outside")
    return buffer.getvalue()


def test_pdf_physical_count_includes_blank_pages() -> None:
    """物理页数来自解析器元信息，不用正文最大页号猜测总页数。"""
    pages = [
        {
            "page_idx": 0,
            "blocks": [
                {
                    "type": "text",
                    "content": [{"type": "text", "content": "中文扫描正文 0x80070005"}],
                }
            ],
        },
        {"page_idx": 1, "blocks": []},
    ]
    chunks, count = parse_artifact(artifact(pages), "a" * 32, ProcessingConfig())
    assert count == 2 and len(chunks) == 1
    assert chunks[0].source_locations.pages[0].page_number == 1
    assert chunks[0].error_codes == ["0x80070005"]
    with pytest.raises(ValueError):
        parse_artifact(artifact(pages, count=1), "a" * 32, ProcessingConfig())


@pytest.mark.parametrize("name", ["../outside", "/outside", "C:/outside", "a\\outside"])
def test_parser_archive_never_accepts_path_escape(name: str) -> None:
    """即使当前只读取JSON，整个包的越界路径仍被拒绝。"""
    with pytest.raises(ValueError, match="路径"):
        data = artifact([], extra=ZipInfo(name))
        if "\\" in name:
            # Windows的ZipInfo会把反斜线规范化；在实际ZIP字节层保留非法原始路径。
            data = data.replace(name.replace("\\", "/").encode(), name.encode())
        parse_artifact(data, "a" * 32, ProcessingConfig())


def test_parser_archive_rejects_symlinks() -> None:
    """符号链接不能借原件解析进入本机文件读取。"""
    entry = ZipInfo("link")
    entry.external_attr = (stat.S_IFLNK | 0o777) << 16
    with pytest.raises(ValueError, match="路径"):
        parse_artifact(artifact([], extra=entry), "a" * 32, ProcessingConfig())


def test_model_cannot_choose_customer_or_another_ticket() -> None:
    """只读工具参数也拒绝任意目标与高权限字段。"""
    with pytest.raises(ValidationError):
        SearchKnowledge.model_validate({"query": "打印", "customerId": "a" * 32})
    with pytest.raises(ValidationError):
        ReadTicketEvents.model_validate({"ticketId": "a" * 32, "limit": 1})
    with pytest.raises(ValidationError):
        ReadTicketEvents(after_sequence=1, event_ids=["a" * 32])
    with pytest.raises(ValidationError):
        ConversationCreate(
            mode="TECHNICIAN_ASSIST", title="我的助手", ticket_id="a" * 32, service_item_id="b" * 32
        )
    with pytest.raises(ValidationError):
        FactChange(
            state_version="1",
            client_action_id="a" * 32,
            action="ASSERT",
            statement="反馈",
            source_type="TOOL_OBSERVATION",
        )


@pytest.mark.parametrize("version", ["9223372036854775808", "9999999999999999999", "01", "-1"])
def test_version_cannot_overflow_java_or_database(version: str) -> None:
    """跨端版本不能因字符串传输绕过Java long范围或标准十进制合同。"""
    with pytest.raises(ValidationError):
        GenerationCreate(question="反馈", client_request_id="a" * 32, state_version=version)


def test_blank_question_is_not_a_paid_generation() -> None:
    """空白输入在HTTP受理之前失败，不占用额度或worker。"""
    with pytest.raises(ValidationError):
        GenerationCreate(question=" \n\t", client_request_id="a" * 32, state_version="1")


def test_feedback_handle_requires_versioned_nonblank_human_note() -> None:
    """反馈处置必须携带当前行版本和人工说明，不能静默改变共享状态。"""
    value = FeedbackHandle(row_version="3", action="RESOLVE", handling_note="已核对来源")
    assert value.row_version == "3"
    with pytest.raises(ValidationError):
        FeedbackHandle(row_version="3", action="DISMISS", handling_note=" \t")


@pytest.mark.parametrize(
    ("statement", "hazard"),
    [
        ("用户报告显示器附近有焦味", True),
        ("电池鼓包，屏幕闪烁", True),
        ("未出现黑屏，但设备冒烟", True),
        ("没有焦味，没有异常发热", False),
        ("是否有焦味？", False),
        ("有无电火花", False),
        ("无焦味；但有电火花", True),
        ("显示器黑屏，电源指示灯亮", False),
        ("系统版本未知但有焦味", True),
        ("没有闻到焦味", False),
        ("未发现明显的异常发热", False),
    ],
)
def test_explicit_user_hazard_stops_before_paid_work(statement: str, hazard: bool) -> None:
    """风险停止区分阳性反馈、否定反馈和待答询问，不依赖模型自行遵从。"""
    from servicedesk_ai.diagnosis.execution import reports_hazard

    assert reports_hazard(statement) is hazard
