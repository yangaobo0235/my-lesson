# 改造验收记录

## 已验证

- Python：61 个测试通过，Ruff 与 Mypy（43 个源文件）通过。
- Java：全 Maven Reactor 通过；外部 Redis 5 组 8314 次请求无超卖，外部 MySQL 订单消费并发唯一约束通过。
- 前端：2 个 Vitest 测试和 Vite 生产构建通过。项目当前没有 `typecheck` 脚本。
- M19 Agent：312 条版本化数据，报告包含数据集 Hash、意图混淆矩阵、失败样例和质量门禁。
- M19 RAG：288 条版本化数据，报告包含数据集与语料 Hash、Recall@6、MRR@6、NDCG@6、完整来源命中率、P95、失败样例和质量门禁。
- OpenAPI：固定 OpenAPI Generator 7.15.0，Java/Python 强类型客户端生成、Java 客户端编译和双端字段/409/鉴权头回归通过。
- 秒杀对账：库存、资格、请求账本和订单使用原 `requestId` 自动对账；库存满足 `initial - reserved = available` 不变量。
- Citation：编号、版本和引用存在性校验后，在线 Judge 再验证结论是否被原文直接支持，失败时拒答。

## 故障矩阵

| 场景 | 可重复验收结果 |
| --- | --- |
| Broker 超时 | 返回结果不确定，稳定 `requestId` 不变 |
| 消费者中断 | PostgreSQL 事务回滚处理标记，可重新投递 |
| Redis 故障 | 原子预占异常直接失败，不换请求 ID 重试 |
| 重复确认 | 相同操作请求返回已有正式计划 |
| 课程变化 | 确认时重新检查课程存在且未删除 |
| JWT 吊销 | Java 校验 Redis 中用户停用和 JTI 吊销标记 |
| 提示注入 | 常见越权措辞在工具选择前进入范围拒绝 |

## 外部环境边界

- Euler 的 MySQL、Redis 和 RocketMQ 端口可达；Redis 5 组 8,314 次库存并发、MySQL 消费幂等外测通过。
- 本轮未启动独立 RocketMQ 生产者/消费者进程做强制终止测试；代码层 Broker 超时、消费者事务回滚和原请求对账回归已通过。
- 312 条路由集当前门禁通过，但仍有失败样例；门禁通过不代表所有案例正确。
