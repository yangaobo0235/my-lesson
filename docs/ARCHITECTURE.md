# Java + Python Agent 架构

## 职责归属

| 能力 | 所有者 | 原因 |
| --- | --- | --- |
| 登录、角色、权限、限流 | Java | 统一业务入口和既有安全体系 |
| MySQL 事务、订单、购物车、学习计划确认 | Java | 业务事实必须确定、可审计、可回滚 |
| 受控 Agent 工具 | Java | 校验委托用户并执行字段级业务规则 |
| 模型、Prompt、Agent 路由、LangGraph | Python | AI 生态和快速迭代 |
| RAG、Embedding、引用、评测 | Python | 与模型运行时保持内聚 |
| 会话、Run、SSE、检索轨迹 | Python/PostgreSQL | AI 运行态，不是业务事实 |
| 正式学习计划和进度 | Java/MySQL | 用户确认后的业务实体 |

## 请求链路

1. 用户以原有 `token` 访问 Gateway。
2. Gateway 从 Redis 读取登录态，清理伪造身份头，生成 60 秒委托 JWT。
3. `/api/v1/ai/**` 进入 Python Agent。
4. Python 分类意图、检索知识并选择允许的工具。
5. Python 携带服务令牌和原委托 JWT 调用 Java `/internal/v1/agent/**`。
6. Java 同时校验服务身份与用户委托，从 `SecurityContext` 获取用户 ID。
7. Python 生成答案、引用和 SSE；业务写入结果由 Java 返回。

## 学习计划状态

```text
Agent 生成候选
  -> Java WAITING_CONFIRMATION 草案
  -> 用户调整 -> 新版本草案，旧版本 SUPERSEDED
  -> 用户确认 -> Java MySQL 事务创建 ACTIVE 正式计划
  -> 用户进度更新 -> Java 校验并更新正式计划
```

Agent 不具备绕过确认直接创建正式计划的工具。

## 知识同步

```text
Java 业务事务
  -> ai_outbox_event
  -> Outbox Relay
  -> RocketMQ ml-ai-knowledge-events
  -> ml-agent-relay
  -> Python /internal/v1/knowledge/events
  -> knowledge_event 幂等/版本检查
  -> Java 只读知识 API
  -> Chunk + Embedding + pgvector
```

删除事件会删除对应 Chunk，并把 Source 标为 `DELETED`。消费失败时 HTTP 返回失败，RocketMQ 重试；重复 `eventId` 和乱序旧版本不会重复索引。
