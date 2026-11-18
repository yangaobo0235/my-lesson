# Python后端规范

本文规定 desk-ai 的协议、用例、类型、资源、事务和 AI 边界。通用要求见[开发规范](开发规范.md)，目录见[项目结构](../设计/项目结构.md)，业务见[系统设计](../设计/系统设计.md)，数据、HTTP、RPC与消息见[数据库设计](../设计/数据库设计.md)和[接口约定](../设计/接口约定.md)。Python 是业务后端，不能把权限、检索或模型决策交给前端。

## 1. 分层与用例

按PDF/文本索引、检索、会话、持久诊断任务/检查反馈、任务内假设、调用账目及有界执行、动作草稿和反馈的完整动作组织，不按每张表生成一套服务，也不照搬 Java 三件套。

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

模块、包、函数和参数 snake_case，类型/异常 PascalCase，常量 UPPER_SNAKE_CASE。动作说明业务及副作用，例如 create_index_job、cancel_run、create_action_draft。绝对导入 servicedesk_ai.*，不用 sys.path 绕过布局，不跨模块调用私有函数，不从生产代码导入测试。__init__.py 不启动数据库、网络或任务。

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

索引与Run使用claim_token/claim_until，短事务领取/续约，结果回写核对状态、令牌及租约。Run过期收敛INTERRUPTED，未落库调用UNKNOWN，不自动重新付费；索引按原远端身份和固定片段清单恢复。取消先提交终态并清空执行权，迟到正文不能复活任务。

Conversation、DiagnosisTask、Run、Message职责分离；Run记录阶段/循环位置，ModelCall/ToolCall承载持久外调边界，不另建Step。task_state_version、diagnosis_context_version、ticket_row_version、ticket_event_sequence不互换。DiagnosticCheck合并一次澄清/检查及其唯一反馈请求，回答先与消息、事实和TaskEvent原子提交，再申请下一Run；无进程内Future、默认回答或跨天占用worker。复查新实例，人工ANSWERED仅代表反馈，执行情况由规范答案表达。假设在任务有界JSON投影中保存，事实更正和环境变化同事务失效相关假设，变化前后写事件，不混入事实。修订内规程检查前置条件由宿主验证，无通用分支图或表达式解释器。

执行入口必须保留单一任务权威：services负责状态/事务，diagnosis只负责策略与类型化行动，repositories执行有界查询/CAS。队列受理锁ai_execution_gate→conversation→task，worker不凭进程锁保证唯一。每次外调先持久Call意图及所属claim_token，提交时核对父Run/IndexJob状态、令牌和未过期租约；Run阶段与调用结果同步更新。新Run只复用完整且仍有效的结果并记录旧call身份，易变业务重新读取，未知效果只对账。Chat、索引/查询Embedding、摘要、重排均记ai_model_call，分别归Run或IndexJob；未知用量保持null，确定无调用才0。累计任务预算从账目和未结预留核对，不因新轮清零。

索引由独立receiver使用官方rocketmq-python-client的gRPC SimpleConsumer接收已提交事务消息，验证完整JSON信封后调用索引用例短事务落库，提交后ACK；同jobId异义进入有界重投/死信，不能ACK为成功。last_command仅SUBMIT→CANCEL，取消先到保留完整墓碑，晚到提交不复活，已终态取消保留执行结果。同步SDK仅在receiver的有界线程边界运行，AsyncSession留在所属事件循环，受理结果经明确桥接后ACK，不能丢弃未等待的受理协程。worker从AI MySQL领取，不在消费窗口运行MinerU或模型；API进程仅以IndexJobService/GetStatus供knowledge查询，无HTTP/RPC索引提交或取消旁路。Broker事务不代表消费恰好一次，不替代受理幂等、执行权或持久墓碑。

连接统一 UTC 和 READ COMMITTED，datetime(6) 在仓储边界显式转换，读回恢复时区语义；JSON 校验对象/数组及 schema_version，不能用 SQLite 替代 MySQL 锁和约束验证。

## 6. 解析、检索与模型

Python 自行编写 MinerU 适配、切片、Embedding 批处理、ES/Qdrant 召回、RRF、证据预算、Prompt、引用、历史、取消、反馈和草稿。百炼仅为 Embedding 与 Chat/结构化生成 API，不使用托管应用/知识库替代业务流程。取证仅接收只读工具提议，无工具提议则进入最终结构化阶段，中间文本不发布或作为指令；最终输出每Run至多一次有预算的纠正；非法JSON、无事实支持的假设确定状态属于输出错误，不能伪报环境变化。权限、租约、环境或依赖失败仍不重试。运行路线固定为有限领域状态机、Pydantic合同和薄模型SDK适配；本目标不引入LangGraph/PydanticAI/Temporal运行时，不建设通用DSL。

原件经knowledge的ReadOriginal server-stream取得，先核对版本元信息，再按连续offset写有界临时文件；总量不超20MiB、每块至多64KiB，最终RPC成功并核对总量/SHA-256后才交解析。断流清理未校验文件，需要时从头读同一版本，不持有OSS凭据或任意URL。MinerU单文件任务先保存意图及远端ID，未知创建结果先对账，不能换ID重试绕过。只跟随允许的同源协议地址，禁重定向，ZIP限条目、字节、解压大小并防路径穿越；PDF正文、表格、物理页及页数校验后才固定切片清单。Markdown至多1MiB，确定性UTF-8/标题/锚点/行区间解析，parser_state=NOT_REQUIRED且无MinerU任务和页数；不从模型输出生成来源定位，两种格式同样校验摘要和双索引。

MySQL 保存正文、片段和运行事实，ES/Qdrant 是独立可检索副本。客户端按进程复用，稳定 chunk ID 幂等写入，分别核对完整 ID、空间及查询可见性，两路成功才提交索引成功。Qdrant 无快照需按原空间重新 Embedding，费用和模型可用性独立核对。

knowledge 返回当前有权任务集合，两路均查询前过滤，融合后回查 MySQL，交付前复核实际来源。空授权不发无过滤查询，一路故障不能伪装完整单路答案。read_evidence读取完整章节/步骤并显式续读，父子展开同修订且重新授权；普通换版与风险撤回/访问撤权分开。候选只用于召回时不记实际输入，元信息进入模型即登记来源；逐次选择后固定实际来源；跨工具步骤、摘要/历史依赖写ai_source_usage并取传递并集。技术工单事件、用户反馈及假设分别授权并标来源，历史/事实摘要/工具结果/草稿重核所有实际来源，不能只查最终引用或本地事实ID。适用规则逐项匹配，未知条件先补问。

Token预算采用固定供应商适配估算器和安全余量，字节限制单独控制存储/传输；摘要放Run.context_manifest，全部根来源展开为本轮source_usage并保护未答/矛盾/前置条件；structured_result引用只用本轮稳定source_number，不能混同实际输入来源全集。模型输出通过类型化合同和引用校验，不能直接执行 SQL、shell、文件写入、知识发布或正式建单。CREATE_TICKET草稿按确认事实模板预填且不依赖成功Run；其余用途按接口合同读取有权事实/已校验建议，创建草稿不额外调用模型。人工编辑后交ticket原用例，校验用途/目标/期限/工单版本及动作权限；AI不写已消费权威状态，正式采用查询ticket统一回执；草稿编辑新增不可变修订，确认绑定根、修订和内容摘要。模型建议不能写成已执行动作，用户声称完成只记USER_REPORT。

## 7. API、SSE 与失败

模型调用已声明工具但参数不符合Pydantic合同时，在工具用例内记录原提议及INVALID_ARGUMENT回执；不外调、不生成来源、不回显ValidationError详情。纠正和重复提议继续计入原预算，工具白名单/执行权/事务异常不能被该分支吞掉。动态输出Schema可移除不可达定义，不能删掉仍被引用的约束或将未读规程转为完整操作依据。

普通JSON使用类型化Result[T]，code/message/data/traceId与Java一致：唯一成功0、系统异常1、公共1xxx及AI的5xxx按[错误合同](../设计/接口约定.md#12-错误码与边界转换)维护，不自创另一套编号。成功/失败均保留data与traceId字段，失败data=null；Pydantic lowerCamelCase别名明确，不启用全局exclude_none删除协议字段。业务用例返回领域结果，不返回HTTP包装；错误定义及BusinessError保留在errors，HTTP和RPC各自显式转换，不在枚举携带传输状态。

分页、Identifier格式及必填版本可在schemas中维护真实复用基型，但不照搬Java Serializable/三件套。Pydantic区分字段存在性和null，集合元素逐个校验业务ID，sessionRef/claimToken保持各自UUID合同。long版本、total和Token等公开序列化为十进制字符串，页码/候选数/分数仍为数值；类型化Enum保留既定字符串，不用中文描述持久化。

管理POST同样校验会话与CSRF，内部凭据不替代用户权限；HTTP Cookie DESK_SESSION及X-CSRF-TOKEN沿user核验，服务凭据失败不得误报用户失效。422约束错误转换为安全1001且保留HTTP422，不回显完整输入和内部异常。健康、文件、OpenAPI和SSE使用独立协议。

RPC成功直接返回类型化message，失败使用gRPC Status及google.rpc.Status中的RpcErrorDetail，按接口约定映射到浏览器错误；服务凭据/TLS失败是依赖故障，不能触发用户退出。内部gRPC在通道创建时关闭隐式HTTP代理，Zipkin关闭其专用Session的环境代理继承，不靠开发机NO_PROXY维持正确性。RocketMQ官方SDK未暴露Channel选项，receiver在创建SDK前仅清除本进程继承的grpc_proxy及HTTPS/HTTP代理变量，覆盖路由后新建连接；不改系统环境、不动态替换SDK函数、不改变TLS或其他应用进程。TLS、认证及调用预算仍按原合同执行。aio拦截器落实认证、方法权限、Deadline、可信W3C追踪，端口只在私网，生产关闭reflection；HTTP、RPC均调用同一用例，原Cookie仅转user会话核验。标准gRPC健康只做受控私网检查，不发起付费模型请求。

长任务持久受理后返回可查询 ID。Web 负责 SSE 连接、心跳、编码和释放，Service 提供已授权业务事件及持久终态。先展示阶段，正文与引用校验并提交后才交付，当前不承诺未经校验的逐 Token 正文。断流只结束订阅，重新查询原运行，不自动重发问题。

只捕获本层能处理的异常。适配器将外部拒绝、网络失败、超时、非法协议及结果未知转换为明确错误，顶层安全记录并结束；禁止 except Exception 后返回 None/空列表。SSE 提交前返回 HTTP 错误，提交后用安全事件，不暴露堆栈和上游内部地址。

日志参数化输出统一JSON，字段与Java一致，关联traceId、job/run/task ID、阶段、耗时和错误码。使用标准logging formatter，装配归bootstrap，文件滚动及队列有界。OpenTelemetry API/SDK/Zipkin JSON exporter同版锁定，在HTTP、接收、持久worker及实际外调边界显式创建span，使用W3C上下文和有界批量投递；不安装无消费者的自动instrumentation全家桶。索引和问答首次受理时保存受控trace_context，worker按scope接续，恢复产生新的执行span，重复消息不覆写原上下文。最终ELK/Zipkin合同见系统设计，不开发另一套日志发送器。密码、Session、提示词、模型正文、资料、设备敏感标识和SQL参数不进普通日志，日志不替代持久任务事实。Zipkin或Logstash故障不应使业务失败；trace_context无baggage或凭据且不参与request_hash，非法追踪仅丢弃并新建。

## 8. 配置与验证

供应商HTTP拒绝和传输故障由模型适配器转换为安全ModelProviderError；明确拒绝与未知效果分别落调用账目，Run诊断遵循[接口约定](../设计/接口约定.md#9-会话诊断任务运行与人工确认)，不把欠费当模型输出错误继续批量试验。命令行worker/receiver使用固定应用logger，确保以__main__运行时仍进入统一JSON处理器；坏消息只记录校验后的消息身份、投递次数和异常类型，不回显载荷。

设置集中类型化，按启用能力校验。开发将desk-ai/.env.example复制为同目录.env，工作目录设为desk-ai；get_settings使用Pydantic Settings原生dotenv源读取，进程变量优先于文件，支持DESK_AI_前缀及双下划线嵌套字段。DESK_AI_ENVIRONMENT=prod必须由进程环境提供，生产入口不读取.env，开发文件不能自行切换生产。直接构造Settings不隐式读取文件，测试显式注入隔离配置；应用通过create_app工厂创建，不在导入main时读取私密文件。相对文件路径以所选配置文件所在运行目录为基准。业务函数不散落读环境变量，Java/Python 不互相回退密钥。客户端、AsyncSession 和大正文不放进流程状态，状态只存必要标识与小值。

API文档在FastAPI装配时按环境显式设置openapi_url/docs_url，dev允许私网GET /openapi.json与/docs，prod两者关闭，/redoc不额外启用；Gateway不公开代理这些路径。使用框架实际OpenAPI版本，不为模仿Java仅改声明版本伪造3.0。文档来自Pydantic字段、有限枚举、JSON Result及真实201/202/文件/SSE媒体类型；RPC以.proto维护，MQ以版本化JSON合同维护。文档开关不改变资源授权、CSRF或方法白名单。

在desk-ai执行uv sync --locked、uv run --locked ruff check --config pyproject.toml . ../.github/scripts、uv run --locked ruff format --check --config pyproject.toml . ../.github/scripts、uv run --locked mypy、uv run --locked pytest。mypy检查全部手写src/servicedesk_ai，启用Pydantic插件及未标注函数体检查；生成合同由其构建校验，不以批量Any/cast、ignore_errors或跳过业务包消除类型问题。持久配置和上下文在读取边界校验为RunPipeline/RunContext，数据库行使用真实投影类型。验证响应码与Java/Gateway公共合同、未知错误安全回退、文档开关和null/数值/枚举映射；构建生成代码排除手写规范检查，但验证其包导入及线兼容。

测试覆盖协议、运行时校验、授权、幂等、执行权、取消、资源与恢复。替身仅替换外部依赖，不能用 mock 证明 MySQL 锁、索引或解析正确。测试装配按需放 tests/support，不让测试文件互相导入，不依赖私密环境或共享业务库；真实中文PDF、Markdown定位、反馈分支、事实更正、技术私有会话、逐次调用未知、模型、双索引和snapshot分项验收，证据随实施任务提供。

初始上下文选取须先扣除来源目录、阶段说明及纠正的收束预留，再分配历史和工具正文空间；与执行循环共用预算门槛，完整请求仍在每次调用前核验，不能通过提高额度或裁剪必要证据掩盖容量不足。取证阶段的模型自述不作为下一轮证据；带工具调用的回复只继承完整调用协议和工具回执，原模型响应仍保存于调用账目。规程澄清只能补问已读检查要求的未知环境字段，不得复用审核键或把检查变成自由问题；在提议准备边界拒绝，再走原有一次纠正。规程候选筛选复用提交用例的规则，不能另写一套前置条件引擎；版本、依赖或授权失败不能包装为空候选继续生成。事实及反馈的先后来自持久记录时间和状态版本，不由提示词排列或模型猜测决定。

最终阶段说明及动态Schema必须在预算判断前组装，与实际发送使用同一请求对象；取证合同装不下时仍检查完整最终请求能否收束。审核检查可用性按来源号及检查键逐项展示，不只给无身份的拒绝列表。工具上下文的结构化投影不改持久回执，去重仅共享同修订完全相同的安全条件，正文与局部条件完整保留；重复压缩须幂等。

原始任务目标可说明复测对象，不能证明故障目前仍在；已执行操作与已复测恢复分别核对。核验输出每个候选片段的assessments（身份、类别和来源号）、整体判定、固定问题码及至多四个disputedPassages编号，不生成解释或重抄原文。candidatePassages由宿主按字段、句界和100字符上限完整分段候选，输入仍受原上下文预算约束。返回编号只用于取得候选原句；未知编号不产生纠正文字，但不能撤销拒绝判定。纠正仅传递宿主取回的原文，每条最多40字符，仍标为user数据。核验协议失败不得作为候选错误触发重写。

最终阶段不再注册工具，已完成调用转换为user角色的只读结果数据，移除调用参数、协议及不再需要的候选定位。完整正文、共享安全条件引用、来源身份、反馈事实关联、消息角色与事实来源类型、章节及分页完整性、失败和空结果均保留；不得通过截断安全条件或删除负面回执降低输入量。原调用和完整回执仍在账目中保存，转换不得修改原对象。发现阶段继续保持完整工具调用/回执配对。

排障最终自由正文须有实际完整资料、诊断记录或已输入且版本有效的检查反馈，否则直接交付宿主的证据不足说明；用户描述、初始事实、澄清和候选预览不能单独打开生成门槛。普通咨询不受此业务依据门槛约束。门槛不能替代提交时操作前提核验或语义审阅。排障自由正文、假设、草稿和自由澄清在公开提交前通过diagnosis/grounding.py进行无工具的依据核验；审核检查原指令及宿主状态说明不进入语义裁定，仍执行来源、角色、前置条件及最终事务校验。输入只含当前请求、原始任务目标与省略提示、环境、实际引用正文、完整人工反馈及已读检查的宿主前提核验；候选不能作为自身证据。MESSAGE回执显式携带messageRole，FACT回执携带factSourceType；旧快照缺失时为UNKNOWN，不按标题猜测。历史助手消息只证明过去说过什么，不能作为设备状态或技术结论的独立证据。检查反馈来源必须含完整答案、原检查类型及指令、原消息摘要与result_fact_id；原指令只解释用户反馈针对的操作，不授予本轮重新执行权限，事实投影保留factId；不能因聊天窗口省略原消息或事实仅存执行摘要而误判支持关系。核验走原ModelCalls账目、授权、租约和预算，以review_of_call_id关联候选。固定拒绝码进入宿主纠正说明，宿主按片段编号取得的候选原文仅作为user数据，不把核验模型文字变成系统指令；无效辅助编号丢弃但拒绝保持有效。只纠正一次，再次无效或预算不足交付固定证据不足说明。已收到供应商响应但协议不合格时保存固定适配器原因到response_payload.protocol_error，保留FAILED/INVALID_MODEL_RESULT和已确认用量；不得保存原始失败正文、参数或隐藏推理，不重试原无效提议；取证白名单拒绝的无工具收束按本节合同执行。

取证阶段模型提出白名单外工具时，整批提议不执行，调用仍记FAILED/INVALID_MODEL_RESULT。仅在供应商响应已确认完整用量且诊断Run尚处取证阶段时，宿主关闭取证能力，按此前有效证据进入无工具最终阶段；没有充分证据则直接给出证据不足结果。不得再次尝试原工具、继承非法回应正文或假装取得新来源。后续候选及核验继续消耗原Run/任务预算；最终阶段协议失败、未知计量、授权、环境、租约及依赖失败仍终止，不走此收束路径。

连续三次取证无新增依据只关闭工具，使用既有有效证据进入统一最终阶段；没有诊断依据则由宿主提交证据不足说明，不追加模型生成。不得直接将已有合法检查改为转人工；后续仍复核执行权、来源授权、检查前提和原Run/任务预算，经原依据核验与最终事务提交，不重置预算或再次取证。

审核规程的宿主状态与可选模型分析分别验收。模型假设、记录/解决草稿及转交对象作为一组核验；仅这组内容被语义拒绝或其核验预算不足时，整组不交付，保留原审核检查或宿主结论进入最终事务校验，不据此更改既有假设。原候选和已发生的核验保留调用账目。自由澄清及普通回答仍整份纠正或拒绝；协议、身份、来源、环境和依赖失败不能走此路径。检查前置条件及当前最新恢复反馈仍在提交锁内复核，舍弃可选内容不等于检查已通过或已执行。

逐段核验最多覆盖64个非空片段，必须各出现一次。冒号引导的标题与后续问题或断言合并后再按100字符上限分段，不把标题单独作为无对象的事实，也不省略其后的断言。SUPPORTED在输出Schema中通过完整对象联合分支要求至少一个来源号或上下文字段；每个分支独立声明片段身份、类别和全部依据字段，避免依赖供应商合并外层字段与局部条件，读取时再次校验；所列来源号须是本次核验实际读取的CONTENT来源号，或绑定确实提供的request/taskGoal/environment/ticket上下文；环境和工单不能支持资料技术断言。QUESTION仅容纳中性问题，夹带原理或原因须按事实断言核验；阅读范围和未读内容说明仍按断言核验，不设免审类别；HYPOTHESIS仅允许结构化假设字段。任何UNSUPPORTED不得整体批准。候选可见文字仅在片段清单出现一次，元数据保留状态、引用、假设及检查身份，不重复整份正文。宿主拒绝缺段、重复编号、虚构来源或上下文及正文伪装假设；覆盖协议失败不交付候选，超预算安全收束。该检查证明审核覆盖及来源身份，不能证明模型的语义判断必然正确。

发现阶段按当前账目收窄工具，并显式提供当轮工具清单；历史回执不授予再次调用已收回工具的权限。一次发现Chat及一次查询Embedding之后仍须保留最多四次最终回答/核验/纠正调用；余量不足时不再提供search_knowledge。提供付费搜索的阶段仅允许一个工具提议，供应商多提议响应拒绝；其他只读工具仍可有界并行。当已知知识候选全部为审核规程、但尚无本轮完整审核检查时，阶段只允许并要求load_procedure，模型选择文档和检查；普通或混合资料不套用此约束。身份、原规程授权及预算继续独立核对，阶段约束不能绕过安全条件。


领域评测放tests/evaluation，生产不得导入题集/模拟器。用户模拟观察来自环境状态，隐藏原因/评分不进Agent，按故障实例分组冻结开发/验证/保留集。模型效果、确定性工程约束、真实设备和恢复分别验证；测试缺少判定条件计INVALID，不能默认成功。对照与消融及发布门槛以系统设计为准。

诊断写工具在Run内仅返回类型化提议，最终提交事务统一落检查/草稿/领域事实/任务事件并比较input_state_version；失败或取消不留可操作半成品。工具完整结果或可重建定位与Call一并持久化，安全摘要不能替代恢复输入。当前假设和事件派生内容同样核验来源Run权限，不能绕过撤权过滤。

Schema纠正不复制整份无效候选，最多传递八个错误的八级字段路径及类型码，每个名称最多64字符，并标记定位缩短；不携带原输入、异常解释或上下文。错误定位作为不可信user数据，按原证据重新生成，完整候选仍留调用账目。领域拒绝沿固定安全原因纠正，依据拒绝沿宿主原句定位纠正；预算、一次纠正上限及最终校验保持。答案类型合同明确只有CHOICE携带选项、只有NUMBER携带数值范围，其他类型对应空数组或null，宿主继续严格拒绝混用。
