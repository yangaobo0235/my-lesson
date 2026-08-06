# Java + Python Agent 架构

## 职责归属

| 能力 | 所有者 | 原因 |
| --- | --- | --- |
| 登录、角色、权限、限流 | Java | 统一业务入口和既有安全体系 |
| MySQL 事务、订单、购物车、学习计划确认 | Java | 业务事实必须确定、可审计、可回滚 |
| 受控 Agent 工具 | Java | 校验委托用户并执行字段级业务规则 |
| Elasticsearch BM25 索引和关键词召回 | Java `ml-ai-search` | 统一 ES 连接、IK mapping、版本保护和内部鉴权 |
| 模型、Prompt、Agent 路由、LangGraph | Python | AI 生态和快速迭代 |
| pgvector 召回、RRF、Rerank、引用、证据门禁 | Python | 与模型运行时保持内聚 |
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
  -> PostgreSQL Chunk + Embedding + pgvector
  -> Java ml-ai-search -> Elasticsearch chunk 索引
```

`knowledge_source.es_indexed_version` 记录 ES 派生索引进度。只有 PG chunk 和 ES 都完成后来源才恢复为
`ACTIVE`；ES 写入失败时同版本事件仍可重试。删除事件同时清理 PG chunk 和 ES 文档，并使用版本
tombstone 防止旧事件复活数据。

## 混合检索

```text
用户问题 -> 查询改写 -> 并行召回
                     |-> PostgreSQL/pgvector cosine search
                     |-> Java /internal/ai/knowledge/search/keyword -> ES BM25
          -> RRF（只融合排名）
          -> PostgreSQL 回源和来源版本校验
          -> Rerank
          -> 引用和证据门禁 -> 回答 / 降级 / 拒答
```

PostgreSQL 是知识正文和向量的事实源，Elasticsearch 是可重建的关键词索引。ES 命中不能直接进入
Prompt，必须按 `chunkId` 回 PostgreSQL 校验活动状态、来源身份和 `contentVersion`。
