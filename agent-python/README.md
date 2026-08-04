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

在另一个终端启动 Worker：

```powershell
Set-Location agent-python
.\.venv\Scripts\python -m mylesson_agent.worker
```

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

## 接口

| 入口 | 地址 |
| --- | --- |
| Swagger UI | `http://localhost:24109/docs` |
| OpenAPI JSON | `http://localhost:24109/openapi.json` |
| 就绪检查 | `http://localhost:24109/health/ready` |
| Prometheus 指标 | `http://localhost:24109/metrics` |

公共与内部契约位于仓库根目录 `contracts/`。公共 API 变化后应运行 `scripts/export_agent_openapi.py` 并提交更新后的契约。
