# Service Desk

面向一家 IT 服务商及其签约企业的办公 IT 支持与协同排障服务台，覆盖办公终端、打印与扫描、办公网络连接及指定办公软件。系统以问题受理、远程处理、必要上门和客户确认为主线，AI协助客户补全信息、辅助技术人员依据知识和反馈逐步排障，并形成可核对的交接与处理建议。

客户可先进行轻量IT咨询，明确故障目标后创建持久诊断任务，也可以直接提交问题，也可以先在已开通服务范围内自助排障，再编辑确认交接草稿。设备按需关联，电脑和打印机是重点资产类型，不是所有问题的必填对象。技术人员在工单内使用本人私有助手，处理记录和解决结果由人确认；人工支持在AI或知识故障时仍可独立运行。

## 1. 系统范围

| 业务域 | 系统提供的能力 |
| --- | --- |
| 身份与组织 | 服务商与客户组织、账号、角色组合、服务范围、登录改密与身份管理审计 |
| 支持范围与资产 | 四类服务项目、客户服务资格及变更审计、可选电脑/打印机资产及固定问题环境 |
| 工单 | 问题提交、待分类分流、接单转派、补充、处理、转介、确认/超时结单及期限任务 |
| 预约 | 区域时段、出勤容量、事务确认、取消回补和真实履约记录 |
| 知识 | 私有PDF/Markdown原件、适用规则、完整阅读、审核规程、人工编写案例、发布/换版/风险撤回/撤权 |
| 排障 | 咨询/任务/运行分离、来源化事实、检查反馈/假设、完整证据、有限规程、持久调用、人工动作回执及全模型调用账目 |
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

原件存阿里云 OSS 私有 Bucket，knowledge管理知识原件，ticket管理诊断附件，分别持有最小权限凭据并代理授权读取。AI 在 MySQL 保存片段与状态，ES 保存关键词副本，Qdrant 保存向量副本，采用 RRF 融合。MinerU负责PDF解析，Markdown本地确定性解析。百炼只提供Embedding与Chat/工具调用API，RAG和有界诊断执行器由项目负责。客户建单草稿使用确认事实模板，技术处理草稿采用该轮已校验结果，创建草稿不额外调用模型；正式动作均由人确认。

最终方案以ELK集中检索脱敏JSON日志，Zipkin贯通Java、Python、MQ接收与持久worker追踪；业务审计和终态仍在MySQL。上述组件均纳入最终交付，按真实职责接入并验证故障恢复。组件选择依据见[系统设计](docs/设计/系统设计.md#13-组件职责与使用依据)。

Java侧以工单状态/期限竞争、预约容量与幂等、统一动作回执及知识索引事务回查形成业务闭环；Agent侧以反馈驱动检查、来源化事实/假设、完整证据RAG、上下文预算、持久等待/取消/未知调用及领域对比评测体现深度。规程随知识修订管理，假设保存在任务有界投影，摘要和引用保存在Run快照；消息、事实、检查反馈、来源及调用账目独立持久化。范围不包含工单关系图、自动案例挖掘、独立候选审批、通用规程引擎或多Agent平台。

## 3. 工程约定

Java使用Controller/RPC适配、Service、Mapper和独立Model，主实体业务采用三件套，聚合用例使用普通Service；手写数据模型统一普通class、Serializable和明确字段合同，Protobuf生成类型遵循生成器合同。每个真实持久化实体保留各层对应文件，允许没有独立行为的空层；自定义SQL统一放同名Mapper XML，简单通用操作继续使用BaseMapper。

普通 JSON 使用 `Result<T>`，成功 code=0、系统异常 code=1，其余错误按业务域登记在[接口约定](docs/设计/接口约定.md#12-错误码与边界转换)，HTTP状态保留真实语义。管理接口采用 POST 动作，公开业务 ID 为32位小写十六进制字符串，分页默认1/10、上限100。后端逐次校验当前权限和资源状态，幂等与结果未知按原请求身份核对。

公共能力按真实消费者进入common/base等职责包；Entity与业务枚举归所有者服务，内部HTTP及跨语言合同归api。JSON、环境、异常和OpenAPI运行装配归各应用，分别适配MVC、WebFlux与Python；不让公共库引入应用启动行为。

本文档体系定义37张自有表的最终交付系统（user 5、ticket 12、knowledge 5、AI 15；框架技术表另计），不划分功能版本；开发阶段仅安排实现顺序，最终范围、接口和失败路径以正式设计为准。实施与验收按[开发规范](docs/开发/开发规范.md#6-验证和交付)执行，开发计划及本机验收记录保存在Git忽略的`docs/开发计划/`，不得提交，规范和设计中列举的类型按真实用例落地，不能机械生成无消费者代码。

前述设计定义最终目标，当前可执行范围与验证见第5节；业务及组件接入进度随具体改造任务提供，不沿用参考项目的测试数量或验收结论。

## 4. 阅读与开发入口

先读[开发规范](docs/开发/开发规范.md)，再按任务阅读端规范、设计和接口。工具版本、依赖及镜像基线集中在[版本约定](docs/开发/版本约定.md)，本机连接、镜像交付和服务器运行见[部署说明](docs/部署/部署说明.md)。

| 目的 | 入口 |
| --- | --- |
| 完整文档 | [文档导航](docs/README.md) |
| 模块、目录、依赖和功能归属 | [项目结构](docs/设计/项目结构.md) |
| 业务、权限和状态 | [系统设计](docs/设计/系统设计.md) |
| 字段、约束、索引与 ER 图 | [数据库设计](docs/设计/数据库设计.md) |
| HTTP、gRPC、内部认证、幂等、MQ和SSE | [接口约定](docs/设计/接口约定.md) |
| Java / Python / Vue 写法 | [Java后端规范](docs/开发/Java后端规范.md) · [Python后端规范](docs/开发/Python后端规范.md) · [前端规范](docs/开发/前端规范.md) |

本机临时产物统一放仓库根目录`.local/`，停止相关进程后可删除并按需重建；模块内不另建`.local/`、`logs/`。Java在IDEA的Working directory设为`$PROJECT_DIR$`，AI命令在`desk-ai`执行；真实配置、业务原件及数据库不放入临时目录。

默认本机入口为 Gateway26101、user26102、ticket26103、knowledge26104、AI26105、Web26199。前端同源调用 /api/v1，内部接口不通过浏览器入口发布。具体启动、配置与验证合同由部署说明和各端规范统一维护。

开发环境通过IDEA的MySQL root数据源创建四个空库，三个Java服务启动执行Flyway，AI API启动执行Alembic；已有手工表先按[数据库初始化与迁移](docs/sql/README.md)比对并显式接管，不重复执行初始DDL。Java三服务的application-local.yml和AI的.env使用root用户名，各自填写MySQL root密码；开发迁移复用该账号，密码不纳入版本管理。

## 5. 当前实现与本地检查

下文的真实组件联调、模型评测和镜像记录对应 2026-11-18 的本地验收基线。后续源码按项目结构调整时，构建、测试和真实组件复验以相应改动的验证记录为准。

截至2026-11-18，HTTP、worker和Vue工作台统一使用Conversation/Task/Message/Run，旧Generation入口及共享反馈队列已移除。现有真实开发MySQL的37张自有表、585个字段与设计逐字段一致；迁移版本为user V1、ticket V7、knowledge V5、AI 0009。开发应用直接连接既有服务器组件，未使用临时Docker隔离数据库。

人工业务与Agent链路已在真实组件上贯通，包括登录及角色页面、独立附件OSS与人工脱敏、PDF/Markdown经MQ受理和双索引、普通咨询、技术私有排障、五类人工确认草稿及Java回执重放、规程检查→人工反馈→转介→客户确认。预约最后名额竞争及取消回补、私聊读写/跨客户拒绝、撤回遮蔽、worker崩溃/取消/未知调用、SSE重连、MQ丢ACK/重启/乱序/死信/事务回查、依赖连接故障均有分项真实证据。进程局部连接故障不冒充组件断电；攻击样例通过不代表穷尽安全证明。验收方法见[验收说明](docs/验收/验收说明.md)。

检索预览以METADATA登记，短预览也不算完整阅读；正文从0开始，叶片展开到父章节不能沿用叶片游标。完整阅读按同片段、同摘要的连续字符区间核验，工具回执区分候选和正文。来源目录避免重复展示当前工具已提供的候选；32条合成记录的定向补读已验证初始省略、实际工具读取、Chat输入清单和回答来源，未提高预算。宽泛字面查询仍可能命中干扰项并耗尽输入预算，这项效果限制保留。内部gRPC及Zipkin显式直连；RocketMQ SDK未暴露通道代理选项，receiver只清除本进程继承的代理变量。官方SDK真实连接回归及原索引任务恢复验证通过，不以进程存在替代接收就绪；身份/依赖故障与登录过期分开记录。

已引入4份固定提交、注明CC BY 4.0许可的Microsoft公开资料，覆盖终端黑屏、打印RPC、Wi-Fi及Edge翻译；配套2种合成型号、4台明确合成设备和4张新体验工单，未捏造厂商序列号。另有独立演示企业及4张不绑定设备的工单，演示身份与批量评测分开。资料受众/环境/原件/双索引32项核验通过，它们是技术参考而非专家审核规程或现场维修成绩，见[样例数据](docs/验收/样例数据.md)。

检查反馈保留原指令、角色、事实身份和环境版本，区分人工报告、助手自述与工具观测。审核规程由模型选择检查来源，宿主展开原指令与选项，并在事务内重核前提；恢复只接受当前环境最新的人工复测反馈。可选假设/草稿整组通过依据核验后才交付，失败不否决独立合法的审核检查；自由澄清和普通回答仍整体校验。逐段核验保留全部候选文字，标题与后续内容同审。仍为37表、585字段，没有增加自动写入业务权限。同模型核验不能代替独立语义审阅。

[领域评测](docs/验收/领域评测.md)分别保留旧批次、v58三策略广域对照与v59修复回归/消融。v58共539次完成，另1条旧基线请求回执未知，明确排除且未重发；动态恢复57/60、交接120/120，固定规程恢复60/60、交接119/119，单次RAG恢复41/60、交接119/120。该题集主要是短规程，固定流程更稳定且调用更少，不能宣称Agent全面优于固定工作流。历史失败与中断账目均保留，保留集曾被使用，不宣称新的独立测试。

本地10用户混合负载运行约30分钟，8970次人工接口请求全部成功、p95约47ms；实际Run并发时间序列有0/1/2，不称全程双模型并发。负载账号已停用、合成负载工单已取消并保留审计。XXL业务调度、原生Logstash持续采集及采集器恢复已有实际回执。四库同服务器新命名库逻辑恢复及摘要核对通过，不代表独立灾备或OSS/Qdrant/MQ联合恢复。

当前Python完整522项测试通过，失败/错误/跳过均0；Ruff lint/format与mypy82源通过。Java/前端本轮未改，沿用最近124/41项构建测试及制品证据。AI local-v59生产源码与已验证镜像一致，六个应用镜像边界通过；v59修复无进展提前交接，27次针对性回归及12次公开问答机械与实现者语义审阅通过。225次消融已完成且账目齐全：完整控制组恢复15/15、交接30/30，与针对性回归合计72/72实现者语义审阅通过；去掉重复检测组有2条过度肯定的假设，原失败保留。状态投影、完整证据与宿主安全门槛共同影响消融结果，解释边界见领域评测文档，不能把安全拒绝算成修复成功。费用因缺单价保持null。

本次本地范围综合矩阵31/31通过，正常API、worker、receiver及六个服务健康已核验；日限额恢复排障20/普通咨询5，不清空计数，后续只调用qwen3.8-flash。模型账目证明没有新增Max调用，历史Max323次成功CHAT独立保留。Windows文件短暂占用的评测工具修复发生在付费批次冻结核验之后，未改生产执行器；原文件、时钟偏差造成的零调用失败及沿原任务恢复的记录均保留。开发机和服务器须保持时间同步，健康接口不能发现所有Deadline问题。

本地运行与当前约定验收已完成，不表示模型在任意故障上正确。公开资料与合成场景不代表实机修复、独立专家审阅或真实参与者试用；未执行生产部署，也未完成独立主机及OSS/Qdrant/MQ联合灾备。

SQL归属与分层要求见[Java后端规范](docs/开发/Java后端规范.md)，文件对应见[项目结构](docs/设计/项目结构.md)。实现与验证以当前代码、配置及可重复检查为准，历史本机记录不代替当前版本回归。独占MySQL/Redis入口能够复现迁移、约束、人工链及AI执行权检查。

先核对mvn -version使用JDK21、uv为0.13.7同系、Node/npm符合版本约定；终端java与Maven的JDK可能不同。以下检查不连接共享业务库，也不要求启动中间件。

在仓库根目录检查Java及制品：

```shell
python .github/scripts/check_structure.py
# IDEA 原生 Java 格式；加 --check 只检查，也可用 DESK_IDEA_HOME 指定安装目录
python .github/scripts/format_java.py --idea-home "<IDEA安装目录>"
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
uv run --locked mypy
uv run --locked pytest
```

API默认监听127.0.0.1:26105，GET /health为进程探针；DESK_AI_ENVIRONMENT=prod关闭文档，DESK_AI_HTTP_HOST/PORT控制监听。构建AI制品使用 `uv run --locked python ../.github/scripts/build_ai.py --output ../.local/build/artifacts`，脚本在构建前生成合同并核对wheel内容，不在应用启动时生成类型。

组件服务器已配置为192.168.23.66。Java将各应用src/main/resources/application-local.example.yml复制为同目录application-local.yml，分别填写本服务的空密码、密钥、本机回调IP及实际账号/桶名。以仓库根为工作目录，在IDEA将Active profiles留空（默认dev）或只填dev，直接运行所属启动类即连接所需组件；也可运行：

```shell
java -jar desk-user/target/desk-user-0.1.0-SNAPSHOT.jar
```

其他应用替换模块及JAR名。默认dev直接启用所需组件，先原生导入本机YAML，再由本机文件链式导入Nacos；真实文件及空模板都不打入JAR，prod不加载本机文件。各服务只保存自己的凭据，不引用其他服务本机文件。

AI将[.env.example](desk-ai/.env.example)复制为desk-ai/.env，直接填写DESK_AI_*嵌套字段，在desk-ai目录运行 `uv run --locked python -m servicedesk_ai`；入口由Pydantic Settings原生加载，进程变量优先。生产通过进程环境提供DESK_AI_ENVIRONMENT=prod及所需参数，不读取开发.env。IDEA/Python无需根目录配置转换或专用启动脚本。

具体填写、Nacos配置、Topic及空间准备见[本机组件连接](docs/部署/部署说明.md#51-本机配置与连接)。[数据库初始化文档](docs/sql/README.md)保留原24表基线的手工SQL快照；新环境建空库后执行完整迁移链，已有手工结构先显式接管。当前完整迁移版本与字段以数据库初始化文档及数据库设计为准。MQ事务回查、receiver/worker及领域mTLS RPC已接入；启动顺序及解析对账见部署说明。

在desk-web目录执行npm ci、npm run typecheck、npm run lint、npm run format:check、npm run test、npm run build；npm run dev启动26199并将/api代理至26101。未交付业务返回明确错误，不以页面占位或健康UP表示业务可用。

本地测试和历史分项联调不等于生产验收。打印机/Microsoft 365设备实测、生产MQ/ELK完整故障矩阵、非空旧库的数据映射、镜像交付和联合恢复须分别取得证据；不得把健康UP、SDK接入或离线盘点当作业务及恢复通过。现有开发库已执行正式迁移并保存合成验收案例，未进行生产部署。
