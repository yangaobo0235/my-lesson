"""索引事务消息的不可变v2信封、处理预算及真实来源定位。作者：yangaobo。"""

import hashlib
import json
from typing import Annotated, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic.alias_generators import to_camel

Identifier = Annotated[str, Field(pattern=r"^[0-9a-f]{32}$")]
Digest = Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]


class WireModel(BaseModel):
    """外部写入拒绝未知字段，持久化snake_case与线协议lowerCamelCase显式转换。"""

    model_config = ConfigDict(extra="forbid", alias_generator=to_camel, populate_by_name=True)


class ProcessingConfig(WireModel):
    """只能选择既定解析档位与固定向量空间，不接受脚本或任意地址。"""

    schema_version: Literal[1] = 1
    parser_contract: Literal["mineru-tasks-v1-docvortex-middle-2.0"] = (
        "mineru-tasks-v1-docvortex-middle-2.0"
    )
    parser_tier: Literal["flash", "basic", "standard", "advanced"] = "basic"
    vector_space: Literal["te4_1024_v1"] = "te4_1024_v1"
    embedding_model: Literal["text-embedding-v4"] = "text-embedding-v4"
    chunk_chars: int = Field(default=1200, ge=200, le=2000)
    max_pages: int = Field(default=200, ge=1, le=200)
    max_chunks: int = Field(default=500, ge=1, le=500)
    total_seconds: int = Field(default=600, ge=30, le=600)


def canonical_hash(value: object) -> str:
    """UTF-8、键排序、无空白JSON；仅用于已类型化的不含浮点值的业务输入。"""
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()
    ).hexdigest()


class IndexCommand(WireModel):
    """取消同样携带完整输入；command与operationId不参与输入摘要。"""

    schema_version: Literal[2]
    command: Literal["SUBMIT", "CANCEL"]
    operation_id: Identifier
    job_id: Identifier
    document_id: Identifier
    attempt_number: int = Field(ge=1)
    content_format: Literal["PDF", "MARKDOWN"]
    file_sha256: Digest
    requested_by: Identifier
    processing_config: ProcessingConfig
    request_hash: Digest

    @model_validator(mode="after")
    def verify_digest(self) -> Self:
        """先核对全部不可变输入，防止同jobId异义及伪造取消墓碑。"""
        source = self.model_dump(by_alias=True, exclude={"command", "operation_id", "request_hash"})
        if self.request_hash != canonical_hash(source):
            raise ValueError("索引输入摘要不匹配")
        if self.command == "SUBMIT" and self.operation_id != self.job_id:
            raise ValueError("提交操作标识必须等于任务标识")
        return self


class MarkdownSegment(WireModel):
    """真实原件标题锚点与包含首尾的行区间。"""

    anchor: str = Field(min_length=1, max_length=200)
    line_start: int = Field(ge=1)
    line_end: int = Field(ge=1)


class PdfPage(WireModel):
    """物理页从1开始，不推断印刷页标签。"""

    page_number: int = Field(ge=1)
    printed_page_label: str | None = None


class SourceLocations(WireModel):
    """两种格式各自维护真实定位，不伪造Markdown页码。"""

    schema_version: Literal[1] = 1
    kind: Literal["PDF", "MARKDOWN"]
    pages: list[PdfPage] = Field(default_factory=list)
    segments: list[MarkdownSegment] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_locations(self) -> Self:
        """格式与定位集合相斥且有效。"""
        if self.kind == "PDF" and (not self.pages or self.segments):
            raise ValueError("PDF缺少物理页")
        if self.kind == "MARKDOWN" and (not self.segments or self.pages):
            raise ValueError("Markdown缺少真实行区间")
        if any(segment.line_end < segment.line_start for segment in self.segments):
            raise ValueError("行区间倒置")
        return self


class ParsedChunk(WireModel):
    """固定清单后，片段ID与正文及定位不可重算。"""

    id: Identifier
    section_path: str | None
    content: str = Field(min_length=1, max_length=49152)
    source_locations: SourceLocations
    error_codes: list[str] = Field(default_factory=list)
    parent_chunk_id: Identifier | None = None
    node_kind: Literal["SECTION", "LEAF"] = "LEAF"
    content_sha256: Digest | None = None
    token_estimate: int | None = Field(default=None, ge=1)

    @model_validator(mode="after")
    def verify_content(self) -> Self:
        """正文与摘要绑定；持久清单不能接受篡改正文或超大节点。"""
        if not self.content.strip() or len(self.content.encode()) > 48 * 1024:
            raise ValueError("片段正文为空或超过预算")
        if self.node_kind == "LEAF" and len(self.content) > 4000:
            raise ValueError("叶片超过解析预算")
        digest = hashlib.sha256(self.content.encode()).hexdigest()
        if self.content_sha256 is not None and self.content_sha256 != digest:
            raise ValueError("片段正文摘要不匹配")
        self.content_sha256 = digest
        return self
