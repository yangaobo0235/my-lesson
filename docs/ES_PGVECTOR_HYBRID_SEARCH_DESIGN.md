# Elasticsearch BM25 + pgvector 混合检索改造设计

## 1. 文档信息

- 状态：已实现并完成本地 Java/Python + 虚拟机 PostgreSQL/Elasticsearch 联调
- 适用项目：`my-lesson`
- 目标：融合 Elasticsearch BM25 关键词检索与 PostgreSQL/pgvector 向量检索。
- 当前开发方式：Java 和 Python 代码在本地运行。
- 当前基础设施：PostgreSQL/pgvector、Elasticsearch、Redis、RocketMQ 等组件运行在虚拟机 `192.168.23.66` 的 Docker 中。

## 2. 方案结论

采用“Java Elasticsearch 访问层 + Python RAG 编排层”的跨服务方案：

```text
用户问题
   |
   v
Python Agent
   |-- 查询改写、Embedding
   |-- pgvector 向量召回
   |-- 调用 Java AI Search 的 BM25 接口
   |-- RRF 融合、PostgreSQL 回源、Rerank
   |-- 引用校验、证据门禁、回答或拒答
   |
   +---- Java ml-ai-search ---- Elasticsearch
```

核心原则：

1. PostgreSQL 是知识内容、chunk 和向量的事实源。
2. Elasticsearch 是由 PostgreSQL 知识数据派生出的关键词检索索引。
3. Java 统一封装 Elasticsearch 的连接、索引、分词、批量写入和查询。
4. Python 只负责 RAG 流程，不直接依赖 Elasticsearch SDK。
5. 最终送入模型的内容从 PostgreSQL 回源并校验版本，避免过期 ES 文档成为回答证据。
6. Elasticsearch 不参与业务事务，不直接写 MySQL，也不生成最终答案。

## 3. 当前代码和问题

### 3.1 改造后的 RAG 检索

当前实现位于 `agent-python/src/mylesson_agent/rag/service.py`：

- 向量检索：`knowledge_chunk.embedding` 使用 pgvector cosine distance。
- 关键词检索：Python 调用 Java `ml-ai-search`，由 Elasticsearch BM25 召回。
- pgvector 与 ES 两路通过 `asyncio.gather` 并行执行。
- 两路结果只按 rank 调用 `reciprocal_rank_fusion`，不比较 BM25 与向量原始分数。
- RRF 结果从 PostgreSQL 回源并校验版本，之后送入 Rerank 和证据门禁。

当前检索链路已经具备可复用的 RRF 和 Rerank 抽象，主要变化是替换关键词后端。

### 3.2 当前知识入库

当前实现位于 `agent-python/src/mylesson_agent/knowledge/service.py`：

1. 获取课程、文章或公告正文。
2. 文本切片。
3. 调用 Embedding 模型。
4. 删除该 source 的旧 chunk。
5. 写入新的 PostgreSQL chunk 和向量。
6. 将 `knowledge_source.status` 设置为 `ACTIVE`。

事件入口位于 `agent-python/src/mylesson_agent/knowledge/events.py`，链路为：

```text
Java 业务事务
  -> ai_outbox_event
  -> RocketMQ
  -> ml-agent-relay
  -> Python /internal/v1/knowledge/events
  -> Python 拉取 Java 只读知识接口
  -> PostgreSQL chunk + embedding
```

### 3.3 当前 Elasticsearch

Java 已经使用 Spring Data Elasticsearch 维护 `ml-course` 等业务索引。现有课程索引是课程级文档，不能直接作为 RAG chunk 索引，原因是：

- 不包含文章和公告。
- 不具备 chunk 级引用信息。
- `contentVersion`、`chunkIndex`、`sourceUrl` 等 RAG 字段不完整。
- 业务课程搜索和知识问答的生命周期、权限和质量要求不同。

因此新增独立的 `ml-ai-search` Java 模块，不修改现有课程业务搜索语义。

### 3.4 当前实现需要修正的风险

当前 RRF 使用同一个 `minimum_relevant_score` 判断两类原始分数。向量相似度通常在 `0~1`，ES BM25 分数没有相同的固定范围，不能直接使用同一个原始分数阈值。

另外，当前事件消费发现 PostgreSQL 已经存在较新版本时会直接 `SKIPPED`。改成 PG + ES 双写后，如果 PG 已成功但 ES 失败，重试不能因为 PG 版本存在而跳过，否则 ES 会永久缺索引。

## 4. 部署拓扑和网络边界

### 4.1 当前开发拓扑

```text
Windows 本地
  - Java 微服务
  - Python Agent
  - 前端或 Gateway
       |
       | TCP，经虚拟机暴露端口访问
       v
虚拟机 192.168.23.66
  - PostgreSQL/pgvector: 5432
  - Elasticsearch 可用节点: 9200
  - Redis: 6379
  - RocketMQ NameServer: 9876
  - RocketMQ Broker: 10911
  - 其他基础设施
```

本地进程访问虚拟机组件时使用虚拟机 IP，不使用 Docker 容器名。例如：

```env
AGENT_DATABASE_URL=postgresql+asyncpg://mylesson_agent:<password>@192.168.23.66:5432/mylesson_agent
REDIS_URL=redis://192.168.23.66:6379/1
ELASTICSEARCH_URIS=http://192.168.23.66:9200
```

宿主机端口必须允许来自本地开发机的访问，并在虚拟机防火墙中限制来源 IP。Elasticsearch 不应直接暴露到公网。

### 4.2 容器化后的拓扑

如果 Java 和 Python 后续也部署到同一个 Docker network，应改用内部 DNS：

```text
http://elasticsearch:9200
http://ml-ai-search:24111
postgresql://...@pgvector:5432/...
```

不要在同一 Docker network 内依赖 `192.168.23.66:9200` 这类宿主机映射地址。最终配置由环境变量区分本地运行和容器运行。

## 5. 服务职责

### 5.1 Python Agent

- 查询改写。
- 生成 query embedding。
- 查询 PostgreSQL/pgvector。
- 调用 Java BM25 搜索接口。
- 以 `chunkId` 合并、去重和 RRF。
- 从 PostgreSQL 回源正文和引用元数据。
- 调用 Rerank 模型。
- 执行证据质量门禁。
- 生成带来源引用的回答，或主动拒答。
- 保存完整检索轨迹。

### 5.2 Java `ml-ai-search`

- 管理 Elasticsearch 连接和连接池。
- 创建索引、别名和 mapping。
- 提供 BM25 关键词搜索。
- 提供 chunk 批量 upsert 和 source 删除。
- 处理 `contentVersion`、幂等和旧版本保护。
- 提供健康检查、批量失败明细和指标。
- 校验 Python 服务令牌。

### 5.3 PostgreSQL/pgvector

- 保存 `knowledge_source`、`knowledge_chunk` 和 embedding。
- 作为回答内容和引用的最终事实源。
- 提供向量召回和回源校验。
- 保存事件状态、检索轨迹和评测结果。

## 6. Elasticsearch 索引设计

### 6.1 索引和别名

使用版本化索引和稳定别名：

```text
索引：mylesson-knowledge-chunk-v1
别名：mylesson-knowledge-chunk
```

重建索引时创建 `v2`，完成全量导入并验证后，将别名原子切换到 `v2`，不直接修改线上索引 mapping。

### 6.2 Mapping

建议 mapping：

```json
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "mylesson_ik_max": {
          "type": "custom",
          "tokenizer": "ik_max_word"
        },
        "mylesson_ik_smart": {
          "type": "custom",
          "tokenizer": "ik_smart"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "chunk_id": {"type": "keyword"},
      "source_type": {"type": "keyword"},
      "source_id": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "mylesson_ik_max",
        "search_analyzer": "mylesson_ik_smart",
        "fields": {"raw": {"type": "keyword"}}
      },
      "content": {
        "type": "text",
        "analyzer": "mylesson_ik_max",
        "search_analyzer": "mylesson_ik_smart"
      },
      "source_url": {"type": "keyword"},
      "content_version": {"type": "long"},
      "chunk_index": {"type": "integer"},
      "content_hash": {"type": "keyword"},
      "status": {"type": "keyword"}
    }
  }
}
```

如果虚拟机 ES 没有 IK 插件，先执行插件检查；没有插件时使用 `standard` analyzer，不能在创建索引后再修改 analyzer。

## 7. 内部 API 契约

所有 API 仅允许内部网络访问，并使用：

```http
X-Internal-Token: <AI_INTERNAL_TOKEN>
X-Trace-Id: <trace-id>
X-Request-Id: <request-id>
```

### 7.1 BM25 搜索

```http
POST /internal/ai/knowledge/search/keyword
Content-Type: application/json
```

请求：

```json
{
  "query": "Java 泛型的使用场景",
  "topK": 20,
  "sourceTypes": ["COURSE", "ARTICLE", "NOTICE"]
}
```

Java 内部使用 `multi_match`，字段权重建议：

```text
title^3
content
```

响应：

```json
{
  "index": "mylesson-knowledge-chunk",
  "tookMs": 8,
  "hits": [
    {
      "chunkId": "7d3...",
      "rank": 1,
      "score": 8.41,
      "sourceType": "COURSE",
      "sourceId": "12",
      "contentVersion": 3
    }
  ],
  "partial": false
}
```

### 7.2 Chunk 批量 upsert

```http
PUT /internal/ai/knowledge/chunks
```

请求：

```json
{
  "eventId": "uuid",
  "sourceType": "COURSE",
  "sourceId": "12",
  "contentVersion": 3,
  "chunks": [
    {
      "chunkId": "7d3...",
      "chunkIndex": 0,
      "title": "Java 泛型",
      "content": "...",
      "sourceUrl": "mylesson://course/12",
      "contentHash": "sha256..."
    }
  ]
}
```

要求：

- 同一个 `chunkId + contentVersion` 重复请求必须幂等。
- 低于 ES 当前版本的请求返回 `SKIPPED_OLD_VERSION`。
- 部分失败时返回每个 chunk 的失败原因。
- 批量接口必须使用 ES Bulk API，不能逐条 HTTP 写入。

### 7.3 Source 删除

```http
DELETE /internal/ai/knowledge/sources/{sourceType}/{sourceId}?contentVersion=4
```

删除必须根据版本判断，旧版本删除事件不能删除新版本文档。

## 8. Python 检索实现

### 8.1 查询改写

在 `ModelClient` 增加可选的 `rewrite_query()`：

- 仅对知识问答路由启用。
- 返回一个简短检索 query，不扩展成答案。
- 模型失败或超时直接使用原问题。
- trace 同时保存原问题和改写结果。

示例：

```text
原问题：Java 泛型有什么使用场景？
改写问题：Java 泛型 使用场景 类型安全 集合 方法
```

### 8.2 两路召回

Python 生成 embedding 后执行：

```text
PostgreSQL vector search
Java keyword search -> Elasticsearch BM25
```

两路结果应使用 `asyncio.gather` 并行执行。Java API 使用持久化 `httpx.AsyncClient`，不能每次检索都创建新连接。

### 8.3 RRF

RRF 公式：

```text
RRF(d) = vectorWeight / (rrfK + vectorRank)
       + keywordWeight / (rrfK + keywordRank)
```

初始配置建议：

```env
RRF_K=60
RRF_VECTOR_WEIGHT=1.0
RRF_KEYWORD_WEIGHT=1.0
VECTOR_TOP_K=20
KEYWORD_TOP_K=20
RRF_TOP_K=20
ANSWER_TOP_N=6
```

不要用 BM25 原始分数和向量相似度进行直接比较。向量可以使用独立的 cosine 阈值，BM25 主要依靠 query、字段权重和召回排名过滤。

### 8.4 PostgreSQL 回源

RRF 后按 `chunkId` 从 PostgreSQL 查询：

```sql
SELECT kc.id, kc.title, kc.content, kc.metadata_json,
       ks.source_url, ks.source_type, ks.source_id,
       ks.content_version, ks.status
FROM knowledge_chunk kc
JOIN knowledge_source ks ON ks.id = kc.source_id
WHERE kc.id = ANY(:chunk_ids)
  AND ks.status = 'ACTIVE'
```

对于 ES 命中，必须校验：

- chunk 仍存在。
- source 状态为 `ACTIVE`。
- PostgreSQL 的 `content_version` 不低于 ES 返回的版本。
- 来源类型和来源 ID 与 ES 返回值一致。

校验失败的 ES 命中直接丢弃，不进入 Rerank 和 Prompt。

### 8.5 Rerank 和证据门禁

Rerank 输入使用回源后的正文，输出最终 top N。建议将 `RetrievalResult` 扩展为：

```python
class RetrievalResult:
    hits: list[RetrievalHit]
    reranked: bool
    rewritten_query: str
    backend_stats: dict[str, Any]
    decision: str  # ANSWERED / REFUSED / DEGRADED
```

证据不足的判断不能只看是否有 citation。至少检查：

- 回源后仍有有效 chunk。
- Rerank 分数达到阈值，或明确处于降级模式。
- 引用正文覆盖问题的关键内容。
- 没有出现版本不一致或检索后端失败。

## 9. 知识入库和双写一致性

### 9.1 状态机

建议将知识源处理状态细化为：

```text
RECEIVED
  -> INDEXING
  -> PG_READY
  -> ES_READY
  -> ACTIVE
```

删除流程：

```text
ACTIVE
  -> DELETING
  -> PG_DELETED
  -> ES_DELETED
  -> DELETED
```

### 9.2 数据库字段

新增 Alembic migration，例如 `0004_es_index_state.py`：

```sql
ALTER TABLE knowledge_source
    ADD COLUMN es_indexed_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN es_indexed_at TIMESTAMPTZ,
    ADD COLUMN es_index_error TEXT;
```

`ACTIVE` 的必要条件：

```text
PG chunk/embedding 已成功
AND es_indexed_version >= content_version
AND ES 删除或更新操作已成功确认
```

### 9.3 Upsert 顺序

推荐顺序：

1. Python 获取 Java 知识详情并切片。
2. 生成 embedding。
3. PostgreSQL 写入新 chunk，source 暂时保持 `INDEXING`。
4. Python 调用 Java `chunks` 批量接口写 ES。
5. Java 返回 Bulk 成功结果。
6. Python 更新 `es_indexed_version`，将 source 设置为 `ACTIVE`。
7. 提交事件为 `PROCESSED`。

如果步骤 4 失败：

- source 保持 `INDEXING` 或 `FAILED`。
- 记录错误。
- 事件返回失败，等待 RocketMQ 重试。
- 重试时不能因为 PostgreSQL 版本存在而 `SKIPPED`。

`KnowledgeIndexer.upsert()` 当前内部直接 `commit()`，需要改为由事件消费者控制事务边界，否则难以准确表示 PG 和 ES 的联合状态。

### 9.4 内容 hash 相同的修复逻辑

当前代码在 content hash 相同时直接返回。改造后应改成：

```text
hash 相同并且 es_indexed_version >= content_version
  -> 直接返回

hash 相同但 ES 未达到版本
  -> 重新生成 ES 文档并修复索引状态
```

### 9.5 删除和旧版本

- ES 文档必须保存 `content_version`。
- Java 删除接口必须拒绝低版本删除请求。
- 同一个 source 的新版本写入不能被旧版本覆盖。
- 删除时优先使用确定性的 chunk ID 或 source 条件批量删除。
- 旧版本 ES 文档不能因为事件乱序重新变为 `ACTIVE`。

## 10. Java `ml-ai-search` 模块设计

### 10.1 模块结构

建议结构：

```text
backend-java/ml-ai-search
  src/main/java/com/yangaobo/AiSearchApplication.java
  src/main/java/com/yangaobo/controller/KnowledgeSearchController.java
  src/main/java/com/yangaobo/service/KnowledgeSearchService.java
  src/main/java/com/yangaobo/service/KnowledgeIndexService.java
  src/main/java/com/yangaobo/es/KnowledgeChunkDocument.java
  src/main/java/com/yangaobo/dto/KeywordSearchRequest.java
  src/main/java/com/yangaobo/dto/KeywordSearchResponse.java
  src/main/resources/bootstrap.yml
```

可以复用父工程已经存在的 Spring Data Elasticsearch 依赖，但要确认 Java 客户端版本和虚拟机 ES 版本兼容。

### 10.2 Java 服务职责边界

Java 服务只返回 BM25 候选，不执行：

- query embedding
- RRF
- Rerank
- LLM 回答
- 引用最终判定

这样能够保持 Python Agent 对整个 RAG 流程的控制力。

### 10.3 ES 连接配置

本地 Java 进程使用虚拟机地址：

```env
ELASTICSEARCH_URIS=http://192.168.23.66:9200
```

正式容器部署时使用 Docker network 地址。不要把用户名、密码或 API Key 写入代码和 Git 文件。

### 10.4 健康检查和指标

Java 服务至少提供：

```text
GET /actuator/health
GET /actuator/prometheus
```

指标建议：

```text
ai_search_request_total{operation,status}
ai_search_latency_seconds{operation}
ai_search_bulk_failure_total
ai_search_index_version_lag
ai_search_partial_response_total
```

## 11. Python 代码改造清单

| 文件 | 改造内容 |
| --- | --- |
| `agent-python/pyproject.toml` | 新增 Java Search HTTP 客户端所需依赖或复用现有 `httpx` |
| `agent-python/src/mylesson_agent/config.py` | 新增 `search_service_url`、超时、topK、RRF 和版本校验配置 |
| `agent-python/src/mylesson_agent/container.py` | 创建并关闭 `JavaKeywordSearchClient` |
| `agent-python/src/mylesson_agent/rag/service.py` | 接入 Java BM25，扩展候选和 trace 模型 |
| `agent-python/src/mylesson_agent/knowledge/service.py` | PG chunk 写入后调用 Java 批量索引，修复事务边界 |
| `agent-python/src/mylesson_agent/knowledge/events.py` | 增加 ES 版本检查、失败重试和删除重试 |
| `agent-python/src/mylesson_agent/infrastructure/orm.py` | 增加 ES 索引状态字段 |
| `agent-python/migrations/versions/0004_es_index_state.py` | 数据库迁移 |
| `agent-python/scripts/backfill_elasticsearch.py` | 将已有 PG chunk 幂等回填到 ES，并记录逐来源索引状态 |
| `agent-python/src/mylesson_agent/conversation/service.py` | 保存两路召回、RRF、Rerank 和拒答决策轨迹 |
| `agent-python/src/mylesson_agent/agents/runtime.py` | 使用证据门禁决定回答或拒答 |
| `agent-python/scripts/run_quality_evaluation.py` | 初始化 Java Search 依赖并增加 hybrid 评测模式 |
| `agent-python/tests/test_rag.py` | 更新 RRF 分数语义并增加 ES 失败降级测试 |
| `deploy/env/agent.env.example` | 增加本地和容器两套 Search Service 配置示例 |
| `deploy/docker-compose.apps.yml` | 后续容器化时加入 `ml-ai-search` 服务 |

## 12. 测试方案

### 12.1 单元测试

- RRF 对相同 `chunkId` 去重。
- BM25 分数和向量分数不直接比较。
- ES 结果缺失 PG chunk 时被丢弃。
- PG 版本高于 ES 版本时拒绝使用旧 ES 命中。
- Rerank 失败时正确进入降级状态。
- Java 搜索接口超时后返回可控错误。
- 同一个 `eventId` 重复消费不重复写入。
- 旧版本事件不能覆盖新版本。
- ES 写入失败后相同版本事件可以重试。
- 删除事件失败后可以重试。

### 12.2 集成测试

至少准备：

1. PostgreSQL/pgvector 可用。
2. Elasticsearch mapping 和 alias 可用。
3. Java BM25 接口可查询。
4. Python 可以完成 PG + Java ES 两路召回。
5. RRF、回源、Rerank、引用和拒答完整跑通。

### 12.3 质量评测

对现有冻结评测集分别测试：

```text
vector-only
BM25-only
vector + BM25 + RRF
vector + BM25 + RRF + Rerank
```

重点比较：

- 来源召回率。
- 内容命中率。
- 引用命中率。
- 无答案准确率。
- Rerank 应用率。
- Java 搜索接口 P95 延迟。
- Python Agent 总体 P95 延迟。

## 13. 上线步骤

### 阶段 0：环境确认

在虚拟机执行：

```bash
curl http://192.168.23.66:9200
curl http://192.168.23.66:9200/_cluster/health
curl http://192.168.23.66:9200/_cat/nodes?v
```

确认：

- `9200` 为 ES 8.4.0 单节点集群；当前从本地访问 `9201`、`9202` 会被拒绝，未确认可用前不得加入客户端节点列表。
- 当前集群健康状态为 `yellow`，新知识索引使用 `number_of_replicas=0`，避免单节点环境产生新的未分配副本。
- ES 版本与 Java 客户端兼容。
- 是否安装 IK 插件。
- PostgreSQL 是否已启用 `vector` 扩展。
- 本地 Java/Python 到虚拟机端口可达。

### 阶段 1：创建索引（已完成）

- 创建 `mylesson-knowledge-chunk-v1`。
- 创建 alias `mylesson-knowledge-chunk`。
- 用少量课程、文章、公告做索引验证。
- 验证中文关键词、来源过滤和版本字段。

### 阶段 2：实现 Java Search API（已完成）

- 完成 BM25 查询。
- 完成 Bulk upsert、source delete。
- 增加内部令牌校验。
- 增加健康检查和请求日志。

### 阶段 3：Python 接入与验证（已完成）

Python 已接入 Java BM25，并通过单元测试与真实接口验证两路候选、降级和版本校验。

重点观察：

- ES 是否漏召回中文关键词。
- 版本是否一致。
- Java 接口超时和错误率。
- ES 命中但 PG 回源失败的比例。

### 阶段 4：切换混合检索（已完成）

启用：

```text
pgvector + ES BM25 + RRF + Rerank
```

当前回答链路使用 pgvector + ES BM25；ES 失败时降级为 pgvector，证据仍不足则拒答。

### 阶段 5：全量回填和校验（已完成一次开发环境验证）

- 全量重建 ES chunk 索引。
- 校验 PG chunk 数量与 ES 文档数量。
- 校验每个 source 的 `contentVersion`。

开发环境执行 `0004` 后已回填 36 个活动来源/36 个 chunk，PG 版本滞后和索引错误均为 0，
ES alias 下活动文档数为 36。正式环境仍应在部署窗口重复执行迁移、回填和数量校验。

旧数据库升级还需执行 `0005`，删除旧 PostgreSQL 关键词索引和扩展。若扩展 owner 为
`postgres`，必须先由虚拟机管理员按 `agent-python/README.md` 中的命令删除，再运行 Alembic；
应用账号无权绕过该数据库所有权限制。

## 14. 风险和处理

| 风险 | 处理方式 |
| --- | --- |
| Java Search 服务不可用 | 短超时、有限重试、降级到 pgvector；证据不足则拒答 |
| ES 文档过期 | ES 返回版本，Python 必须回 PG 校验 |
| ES 写入成功但 PG 提交失败 | 使用确定性 chunk ID，重复写入幂等 |
| PG 成功但 ES 失败 | 保存 `es_indexed_version`，事件不能被错误跳过 |
| BM25 分数与向量分数尺度不同 | RRF 只使用 rank，不混合原始 score |
| 旧索引文档残留 | source 级删除、版本检查、全量重建 alias |
| 本地访问虚拟机失败 | 检查虚拟机防火墙、Docker 端口映射和路由 |
| Java/Python 版本配置不一致 | 将 ES URL、索引名和超时统一写入环境配置 |

## 15. 验收标准

改造完成后应满足：

1. Python 能同时拿到 pgvector 和 ES BM25 两路候选。
2. RRF 以 `chunkId` 去重并正确融合。
3. ES 命中内容必须经过 PostgreSQL 回源和版本校验。
4. Rerank 失败时系统有明确降级状态。
5. 证据不足时不会调用模型生成无依据回答。
6. 来源引用包含标题、来源 URL、source type 和 source ID。
7. 知识更新、删除、重复、乱序事件都能保持 PG/ES 最终一致。
8. 本地 Java/Python 可通过 `192.168.23.66` 访问虚拟机组件。
9. 后续容器化后可切换为 Docker service name，无需改业务代码。
10. 现有 Java 课程业务搜索不受影响。
