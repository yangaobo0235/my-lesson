# Service Desk

面向 IT 服务商及其签约企业的设备售后服务台，服务对象是已登记的电脑与打印机。系统将设备登记、手动报修、客服受理、技术处理、上门预约和客户确认连成闭环，并通过有来源的知识检索与 AI 排障减少重复咨询。

客户可以直接报修，也可以先按设备型号查阅手册、完成排障，再编辑确认报修草稿。人工售后独立运行，AI 或知识服务故障时仍能提交和处理工单。

## 1. 系统范围

| 业务域 | 系统提供的能力 |
| --- | --- |
| 身份与组织 | 服务商与客户组织、账号、角色组合、服务范围、登录改密与身份管理审计 |
| 设备 | 产品型号、客户资产、服务期限、上门区域与地点 |
| 工单 | 报修、受理分派、接单转派、补充、处理记录、解决、确认、取消与期限任务 |
| 预约 | 区域时段、出勤容量、事务确认、取消回补和真实履约记录 |
| 知识 | 私有 PDF 原件、系列修订、解析索引、人工核对、发布、停用、恢复及替换 |
| 排障 | 型号内授权混合检索、澄清、证据答复、引用原件、本人历史和报修草稿 |
| 工作台 | 客户待办、客服队列、技术人员处理、站内通知、反馈和检索诊断 |
| 运维 | 服务治理、可观测性、可靠投递、持久任务、独立镜像交付与联合恢复 |

角色包括 CUSTOMER_USER、SERVICE_AGENT、TECHNICIAN、KNOWLEDGE_ADMIN、SYSTEM_ADMIN。客户设备按组织可见，工单与排障记录按本人及原客户组织授权；服务团队按明确客户范围操作，管理员角色不自动授予全部业务正文访问权。

## 2. 技术栈与服务划分

采用 Java21、Spring Boot、Spring Cloud Alibaba、MyBatis-Plus、MySQL、Python3.12/FastAPI 和 Vue3/TypeScript。Java 承担身份、设备、工单、预约及知识发布；Python 承担解析、检索与排障；前端承担展示和交互。

| 模块 | 类型 | 主要职责 |
| --- | --- | --- |
| desk-common | Java 基础库 | 基础响应/表单、错误与异常、枚举接口、校验、序列化及环境值 |
| desk-api | Java合同JAR与跨语言合同源 | Java内部模型/Feign及跨语言.proto合同 |
| desk-gateway | WebFlux 应用 | 同源入口、路由、入口保护和身份头清理 |
| desk-user | MVC 应用 | 身份、组织、角色、范围、会话及审计 |
| desk-ticket | MVC 应用 | 型号、设备、工单、通知、概况、时段和预约 |
| desk-knowledge | MVC 应用 | 原件、修订、授权、发布及索引指令 |
| desk-ai | Python Web、receiver与worker | MinerU、ES/Qdrant、排障、引用、草稿及反馈 |
| desk-web | Vue 应用 | 客户、服务人员及管理工作台 |

每个业务服务独立维护 MySQL 库、账号和迁移。跨服务通过内部认证和明确合同访问，不共享实体或跨库联表。common/api 不独立部署，Python Web、receiver与worker共用业务模块及数据所有权。

Nacos管Java注册与配置，Feign/LoadBalancer承担Java内部通信。Redis用于登录防刷、用户日调用次数及knowledge的Redisson上传互斥，预约容量由MySQL本地事务确认。Java↔Python采用gRPC + Protobuf；跨服务异步统一RocketMQ，当前索引使用原生事务消息与本地任务事实回查，receiver落库后ACK、worker执行，不设outbox投递表/扫描发送器。XXL-JOB触发工单期限及索引过期准备收敛。Sentinel保护Gateway及Java实际依赖，Python另设队列和执行预算。

原件存阿里云 OSS 私有 Bucket，knowledge 独占凭据并代理授权读取。AI 在 MySQL 保存片段与状态，ES 保存关键词副本，Qdrant 保存向量副本，采用 RRF 融合。MinerU 负责解析，百炼只提供Embedding和Chat模型API，RAG与排障流程由项目实现。报修草稿按模板预填用户确认的字段，不额外调用模型。

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

默认本机入口为 Gateway28101、user28102、ticket28103、knowledge28104、AI28105、Web28199。前端同源调用 /api/v1，内部接口不通过浏览器入口发布。具体启动、配置与验证合同由部署说明和各端规范统一维护。

## 5. 当前骨架与本地检查

| 范围 | 已实现 |
| --- | --- |
| Maven | 六模块聚合；common/api普通JAR，四个应用独立Boot JAR；JDK/Maven约束及本机配置打包排除 |
| common | Result、公共错误与BusinessException、BaseEnum及无Spring环境快照，已接入真实应用边界 |
| api | ActorRef/RpcErrorDetail唯一.proto、Java生成与JAR打包、Python生成与wheel打包、固定线向量检查 |
| Java应用 | dev/prod配置、MVC/Reactive独立错误处理、请求关联标识、原生健康；MVC开发OpenAPI3.0及生产关闭 |
| Gateway | 公开资源及13个管理资源白名单、原路径转发、外来身份/追踪头清理、下游地址启动校验 |
| AI | 类型化配置、HTTP模块入口、进程健康、安全错误及环境文档开关，生成合同可脱离源码导入 |
| Web | 公共布局、主页、404、路由、样式入口及严格类型检查 |
| CI | 文件/依赖边界、公共错误编号对齐、生成目录忽略、真实JAR及wheel检查，Java/Python/前端检查 |

公共分页/动作表单、标识校验、字段级序列化和枚举文档定制器在对应业务消费者接入时建立。当前未建立空Controller/Service/Mapper或业务目录；api尚无业务Feign与RPC方法。AI当前仅启动HTTP进程，双监听、receiver与worker随业务用例落地，不以空进程假装可靠执行。跨服务认证、Nacos、数据库迁移、MQ、XXL-JOB、Redisson、Sentinel、ELK及Zipkin尚未联调；它们仍属于最终范围，P0关键依赖实验与后续业务阶段按改造计划完成。

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
uv run --locked python -m servicedesk_ai
```

API默认监听127.0.0.1:28105，GET /health为进程探针；DESK_AI_ENVIRONMENT=prod关闭文档，DESK_AI_HTTP_HOST/PORT控制监听。构建AI制品使用 `uv run --locked python ../.github/scripts/build_ai.py --output <仓库外制品目录>`，脚本在构建前生成合同并核对wheel内容，不在应用启动时生成类型。

Java从仓库根目录以 `java -jar desk-user/target/desk-user-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev` 启动，其他应用替换模块和JAR名称。dev默认回环绑定；可在所属模块src/main/resources/application-local.yml维护忽略的本机配置，由仓库根工作目录显式读取，真实文件及同名模板均不打入JAR。prod不读取本机文件；Gateway须提供四个DESK_*_URL下游地址，其余运行参数按部署说明维护。

在desk-web目录执行npm ci、npm run typecheck、npm run lint、npm run format:check、npm run build；npm run dev启动28199并将/api代理至28101。未交付业务返回明确错误，不以页面占位或健康UP表示业务可用。

本次骨架验证：JDK21.0.6、Maven3.9.9、CPython3.12.16、uv0.13.7、Node24.22.2、npm11.19.1；Java24项、Python7项测试通过，失败/错误/跳过均为0，前端类型/lint/格式/构建通过。JAR边界、AI wheel内容及仓库外安装/HTTP进程健康/生产文档关闭通过。Java HTTP测试使用临时下游，协议测试使用固定线向量；没有验证业务gRPC调用、数据库或中间件，也没有部署。
