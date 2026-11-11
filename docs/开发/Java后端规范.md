# Java后端规范

本文规定 Java 后端的分层、模型、SQL、接口、注释与运行边界。通用方法见[开发规范](开发规范.md)，模块与类型归属见[项目结构](../设计/项目结构.md)，业务与字段见[系统设计](../设计/系统设计.md)和[数据库设计](../设计/数据库设计.md)，公开协议见[接口约定](../设计/接口约定.md)。示例说明目标写法，不代表类型已存在，不要求生成无消费者接口。

## 1. 分层与依赖

业务沿 Controller → Service 接口 → Service 实现 → Mapper 执行。任务和内部入口调用同一完整业务动作，Service 按需组合其他 Service 与独立技术组件。

| 位置 | 职责 | 限制 |
| --- | --- | --- |
| controller | HTTP 绑定、参数校验、取得操作者、调用动作、包装响应 | 不访问 Mapper，不维护业务事务 |
| rpc | 跨语言gRPC服务端、认证/Deadline/错误及类型转换 | 复用Service，不复制Controller业务或套Result |
| service、service.impl | 资源授权、业务校验、状态、并发、事务与编排 | 不接收 Servlet、Cookie、MultipartFile、ResponseEntity 或 SSE 连接 |
| mapper | 查询、投影、统计和条件写入 | 不读会话，不调用 Service，不决定业务流程 |
| model | 输入、内部结果、持久化、输出、枚举与值对象 | 不调用数据库或外部服务 |
| component | Feign、OSS、消息、限流、Web 编码等独立技术能力 | 不用来容纳复杂业务或无主实体聚合 |
| task | 本地定时触发、扫描及执行入口 | 不复制 Service 的规则和终态维护 |
| security、config | 安全链、身份适配、框架装配、配置校验 | 不代替资源级业务授权 |
| error、exception、typehandler | 错误定义、边界转换及必要持久化映射 | 保持独立职责 |

业务服务只操作所属 MySQL 库，不跨库联表，不引用兄弟服务的 Entity、Mapper、ServiceImpl。远端事实通过内部合同取得，供应商和 SDK 类型留在适配边界。Gateway 使用 WebFlux，不引入 MVC、阻塞 Mapper 或会话数据库。

desk-common承载稳定基础合同、通用业务异常、枚举接口、标识校验与字段级序列化，统一使用base、exception、validator、serialize等职责包，具体类型及消费者见项目结构。只声明这些能力实际需要的Validation、Jackson、文档注解和编译期依赖，不带应用starter或自动装配。公共类型不以“已有两个Maven消费者”为准入门槛；职责稳定且有真实消费者即可共享，后续实施必须接入使用者，不能只建模板。

各服务独立持有Entity和业务枚举，common不集中四个库的持久模型，也不放角色、业务错误全集、身份上下文、Mapper、Web/Security/MyBatis配置或外部客户端。JacksonConfig、EnvironmentConfig、SwaggerConfig等应用装配留在所属应用；公共层只提供无启动副作用的类型与工具。MVC、WebFlux和Python不共用一套框架装配，跨服务共享的是线合同。

desk-api维护Java内部Form/DTO/Feign及跨语言.proto唯一源，按数据所有者分包，不依赖应用；调用方显式装配实际客户端、认证、预算及异常转换，不能将Entity或对外VO移入合同库。Java↔Python从同一.proto生成message/stub；Java生成物编入api普通JAR，Python生成物归自身构建制品，均不提交或手改。手写模型的普通class、Serializable及中文Javadoc要求不适用于生成物；合同中文说明写在.proto，生成类型仅在适配边界转换。具体依赖见项目结构。

单例 Bean 使用构造器注入及 final 依赖，不保存当前用户、请求、任务的可变状态。Controller 依赖 Service 接口，依赖字段使用 ticketService、documentService 等明确名称，不统一叫 service。

## 2. Controller 与 HTTP

### 2.1 入口拆分

各应用保持一个启动入口，Controller 按 auth、app、admin、internal、common 及稳定业务职责组织。不是每个服务都必须建立所有包；一个 Controller 负责一个资源或独立用例，不建立万能 AdminController，不按关联表、片段表自动生成入口。

同一实体可以支撑客户端和管理端两个 Controller。内部身份核验、原件读取、索引任务受理不是普通管理 CRUD。包移动或拆类本身不改变 URL，路径与方法以接口约定为准。

### 2.2 请求与响应

管理普通 JSON 统一 `POST /api/v1/admin/{resource}/{action}`，资源单数，资源及动作小写短横线，基础动作为 list、query、add、update、delete，其他用 publish、close、replay-command 等业务名。只提供真实允许的动作。

筛选、ID、版本和可写字段通过`@Valid @RequestBody` Form绑定，list/query也执行会话与CSRF校验。只需ID用common/base/IdForm，只需必填版本用VersionForm，同时需要ID和必填版本用IdVersionForm；其他业务字段使用具名动作Form。无输入不造空Form，路径已有ID不要求请求体再提供同一ID。公共字段与具体动作的适用条件见第5节，不能为继承改变输入合同。

普通JSON显式返回`common/base/Result<T>`，先用有明确类型的局部变量接收Service结果，再调用Result.success(data)；无数据动作先执行Service再返回Result.success()。Service不返回Result，Entity和MyBatis-Plus IPage不直接暴露，不使用裸Result、`Result<Object>`或Map拼响应外壳。

| 场景 | 返回类型 | 合同 |
| --- | --- | --- |
| 详情、概况、当前身份 | `Result<具名VO>` | 只返回授权后的字段 |
| 分页 | `Result<PageVO<具名VO>>` | common分页输出，total为字符串 |
| 只需返回新资源ID | `Result<String>` | 仅在消费者确实只需ID时使用 |
| 工单创建、预约和动作回执 | `ResponseEntity<Result<具名VO>>`或`Result<具名VO>` | 保留ticketId/bookingId/版本/状态等既定数据，不能缩成字符串ID |
| 更新等无有效载荷动作 | `Result<Void>` | 先调用Service，再返回无数据成功 |
| 202索引或运行受理 | `ResponseEntity<Result<具名VO>>` | 只有满足该用例的持久提交条件才返回202，提供原身份查询 |
| 原件、健康、OpenAPI | `ResponseEntity<Resource>`、流或框架类型 | 保留200/206/416及协议头，不包Result |
| 内部gRPC | 生成的message/stream及gRPC Status | 不套Result，转换后复用同一Service |

普通JSON的唯一成功业务码为Result.SUCCESS_CODE=0，HTTP仍保留200/201/202等真实状态；失败编号、默认状态及跨语言转换统一见[错误合同](../设计/接口约定.md#12-错误码与边界转换)。有响应头或非200状态时使用ResponseEntity，不能为统一签名丢掉创建和受理语义。

```java
/**
 * 提供知识修订的管理查询入口。
 *
 * @author yangaobo
 */
@RestController
@RequestMapping("/api/v1/admin/document")
@RequiredArgsConstructor
public class DocumentAdminController {

    private final IDocumentService documentService;

    private final CurrentActor currentActor;

    /**
     * 查询操作者有权管理的资料修订。
     *
     * @param form 修订查询参数
     * @return 修订详情
     */
    @PostMapping("/query")
    public Result<DocumentDetailVO> query(@Valid @RequestBody IdForm form) {
        ActorIdentity actor = currentActor.requireIdentity();
        DocumentDetailVO data = documentService.getDocument(actor, form.getId());
        return Result.success(data);
    }
}
```

示例省略 import。CurrentActor 由本服务安全适配提供，经内部身份核验取得可信身份，不能从任意请求头直接构造。普通方法不为传 traceId 添加 HttpServletRequest；登录、Session、文件范围、CSRF 等确需 HTTP 元数据的入口按协议接收。

每个服务的请求适配生成或接续受信任链路标识，写入请求属性和响应头。MVC 的 ResultResponseAdvice 只补充已有 Result 的 traceId，不二次包装，保留 ResponseEntity 的状态和响应头；异步分派从请求属性读取，不依赖已经释放的 MDC。common 不读 Servlet、Spring 上下文或 ThreadLocal。安全链和 WebFlux 在各自边界输出同结构错误，不能依赖 MVC Advice。文件、健康与 SSE 不包装 Result。

## 3. Service 与三件套

### 3.1 先确定自然主实体

主实体是服务维护其身份、生命周期或业务事实的持久化对象，不是随意选出的泛型。跨表不能决定有无主实体。

| 服务性质 | 接口与实现 | 数据访问 |
| --- | --- | --- |
| 有自然主实体 | IXxxService extends `IService<Entity>`；XxxServiceImpl extends `ServiceImpl<Mapper, Entity>` | 主实体 Mapper extends `BaseMapper<Entity>` |
| 无自然主实体的业务聚合/编排 | 普通 IXxxService 与 XxxServiceImpl | 组合业务 Service 或专用查询 Mapper |
| 独立技术能力 | Component，有替换/隔离需要时提供技术接口 | 对应客户端，不虚构业务实体 |

MyBatis-Plus 3.5.18 的 IService 和 ServiceImpl 分别使用 `com.baomidou.mybatisplus.spring.service` 与 `com.baomidou.mybatisplus.spring.service.impl`，不照搬旧版 extension.service 包名。

| 本项目职责 | 主实体或组织方式 |
| --- | --- |
| 账号与认证、组织 | UserEntity、OrganizationEntity；各稳定职责使用三件套，可共享实体 Mapper |
| 型号、设备、工单 | ProductModelEntity、DeviceEntity、TicketEntity；状态与时间线/通知可跨表 |
| 上门时段、申请确认 | OnsiteSlotEntity、OnsiteBookingEntity；容量事务属于确认用例 |
| 资料、发布与索引协调 | DocumentEntity，配合系列、关联、事件及IndexJobEntity本地尝试/事务事实，不设投递表 |
| 工单工作台概况 | ITicketOverviewService / TicketOverviewServiceImpl，普通聚合服务，无概况实体 |
| 索引副本运维等无主实体用例 | 按实际消费者定义普通接口和实现，不挂无关实体、不搬入 Component |

一个 Service 可跨表，多个独立职责的 Service 可共享主实体。不按每张表生成接口，也不因统计工单就强迫概况继承 TicketEntity 三件套。ticket 的通知和时间线可由维护工单完整动作的服务协作，不为凑数量拆模块。

### 3.2 完整动作与 Mapper 使用

公开 acceptTicket、confirmBooking、publishDocument 等完整动作，负责当前操作者、范围、存在性、状态、版本、幂等与失败语义。Controller、任务和跨业务消费者不能调用通用 getById/save/removeById 绕过规则。

自身主 Mapper 使用 baseMapper，不重复注入。其他表 Mapper 可以按需注入；涉及其他业务完整动作时调用对应 Service。专用查询不增加空转服务。自定义 Mapper 方法用明确名称，不重载 insert/update/delete 等框架 statement。

Service 可接收普通 Form、操作者及明确参数，返回安全 VO、内部结果或无传输依赖的业务事件。取得 Cookie、读取 Session、写响应及 SSE 编码由 Web 边界负责。

### 3.3 事务与并发

事务放在负责完整本地原子动作的方法上，不统一加到整个类。多表变更明确回滚策略，必要时 `rollbackFor = Exception.class`，不能捕获异常后误提交；短事务经被代理接口调用，避免 this 自调用失效。

身份核验、OSS、HTTP、Broker、模型及文件操作在长事务外。短事务领取任务、登记意图、确认结果，跨存储通过幂等与对账收敛，不声称一个本地事务覆盖所有服务。

条件更新携带必要归属、状态、row_version 和执行令牌并检查影响行数。先查再无条件更新不提供并发保护。最后管理员等跨行约束使用共同串行化边界，取得锁后重查。预约取得全部行锁后另发 `SELECT UTC_TIMESTAMP(6)`，避免等待锁前求值的时刻决定截止资格。

## 4. Mapper 与 SQL

### 4.1 组织与参数

实体 Mapper 默认按实体集中维护，统一 @MapperScan 注册；专用聚合 Mapper 可以是普通接口，不绑定无关实体。简单单表用 BaseMapper 与 Lambda Wrapper，联表、授权、统计、复杂筛选和竞争用注解 SQL 与 Java 文本块。动态条件使用 script/if/where/set/foreach，不新增 Mapper XML；DDL 与索引用 Flyway。

自定义参数显式 @Param，值用 `#{...}` 绑定。排序与动态标识来自服务器白名单，不拼接用户 `${...}`。SQL 关键字大写，列、JOIN、WHERE、GROUP BY、ORDER BY 分段排列；多列逐行，动态标签独立缩进，转义 XML 比较符。明确 null、空串与有效文本，模糊查询说明通配符语义。

```java
/**
 * 查询工单时间线和动作回执。
 *
 * @author yangaobo
 */
public interface TicketEventMapper extends BaseMapper<TicketEventEntity> {

    /**
     * 在已确认的工单范围内读取动作回执。
     *
     * @param ticketId 已授权工单标识
     * @param actorId 当前操作者标识
     * @param actionId 原动作幂等键
     * @return 原事件；不存在时为 null
     */
    @Select("""
            SELECT
                id,
                sequence_number,
                to_status,
                action_request_hash
            FROM sd_ticket_event
            WHERE ticket_id = #{ticketId}
                AND actor_id = #{actorId}
                AND client_action_id = #{actionId}
            """)
    TicketActionReceiptDTO findActionReceipt(
            @Param("ticketId") String ticketId,
            @Param("actorId") String actorId,
            @Param("actionId") String actionId);
}
```

Mapper 返回 Entity、DTO、字段合同一致的直接 VO 投影、标量或影响行数，不返回 Result。COUNT 用 long/Long，EXISTS 可返回 boolean，写入返回 int，Service 解释结果。查询模型独立放 model，不在 Mapper 内嵌套类型；同一字段合同不机械复制 DTO/VO。

查询列和别名明确，不用 SELECT *。联表或有界批量避免 N+1。授权由 Service 计算并通过范围条件进入 SQL；空授权直接空结果，批量空集合拒绝或无操作，不能省略条件退化全表查询/写入。

### 4.2 MySQL 与迁移

使用 MySQL 方言；业务 ID 为字符串，JSON、复合 FK、生成列唯一与 UTC 转换按数据库设计执行，Entity 不写生成列。真实 MySQL 8.4 验证映射、锁等待、JSON、时间与约束，不能用 H2 或旧 PG 样例替代。

Flyway core/flyway-mysql 同版，各服务独立库及迁移历史，版本号在所属链内唯一。共享环境执行后的迁移不修改，禁止 clean 或默认 baseline。MySQL DDL 通常隐式提交，失败先核查已生效结构，不假定整脚本回滚。删除与引用保留按生命周期，不统一级联或 @TableLogic。

## 5. Model 分类与写法

| 类型 | 职责 | 命名 |
| --- | --- | --- |
| Entity | 持久化映射 | TicketEntity |
| Form | 客户端可写输入及基础校验 | TicketCreateForm、TicketResolveForm |
| DTO | 内部合同、查询投影与处理结果 | TicketActionReceiptDTO |
| VO | 对外允许输出的数据 | TicketDetailVO、TicketOverviewVO |
| Enum | 有限状态 | TicketStatusEnum |
| Value | 有明确不变量的值对象 | SourceLocation |

### 5.1 归属与公共输入

Entity直接放本服务model/entity，一实体一同名文件；业务枚举归本服务model/enums，按实际业务域分组。Form/DTO/VO按auth、account、organization、device、ticket、booking、document等实际业务分包，不把所有管理模型堆进administration。Service接口放service，实现放service/impl，Mapper放mapper，不加biz中间层。业务模型独立顶层文件，不以嵌套模型集合类收纳一组接口输入。

Result、PageVO、BasePageForm、IdForm、VersionForm、IdVersionForm、BaseEnum、ErrorCode及CommonErrorCode统一归common/base，不在各服务保留同义副本。Python按线合同实现自身类型，不引Java类。

| 公共输入 | 字段和基础校验 | 适用例子 |
| --- | --- | --- |
| IdForm | id必填、Identifier格式 | account/query、document/query |
| VersionForm | rowVersion必填、非负Long | 作为IdVersionForm的版本基类；路径已有ID且只需版本时可直接使用 |
| IdVersionForm extends VersionForm | 增加必填id | account/disable、device/retire、document/disable |
| BasePageForm | pageNum/pageSize及统一默认、范围 | 无其他筛选的管理分页 |

具名可变Form在完全相同的id/必填rowVersion语义下继承IdVersionForm，只声明额外业务字段；需要其他有效基类、可选版本、不可变构造或不同ID字段名时不强制继承。路径已有ID不通过父类增加重复ID。index-job/add的documentId与原请求键、取消的jobId均保留具名Form，不把所有标识改名为id以迁就基类。

继承保留无参绑定、真实需要的构造器与@EqualsAndHashCode(callSuper = true)，每个数据类声明自己的UID；JSON字段仍扁平，父类校验同样执行。公共表单只提供格式与必填性，不承担授权、版本相等或幂等判断。反馈首次提交可不带版本，更新才要求版本，不能继承必填VersionForm改变该合同。

### 5.2 枚举与线值

稳定业务枚举实现`common/base/BaseEnum<T>`，提供getValue()与getDesc()；字符串状态使用`BaseEnum<String>`，值沿用数据库和接口已经确定的大写名称，中文描述只用于展示与文档。权限、SQL参数、快照需要值时显式getValue()，不能用序号ordinal()或中文描述持久化。

BaseEnum不附加@JsonValue/@EnumValue，不自动改变JSON或MyBatis映射；同服务Form优先使用明确枚举类型，String字段按真实需要执行约束。SchemaEnum只声明文档来源，不承担校验或序列化。跨服务传输枚举归desk-api的所有者合同或.proto，接收后显式转本地枚举；不让其他服务依赖本地model/enums。Protobuf生成枚举不实现手写BaseEnum，零值及未知值按RPC合同处理。

### 5.3 普通类、字段与转换

手写Form、DTO、Entity、VO、值对象及其他数据模型统一普通class，不使用record。手写数据模型实现Serializable，声明`@Serial private static final long serialVersionUID = 1L;`；枚举沿用自身序列化合同，不重复声明UID。模型使用明确泛型，不以Object或裸类型掩盖协议。Protobuf生成类型遵循生成器合同，不修改继承、序列化或构造方式。

可变 Entity 默认 @Data、@NoArgsConstructor、@AllArgsConstructor；其他可变模型可用 @Data，构造与 Builder 按实际调用需要选择。敏感字段排除 toString；特殊相等性、校验、归一化及防御复制保留显式逻辑，不用 force=true 绕过 final 初始化。不可变结果仍用 final 字段、构造器和只读访问器的普通 class。

字段按照“中文 Javadoc → 注解 → 字段”成组，组间空行；类注释在类注解之前。下面仅示意输入模型部分字段：

```java
/**
 * 提交人工报修的输入参数。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
public class TicketCreateForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本次创建请求键，核对或重试时复用。
     */
    @NotBlank(message = "请求键不能为空")
    @Identifier
    private String clientRequestId;

    /**
     * 用户确认的故障标题，最多200个字符。
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;
}
```

Entity 用 @TableName、@TableId(value = "id", type = IdType.INPUT) 及必要 @TableField 对齐列名。业务 ID 由应用生成；UID 不属于表字段，必要时显式排除。DTO/VO 不加持久化注解，输入校验放 Form。

Entity 不直接用作 HTTP 输入输出。普通 Form 可以直接交给 Service，不在 Controller 拆成重复标量或复制同字段 DTO；路径与文件协议参数仍显式传递。可写字段、校验或空值语义不同的新增/修改拆 Form，完全一致才共用 SaveForm。

区分未提供、显式 null 和主动清空，不用忽略 null 的批量复制假装支持清空。简单转换在 Service 显式赋值，复杂复用转换才提取具名组件。只复制允许字段，操作者、角色、状态、版本递增、存储位置由服务端决定。

## 6. 参数校验与分页

### 6.1 两层校验

Controller 使用 Jakarta Validation 并触发校验：必填字符串 @NotBlank，对象 @NotNull，集合按语义 @NotEmpty/@Size，嵌套 @Valid。长度、UTF-8 字节、状态与数据库预算对齐。Service 和后台入口仍检查当前身份、范围、关联、跨字段条件、状态与版本，不能只依赖 HTTP 校验或统一禁止 null 的切面。

业务标识在Form、Entity、DTO、VO、消息和Mapper参数中统一String。common/constant/IdentifierConst维护32位小写十六进制格式，common/validator/Identifier复用该模式，支持FIELD/PARAMETER/TYPE_USE；它允许null，必填同时声明@NotBlank或@NotNull。集合逐项使用`Set<@NotBlank @Identifier String>`等容器约束，集合必填、大小及空集合清空语义另行声明；路径、RPC及MQ入口也执行同等格式与业务校验。sessionRef、claimToken及供应商ID不套业务ID模式。

rowVersion、authVersion、total、Token等bigint公开为十进制字符串，Long字段使用common/serialize/DecimalStringSerializer，已有String版本不再二次序列化。输入版本绑定非负Long，明确接受十进制字符串。评分、浮点、页号及pageNum/pageSize保持JSON数值；Jackson不全局将所有Long、Double、Float或BigDecimal改为字符串，也不全局忽略null。

### 6.2 分页合同

本节统一分页合同，前端、Controller、Service、Mapper 和 Python 使用相同规则。

| 项目 | 约定 |
| --- | --- |
| 输入 | pageNum、pageSize |
| 输出 | records、total、pageNum、pageSize |
| 缺省 | pageNum = 1，pageSize = 10 |
| 有效范围 | pageNum ≥ 1；1 ≤ pageSize ≤ 100 |
| total | 相同授权/筛选条件的总数，JSON 十进制字符串 |
| 执行 | 数据库分页，排序包含唯一键 |

只有纯分页时用 common 的无泛型 BasePageForm，业务 QueryForm 按需继承；排序和筛选留所属业务，不放进公共基类。缺省使用默认值，非法值拒绝，不静默截断。空页为 records=[]、total="0"，查询失败不能返回空页。

公开 `PageVO<T>`，IPage 留内部。pageNum/pageSize 是 JSON 整数，偏移用足够范围类型计算并限制查询预算，避免 int 乘法溢出。前端不读取全量再截页，不能另定默认值。

## 7. Javadoc、命名与格式

包名全小写，类型 PascalCase，字段/方法 lowerCamelCase，常量/枚举项 UPPER_SNAKE_CASE。Controller、接口、实现、Mapper 为 XxxController、IXxxService、XxxServiceImpl、XxxMapper，管理入口 XxxAdminController。技术组件用 IdentityVerificationClient、OssDocumentStorage 等能力名，不用泛化 Manager/Helper 或拼音、数字后缀。

所有手写正式Java类型含class/interface/enum/注解都有中文Javadoc，首句概括职责，必要时补边界，空行后只在类型顶部写`@author yangaobo`。不加机械日期、版本和修改历史。生成类型维护.proto中文合同，不补写生成文件注释。

Controller 方法、Service 业务接口与实现、自定义 Mapper、公共技术接口、手写规则和复杂算法都有合同注释，按实际说明 @param、@return、@throws、单位、空值和失败条件。手写构造器说明不变量。实现及重写使用 @Override；实现可用 `{@inheritDoc}`，额外事务/并发/资源约束补中文，不能因 Override 省略注释。Lombok 访问器不手写样板说明。

模型字段逐个写中文说明，必要时标单位、范围、空值和状态，UID不写无意义说明。作者不放方法或字段。面向HTTP的Controller使用中文@Tag/@Operation，Form/VO使用@Schema，不能替代Javadoc或校验；OpenAPI接入及不同运行栈的边界见第10节。

Java21、4空格，包/导入/类型分组，无通配符和无效导入。成员按常量、依赖/字段、构造、公开方法、私有协作排列。类型、方法和字段注解独立成行；参数保持 `@Valid @RequestBody XxxForm form` 一组，长参数按参数换行。Stream 用于清楚转换，状态与异常使用直观控制流。

## 8. 安全、异常与日志

common/base/ErrorCode只提供code()与message()，CommonErrorCode保存公共错误，common/exception/BusinessException接收ErrorCode表达预期业务拒绝。业务专属错误枚举归所属应用error，不能让common反向依赖它们；各项只有数字码及安全中文提示，不包含HTTP状态、Cookie、重试器或客户端。

HttpErrorMapping归所属应用component/web，按已登记枚举项显式选择HTTP状态；ApiExceptionHandler和ApiErrorWriter分别负责MVC及过滤链，Gateway在Reactive边界适配，RPC另经RpcErrorMapping转换gRPC Status和RpcErrorDetail。不能按code前缀、位数或整除推断状态，也不能把业务码直接写入HTTP状态。成功0、系统异常1及全局分域编号统一见接口约定，不在技术规范重复分配第二套码。

错误响应为`Result<Void>`且data=null，失败不返回成功码或空列表。未知本地异常及未登记ErrorCode类型统一安全回退HTTP500/code=1，内部保留异常链；未登记上游载荷按HTTP502/code=1012处理，不回显外部消息。已知网络、超时、限流及协议故障显式转相应稳定码，不能一律掩盖为参数错误。框架原状态及Allow、Retry-After、Content-Range等必要头保留。MVC只补traceId，过滤链与WebFlux使用同一请求标识，不泄露上游堆栈或另加coderMessage。

持久任务的last_error_code、error_code、failure_code以及预约resultCode是独立事实，不能批量替换成数字响应码。SSE已提交后只发送约定安全事件，PDF已开始传输后不能改写Result；失败状态和取消仍以业务库及专用协议为准。

Spring Security 负责入口及会话，资源 Service 逐次授权；Gateway 及业务服务均不信任客户端身份头。权限依赖失败关闭，不能回退默认管理员或过期权限。内部凭据只证明调用方，用户 ID 本身不证明用户权限。

日志参数化并输出统一JSON，记录traceId、service/environment/instance、资源、任务、动作、阶段、耗时和错误码，字段见系统设计。使用Boot Logback结构化能力及必要定制，滚动文件有保留和字节上限，通过Logstash集中采集；Java使用Micrometer/Brave及Zipkin reporter，统一W3C传播、采样和有界投递，不引入自定义日志远端发送器。异常在负责边界记录一次，保留安全上下文及异常链；成功动作在事务提交后记录，回滚另记，观测失败不回滚业务。密码、Cookie、SQL参数、提示词、聊天及资料全文不进普通日志，前端不接收内部地址、上游原始错误或堆栈。SSE提交后发安全事件。

## 9. 微服务、任务与资源

各应用只扫描自己的子包，common为普通JAR无启动类和自动装配。Java↔Java使用Feign/HTTP及Nacos/LoadBalancer；Java↔Python使用gRPC/Protobuf独立私网端口。Controller与RPC适配器复用同一Service、授权及事务，协议对象不渗透持久模型。Channel/stub进程复用，TLS/服务凭据、Deadline、错误详情和原件流按接口约定装配；成功RPC不套Result。必要外调在事务外执行，失败、拒绝和结果未知分别处理。

knowledge用RocketMQ原生事务消息发送SUBMIT/CANCEL，不设outbox表或扫描发送器。先短事务登记kb_index_job的PREPARING及固定操作身份，再事务外发送半消息，明确成功后短事务重核并提交COMMITTED及资料事件，随后向Broker COMMIT。TransactionChecker由生产者显式注册，读取持久业务事实，数据库失败/准备未决返回UNKNOWN；到期准备须先按文档→任务锁序原子终止，才能ROLLBACK，不能猜测或让迟到请求继续提交。已提交SUBMIT不因后来取消而回滚，取消按独立operationId提交；重启重新注册回查。半消息失败/未知且本地未提交不返回已受理，二次确认丢失由回查恢复。AI短事务受理后ACK、worker执行；gRPC只查状态，不提供提交旁路。Java提交、AI受理、AI执行分别表达，重复/乱序按原jobId及摘要收敛。跨服务异步统一RocketMQ；当前Java↔Java无异步业务，ticket预约、事件及通知同本地事务完成。

user以Lettuce/Lua维护TTL防刷，knowledge独立Redisson客户端按上传者/请求键互斥OSS写入，持锁不跨线程、事务外等锁，watchdog不替代唯一约束及未知结果对账。

knowledge 持有 OSS 适配与凭据，原件按 Bucket/Object Key/Version ID 精确访问。上传请求键只保证修订回执唯一，外部对象不保证恰好一次；未知结果核对原定位与摘要，孤立对象经受控窗口清理。已发布成功任务不取消或原地重建，换空间走新修订。

XXL-JOB仅以ticketDeadlineScan、knowledgeIndexPrepareExpire触发所属Service有界收敛期限/过期准备，不发送消息。执行器生命周期随ticket/knowledge，业务进度在本库；同批不再注册@Scheduled。user沿用Spring Session清理，Python使用receiver和独立worker。线程、批次及外调有界，关闭释放资源；仅显式捕获受控追踪上下文，按scope接续/关闭，不把请求ThreadLocal、Cookie或权限对象直接带到后台。

Sentinel在Gateway稳定API分组及Java真实外调边界显式进入/退出资源，并发隔离及熔断独立于幂等/事务。HTTP业务4xx、RPC输入/权限/状态拒绝不计依赖异常，UNAVAILABLE/DEADLINE_EXCEEDED/INTERNAL等技术故障按接口约定统计；429流控与503熔断准确映射，禁止fallback伪成功、空列表或旧权限放行。SDK自动适配与手写资源不能对同一边界重复统计，Reactive及异步gRPC资源覆盖调用至完成/取消；gRPC拦截器传播可信W3C metadata并安全转换错误。Nacos规则失败保留有效规则或加载启动基线，完整初值及恢复见部署说明。

## 10. 配置、接口文档与验证

### 10.1 配置归属与环境

自定义@ConfigurationProperties集中绑定和校验，沿用框架已有属性。各启动应用仅扫描本应用包，common无自动装配入口；框架配置留本应用config，不能扩大扫描范围顺带启用兄弟服务或公共配置。版本只由根POM管理，应用显式声明实际starter、驱动与客户端；optional仅控制依赖传递，不能代替装配隔离。

common/env提供不依赖Spring的SystemEnvironmentEnum与不可变SystemEnvironment，应用EnvironmentConfig读取active/default Profiles后构造一次快照，其他配置通过构造器注入同一快照，不在各组件重复判断字符串。基础环境只选dev或prod，两者同时启用拒绝启动，无显式激活时由应用声明default=dev；额外功能Profile不改变基础环境。dev映射隔离的test资源，prod映射prod资源；自动测试使用专用ci实例与显式注入，不能通过环境判断连接真实库。部署时仍逐应用核对Nacos、库名、地址、前缀及凭据，环境类型本身不提供授权或连接。

JacksonConfig在各真实JSON应用定制Boot builder，不另建替代ObjectMapper，不丢模块发现、不全局忽略null；DecimalStringSerializer只作用字段。Gateway错误编码也使用Boot配置后的JSON能力，不能复制一套默认ObjectMapper。Java/Python时间均按接口约定，数据库UTC与浏览器显示时区分别处理。

Java应用YAML在各自resources，dev显式导入忽略的application-local.yml，prod不加载本机文件。application-local.example.yml只留空凭据；根POM排除application-local*.yml/yaml的资源复制与打包，Git与镜像上下文另行排除。common/api无应用配置、真实连接或启动类。

### 10.2 HTTP接口文档

user、ticket、knowledge分别使用springdoc的webmvc-ui starter，OpenAPI 3.0；SwaggerConfig/SwaggerProperties与EnumPropertyCustomizer归所属应用swagger，通过显式@Import启用。common仅维护无框架发现的SchemaEnum注解及BaseEnum合同，不引入springdoc运行栈。Gateway无业务Controller，不装MVC文档starter，不提供自动聚合或公开下游文档旁路；Python文档按Python规范维护。

GET /v3/api-docs返回原生OpenAPI，GET /swagger-ui.html及静态资源为界面入口，均不套Result。api-docs与swagger-ui开关默认false，dev显式启用，prod关闭；临时开放须配置开关和私网限制。文档分组只列实际启用的/api/v1业务；内部HTTP合同独立分组且仅服务私网访问，不把/error、Actuator或gRPC方法伪装成普通REST接口。

user文档以DESK_SESSION Cookie和X-CSRF-TOKEN描述身份。匿名先GET CSRF，登录也需要CSRF；其他写请求把Session与CSRF放同一security requirement，表示同时满足，管理list/query也遵守。ticket/knowledge调试仍通过user核验和当前资源授权，不能因Swagger打开放行业务请求。浏览器使用同源Cookie，文档直接私网调试时需在同一受控开发反向代理下提供user认证及目标接口，不能在UI中手填HttpOnly会话值或接入万能调试令牌。

Form/VO字段维护中文说明、必填/可空、长度、默认和示例，版本与total为string，枚举根据getValue/getDesc展示真实字符串与中文含义；String字段可用SchemaEnum指定本地或api枚举，注解不校验输入。@JsonCreator与显式null清空合同不能被文档标成“任意省略”。202/201、原件200/206/416与SSE分别声明正确媒体类型、有效载荷及响应头。

### 10.3 公共能力接入与持久化配置

common每项能力须有结构文档登记的实际消费者；Controller使用Result和PageVO，兼容Form复用基础输入，ErrorCode/BusinessException经真实边界转换，Identifier用于字段/集合约束，DecimalStringSerializer落实公开精度。环境快照必须由配置校验消费，SchemaEnum必须由实际文档定制器消费，不能创建无使用者注解。

Java分页统一显式COUNT和LIMIT/OFFSET，普通单表可用MP查询工具，但不默认注册第二套分页、乐观锁、逻辑删除或自动填充插件。rowVersion、时间与操作者按完整业务动作显式写入；条件SQL不能再被通用填充器覆盖。新增插件须有明确查询/写入消费者并验证拦截与并发语义。

不新增通用RequestLimit或记录所有Service参数的切面：Sentinel、登录防刷和日次数各有已确定职责；用户/工单/知识审计分别按本库事务记录，不靠日志切面假装审计原子性。缓存、导出、JWT、额外存储客户端及线程池只按真实用例接入，不复制无消费者的参考工具。

### 10.4 变更验证

修改接口/模型/包时核对消费者、JSON、校验、MyBatis、扫描、事务代理、反射、SPI和资源打包；迁包或删除后clean verify。基础表单验证继承约束、容器元素、缺省与清空；Result验证泛型、0/1、traceId与安全错误映射；枚举验证线值不变；环境验证冲突/默认及库隔离；文档验证开关、安全、nullable与专用协议。公共能力不能只验证类存在或编译通过。

业务数字码检查全局唯一、号段所有权、登记项与Java/Python实际定义一致、边界映射与未知类型回退；测试仅随实际实现落地，不在文档阶段生成空测试。MySQL、事务MQ、RPC、模型与双索引分别真实联调，不用构建或替身结果替代，命令和交付标准见开发规范。
