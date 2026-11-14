# Service Desk

面向一家 IT 服务商及其签约企业的办公 IT 支持与协同排障服务台，覆盖办公终端、打印与扫描、办公网络连接及指定办公软件。系统以问题受理、远程处理、必要上门和客户确认为主线，AI协助客户补全信息、辅助技术人员依据知识和反馈逐步排障，并形成可核对的交接与处理建议。

客户可以直接提交问题，也可以先在已开通服务范围内自助排障，再编辑确认交接草稿。设备按需关联，电脑和打印机是重点资产类型，不是所有问题的必填对象。技术人员在工单内使用本人私有助手，处理记录和解决结果由人确认；人工支持在AI或知识故障时仍可独立运行。

## 1. 系统范围

| 业务域 | 系统提供的能力 |
| --- | --- |
| 身份与组织 | 服务商与客户组织、账号、角色组合、服务范围、登录改密与身份管理审计 |
| 支持范围与资产 | 四类服务项目、客户服务资格及变更审计、可选电脑/打印机资产及固定问题环境 |
| 工单 | 问题提交、待分类分流、接单转派、补充、处理、转介、确认/超时结单及期限任务 |
| 预约 | 区域时段、出勤容量、事务确认、取消回补和真实履约记录 |
| 知识 | 私有PDF/Markdown原件、适用规则、修订索引、人工核对、发布、停用及替换 |
| 排障 | 客户自助/技术人员辅助、带来源诊断事实、有界工具循环、实际证据、动作草稿及逐次调用用量 |
| 工作台 | 客户待办、客服队列、技术人员处理、站内通知、反馈和检索诊断 |
| 运维 | 服务治理、可观测性、可靠投递、持久任务、独立镜像交付与联合恢复 |

角色包括 CUSTOMER_USER、SERVICE_AGENT、TECHNICIAN、KNOWLEDGE_ADMIN、SYSTEM_ADMIN。客户设备按组织可见，客户工单按本人及原客户组织授权，技术助手按本人及当前工单读取资格授权；他人工单权限不授予私有会话权限。服务团队按明确客户范围操作，管理员角色不自动授予全部业务正文访问权。

## 2. 技术栈与服务划分

采用 Java21、Spring Boot、Spring Cloud Alibaba、MyBatis-Plus、MySQL、Python3.12/FastAPI 和 Vue3/TypeScript。Java承担身份、支持范围、设备、工单、预约及知识发布；Python承担解析、检索、诊断事实及模型/工具执行；前端承担展示和交互。

| 模块 | 类型 | 主要职责 |
| --- | --- | --- |
| desk-common | Java 基础库 | 基础响应/表单、错误与异常、枚举接口、校验、序列化及环境值 |
| desk-api | Java合同JAR与跨语言合同源 | Java内部模型/Feign及跨语言.proto合同 |
| desk-gateway | WebFlux 应用 | 同源入口、路由、入口保护和身份头清理 |
| desk-user | MVC 应用 | 身份、组织、角色、范围、会话及审计 |
| desk-ticket | MVC 应用 | 服务项目、客户开通范围、设备、工单、通知、概况及时段预约 |
| desk-knowledge | MVC 应用 | 原件、修订、授权、发布及索引指令 |
| desk-ai | Python Web、receiver与worker | PDF/文本解析、ES/Qdrant、诊断、工具/模型调用、来源、草稿及反馈 |
| desk-web | Vue 应用 | 客户、服务人员及管理工作台 |

每个业务服务独立维护 MySQL 库和迁移；生产使用独立账号，当前开发统一使用 root 连接各自 test 库。跨服务通过内部认证和明确合同访问，不共享实体或跨库联表。common/api 不独立部署，Python Web、receiver与worker共用业务模块及数据所有权。

Nacos管Java注册与配置，Feign/LoadBalancer承担Java内部通信。Redis用于登录防刷、用户日调用次数及knowledge的Redisson上传互斥，预约容量由MySQL本地事务确认。Java↔Python采用gRPC + Protobuf；跨服务异步统一RocketMQ，当前索引使用原生事务消息与本地任务事实回查，receiver落库后ACK、worker执行，不设outbox投递表/扫描发送器。XXL-JOB触发工单期限及索引过期准备收敛。Sentinel保护Gateway及Java实际依赖，Python另设队列和执行预算。

原件存阿里云 OSS 私有 Bucket，knowledge 独占凭据并代理授权读取。AI 在 MySQL 保存片段与状态，ES 保存关键词副本，Qdrant 保存向量副本，采用 RRF 融合。MinerU负责PDF解析，Markdown本地确定性解析。百炼只提供Embedding与Chat/工具调用API，RAG和有界诊断执行器由项目负责。客户建单草稿使用确认事实模板，技术处理草稿采用该轮已校验结果，创建草稿不额外调用模型；正式动作均由人确认。

最终方案以ELK集中检索脱敏JSON日志，Zipkin贯通Java、Python、MQ接收与持久worker追踪；业务审计和终态仍在MySQL。上述组件均纳入最终交付，按真实职责接入并验证故障恢复。组件选择依据见[系统设计](docs/设计/系统设计.md#13-组件职责与使用依据)。

## 3. 工程约定

Java使用Controller/RPC适配、Service、Mapper和独立Model，主实体业务采用三件套，聚合用例使用普通Service；手写数据模型统一普通class、Serializable和明确字段合同，Protobuf生成类型遵循生成器合同。自定义SQL使用注解与文本块，不新增Mapper XML。

普通 JSON 使用 `Result<T>`，成功 code=0、系统异常 code=1，其余错误按业务域登记在[接口约定](docs/设计/接口约定.md#12-错误码与边界转换)，HTTP状态保留真实语义。管理接口采用 POST 动作，公开业务 ID 为32位小写十六进制字符串，分页默认1/10、上限100。后端逐次校验当前权限和资源状态，幂等与结果未知按原请求身份核对。

公共能力按真实消费者进入common/base等职责包；Entity与业务枚举归所有者服务，内部HTTP及跨语言合同归api。JSON、环境、异常和OpenAPI运行装配归各应用，分别适配MVC、WebFlux与Python；不让公共库引入应用启动行为。

本文档体系定义最终交付系统，不划分功能版本；开发阶段仅安排实现顺序，最终范围、接口和失败路径以正式设计为准。实施与验收按[开发改造计划](docs/开发计划/开发改造计划.md)组织，规范和设计中列举的类型按真实用例落地，不能机械生成无消费者代码。

前述设计定义最终目标，当前可执行范围与验证见第5节；业务及组件接入进度随具体改造任务提供，不沿用参考项目的测试数量或验收结论。

## 4. 阅读与开发入口

先读[开发规范](docs/开发/开发规范.md)，再按任务阅读端规范、设计和接口。工具版本、依赖及镜像基线集中在[版本约定](docs/开发/版本约定.md)，本机连接、镜像交付和服务器运行见[部署说明](docs/开发/部署说明.md)。

| 目的 | 入口 |
| --- | --- |
| 完整文档 | [文档导航](docs/README.md) |
| 模块、目录、依赖和功能归属 | [项目结构](docs/设计/项目结构.md) |
| 业务、权限和状态 | [系统设计](docs/设计/系统设计.md) |
| 字段、约束、索引与 ER 图 | [数据库设计](docs/设计/数据库设计.md) |
| HTTP、gRPC、内部认证、幂等、MQ和SSE | [接口约定](docs/设计/接口约定.md) |
| Java / Python / Vue 写法 | [Java后端规范](docs/开发/Java后端规范.md) · [Python后端规范](docs/开发/Python后端规范.md) · [前端规范](docs/开发/前端规范.md) |

默认本机入口为 Gateway26101、user26102、ticket26103、knowledge26104、AI26105、Web26199。前端同源调用 /api/v1，内部接口不通过浏览器入口发布。具体启动、配置与验证合同由部署说明和各端规范统一维护。

开发环境通过IDEA的MySQL root数据源创建四个空库，三个Java服务启动执行Flyway，AI API启动执行Alembic；已有手工表先按[数据库初始化与迁移](docs/sql/README.md)比对并显式接管，不重复执行初始DDL。Java三服务的application-local.yml和AI的.env使用root用户名，各自填写MySQL root密码；开发迁移复用该账号，密码不纳入版本管理。

## 5. 当前骨架与本地检查

当前已完成办公IT支持目标的工程骨架与数据库结构准备：31张自有表、435个字段；ticket/knowledge的增量迁移、AI的Alembic 0003，以及15个管理资源的网关白名单已同步。V1/0001和手工SQL保留不可变旧基线，新版本在全部旧业务表为空时升级；非空旧库在任何DDL前拒绝执行，必须另做显式数据映射。步骤和恢复边界见[数据库初始化与迁移](docs/sql/README.md#6-办公it支持目标的增量迁移)。表结构就绪不表示业务接口已经实现。

| 范围 | 已实现 |
| --- | --- |
| Maven | 六模块聚合；common/api普通JAR，四个应用独立Boot JAR；JDK/Maven约束及本机配置打包排除 |
| 数据库迁移 | user的Flyway V1、ticket的V1→V2、knowledge的V1→V3、AI的0001→0003；启动迁移、非空基线保护、校验和与失败就绪边界；保留初始快照 |
| common | Result、公共错误与BusinessException、BaseEnum及无Spring环境快照，已接入真实应用边界 |
| api | ActorRef/RpcErrorDetail唯一.proto、Java生成与JAR打包、Python生成与wheel打包、固定线向量检查 |
| Java应用 | dev/prod配置、MVC/Reactive独立错误处理、请求关联标识、原生健康；MVC开发OpenAPI3.0及生产关闭 |
| Gateway | 公开资源及15个管理资源白名单、原路径转发、外来身份/追踪头清理、下游地址启动校验 |
| AI | 类型化组件配置、ASGI资源生命周期、索引receiver/worker、百炼类型化Chat/工具建议/Embedding合同、实际用量及向量校验、JSON日志/Zipkin、HTTP错误和健康，生成合同可脱离源码导入 |
| 组件配置 | Java各应用本机YAML、AI独立dotenv及原生启动；Java默认组件装配、MySQL/Lettuce、OSS/Redisson、XXL执行器、条件事务MQ生产者、Nacos/Feign/Zipkin及Gateway Sentinel冷启动基线 |
| Web | 公共布局、主页、404、路由、样式入口及严格类型检查 |
| CI | 文件/依赖边界、公共错误编号对齐、生成目录忽略、真实JAR及wheel检查，Java/Python/前端检查，独占MySQL原生迁移与业务约束检查 |

P1–P3已落地组织/账号与范围管理、JDBC Session/CSRF/改密、内部HTTP与双向TLS身份RPC、服务目录/资格/审计、可选资产、直接提单、人工处理/确认、通知/概况及XXL期限扫描；P4–P6已落地现场、知识、索引receiver/worker、客户/技术私有排障及人工草稿采用；P7补齐知识管理员共享反馈队列、来源复核和版本化处置；P8已有固定隔离证据。Vue页面使用真实接口，人工链不依赖知识、解析或AI。实现、验证及未验收边界见[P0–P3实施与验收](docs/开发计划/P0-P3实施与验收.md)、[P4–P6实施与验收](docs/开发计划/P4-P6实施与验收.md)和[P7–P8本地交付与验收](docs/开发计划/P7-P8本地交付与验收.md)。

先核对mvn -version使用JDK21、uv为0.13.7同系、Node/npm符合版本约定；终端java与Maven的JDK可能不同。以下检查不连接共享业务库，也不要求启动中间件。

在仓库根目录检查Java及制品：

```shell
python .github/scripts/check_structure.py
mvn -B -ntp clean verify
python .github/scripts/check_java_artifacts.py
```

在desk-ai目录首次安装、生成并检查；变更.proto后同样重新生成：

```shell
uv sync --locked --no-install-project
uv run --locked --no-sync python ../.github/scripts/generate_contracts.py
uv sync --locked
uv run --locked ruff check --config pyproject.toml . ../.github/scripts
uv run --locked ruff format --check --config pyproject.toml . ../.github/scripts
uv run --locked pytest
```

API默认监听127.0.0.1:26105，GET /health为进程探针；DESK_AI_ENVIRONMENT=prod关闭文档，DESK_AI_HTTP_HOST/PORT控制监听。构建AI制品使用 `uv run --locked python ../.github/scripts/build_ai.py --output ../.local/build/artifacts`，脚本在构建前生成合同并核对wheel内容，不在应用启动时生成类型。

组件服务器已配置为192.168.23.66。Java将各应用src/main/resources/application-local.example.yml复制为同目录application-local.yml，分别填写本服务的空密码、密钥、本机回调IP及实际账号/桶名。以仓库根为工作目录，在IDEA将Active profiles留空（默认dev）或只填dev，直接运行所属启动类即连接所需组件；也可运行：

```shell
java -jar desk-user/target/desk-user-0.1.0-SNAPSHOT.jar
```

其他应用替换模块及JAR名。默认dev直接启用所需组件，先原生导入本机YAML，再由本机文件链式导入Nacos；真实文件及空模板都不打入JAR，prod不加载本机文件。各服务只保存自己的凭据，不引用其他服务本机文件。

AI将[.env.example](desk-ai/.env.example)复制为desk-ai/.env，直接填写DESK_AI_*嵌套字段，在desk-ai目录运行 `uv run --locked python -m servicedesk_ai`；入口由Pydantic Settings原生加载，进程变量优先。生产通过进程环境提供DESK_AI_ENVIRONMENT=prod及所需参数，不读取开发.env。IDEA/Python无需根目录配置转换或专用启动脚本。

具体填写、Nacos配置、Topic及空间准备见[本机组件连接](docs/开发/部署说明.md#51-本机配置与连接)。[数据库初始化文档](docs/sql/README.md)保留原24表基线的手工SQL快照；新环境建空库后执行完整迁移链，已有手工结构先显式接管。当前完整迁移为user V1、ticket V2、knowledge V3和AI 0003，共31张自有表435字段。MQ事务回查、receiver/worker及领域mTLS RPC已接入；启动顺序及解析对账见部署说明。

在desk-web目录执行npm ci、npm run typecheck、npm run lint、npm run format:check、npm run test、npm run build；npm run dev启动26199并将/api代理至26101。未交付业务返回明确错误，不以页面占位或健康UP表示业务可用。

2026-11-14已实现P1–P8的本地开发范围，并取得现场容量/履约、真实OSS→MQ→AI→MinerU/Markdown→双索引→发布、客户/技术私有排障、人工草稿采用、共享反馈来源复核及固定故障分支的分项证据。Nacos动态规则、实际Gateway Reactive生命周期、XXL注册/触发/回调及Zipkin跨语言/持久worker接续通过。没有打印机及Microsoft 365环境，相关设备实测明确未验证；生产级MQ/ELK完整故障矩阵、非空旧库迁移、镜像交付和联合恢复留待实际部署。未部署业务应用，未改共享业务数据。具体结果和限制见[P0–P3记录](docs/开发计划/P0-P3实施与验收.md)、[P4–P6记录](docs/开发计划/P4-P6实施与验收.md)及[P7–P8本地交付与验收](docs/开发计划/P7-P8本地交付与验收.md)。
