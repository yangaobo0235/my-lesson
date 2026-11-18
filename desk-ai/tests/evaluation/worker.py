"""只领取指定冻结批次Run的评测worker；复用正式执行、租约与调用账目。作者：yangaobo。"""

import argparse
import asyncio
import hashlib
import json
import time
from pathlib import Path
from unittest.mock import patch
from uuid import uuid4

from sqlalchemy import bindparam, text

from servicedesk_ai.bootstrap.resources import open_resources
from servicedesk_ai.bootstrap.settings import get_settings
from servicedesk_ai.diagnosis.execution import RunWorker
from servicedesk_ai.infrastructure.domain_rpc import open_domain_rpc
from servicedesk_ai.infrastructure.index_replicas import IndexReplicas
from servicedesk_ai.repositories import runs
from servicedesk_ai.services.evidence import EvidenceService
from servicedesk_ai.services.model_calls import ModelCalls
from servicedesk_ai.services.run_tools import RunTools
from tests.evaluation.__main__ import output_path
from tests.evaluation.ablations import ablate
from tests.evaluation.client import write_json
from tests.evaluation.environment import Trial


def read_trial(path: Path) -> Trial:
    """只对Windows短暂文件占用有界重读同一试验；非法内容和永久失败向上传播。"""
    attempt = 0
    while True:
        try:
            return Trial.model_validate_json(path.read_text(encoding="utf-8"))
        except PermissionError as error:
            if getattr(error, "winerror", None) not in {5, 32, 33} or attempt == 4:
                raise
            time.sleep(0.025 * 2**attempt)
            attempt += 1


def batch_runs(batch: Path) -> set[str]:
    """只接收正式试验文件内已持久记录的Run身份，不扫描或领取其他客户队列。"""
    identifiers: set[str] = set()
    for path in batch.glob("*.dynamic.*.json"):
        trial = read_trial(path)
        if trial.status == "RUNNING":
            identifiers.update(trial.run_ids)
    return identifiers


async def claim_batch(db, batch: Path):
    """测试调度只缩小领取范围；保留正式令牌、Deadline、行锁和后续执行核验。"""
    identifiers = await asyncio.to_thread(batch_runs, batch)
    if not identifiers:
        return None
    identifier = (
        await db.execute(
            text("""
                SELECT id FROM ai_run WHERE id IN :ids AND status='QUEUED'
                  AND deadline_at>UTC_TIMESTAMP(6)
                ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            """).bindparams(bindparam("ids", expanding=True)),
            {"ids": sorted(identifiers)},
        )
    ).scalar_one_or_none()
    if identifier is None:
        return None
    value = await runs.run(db, identifier)
    await db.execute(
        text("""
            UPDATE ai_run SET status='RUNNING',started_at=UTC_TIMESTAMP(6),claim_token=:token,
                claim_until=LEAST(deadline_at,UTC_TIMESTAMP(6)+INTERVAL :lease SECOND),
                updated_at=UTC_TIMESTAMP(6) WHERE id=:id AND status='QUEUED'
        """),
        {"id": identifier, "token": str(uuid4()), "lease": value.pipeline_config.lease_seconds},
    )
    return await runs.run(db, identifier)


async def serve(batch: Path) -> None:
    """生产环境拒绝运行；源码、模型和预算须与冻结计划一致。"""
    settings = get_settings()
    if settings.environment != "dev" or settings.resource_env != "test":
        raise ValueError("消融worker只允许显式开发测试环境")
    plan = json.loads((batch / "plan.json").read_text(encoding="utf-8"))
    root = Path(__file__).resolve().parents[2]
    for name, digest in plan["sources"].items():
        if hashlib.sha256((root / name).read_bytes()).hexdigest() != digest:
            raise ValueError("冻结源码已变化，拒绝执行")
    if (
        plan["model"] != settings.model.chat_model
        or plan["embedding"] != settings.model.embedding_model
        or plan["temperature"] != settings.model.temperature
        or plan["thinking"] != settings.model.enable_thinking
        or plan["executionBudget"] != settings.execution.model_dump(mode="json")
        or plan["taskBudget"] != settings.task_budget.model_dump(mode="json")
    ):
        raise ValueError("评测worker与冻结模型或预算不同")
    async with (
        open_resources(settings, process="worker") as resources,
        open_domain_rpc(settings.rpc) as rpc,
    ):
        replicas = IndexReplicas(
            resources.elasticsearch,
            resources.qdrant,
            settings.elasticsearch.index,
            settings.qdrant.collection,
            settings.model,
        )
        models = ModelCalls(
            resources.database,
            resources.model,
            settings.chat_pricing,
            settings.embedding_pricing,
        )
        evidence = EvidenceService(resources.database, rpc)
        worker = RunWorker(
            resources.database,
            rpc,
            settings,
            evidence,
            models,
            RunTools(resources.database, rpc, replicas, evidence, models),
            resources.tracer_provider.get_tracer("servicedesk_ai.evaluation"),
        )

        async def claim(db):
            run = await claim_batch(db, batch)
            if run is not None:
                write_json(
                    batch / "workers" / (run.id + ".json"),
                    {
                        "runId": run.id,
                        "ablation": plan["ablation"],
                        "claimTokenHash": hashlib.sha256(run.claim_token.encode()).hexdigest(),
                        "sources": plan["sources"],
                    },
                )
            return run

        with ablate(plan["ablation"]), patch.object(runs, "claim", claim):
            while True:
                if not await worker.run_one():
                    await asyncio.sleep(0.25)


def main() -> None:
    """仅显式模块命令启动，不由应用装配或pytest隐式启动。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--batch", type=output_path, required=True)
    args = parser.parse_args()
    try:
        asyncio.run(serve(args.batch))
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
