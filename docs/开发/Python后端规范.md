# Python后端规范

本文规定 desk-ai 的协议、用例、类型、资源、事务和 AI 边界。通用要求见[开发规范](开发规范.md)，目录见[项目结构](../设计/项目结构.md)，业务见[系统设计](../设计/系统设计.md)，数据、HTTP、RPC与消息见[数据库设计](../设计/数据库设计.md)和[接口约定](../设计/接口约定.md)。Python 是业务后端，不能把权限、检索或模型决策交给前端。

## 1. 分层与用例

按PDF/文本索引、检索、会话诊断事实、有界执行、动作草稿和反馈的完整动作组织，不按每张表生成一套服务，也不照搬 Java 三件套。

| 位置 | 负责内容 | 边界 |
| --- | --- | --- |
| api | HTTP 绑定、身份依赖、响应/异常转换和 SSE | 不直接查库，不在请求内跑持久长任务 |
| rpc | grpc.aio服务端、认证/Deadline、类型及错误转换 | 复用services，不复制HTTP用例规则 |
| schemas | Pydantic输入、输出、分页及基础动作协议 | 不反向导入路由或真实资源 |
| errors | 公共及AI分域数字码、业务异常 | 不包含HTTP或gRPC状态、数据库状态全集 |
| services | 当前授权、业务条件、状态、事务及资源编排 | 不接收 Request、Response 或 UI 状态 |
| diagnosis | 上下文预算、类型化只读工具及有界模型循环 | 不控制第二套持久队列，不绕过services写工单/知识 |
| retrieval | 召回组合、RRF、去重和证据预算 | 只处理已确定授权范围，不自造发布权限 |
| repositories | 类型化查询、领取、CAS 和本地持久化 | 不调模型，不决定完整工作流 |
| infrastructure | Java gRPC、RocketMQ、MinerU、ES、Qdrant、Redis、模型与追踪适配 | 外部协议和 SDK 类型不泄漏到用例 |
| bootstrap | 设置、装配、进程资源与关闭 | 不承载业务规则 |

纯计算优先函数，有稳定依赖或生命周期再使用类。Protocol 用于真实替换/隔离边界，不为每类增加 Impl、BaseService、Manager。身份、客户支持资格、设备/工单事实及资料发布由对应Java服务提供；用户自述和模型假设须保留不同来源，AI 不直接读写兄弟服务库。

## 2. 命名、导入与类型

模块、包、函数和参数 snake_case，类型/异常 PascalCase，常量 UPPER_SNAKE_CASE。动作说明业务及副作用，例如 create_index_job、cancel_generation、create_action_draft。绝对导入 servicedesk_ai.*，不用 sys.path 绕过布局，不跨模块调用私有函数，不从生产代码导入测试。__init__.py 不启动数据库、网络或任务。

公共用例、仓储、端口及关键规则完整声明参数与返回类型；可推导局部值不重复标注。使用 Python3.12 支持的 list[T]、dict[K,V]、T | None。Ruff 管理导入与格式，TYPE_CHECKING 不掩盖循环依赖。

在 desk-ai 执行 `uv run --locked ruff format --config pyproject.toml . ../.github/scripts` 写入格式，加 `--check` 只检查。IDE 使用 Ruff 格式集成时选择项目环境及 pyproject.toml；未接入 Ruff 的 Python 原生快捷键格式不能视为项目格式检查。

| 数据职责 | 类型选择 |
| --- | --- |
| HTTP、工具参数、供应商输出 | Pydantic，边界运行时校验 |
| 内部值、计划和结果 | dataclass 或现有领域类型，适合时不可变 |
| 稳定字典协议 | TypedDict，明确可选键和更新语义 |
| 有限状态与受控动作 | Enum 或 Literal |
| 可替换资源 | Protocol，写明失败和关闭责任 |

未知外部 JSON 只在边界局部使用 Any/unknown，校验后进入业务，不以 cast 或类型提示代替验证。稳定协议不能用任意 dict 承载。Pydantic、领域对象与 ORM 仅在实际语义不同处转换，不机械复制同字段模型。

跨语言协议仅从desk-api/src/main/proto生成至src/desk_contracts顶层包，保持.proto导入布局；_pb2/_pb2_grpc/_pb2.pyi不手写、不提交，不套手写docstring或Ruff修复。CI及镜像构建先生成再打入AI wheel，运行不读取兄弟模块源码或JAR，不在服务启动时编译。生成类型在RPC适配器与用例类型间显式转换，字段存在性、枚举及业务校验按接口约定；浏览器Pydantic仍使用JSON合同。

区分必填、缺失、null、空串、空集合与清空，PATCH 使用字段存在性；可变默认值使用工厂。写入及工具输入拒绝未知字段，读取扩展按协议处理。业务 ID 使用规范字符串，sessionRef、claim_token 和供应商 ID 按各自合同，不统一当业务 UUID。

## 3. 中文说明

正式模块顶部写中文 docstring，说明该模块职责。类型、业务用例、公共协议、仓储动作、资源管理和复杂规则写中文 docstring，必要时用 Args、Returns、Raises、Attributes 说明范围、单位、空值、状态及恢复语义。

```python
def validate_citations(answer: str, evidence: list[Evidence]) -> CitationValidation:
    """核对回答引用是否来自本轮固定证据。

    Args:
        answer: 待校验正文，不含供应商内部字段。
        evidence: 按实际上下文顺序固定的证据。

    Returns:
        真实来源与非法编号的校验结果；无证据不视为校验通过。
    """
```

示例只说明风格。模块或类型说明按实际标注作者 yangaobo，不为每个局部变量重复作者。Pydantic 非自明字段用中文 Field(description=...)，约束由类型和校验器执行；内部字段写明租约、修订及可空性。简单属性不机械长注释，行内解释原因，不保留讨论历史、旧代码或空 TODO。

## 4. 异步与资源生命周期

async用于实际异步I/O，同步解码、文件及SDK调用明确放置边界，不长期阻塞事件循环。asyncio.run只在同步顶层使用。Java调用使用grpc.aio Channel/stub，HTTPX用于MinerU/ES/Qdrant等供应商HTTP；Channel、HTTP/模型客户端、数据库engine、流、句柄与线程/进程池由创建者关闭，限定所属进程与事件循环，AsyncSession不跨并发任务共享。

API进程通过FastAPI lifespan在同一事件循环启动/有界关闭grpc.aio服务，同时提供HTTP与RPC；任一监听启动失败整体readiness失败，不留下半启动服务。RPC Channel按进程/目标复用，验证TLS/主机名、服务凭据、方法白名单及用户授权；默认禁业务自动重试与无限wait-for-ready，显式Deadline受父剩余预算限制。receiver的同步RocketMQ SDK边界与业务grpc.aio是不同客户端，不跨事件循环复用资源。

每个 Task 有所有者、引用、异常收集和取消回收路径，相关任务使用结构化作用域。持久任务由独立 worker 进程按数据库领取，Web 的 BackgroundTasks 不承担可靠解析或付费生成。解析与排障分别设置有界并发、批量和内存预算。等待用户操作/补充时保存待答项并结束本轮，不把本地AskUser阻塞等待移入服务端worker。

统一截止时间覆盖排队、重试及执行，不让每次重试重新获得完整预算。CancelledError 必要清理后继续传播，只有接管业务终态的边界才转换。取消 to_thread 等待不能证明线程停止，并发槽在实际结束后释放；子进程先取得启动结果，再终止、有界等待和回收。租约过期不证明外部解析停止。

## 5. 数据、事务与执行权

SQLAlchemy访问desk_ai_{env}，运行用mysql+asyncmy，Alembic迁移用mysql+pymysql。API在lifespan就绪前通过线程执行upgrade head，失败中止启动；receiver/worker不发起迁移，部署时先完成API迁移再启动消费者。迁移与命令行维护使用同一连接的MySQL命名锁串行执行。开发迁移可复用本服务数据库账号，生产必须显式填写独立迁移账号。不用create_all，不在模块导入时执行DDL，不根据模型自动改表。已有手工结构先核对再显式stamp，后续变更新增revision，不修改已应用版本。MySQL DDL不承诺整段回滚，失败先核查实际结构；步骤见[数据库初始化与迁移](../sql/README.md)。

仓储输入包含明确范围、状态和归属，参数绑定 SQL，动态标识与排序来自服务器白名单，空集合不能省略条件变全表。列表查询不返回完整正文/证据，稳定排序及分页遵循[统一合同](Java后端规范.md#62-分页合同)，输出 total 为十进制字符串。

用例定义原子动作，关联状态与回执同事务，底层函数不自行 commit。网络、模型、MinerU、文件及人工等待在事务外：记录意图、执行、按原标识保存结果。提交后才返回持久成功或更新缓存。

索引和生成使用 claim_token/claim_until，短事务领取与续约，阶段/片段/终态回写核对状态、令牌和租约。恢复只接管已过期执行权，不因单个 Web 重启结束其他实例有效运行。诊断事实、输入/输出state_version、技术ticket_version及实际来源按系统设计维护；更正只追加不覆盖原事实，RETRACT必须指向同会话有效前驱。ASSISTANT_PROPOSAL仅承载HYPOTHESIS/OPEN_QUESTION且区分MODEL/RULE，只读工具只形成OBSERVATION；环境变化后的旧检查待复核，不能直接沿用。纯规则澄清的首次模型来源编号为空，不伪造ai_model_call。每次真实模型请求先落ai_model_call，逐次工具和实际上下文来源分别落表，费用/Token未知保留null；确定没有任何模型请求的规则轮可明确为0。失效执行权不允许迟到正文、假设或工具结果进入事实。索引按原阶段和远端任务对账；过期 RUNNING 问答变 INTERRUPTED，不自动重新付费生成。取消先提交终态并清空执行权，再尽力停止上游，迟到结果不能写正文或引用。

索引由独立receiver使用官方rocketmq-python-client的gRPC SimpleConsumer接收已提交事务消息，验证完整JSON信封后调用索引用例短事务落库，提交后ACK；同jobId异义进入有界重投/死信，不能ACK为成功。last_command仅SUBMIT→CANCEL，取消先到保留完整墓碑，晚到提交不复活，已终态取消保留执行结果。同步SDK仅在receiver的有界线程边界运行，AsyncSession留在所属事件循环，受理结果经明确桥接后ACK，不能丢弃未等待的受理协程。worker从AI MySQL领取，不在消费窗口运行MinerU或模型；API进程仅以IndexJobService/GetStatus供knowledge查询，无HTTP/RPC索引提交或取消旁路。Broker事务不代表消费恰好一次，不替代受理幂等、执行权或持久墓碑。

连接统一 UTC 和 READ COMMITTED，datetime(6) 在仓储边界显式转换，读回恢复时区语义；JSON 校验对象/数组及 schema_version，不能用 SQLite 替代 MySQL 锁和约束验证。

## 6. 解析、检索与模型

Python 自行编写 MinerU 适配、切片、Embedding 批处理、ES/Qdrant 召回、RRF、证据预算、Prompt、引用、历史、取消、反馈和草稿。百炼仅为 Embedding 与 Chat/结构化生成 API，不使用托管应用/知识库替代业务流程。固定流程先用类型化 Python，分支确有需要再评估流程框架。

原件经knowledge的ReadOriginal server-stream取得，先核对版本元信息，再按连续offset写有界临时文件；总量不超20MiB、每块至多64KiB，最终RPC成功并核对总量/SHA-256后才交解析。断流清理未校验文件，需要时从头读同一版本，不持有OSS凭据或任意URL。MinerU单文件任务先保存意图及远端ID，未知创建结果先对账，不能换ID重试绕过。只跟随允许的同源协议地址，禁重定向，ZIP限条目、字节、解压大小并防路径穿越；PDF正文、表格、物理页及页数校验后才固定切片清单。Markdown至多1MiB，确定性UTF-8/标题/锚点/行区间解析，parser_state=NOT_REQUIRED且无MinerU任务和页数；不从模型输出生成来源定位，两种格式同样校验摘要和双索引。

MySQL 保存正文、片段和运行事实，ES/Qdrant 是独立可检索副本。客户端按进程复用，稳定 chunk ID 幂等写入，分别核对完整 ID、空间及查询可见性，两路成功才提交索引成功。Qdrant 无快照需按原空间重新 Embedding，费用和模型可用性独立核对。

knowledge 返回当前有权任务集合，两路均查询前过滤，融合后回查 MySQL，交付前复核实际来源。空授权不发无过滤查询，一路故障不能伪装完整单路答案。候选不是证据，逐次裁剪后固定实际来源；跨工具步骤、摘要/历史依赖写ai_source_usage并取传递并集。技术工单事件、用户反馈及假设分别授权并标来源，历史/事实摘要/工具结果/草稿重核所有实际来源，不能只查最终引用或本地事实ID。适用规则逐项匹配，未知条件先补问。

模型输出通过类型化合同和引用校验，不能直接执行 SQL、shell、文件写入、知识发布或正式建单。客户CREATE_TICKET草稿按确认事实模板预填；技术ADD_RECORD/RESOLVE_TICKET草稿采用源运行已有经校验结构化结果，创建草稿不额外调用模型。人工编辑后交ticket原用例，校验用途/目标/期限/工单版本及动作权限；AI不写已消费或正式结果。模型建议不能写成已执行动作，用户声称完成只记USER_REPORT。

## 7. API、SSE 与失败

普通JSON使用类型化Result[T]，code/message/data/traceId与Java一致：唯一成功0、系统异常1、公共1xxx及AI的5xxx按[错误合同](../设计/接口约定.md#12-错误码与边界转换)维护，不自创另一套编号。成功/失败均保留data与traceId字段，失败data=null；Pydantic lowerCamelCase别名明确，不启用全局exclude_none删除协议字段。业务用例返回领域结果，不返回HTTP包装；错误定义及BusinessError保留在errors，HTTP和RPC各自显式转换，不在枚举携带传输状态。

分页、Identifier格式及必填版本可在schemas中维护真实复用基型，但不照搬Java Serializable/三件套。Pydantic区分字段存在性和null，集合元素逐个校验业务ID，sessionRef/claimToken保持各自UUID合同。long版本、total和Token等公开序列化为十进制字符串，页码/候选数/分数仍为数值；类型化Enum保留既定字符串，不用中文描述持久化。

管理POST同样校验会话与CSRF，内部凭据不替代用户权限；HTTP Cookie DESK_SESSION及X-CSRF-TOKEN沿user核验，服务凭据失败不得误报用户失效。422约束错误转换为安全1001且保留HTTP422，不回显完整输入和内部异常。健康、文件、OpenAPI和SSE使用独立协议。

RPC成功直接返回类型化message，失败使用gRPC Status及google.rpc.Status中的RpcErrorDetail，按接口约定映射到浏览器错误；服务凭据/TLS失败是依赖故障，不能触发用户退出。aio拦截器落实认证、方法权限、Deadline、可信W3C追踪，端口只在私网，生产关闭reflection；HTTP、RPC均调用同一用例，原Cookie仅转user会话核验。标准gRPC健康只做受控私网检查，不发起付费模型请求。

长任务持久受理后返回可查询 ID。Web 负责 SSE 连接、心跳、编码和释放，Service 提供已授权业务事件及持久终态。先展示阶段，正文与引用校验并提交后才交付，当前不承诺未经校验的逐 Token 正文。断流只结束订阅，重新查询原运行，不自动重发问题。

只捕获本层能处理的异常。适配器将外部拒绝、网络失败、超时、非法协议及结果未知转换为明确错误，顶层安全记录并结束；禁止 except Exception 后返回 None/空列表。SSE 提交前返回 HTTP 错误，提交后用安全事件，不暴露堆栈和上游内部地址。

日志参数化输出统一JSON，字段与Java一致，关联traceId、job/generation/task ID、阶段、耗时和错误码。使用标准logging formatter，装配归bootstrap，文件滚动及队列有界。OpenTelemetry API/SDK/Zipkin JSON exporter同版锁定，在HTTP、接收、持久worker及实际外调边界显式创建span，使用W3C上下文和有界批量投递；不安装无消费者的自动instrumentation全家桶。索引和问答首次受理时保存受控trace_context，worker按scope接续，恢复产生新的执行span，重复消息不覆写原上下文。最终ELK/Zipkin合同见系统设计，不开发另一套日志发送器。密码、Session、提示词、模型正文、资料、设备敏感标识和SQL参数不进普通日志，日志不替代持久任务事实。Zipkin或Logstash故障不应使业务失败；trace_context无baggage或凭据且不参与request_hash，非法追踪仅丢弃并新建。

## 8. 配置与验证

设置集中类型化，按启用能力校验。开发将desk-ai/.env.example复制为同目录.env，工作目录设为desk-ai；get_settings使用Pydantic Settings原生dotenv源读取，进程变量优先于文件，支持DESK_AI_前缀及双下划线嵌套字段。DESK_AI_ENVIRONMENT=prod必须由进程环境提供，生产入口不读取.env，开发文件不能自行切换生产。直接构造Settings不隐式读取文件，测试显式注入隔离配置；应用通过create_app工厂创建，不在导入main时读取私密文件。相对文件路径以所选配置文件所在运行目录为基准。业务函数不散落读环境变量，Java/Python 不互相回退密钥。客户端、AsyncSession 和大正文不放进流程状态，状态只存必要标识与小值。

API文档在FastAPI装配时按环境显式设置openapi_url/docs_url，dev允许私网GET /openapi.json与/docs，prod两者关闭，/redoc不额外启用；Gateway不公开代理这些路径。使用框架实际OpenAPI版本，不为模仿Java仅改声明版本伪造3.0。文档来自Pydantic字段、有限枚举、JSON Result及真实201/202/文件/SSE媒体类型；RPC以.proto维护，MQ以版本化JSON合同维护。文档开关不改变资源授权、CSRF或方法白名单。

在desk-ai执行uv sync --locked、uv run --locked ruff check --config pyproject.toml . ../.github/scripts、uv run --locked ruff format --check --config pyproject.toml . ../.github/scripts、uv run --locked mypy、uv run --locked pytest。mypy检查全部手写src/servicedesk_ai，启用Pydantic插件及未标注函数体检查；生成合同由其构建校验，不以批量Any/cast、ignore_errors或跳过业务包消除类型问题。持久配置和上下文在读取边界校验为GenerationPipeline/GenerationContext，数据库行使用真实投影类型。验证响应码与Java/Gateway公共合同、未知错误安全回退、文档开关和null/数值/枚举映射；构建生成代码排除手写规范检查，但验证其包导入及线兼容。

测试覆盖协议、运行时校验、授权、幂等、执行权、取消、资源与恢复。替身仅替换外部依赖，不能用 mock 证明 MySQL 锁、索引或解析正确。测试装配按需放 tests/support，不让测试文件互相导入，不依赖私密环境或共享业务库；真实中文PDF、Markdown定位、反馈分支、事实更正、技术私有会话、逐次调用未知、模型、双索引和snapshot分项验收，证据随实施任务提供。
