"""受控核对无远端ID的未知PDF任务；不得用于跳过可查询的原解析任务。作者：yangaobo。"""

import argparse
import asyncio
import json
import re

from sqlalchemy import URL, text
from sqlalchemy.ext.asyncio import create_async_engine

from servicedesk_ai.bootstrap.settings import load_settings


async def reconcile(job_id: str, operator: str, evidence: str) -> None:
    """仅终态、无原ID、未知创建可在人工确认旧环境隔离后安全结算。"""
    settings = load_settings(validate_infrastructure=False).mysql
    engine = create_async_engine(
        URL.create(
            "mysql+asyncmy",
            username=settings.username,
            password=settings.password.get_secret_value(),
            host=settings.host,
            port=settings.port,
            database=settings.database,
        ),
        hide_parameters=True,
        connect_args={
            "connect_timeout": 2,
            "read_timeout": 5,
            "init_command": "SET time_zone='+00:00'",
        },
    )
    try:
        async with engine.begin() as db:
            result = await db.execute(
                text("""
                UPDATE ai_index_job SET parser_state='SETTLED',parser_reconciliation=
                JSON_SET(CAST(:audit AS JSON),'$.checked_at',DATE_FORMAT(UTC_TIMESTAMP(6),
                '%Y-%m-%dT%H:%i:%s.%fZ')),updated_at=UTC_TIMESTAMP(6)
                WHERE id=:id AND content_format='PDF' AND status IN ('FAILED','CANCELLED')
                AND parser_state IN ('SUBMITTING','UNKNOWN')
                AND parser_task_id IS NULL AND parser_file_id IS NULL
                """),
                {
                    "id": job_id,
                    "audit": json.dumps(
                        {
                            "schema_version": 1,
                            "operator": operator,
                            "evidence": evidence,
                            "old_environment_isolated": True,
                        },
                        ensure_ascii=False,
                    ),
                },
            )
            if result.rowcount != 1:
                raise RuntimeError("任务不符合未知解析对账条件，未更改")
        print("已记录原任务的人工隔离核对，业务终态保持不变")
    finally:
        await engine.dispose()


def main() -> None:
    """运维入口显式要求原身份、操作者、依据与隔离声明，不提供HTTP旁路。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--job-id", required=True)
    parser.add_argument("--operator", required=True)
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--confirm-old-parser-environment-isolated", action="store_true")
    args = parser.parse_args()
    if (
        not re.fullmatch(r"[0-9a-f]{32}", args.job_id)
        or not 1 <= len(args.operator.strip()) <= 100
        or not 10 <= len(args.evidence.strip()) <= 2000
        or not args.confirm_old_parser_environment_isolated
    ):
        parser.error("需要合法原任务、操作者、核对依据及旧解析环境已隔离声明")
    asyncio.run(reconcile(args.job_id, args.operator.strip(), args.evidence.strip()))


if __name__ == "__main__":
    main()
