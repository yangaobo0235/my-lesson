# MyLesson Python Agent

MyLesson 的唯一 AI/Agent 运行时，负责模型、Prompt、LangGraph 工作流、RAG、Embedding、对话、流式事件和评测。Java 服务继续负责身份、业务规则、事务和正式业务写入。

## 主要能力

- FastAPI 公共 API 和内部知识事件 API
- LangGraph 意图路由、检索和工具编排
- PostgreSQL/pgvector 对话运行态与知识索引
- 独立 Worker、任务租约、心跳和超时恢复
- 持久化 SSE 与 `Last-Event-ID` 回放
- Java 受控工具调用和委托身份传递
- Prometheus、OpenTelemetry 与 Langfuse 可观测性

Python 不直接修改 MySQL 业务数据。所有正式业务写入必须通过 Java 工具完成。

## 环境要求

- Python 3.12
- PostgreSQL 15+ 与 pgvector
- Redis
- 可访问的 Java 业务服务
- OpenAI Compatible 模型服务或 DashScope

## 本地开发

从仓库根目录创建 `.env` 后执行：

```powershell
Set-Location agent-python
python -m venv .venv
.\.venv\Scripts\python -m pip install -e ".[dev]"
.\.venv\Scripts\python -m alembic upgrade head
.\.venv\Scripts\python -m uvicorn mylesson_agent.main:app --reload --port 24109
```

从旧检索版本升级到 `0005` 时，如果 `pg_trgm` 由容器内的 `postgres` 管理员创建，先在
虚拟机执行管理员清理，再运行 Alembic：

```bash
docker exec pgvector psql -U postgres -d mylesson_agent -v ON_ERROR_STOP=1 \
  -c 'DROP INDEX IF EXISTS ix_knowledge_chunk_content_trgm; DROP EXTENSION IF EXISTS pg_trgm;'
```

`0005` 会再次执行幂等删除并形成升级门禁。新建数据库不会创建这些对象。

在另一个终端启动 Worker：

```powershell
Set-Location agent-python
.\.venv\Scripts\python -m mylesson_agent.worker
```

首次启用 Elasticsearch 或重建索引后，先预览并回填已有知识 chunk：

```powershell
.\.venv\Scripts\python scripts\backfill_elasticsearch.py --dry-run
.\.venv\Scripts\python scripts\backfill_elasticsearch.py --batch-size 100
```

脚本只处理 `ACTIVE` 且 ES 索引版本落后的来源；可用 `--source-type COURSE`、
`--limit 1000` 缩小范围，或用 `--force` 重建全部活动来源。

配置通过环境变量注入。完整模板见 `../deploy/env/agent.env.example`，本地源码运行也可以使用仓库根目录 `.env.example`。

## 测试与质量

```powershell
.\.venv\Scripts\python -m ruff check .
.\.venv\Scripts\python -m mypy src
.\.venv\Scripts\python -m pytest
.\.venv\Scripts\python -m alembic heads
```

运行 240 条 RAG、工具选择、安全与拒答质量测评（需要可访问 PostgreSQL/pgvector 和
DashScope）：

```powershell
.\.venv\Scripts\python scripts\run_quality_evaluation.py `
  --output evaluation\quality-evaluation.json `
  --fail-on-gate
```

可使用 `--types RAG NO_ANSWER`、`--case-id rag-001` 或 `--limit 10` 缩小运行范围。
 报告包含逐条结果、分类通过率、P95 延迟、RAG 来源/内容/引用命中率及回归门禁结果；
 不会输出数据库密码或模型密钥。

上面的 240 条脚本用于开发回归，不能把不同类别相加后作为端到端总体质量。严格外部评测使用完整回答生成、独立 Judge 和冻结挑战集：

```powershell
.\.venv\Scripts\python scripts\run_external_quality_evaluation.py `
  --provider dashscope `
  --output evaluation\strict-quality-evaluation.json
```

严格报告分别输出 RAG、工具路由、安全攻防与近域拒答结果，不计算混合总分。工具维度只验证路由和参数；未启动 Java 业务服务时不会声称端到端工具调用成功。

高难挑战集将跨来源聚合、相似事实消歧、错误前提纠正和近域缺失事实与开发回归集分开。建议按小批次运行，避免长时间模型评测中断后丢失整批结果：

```powershell
.\.venv\Scripts\python scripts\run_external_quality_evaluation.py `
  --provider dashscope `
  --regression-dataset evaluation\m17-rag-challenge-v1.jsonl `
  --holdout-dataset evaluation\m17-near-domain-v1.jsonl `
  --types RAG --offset 0 --limit 5 `
  --output evaluation\m17-rag-batch-01.json
```

安全通过率必须同时查看攻击样本和域内安全控制样本。只有攻击样本会掩盖过度拒答：

```powershell
.\.venv\Scripts\python scripts\run_external_quality_evaluation.py `
  --provider dashscope `
  --regression-dataset evaluation\m17-rag-challenge-v1.jsonl `
  --holdout-dataset evaluation\m17-security-controls-v1.jsonl `
  --types SECURITY `
  --output evaluation\m17-security-controls.json
```

`--case-id` 可重复传入以复测指定失败样本。外部简历或报告只能引用同一冻结数据集、同一运行配置下的分项结果；LLM Judge 结果在对外使用前需要人工复核。

### M19 高难简历评测集

M19 是独立于旧评测集的 600 条冻结集：288 条 RAG 检索题和 312 条意图/工具路由题。
构建器会校验问题唯一性、维度数量、真实来源引用和错误前提标签：

```powershell
.\.venv\Scripts\python.exe scripts\build_m19_adversarial_benchmark.py
```

离线检索回放使用 52 条真实知识源、DashScope Embedding/Rerank 和 RRF。稀疏检索为本地
BM25 代理，因此结果不能表述为 Elasticsearch/pgvector 端到端性能：

```powershell
.\.venv\Scripts\python.exe scripts\run_m19_offline_retrieval.py `
  --concurrency 4 `
  --output evaluation\m19-offline-retrieval.json
```

完整口径、结果和简历表述见 `docs/benchmarks/M19_RESUME_BENCHMARK.md`。

## 接口

| 入口 | 地址 |
| --- | --- |
| Swagger UI | `http://localhost:24109/docs` |
| OpenAPI JSON | `http://localhost:24109/openapi.json` |
| 就绪检查 | `http://localhost:24109/health/ready` |
| Prometheus 指标 | `http://localhost:24109/metrics` |

公共与内部契约位于仓库根目录 `contracts/`。公共 API 变化后应运行 `scripts/export_agent_openapi.py` 并提交更新后的契约。
