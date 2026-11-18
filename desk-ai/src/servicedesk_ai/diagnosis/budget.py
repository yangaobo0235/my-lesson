"""模型上下文估算与完整输入选择；真实用量由持久调用账目负责。作者：yangaobo。"""

import json
import math
import re
from dataclasses import dataclass

from servicedesk_ai.infrastructure.models import ModelMessage, ModelRequest

FINALIZATION_HEADROOM = 1800
"""来源目录、阶段说明和一次纠正的预留Token；实际请求仍按完整输入重新核验。"""


class ExecutionBudgetExceeded(ValueError):
    """当前执行的资源预算不足；不代表持久诊断任务跨轮累计额度耗尽。"""


def history_excerpt(identifier: str, content: str, limit: int = 240) -> str:
    """历史助手仅作为可定位摘录；完整检查和当前事实另存，不把截取文本当操作证据。"""
    if not 0 < limit <= 240:
        raise ValueError("历史摘录字符预算须在1至240之间")
    while True:
        result = json.dumps(
            {
                "historicalAssistantMessageId": identifier,
                "notice": (
                    "历史回答摘录，仅帮助定位；旧引用编号不能作为本轮引用。"
                    "操作前须重新阅读完整证据。"
                ),
                "excerpt": content[:limit],
                "omitted": len(content) > limit,
            },
            ensure_ascii=False,
            separators=(",", ":"),
        )
        if len(result) <= 360:
            return result
        limit -= 1
        if limit < 0:
            raise ValueError("历史消息定位超出摘录预算")


def final_context(messages: list[ModelMessage]) -> list[ModelMessage]:
    """最终阶段不再调用工具，去掉调用协议与冗余定位，正文及非证据工具结果完整保留。"""
    result = []
    evidence_fields = {
        "sourceNumber",
        "sourceType",
        "usageKind",
        "title",
        "content",
        "complete",
        "contentScope",
        "scopeNotice",
        "factId",
        "messageId",
        "messageRole",
        "factSourceType",
        "chunkId",
        "offset",
        "endOffset",
        "totalCharacters",
        "sectionPart",
        "sectionParts",
        "documentId",
        "attachmentId",
        "redactionVersion",
        "nextOffset",
    }
    for message in compact_tool_context(messages):
        if message.role == "assistant" and message.tool_calls:
            continue
        if message.role != "tool":
            result.append(message)
            continue
        values = json.loads(message.content or "[]")
        if not isinstance(values, list):
            raise ValueError("工具回执必须为列表")
        projected = []
        for value in values:
            if not isinstance(value, dict):
                raise ValueError("工具条目必须为对象")
            if value.get("usageKind") == "CONTENT" and "sourceNumber" in value:
                projected.append({key: val for key, val in value.items() if key in evidence_fields})
            elif value.get("usageKind") == "METADATA" and "sourceNumber" in value:
                projected.append(
                    {
                        key: val
                        for key, val in value.items()
                        if key
                        in {
                            "sourceNumber",
                            "sourceType",
                            "title",
                            "usageKind",
                            "procedureCheckKeys",
                        }
                    }
                )
            else:
                # 事实/检查补读、失败及空结果不能因没有正文编号而被静默丢弃。
                projected.append(value)
        result.append(
            ModelMessage(
                role="user",
                content=json.dumps(
                    {"readonlyResults": projected}, ensure_ascii=False, separators=(",", ":")
                ),
            )
        )
    return result


def compact_tool_context(messages: list[ModelMessage]) -> list[ModelMessage]:
    """模型投影去重公共安全字段并展开内嵌JSON，原始回执及完整局部条件保持不变。"""
    names = {call.id: call.name for message in messages for call in message.tool_calls}
    globals_seen: dict[str, int] = {}
    candidates_seen: set[tuple[int, str]] = set()
    result = []
    for message in messages:
        name = names.get(message.tool_call_id or "")
        if message.role != "tool" or name not in {
            "search_knowledge",
            "list_diagnostic_attachments",
            "load_procedure",
        }:
            result.append(message)
            continue
        value = json.loads(message.content or "[]")
        if not isinstance(value, list):
            raise ValueError("工具回执必须为列表")
        compact = []
        for original in value:
            if not isinstance(original, dict):
                raise ValueError("工具条目必须为对象")
            item = dict(original)
            number = item.get("sourceNumber")
            if name == "search_knowledge" and isinstance(number, int):
                candidate_identity = (
                    number,
                    json.dumps(original, ensure_ascii=False, sort_keys=True),
                )
                if candidate_identity in candidates_seen:
                    item = {
                        "previousCandidateSourceNumber": number,
                        "notice": "与先前候选完全相同，仍须读取正文或审核检查",
                    }
                else:
                    item.pop("content", None)
                    item["notice"] = "预览已省略，须读取完整正文或审核检查"
                    candidates_seen.add(candidate_identity)
            elif name == "list_diagnostic_attachments":
                item["items"] = [
                    {key: val for key, val in entry.items() if key != "sha256"}
                    for entry in item.get("items", [])
                ]
            elif item.get("sourceType") == "PROCEDURE_CHECK" and isinstance(number, int):
                snapshot = (
                    json.loads(item["content"])
                    if isinstance(item["content"], str)
                    else item["content"]
                )
                if not isinstance(snapshot, dict):
                    raise ValueError("审核检查正文必须为对象")
                item["content"] = snapshot
                if "sharedSafetySourceNumber" in snapshot:
                    compact.append(item)
                    continue
                shared = {key: val for key, val in snapshot.items() if key != "check"}
                safety_identity = json.dumps(
                    [item.get("documentId"), shared], ensure_ascii=False, sort_keys=True
                )
                if safety_identity in globals_seen:
                    item["content"] = {
                        "sharedSafetySourceNumber": globals_seen[safety_identity],
                        "check": snapshot["check"],
                    }
                else:
                    globals_seen[safety_identity] = number
            compact.append(item)
        result.append(
            message.model_copy(
                update={"content": json.dumps(compact, ensure_ascii=False, separators=(",", ":"))}
            )
        )
    return result


@dataclass(frozen=True)
class ContextGroup:
    """不可拆分的上下文单元；工具调用与所有回执必须属于同一组。"""

    key: str
    messages: tuple[ModelMessage, ...]
    priority: int = 0
    required: bool = False


def initial_context_limit(template: ModelRequest, maximum: int) -> int:
    """先预留动态目录与收束空间，扣固定协议后平分给初始状态和工具证据。

    此预留与执行循环的收束门槛共用，避免初始历史合法装满后，首次外调就被后加目录
    推入最终阶段。必需输入不裁剪；超过剩余空间明确拒绝，实际请求仍完整估算。
    """
    fixed = estimate_input_tokens(template)
    available = maximum - FINALIZATION_HEADROOM
    if fixed >= available:
        raise ExecutionBudgetExceeded("固定协议已占满上下文预算")
    return fixed + (available - fixed) // 2


def fit_context(
    template: ModelRequest, groups: list[ContextGroup], limit: int
) -> tuple[ModelRequest, set[str]]:
    """按完整序列化请求估算，先保留必需输入，再按稳定优先级选取整组。"""
    if len({group.key for group in groups}) != len(groups):
        raise ValueError("上下文组身份重复")
    selected = {group.key for group in groups if group.required}
    if not selected or any(not group.messages for group in groups):
        raise ValueError("上下文必须含非空必需组")

    def request(keys: set[str]) -> ModelRequest:
        """选择顺序不改变原始对话和工具协议的顺序。"""
        return template.model_copy(
            update={
                "messages": tuple(
                    message for group in groups if group.key in keys for message in group.messages
                )
            }
        )

    if estimate_input_tokens(request(selected)) > limit:
        raise ExecutionBudgetExceeded("必需输入超过上下文预算，请缩短本次输入或拆分任务")
    for group in sorted(groups, key=lambda item: -item.priority):
        if group.key in selected:
            continue
        proposed = selected | {group.key}
        if estimate_input_tokens(request(proposed)) <= limit:
            selected = proposed
    return request(selected), selected


ESTIMATOR_VERSION = "bailian-message-v2"


def estimate_input_tokens(request: ModelRequest) -> int:
    """按模型消费的消息与协议字段估算，不把HTTP外层JSON转义重复计费。

    正文、工具参数及Schema完整计量；每消息/工具另预留协议开销。
    文本保持25%余量和每Unicode字符两Token，不宣称等于供应商分词器。
    实际消耗以供应商回执结算，模型名和温度等HTTP配置不作为正文。
    """
    total = 32
    for message in request.messages:
        total += 16 + estimate_text_tokens(message.role)
        if message.content is not None:
            total += estimate_text_tokens(message.content)
        if message.tool_call_id:
            total += estimate_text_tokens(message.tool_call_id)
        for call in message.tool_calls:
            total += 32 + estimate_text_tokens(call.id) + estimate_text_tokens(call.name)
            total += estimate_text_tokens(json.dumps(call.arguments, ensure_ascii=False))
    for tool in request.tools:
        total += 32 + estimate_text_tokens(tool.name) + estimate_text_tokens(tool.description)
        total += estimate_text_tokens(json.dumps(tool.parameters, ensure_ascii=False))
    if request.result_schema is not None:
        total += 32 + estimate_text_tokens(json.dumps(request.result_schema, ensure_ascii=False))
    return total


def estimate_text_tokens(content: str) -> int:
    """固定文本估算器，供索引清单和模型输入共用，不使用UTF-8字节数。"""
    units = 0
    for part in re.findall(r"[A-Za-z ]+|[0-9]+|[^A-Za-z 0-9]", content):
        if part.isascii() and (part[0].isalpha() or part[0] == " "):
            units += math.ceil(len(part) / 4)
        elif part.isascii():
            units += len(part)
        else:
            units += 2 * len(part)
    return max(1, math.ceil(units * 1.25))
