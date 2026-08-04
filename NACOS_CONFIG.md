# Nacos 配置说明

Java 业务服务和 Gateway 使用 Nacos 配置中心/服务发现。Python Agent 使用容器环境变量运行，不在 Nacos 中保存模型密钥或数据库密码。

## 基本信息

| 项目 | 值 |
| --- | --- |
| Namespace | `public` 或独立环境 Namespace |
| Group | `ml-group` |
| Format | `YAML` |
| Profile | `dev` / `prod` |

## Data ID

| Data ID | 用途 |
| --- | --- |
| `common-config.yaml` | Java 公共 MySQL、Redis、Nacos、追踪配置 |
| `ml-gateway-dev.yaml` | Gateway 路由、白名单和委托 JWT 密钥引用 |
| `ml-user-dev.yaml` | 用户服务 |
| `ml-course-dev.yaml` | 课程、学习计划、Outbox |
| `ml-sale-dev.yaml` | 营销内容、Outbox |
| `ml-order-dev.yaml` | 订单与 RocketMQ |

AI 路由由 `agent-python` 提供，其运行配置通过 `deploy/env/agent.env.example` 注入，不在 Nacos 中保存模型密钥或数据库密码。

## Gateway 配置

在 `ml-gateway-dev.yaml` 的 `spring.cloud.gateway.routes` 中声明以下 Python Agent 路由：

```yaml
server:
  port: 24101

spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
      routes:
        - id: user-route
          uri: lb://ml-user
          predicates: [Path=/user-server/**]
          filters: [StripPrefix=1]
        - id: course-route
          uri: lb://ml-course
          predicates: [Path=/course-server/**]
          filters: [StripPrefix=1]
        - id: sale-route
          uri: lb://ml-sale
          predicates: [Path=/sale-server/**]
          filters: [StripPrefix=1]
        - id: order-route
          uri: lb://ml-order
          predicates: [Path=/order-server/**]
          filters: [StripPrefix=1]
        - id: python-agent-route
          uri: ${AI_AGENT_URI:http://ml-agent-python:24109}
          order: 0
          predicates:
            - Path=/api/v1/ai/**
          metadata:
            connect-timeout: 3000
            response-timeout: -1

token:
  white_list: "banner/top,article/top,notice/top,seckill/near,user/getVcode,user/loginByAccount,user/loginByPhone,user/insert,course/page,course/search,course/select,order/prePayNotify"

ai:
  identity-secret: ${AI_IDENTITY_SECRET}
```

`response-timeout: -1` 用于 SSE 长连接；普通模型与工具调用超时由 Python 自身控制。

## Java 公共配置

真实凭据全部使用环境变量注入：

```yaml
spring:
  datasource:
    username: ${MYSQL_USERNAME}
    password: ${MYSQL_PASSWORD}
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
  flyway:
    enabled: true
    baseline-on-migrate: true
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}

ai:
  identity-secret: ${AI_IDENTITY_SECRET}
  internal-token: ${AI_INTERNAL_TOKEN}
```

`AI_IDENTITY_SECRET` 必须与 Python 的 `AI_DELEGATION_SECRET` 相同；`AI_INTERNAL_TOKEN` 必须在 Java 和 Python 中一致。这两个值用途不同，不应复用同一个随机值。

## Course/Sale Outbox

`ml-course-dev.yaml` 和 `ml-sale-dev.yaml`：

```yaml
rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}
  producer:
    group: ${ROCKETMQ_PRODUCER_GROUP:${spring.application.name}-producer}

ai:
  internal-token: ${AI_INTERNAL_TOKEN}
  identity-secret: ${AI_IDENTITY_SECRET}
  knowledge-sync:
    outbox-enabled: true
    topic: ${AI_KNOWLEDGE_TOPIC:ml-ai-knowledge-events}
    outbox-batch-size: 100
    outbox-poll-interval: 3000
```

## Agent Relay

`ml-agent-relay` 默认从应用环境变量读取配置，不要求注册 Nacos：

```dotenv
ROCKETMQ_NAME_SERVER=infra.example.com:9876
AI_KNOWLEDGE_TOPIC=ml-ai-knowledge-events
AI_KNOWLEDGE_CONSUMER_GROUP=ml-agent-python-relay
AI_AGENT_BASE_URL=http://ml-agent-python:24109
AI_INTERNAL_TOKEN=change-me
```

消费失败会抛出异常并由 RocketMQ 重试。Python 的 `knowledge_event` 表负责 `eventId` 幂等、版本排序、处理状态和错误记录。

## 必填环境变量

```dotenv
NACOS_SERVER_ADDR=infra.example.com:8848
MYSQL_USERNAME=change-me
MYSQL_PASSWORD=change-me
REDIS_HOST=infra.example.com
REDIS_PORT=6379
ROCKETMQ_NAME_SERVER=infra.example.com:9876
AI_IDENTITY_SECRET=generate-at-least-32-random-bytes
AI_INTERNAL_TOKEN=generate-an-independent-service-token
AI_AGENT_URI=http://ml-agent-python:24109
```

源码方式在本机运行时，将 `AI_AGENT_URI` 设置为
`http://127.0.0.1:24109`；Compose 运行时使用服务名
`http://ml-agent-python:24109`。

不要把真实密码、API Key、支付私钥或令牌写入 Nacos 导出文件、Git、镜像层或 Compose YAML。
