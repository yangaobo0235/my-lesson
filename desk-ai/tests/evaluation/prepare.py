"""通过正式Java/索引流程安装按服务项目隔离的合成规程。作者：yangaobo。"""

import hashlib
import json
import time
from datetime import date, timedelta
from pathlib import Path

from tests.evaluation.catalog import Family
from tests.evaluation.client import Client, key, write_json


def procedure(family: Family) -> dict:
    """公开检查定义精确对应原Markdown行，不包含实例隐藏原因。"""
    lines = family.guide().splitlines()
    checks = []
    for step in ("observe", "recover", "verify"):
        start = lines.index("## " + step) + 1
        checks.append(
            {
                "checkKey": step,
                "roles": ["CUSTOMER", "TECHNICIAN"],
                "instruction": family.instruction(step),
                "answerSchema": {
                    "schemaVersion": 1,
                    "kind": "CHOICE",
                    "options": family.options(step),
                    "minimum": None,
                    "maximum": None,
                    "maxLength": 2000,
                },
                "requiredFields": [],
                "prerequisiteCheckKeys": []
                if step == "observe"
                else ["observe" if step == "recover" else "recover"],
                "prerequisiteAnswers": []
                if step == "observe"
                else [
                    {
                        "checkKey": "observe" if step == "recover" else "recover",
                        "allowedValues": [
                            family.observations[0] if step == "recover" else "已完成"
                        ],
                    }
                ],
                "riskLevel": "LOW",
                "stopConditions": ["SAFETY_HAZARD_REPORTED", "DATA_LOSS_RISK_REPORTED"],
                "sourceLocator": {
                    "kind": "MARKDOWN",
                    "anchor": step,
                    "lineStart": start,
                    "lineEnd": start + 2,
                },
            }
        )
    return {
        "schemaVersion": 1,
        "applicability": "Windows 11、本人普通界面、合成实验项目",
        "requiredFields": ["os_name", "os_version"],
        "checks": checks,
        "stopConditions": ["SAFETY_HAZARD_REPORTED", "DATA_LOSS_RISK_REPORTED"],
        "handoverConditions": [
            "ADMIN_PRIVILEGE_REQUIRED",
            "OUT_OF_SERVICE_SCOPE",
            "NO_PROGRESS_LIMIT",
        ],
    }


def prepare(base_url: str, accounts: Path, path: Path, families: list[Family]) -> None:
    """每个族拥有独立服务范围，防止其他资料混入同条件对照；不修改已有资料。"""
    state = (
        json.loads(path.read_text(encoding="utf-8"))
        if path.exists()
        else {"schemaVersion": 1, "families": {}}
    )
    clients = []
    try:
        for role in ("admin", "technician", "knowledge", "customer"):
            clients.append(Client(base_url, accounts, role))
        admin, technician, knowledge, customer = clients
        customer_id = customer.identity["organizationId"]
        for family in families:
            item = state["families"].setdefault(family.key, {})
            digest = hashlib.sha256(family.guide().encode()).hexdigest()
            if item.get("guideSha256", digest) != digest:
                raise ValueError("已冻结规程发生变化，必须使用新评测批次和修订")
            item["guideSha256"] = digest
            for name in ("service", "grant", "upload", "validate", "index"):
                item.setdefault(name + "Key", key())
            write_json(path, state)
            if "serviceItemId" not in item:
                service = admin.call(
                    "admin/service-item/add",
                    {
                        "clientActionId": item["serviceKey"],
                        "serviceCode": "EVAL_" + family.key.upper(),
                        "category": family.category,
                        "serviceName": family.title + "（合成评测专用）",
                        "supportScope": (
                            "仅用于冻结合成案例和用户模拟，不受理真实设备；无远程执行或管理员操作。"
                        ),
                        "onsiteSupported": False,
                        "reason": "领域评测专用服务范围，防止知识跨族混入",
                    },
                )
                item["serviceItemId"] = service["id"]
                write_json(path, state)
            if not item.get("granted"):
                technician.call(
                    "admin/service-entitlement/grant",
                    {
                        "clientActionId": item["grantKey"],
                        "customerId": customer_id,
                        "serviceItemId": item["serviceItemId"],
                        "startsOn": str(date.today()),
                        "endsOn": str(date.today() + timedelta(days=30)),
                        "reason": "合成评测30天有效范围",
                    },
                )
                item["granted"] = True
                write_json(path, state)
            if "seriesId" not in item:
                series = knowledge.call(
                    "admin/document-series/add",
                    {
                        "sourceType": "TROUBLESHOOTING_GUIDE",
                        "sourceNamespace": "evaluation-v1",
                        "sourceCode": family.key,
                        "sourceOrganization": "合成实验支持组",
                        "languageCode": "zh-CN",
                        "audience": "CUSTOMER",
                    },
                )
                item["seriesId"] = series["id"]
                write_json(path, state)
            if "documentId" not in item:
                document = knowledge.upload(
                    {
                        "clientUploadId": item["uploadKey"],
                        "seriesId": item["seriesId"],
                        "contentFormat": "MARKDOWN",
                        "title": family.title + "（合成实验规程）",
                        "sourceUrl": None,
                        "sourceReviewNote": "原创合成测试夹具，结构校验不代表专家审阅或现场验证。",
                        "revisionLabel": "evaluation-v2",
                        "applicabilityNote": "Windows 11、仅本人普通界面",
                        "rules": [
                            {
                                "serviceItemId": item["serviceItemId"],
                                "productModelId": None,
                                "osName": "Windows",
                                "osVersion": "11",
                                "softwareName": None,
                                "softwareVersion": None,
                            }
                        ],
                        "procedureDefinition": procedure(family),
                    },
                    family.guide().encode(),
                )
                item["documentId"] = document["id"]
                write_json(path, state)
            document = knowledge.call("admin/document/query", {"id": item["documentId"]})
            if document["procedureDefinition"] != procedure(family):
                raise ValueError("已发布规程与冻结定义不同，必须建立新修订及新批次，不能原地修改")
            if not document.get("procedureValidatedSha256"):
                document = knowledge.call(
                    "admin/document/validate-procedure",
                    {
                        "id": document["id"],
                        "rowVersion": document["rowVersion"],
                        "clientActionId": item["validateKey"],
                    },
                )
            if "indexJobId" not in item:
                job = knowledge.call(
                    "admin/index-job/add",
                    {
                        "id": document["id"],
                        "rowVersion": document["rowVersion"],
                        "clientRequestId": item["indexKey"],
                        "processingConfig": {"parserTier": "flash"},
                    },
                )
                item["indexJobId"] = job["id"]
                write_json(path, state)
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                job = knowledge.call("admin/index-job/query", {"id": item["indexJobId"]})
                if job.get("aiStatus") in {"SUCCEEDED", "FAILED", "CANCELLED"}:
                    break
                time.sleep(1)
            if job.get("aiStatus") != "SUCCEEDED":
                raise RuntimeError("原索引任务未成功；保留原身份，不创建替代任务")
            document = knowledge.call("admin/document/query", {"id": item["documentId"]})
            if document["publicationStatus"] == "DRAFT":
                knowledge.call(
                    "admin/document/publish",
                    {
                        "id": document["id"],
                        "rowVersion": document["rowVersion"],
                        "replacesDocumentId": item.get("previousDocumentId"),
                    },
                )
            item["ready"] = True
            item["procedureSha256"] = document["procedureSha256"]
            write_json(path, state)
            print("READY", family.key, flush=True)
    finally:
        for client in clients:
            client.close()
