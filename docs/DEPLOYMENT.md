# 部署说明

## 现有基础设施

应用编排不重复创建基础设施，直接连接现有 Euler Docker 主机中的 MySQL、PostgreSQL/pgvector、Redis、RocketMQ、Nacos、MinIO、Elasticsearch、OTel、Tempo、Prometheus、Grafana 和 Langfuse。

## 首次准备

1. 在 pgvector PostgreSQL 创建独立数据库 `mylesson_agent` 和最小权限账号。
2. 从模板创建 `deploy/env/agent.env` 与 `deploy/env/java.env`，填入真实凭据。
3. 确保 `AI_IDENTITY_SECRET` 与 `AI_DELEGATION_SECRET` 使用同一个 32 字节以上随机值。
4. 确保 Java 与 Python 使用同一个独立 `AI_INTERNAL_TOKEN`。
5. 执行 `python -m alembic upgrade head`。
6. 更新 Nacos Gateway 路由，使 `/api/v1/ai/**` 指向 Python 24109。
7. 启动应用服务，再检查 Python `/health/ready` 和 Relay `/actuator/health`。

## 网络

Euler 防火墙只需向应用主机开放实际使用的基础设施端口。PostgreSQL、Redis、RocketMQ、Nacos 和观测组件不应直接暴露到不可信网络。Java 内部工具和 Python `/internal/**` 只允许应用网络访问。

Nginx 使用 Docker 内置 DNS 在请求时解析 `ml-gateway`。因此 Web 容器可以先于 Gateway 启动；Gateway 未就绪时 API 请求返回 502，服务恢复后不需要重启 Nginx。

## 运行资源

- Java 应用默认使用 `-Xms64m -Xmx256m`，可通过 `JAVA_OPTS` 调整。
- Windows 源码运行和容器环境都设置 `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8`，
  避免 Nacos YAML 中的中文内容受平台默认编码影响。
- 同机运行 ClickHouse、Elasticsearch、Langfuse、SQL Server 和全部 MyLesson 服务时，建议至少 12 GiB 内存；8 GiB 主机不应在业务容器运行期间执行完整 Docker 镜像构建。
- 生产构建应在 CI 或独立构建机完成，再以不可变镜像或离线制品部署到 Euler。
- 启动 Java 服务时应串行执行并等待健康检查，避免同时类加载和迁移造成内存峰值。

## Agent 可靠性

- API 只将 run 写入 PostgreSQL 队列；`ml-agent-worker` 使用 `FOR UPDATE SKIP LOCKED` 独立领取任务。
- worker 通过租约心跳标记运行状态，异常退出后由 stale recovery 将超时任务重新排队。
- 同一会话在数据库层串行执行，同一个 `requestId` 返回同一个 run，不重复写入用户消息。
- SSE 事件先持久化到 PostgreSQL，再发布到 Redis。客户端通过 `Last-Event-ID` 重连时从数据库回放缺失事件。
- 不得把 API 与 worker 合并成同一进程，否则扩缩容和故障恢复语义会退化。

## 可观测性

- Python `/metrics` 供 Prometheus 抓取。
- Python 使用 `OTEL_EXPORTER_OTLP_ENDPOINT` 上报链路。
- 模型与 Agent 追踪使用 Langfuse 环境变量。
- Java Relay 通过 Actuator 暴露健康状态。
- `X-Trace-Id` 在 Python 和 Java 工具调用间传递。

## 回滚

应用镜像应使用不可变版本标签。回滚只切换应用镜像；Alembic/Flyway 迁移必须遵守向后兼容的扩展-收缩策略。知识索引可从 Java 业务数据与 Outbox 事件重建，不作为业务事实来源。
