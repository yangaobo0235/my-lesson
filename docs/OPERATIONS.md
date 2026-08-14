# 运行与回滚手册

## 运行前检查

1. 使用 JDK 17、Python 3.12 和 Node.js 20 以上版本。
2. 从 `.env.example` 创建本地 `.env`，只在进程环境中加载凭据，不提交真实值。
3. 确认 MySQL、PostgreSQL/pgvector、Redis、RocketMQ、Nacos 和 Elasticsearch 可达。
4. 执行 Java Flyway 与 Python Alembic 迁移。禁止修改已经发布的迁移文件校验和。

## 验证命令

```powershell
Set-Location backend-java
mvn test

Set-Location ..\agent-python
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m ruff check .
.\.venv\Scripts\python.exe -m mypy src

Set-Location ..\frontend\ml-web
npm test
npm run build

Set-Location ..\..\contracts
mvn -B -f client-generation-pom.xml generate-sources
Set-Location ..
.\agent-python\.venv\Scripts\python.exe scripts\verify_generated_contracts.py
```

外部库存和订单幂等回归需要显式设置 `RUN_EXTERNAL_INTEGRATION_TESTS=true`。测试使用随机业务键并清理自身数据，不使用生产流量或生产指标。

## 启动顺序

1. 基础设施与 Nacos。
2. Java 业务服务、Gateway 和 Agent Relay。
3. Python Alembic 迁移、Agent API 与 Worker。
4. Vue 前端。
5. 检查 Java Actuator、Python `/health/ready` 和关键依赖连接。

## 故障处理

- Broker 超时：结果按 UNKNOWN 处理，只允许原 `requestId` 查询或重放，不释放资格后换 ID 重试。
- 消费者异常：事务回滚订单与处理标记，由 RocketMQ 重新投递；相同请求由唯一键幂等拦截。
- Redis 异常：秒杀入口失败关闭，不切换请求身份，不回退到非原子数据库扣减。
- 秒杀对账：定时任务只使用原 `requestId` 重投消息，并校验初始库存、已保留数和可用库存不变量。
- ES 旧版本：外部版本和来源版本校验拒绝旧块覆盖或回源。
- Agent 工具超时：只读工具有限重试；写工具查询原请求结果，不盲目重放。

## 回滚

- 应用：切换到上一不可变制品，保留当前数据库和消息，不清理幂等记录。
- 数据库：Flyway/Alembic 采用向前修复迁移。不得执行 Flyway `repair`、删除历史记录或修改已发布迁移。
- 消息：暂停消费者后修复依赖，再恢复消费；保留原 `requestId`、消费记录和死信证据。
- 检索：停止新代索引写入，切回已验证代次；PostgreSQL 业务事实不随 ES 回滚。
- 严重数据问题：从变更前备份恢复到新数据库，验证后切换连接，不覆盖原库。
