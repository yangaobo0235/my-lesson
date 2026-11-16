"""MinerU V1单文件解析适配与有界ZIP真实物理页校验。作者：yangaobo。"""

import io
import json
from pathlib import PurePosixPath
from typing import Literal
from urllib.parse import quote, urlsplit
from zipfile import ZipFile

import httpx
from pydantic import BaseModel, ConfigDict, Field

from servicedesk_ai.schemas.indexing import ParsedChunk, PdfPage, ProcessingConfig, SourceLocations
from servicedesk_ai.services.parsing import chunk_identifier, error_codes


class InlineContent(BaseModel):
    """实际解析合同中的只读文本节点，不执行HTML或文档脚本。"""

    model_config = ConfigDict(extra="ignore")
    type: str
    content: str


class CanonicalBlock(BaseModel):
    """DocVortex middle 2.0真实块，未经确认的新节点明确拒绝。"""

    model_config = ConfigDict(extra="ignore")
    type: str
    content: list[InlineContent] | str


class CanonicalPage(BaseModel):
    """包含空页的物理页集合，不能从有正文的最大页号推测总页数。"""

    model_config = ConfigDict(extra="ignore")
    page_idx: int = Field(ge=0)
    blocks: list[CanonicalBlock]


class PhysicalDocument(BaseModel):
    """解析器明确给出全原件物理页数。"""

    page_count: int = Field(ge=1, le=200)
    page_count_kind: Literal["physical"]


class ParseMetadata(BaseModel):
    """必须为PDF及物理页合同，不接受逻辑页或选页结果。"""

    model_config = ConfigDict(extra="ignore")
    file_suffix: Literal["pdf"]
    document: PhysicalDocument


class ParsedDocument(BaseModel):
    """按实际V1 Tasks返回的固定中间格式校验完整原件。"""

    model_config = ConfigDict(extra="ignore")
    schema_name: Literal["docvortex.middle"] = Field(alias="schema")
    schema_version: Literal["2.0"]
    is_full_document: Literal[True]
    metadata: ParseMetadata
    pages: list[CanonicalPage] = Field(min_length=1, max_length=200)


def parse_artifact(
    data: bytes, job_id: str, config: ProcessingConfig
) -> tuple[list[ParsedChunk], int]:
    """不落盘解压，拒绝穿越路径、符号链接、ZIP炸弹及无页正文。"""
    if len(data) > 40 * 1024 * 1024:
        raise ValueError("解析包过大")
    with ZipFile(io.BytesIO(data)) as archive:
        entries = archive.infolist()
        if len(entries) > 1000 or sum(item.file_size for item in entries) > 80 * 1024 * 1024:
            raise ValueError("解压预算超限")
        for item in entries:
            # Windows 的 ZipInfo 会规范化 filename，安全检查必须使用包内原始名称。
            original_name = item.orig_filename
            path = PurePosixPath(original_name)
            if (
                path.is_absolute()
                or ".." in path.parts
                or "\\" in original_name
                or ":" in original_name
                or (item.external_attr >> 16) & 0o170000 == 0o120000
            ):
                raise ValueError("解析包包含非法路径")
        if len({item.filename for item in entries}) != len(entries):
            raise ValueError("解析包包含重复名称")
        manifests = [item for item in entries if item.filename == "middle_json.json"]
        if len(manifests) != 1 or manifests[0].file_size > 20 * 1024 * 1024:
            raise ValueError("缺少唯一内容清单")
        raw = json.loads(archive.read(manifests[0]))
    document = ParsedDocument.model_validate(raw)
    pages = document.metadata.document.page_count
    if (
        pages > config.max_pages
        or len(document.pages) != pages
        or [page.page_idx for page in document.pages] != list(range(pages))
    ):
        raise ValueError("物理页超过预算")
    chunks: list[ParsedChunk] = []
    section = None
    blocks = [
        (physical_page, block) for physical_page in document.pages for block in physical_page.blocks
    ]
    for page, element in blocks:
        if element.type not in {
            "text",
            "heading",
            "table",
            "list",
            "equation",
            "image",
            "header",
            "footer",
            "page_number",
        }:
            raise ValueError("解析器返回未确认块类型")
        if element.type == "image":
            continue
        content = (
            element.content
            if isinstance(element.content, str)
            else "\n".join(node.content for node in element.content)
        )
        if element.type == "heading":
            section = content.strip()
        content = content.strip()
        for offset in range(0, len(content), config.chunk_chars):
            part = content[offset : offset + config.chunk_chars]
            if part.strip():
                chunks.append(
                    ParsedChunk(
                        id=chunk_identifier(job_id, len(chunks) + 1),
                        section_path=section,
                        content=part,
                        source_locations=SourceLocations(
                            kind="PDF", pages=[PdfPage(page_number=page.page_idx + 1)]
                        ),
                        error_codes=error_codes(part),
                    )
                )
        if len(chunks) > config.max_chunks:
            raise ValueError("PDF片段超过预算")
    if not chunks:
        raise ValueError("PDF无可读正文")
    return chunks, pages


class MineruClient:
    """创建意图由调用者预先提交，未知响应不自动再次创建任务。"""

    def __init__(self, client: httpx.AsyncClient) -> None:
        """HTTP连接由进程资源统一释放。"""
        self.client = client

    def same_origin(self, url: str) -> str:
        """服务返回的下载或上传地址也必须匹配配置源，禁止重定向。"""
        target = urlsplit(url)
        base = urlsplit(str(self.client.base_url))
        if target.scheme or target.netloc:
            if (target.scheme, target.hostname, target.port) != (
                base.scheme,
                base.hostname,
                base.port,
            ):
                raise ValueError("解析地址跨源")
        if (
            target.username
            or target.password
            or target.fragment
            or not target.path.startswith("/v1/")
        ):
            raise ValueError("解析地址不合法")
        return url

    async def create(self, size: int, sha: str, config: ProcessingConfig) -> tuple[str, str]:
        """只进行一次创建；丢失身份必须进入UNKNOWN对账。"""
        response = await self.client.post(
            "/v1/tasks",
            json={
                "tier": config.parser_tier,
                "ocr_mode": "auto",
                "output_formats": ["default"],
                "files": [{"file_name": "original.pdf", "bytes": size, "sha256sum": sha}],
            },
        )
        response.raise_for_status()
        value = response.json()
        files = value.get("files", [])
        if (
            len(files) != 1
            or not isinstance(value.get("task_id"), str)
            or not isinstance(files[0].get("file_id"), str)
        ):
            raise ValueError("解析创建回执不合法")
        return value["task_id"], files[0]["file_id"]

    async def upload(self, task_id: str, file_id: str, data: bytes) -> None:
        """固定原任务上传，不接受浏览器URL。"""
        response = await self.client.put(
            f"/v1/tasks/{quote(task_id, safe='')}/uploads/{quote(file_id, safe='')}",
            content=data,
            headers={"Content-Type": "application/pdf"},
        )
        response.raise_for_status()

    async def status(self, task_id: str, file_id: str) -> tuple[str, str | None]:
        """沿原解析身份查询，找不到不能当作远端已停止。"""
        response = await self.client.get(f"/v1/tasks/{quote(task_id, safe='')}")
        response.raise_for_status()
        value = response.json()
        files = [item for item in value.get("files", []) if item.get("file_id") == file_id]
        if len(files) != 1:
            raise ValueError("解析文件身份不匹配")
        return files[0]["status"], files[0].get("result_url")

    async def cancel(self, task_id: str) -> None:
        """取消回执不等于停止；调用者仍需查询原任务收敛。"""
        response = await self.client.post(f"/v1/tasks/{quote(task_id, safe='')}/cancel")
        response.raise_for_status()

    async def download(self, url: str) -> bytes:
        """下载体总量受限，即使Content-Length缺失也逐块计数。"""
        result = bytearray()
        async with self.client.stream("GET", self.same_origin(url)) as response:
            response.raise_for_status()
            async for part in response.aiter_bytes(65536):
                result.extend(part)
                if len(result) > 40 * 1024 * 1024:
                    raise ValueError("解析下载超预算")
        return bytes(result)
