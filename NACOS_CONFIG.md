# Nacos 配置说明

MyLesson 后端服务使用 Nacos 作为配置中心和服务发现中心。启动服务前，需要在 Nacos 中创建配置分组并导入对应 Data ID。

本文档提供可提交到 Git 的配置模板。真实密码、AccessKey、API Key、支付宝私钥等敏感信息不要写死在 YAML 中，应通过环境变量、启动参数或容器编排注入。

## 基本信息

| 项目 | 值 |
| --- | --- |
| Namespace | `public` 或自定义命名空间 |
| Group | `ml-group` |
| Format | `YAML` |
| Profile | `dev` |

## 配置清单

| Data ID | 用途 |
| --- | --- |
| `common-config.yaml` | 公共 MySQL、Redis、MinIO、短信、链路追踪、SpringDoc、Sentinel 配置 |
| `ml-gateway-dev.yaml` | 网关路由、白名单、CORS、AI 身份签名配置 |
| `ml-user-dev.yaml` | 用户服务端口、数据源、XXL-JOB 配置 |
| `ml-course-dev.yaml` | 课程服务端口、数据源、Elasticsearch、RocketMQ、AI 知识同步配置 |
| `ml-sale-dev.yaml` | 营销服务端口、数据源、XXL-JOB、RocketMQ、AI 知识同步配置 |
| `ml-order-dev.yaml` | 订单服务端口、数据源、RocketMQ、支付宝沙箱配置 |
| `ml-ai-dev.yaml` | AI 服务端口、PostgreSQL、DashScope、pgvector、RAG、Agent、审批和知识同步配置 |

## 本地服务如何加载 Nacos

各微服务的 `bootstrap.yml` / `bootstrap.yaml` 会连接 Nacos：

```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        group: ml-group
```

其中 `ml-user`、`ml-course`、`ml-sale`、`ml-order` 会加载 `common-config.yaml` 作为共享配置，再加载自己的 `*-dev.yaml`。

`ml-ai` 当前通过 Spring Config Data 加载：

```yaml
spring:
  config:
    import:
      - optional:nacos:${spring.application.name}-${spring.profiles.active}.yaml?group=ml-group&refreshEnabled=true
```

## 必填环境变量

至少需要准备以下变量：

```dotenv
NACOS_SERVER_ADDR=127.0.0.1:8848

MYSQL_USERNAME=root
MYSQL_PASSWORD=your-mysql-password

USER_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/ml_ums?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8
COURSE_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/ml_cms?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8
SALE_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/ml_sms?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8
ORDER_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/ml_oms?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8

REDIS_HOST=127.0.0.1
REDIS_PORT=6379
MINIO_ENDPOINT=http://127.0.0.1:9001
MINIO_ACCESS_KEY=your-minio-access-key
MINIO_SECRET_KEY=your-minio-secret-key
ROCKETMQ_NAME_SERVER=127.0.0.1:9876
ELASTICSEARCH_URIS=http://127.0.0.1:9200

AI_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/mylesson_ai
AI_DATASOURCE_USERNAME=postgres
AI_DATASOURCE_PASSWORD=your-postgres-password
DASHSCOPE_API_KEY=your-dashscope-api-key
AI_IDENTITY_SECRET=your-random-secret
AI_INTERNAL_TOKEN=your-random-token

CORS_ALLOWED_ORIGINS=http://localhost:24108

SMS_PROVIDER=aliyun
ALIBABA_CLOUD_ACCESS_KEY_ID=your-access-key-id
ALIBABA_CLOUD_ACCESS_KEY_SECRET=your-access-key-secret
ALIYUN_SMS_SIGN_NAME=your-sign-name
ALIYUN_SMS_TEMPLATE_CODE=your-template-code

ALIPAY_APP_ID=your-sandbox-app-id
ALIPAY_PUBLIC_KEY=your-alipay-public-key
ALIPAY_MERCHANT_PRIVATE_KEY=your-merchant-private-key
ALIPAY_NOTIFY_URL=https://your-public-domain/order-server/api/v1/order/prePayNotify
ALIPAY_IGNORE_SSL=false

XXL_JOB_ADMIN_ADDRESSES=http://127.0.0.1:9527/xxl-job-admin
XXL_JOB_ACCESS_TOKEN=your-xxl-job-token
XXL_JOB_EXECUTOR_IP=127.0.0.1
```

## 配置模板

### common-config.yaml

```yaml
server:
  tomcat:
    threads:
      max: 200

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD}
    type: com.zaxxer.hikari.HikariDataSource
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      idle-timeout: 1800000

  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1

  servlet:
    multipart:
      max-file-size: ${UPLOAD_MAX_FILE_SIZE:20MB}
      max-request-size: ${UPLOAD_MAX_REQUEST_SIZE:100MB}

  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}

  sentinel:
    transport:
      dashboard: ${SENTINEL_DASHBOARD:127.0.0.1:8808}
      port: 8888

  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      timeout: 3000ms
      jedis:
        pool:
          max-active: 8
          max-wait: -1ms
          max-idle: 8
          min-idle: 0

management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:0.1}
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://127.0.0.1:9411/api/v2/spans}

mybatis-flex:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: ${MYBATIS_LOG_IMPL:org.apache.ibatis.logging.nologging.NoLoggingImpl}
  type-aliases-package: com.yangaobo.entity

feign:
  sentinel:
    enabled: true

springdoc:
  api-docs:
    enabled: true
  group-configs:
    - group: dev
      paths-to-match: /**
      packages-to-scan: com.yangaobo.controller
  author: "杨奥博"
  version: "v1.0.0"
  title: ${spring.application.name}
  url: "${SERVICE_PUBLIC_URL:http://127.0.0.1:${server.port}}"

knife4j:
  enable: true
  setting:
    language: zh_cn

logging:
  level:
    com.yangaobo.feign: debug
  pattern:
    level: ${spring.application.name:},%X{traceId:-},%X{spanId:-}

ai:
  internal-token: ${AI_INTERNAL_TOKEN}

minio:
  endpoint: ${MINIO_ENDPOINT:http://127.0.0.1:9001}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}

sms:
  provider: ${SMS_PROVIDER:aliyun}
  aliyun:
    endpoint: dypnsapi.aliyuncs.com
    access-key-id: ${ALIBABA_CLOUD_ACCESS_KEY_ID}
    access-key-secret: ${ALIBABA_CLOUD_ACCESS_KEY_SECRET}
    sign-name: ${ALIYUN_SMS_SIGN_NAME}
    template-code: ${ALIYUN_SMS_TEMPLATE_CODE}
```

### ml-gateway-dev.yaml

```yaml
server:
  port: 24101

spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
    gateway:
      discovery:
        locator:
          enabled: true
      routes:
        - id: user-route
          uri: lb://ml-user
          order: 1
          predicates:
            - Path=/user-server/**
          filters:
            - StripPrefix=1
        - id: course-route
          uri: lb://ml-course
          order: 1
          predicates:
            - Path=/course-server/**
          filters:
            - StripPrefix=1
        - id: sale-route
          uri: lb://ml-sale
          order: 1
          predicates:
            - Path=/sale-server/**
          filters:
            - StripPrefix=1
        - id: order-route
          uri: lb://ml-order
          order: 1
          predicates:
            - Path=/order-server/**
          filters:
            - StripPrefix=1
        - id: ai-route
          uri: lb://ml-ai
          order: 1
          predicates:
            - Path=/api/v1/ai/**
          metadata:
            connect-timeout: 3000
            response-timeout: -1

  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      timeout: 3000ms

management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:0.1}
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://127.0.0.1:9411/api/v2/spans}

logging:
  pattern:
    level: ${spring.application.name:},%X{traceId:-},%X{spanId:-}

token:
  white_list: "banner/top,article/top,notice/top,seckill/near,user/getVcode,user/loginByAccount,user/loginByPhone,user/insert,course/page,course/search,course/select,order/prePayNotify"

ai:
  identity-secret: ${AI_IDENTITY_SECRET}

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:24108}
```

### ml-user-dev.yaml

```yaml
server:
  port: 24102

spring:
  application:
    name: ml-user
  datasource:
    url: ${USER_DATASOURCE_URL:jdbc:mysql://127.0.0.1:3306/ml_ums?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8}

feign:
  client:
    config:
      ml-user:
        connectTimeout: 2000

xxl:
  job:
    admin:
      addresses: ${XXL_JOB_ADMIN_ADDRESSES:http://127.0.0.1:9527/xxl-job-admin}
    accessToken: ${XXL_JOB_ACCESS_TOKEN}
    executor:
      appName: ml-user-executor
      ip: ${XXL_JOB_EXECUTOR_IP}
      port: 6200

springdoc:
  description: "<strong>MyLesson - 用户微服务</strong>：包括登录、注册、用户管理、角色管理和菜单管理等功能模块。"
```

### ml-course-dev.yaml

```yaml
server:
  port: 24103

spring:
  application:
    name: ml-course
  datasource:
    url: ${COURSE_DATASOURCE_URL:jdbc:mysql://127.0.0.1:3306/ml_cms?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8}
  elasticsearch:
    uris: ${ELASTICSEARCH_URIS:http://127.0.0.1:9200}

feign:
  client:
    config:
      ml-course:
        connectTimeout: 2000
        readTimeout: 10000

rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}
  producer:
    group: ml-course-ai-outbox-producer
    send-message-timeout: 10000
    retry-times-when-send-failed: 2

springdoc:
  description: "<strong>MyLesson - 课程微服务</strong>：包括课程管理、季次管理、集次管理、评价管理和举报管理等功能模块。"

ai:
  knowledge-sync:
    outbox-enabled: true
    topic: ml-ai-knowledge-events
    outbox-batch-size: 100
    outbox-poll-interval: 3000
```

### ml-sale-dev.yaml

```yaml
server:
  port: 24104

spring:
  application:
    name: ml-sale
  datasource:
    url: ${SALE_DATASOURCE_URL:jdbc:mysql://127.0.0.1:3306/ml_sms?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8}

feign:
  client:
    config:
      ml-sale:
        connectTimeout: 2000
        readTimeout: 10000

xxl:
  job:
    admin:
      addresses: ${XXL_JOB_ADMIN_ADDRESSES:http://127.0.0.1:9527/xxl-job-admin}
    accessToken: ${XXL_JOB_ACCESS_TOKEN}
    executor:
      appName: ml-sale-executor
      ip: ${XXL_JOB_EXECUTOR_IP}
      port: 6400

rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}
  producer:
    group: ml-producer-group
    send-message-timeout: 10000
    retry-times-when-send-failed: 2

springdoc:
  description: "<strong>MyLesson - 营销微服务</strong>：包括广告管理、新闻管理、横幅管理、通知管理和秒杀活动管理等功能模块。"

ai:
  knowledge-sync:
    outbox-enabled: true
    topic: ml-ai-knowledge-events
    outbox-batch-size: 100
    outbox-poll-interval: 3000
```

### ml-order-dev.yaml

```yaml
server:
  port: 24105

spring:
  application:
    name: ml-order
  datasource:
    url: ${ORDER_DATASOURCE_URL:jdbc:mysql://127.0.0.1:3306/ml_oms?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8}

feign:
  client:
    config:
      ml-order:
        connectTimeout: 2000

rocketmq:
  name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}

springdoc:
  description: "<strong>MyLesson - 订单微服务</strong>：包括购物车管理、订单管理等功能模块。"

alipay:
  app-id: ${ALIPAY_APP_ID}
  alipay-public-key: ${ALIPAY_PUBLIC_KEY}
  merchant-private-key: ${ALIPAY_MERCHANT_PRIVATE_KEY}
  gateway-host: "openapi-sandbox.dl.alipaydev.com"
  notify-url: ${ALIPAY_NOTIFY_URL}
  sign-type: "RSA2"
  ignore-ssl: ${ALIPAY_IGNORE_SSL:false}
```

### ml-ai-dev.yaml

```yaml
server:
  port: 24107

spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: ${AI_DATASOURCE_URL:jdbc:postgresql://127.0.0.1:5432/mylesson_ai}
    username: ${AI_DATASOURCE_USERNAME:postgres}
    password: ${AI_DATASOURCE_PASSWORD}

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      database: 0
      timeout: 3s

  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
          temperature: 0.2
      embedding:
        options:
          model: text-embedding-v4
          dimensions: 1024
    vectorstore:
      pgvector:
        initialize-schema: false
        dimensions: 1024
        index-type: hnsw
        distance-type: cosine-distance
        table-name: vector_store
    mcp:
      client:
        enabled: ${MCP_CLIENT_ENABLED:false}
        type: SYNC
        request-timeout: 12s
        toolcallback:
          enabled: true
        sse:
          connections:
            course-resource:
              url: ${MCP_COURSE_RESOURCE_URL:http://127.0.0.1:24200}
              sse-endpoint: /sse

  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 10000
    stream:
      function:
        autodetect: false
        definition: knowledgeSyncConsumer
      bindings:
        knowledgeSyncConsumer-in-0:
          destination: ml-ai-knowledge-events
          group: ml-ai-knowledge-sync
          content-type: text/plain
      rocketmq:
        binder:
          name-server: ${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}
        bindings:
          knowledgeSyncConsumer-in-0:
            consumer:
              orderly: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: when-authorized

springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
  group-configs:
    - group: dev
      paths-to-match: /**
      packages-to-scan: com.yangaobo.ai
  title: "ml-ai"
  description: "<strong>MyLesson - AI 服务</strong>：智能问答、RAG、工具调用和学习计划。"
  version: "v1.0.0"
  author: "杨奥博"
  url: "${AI_PUBLIC_URL:http://127.0.0.1:24107}"

knife4j:
  enable: true
  setting:
    language: zh_cn

ai:
  identity-secret: ${AI_IDENTITY_SECRET}
  internal-token: ${AI_INTERNAL_TOKEN}

  rag:
    vector-top-k: 12
    vector-similarity-threshold: 0.55
    keyword-top-k: 12
    fusion-top-k: 20
    answer-top-n: 6
    minimum-relevant-score: 0.55
    strong-relevant-score: 0.75
    citation-excerpt-length: 300
    source-base-url: ${GATEWAY_BASE_URL:http://127.0.0.1:24101}
    rerank:
      enabled: true
      model: qwen3-rerank
      endpoint: https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
      top-n: 8
      connect-timeout-millis: 3000
      read-timeout-millis: 10000
      instruct: "根据用户问题判断候选资料的相关性，优先保留能直接支持答案的资料。"

  conversation:
    recent-message-limit: 12
    summary-batch-size: 200
    message-list-limit: 100
    answer-delta-size: 48
    lock-ttl: 2m
    stream-timeout: 30m

  tools:
    timeout: 12s
    read-timeout-retry-count: 1
    max-course-search-limit: 10
    max-recent-order-limit: 20

  knowledge:
    source-base-url: ${GATEWAY_BASE_URL:http://127.0.0.1:24101}
    admin-roles: "管理员,超级管理员,ADMIN,ROLE_ADMIN"

  agent:
    intent-confidence-threshold: 0.65
    max-model-calls: 6
    model-retry-count: 1
    intent-timeout: 10s
    model-timeout: 45s
    checkpoint:
      type: redis
      fallback-to-memory: true
      release-thread: false

  mcp:
    enabled: ${MCP_CLIENT_ENABLED:false}
    tool-name-prefix: "mcp_"
    allow-all-tools: true
    disabled-tools: []
    timeout: 12s

  approval:
    ttl: 30m
    list-limit: 100

  knowledge-sync:
    reconciliation-cron: "0 0 3 * * *"
```

## 常见问题

### 服务启动后没有注册到 Nacos

检查 `NACOS_SERVER_ADDR` 是否正确，并确认服务启动日志中使用的是同一个 Nacos 地址和命名空间。

### AI 服务调用业务服务失败

检查 `AI_INTERNAL_TOKEN` 是否在 `common-config.yaml`、`ml-ai-dev.yaml` 和各业务服务运行环境中保持一致。

### Web 前端跨域失败

检查 `ml-gateway-dev.yaml` 中的 `cors.allowed-origins` 是否包含前端访问地址，例如 `http://localhost:24108`。

### 支付宝回调失败

`ALIPAY_NOTIFY_URL` 必须是支付宝沙箱可访问的公网 HTTPS 地址，本机地址和局域网地址不能作为正式回调地址。
