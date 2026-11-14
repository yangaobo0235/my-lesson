# 数据库初始化与迁移

三个Java业务服务使用Flyway，AI使用Alembic；各服务只管理自己的MySQL库。三个MVC应用和AI API在启动就绪前自动执行未应用的版本，迁移失败则中止启动。Gateway不连接数据库，AI receiver/worker不执行DDL。自动迁移执行明确的版本脚本，不根据Entity或Python模型自动推断表结构。

## 1. 迁移源与首次启动

| 所属服务 | 当前版本 | 唯一迁移源 | 测试库 |
| --- | --- | --- | --- |
| user | 1 | [Flyway V1](../../desk-user/src/main/resources/db/migration/V1__initial_schema.sql) | desk_user_test |
| ticket | 2 | [V1基线](../../desk-ticket/src/main/resources/db/migration/V1__initial_schema.sql) → [V2受控升级](../../desk-ticket/src/main/java/com/yangaobo/servicedesk/ticket/migration/V2__office_it_support.java) | desk_ticket_test |
| knowledge | 3 | [V1基线](../../desk-knowledge/src/main/resources/db/migration/V1__initial_schema.sql) → [V2受控升级](../../desk-knowledge/src/main/java/com/yangaobo/servicedesk/knowledge/migration/V2__office_it_support.java) → [V3原件摘要](../../desk-knowledge/src/main/java/com/yangaobo/servicedesk/knowledge/migration/V3__restore_original_digest.java) | desk_knowledge_test |
| AI | 0003 | [0001基线](../../desk-ai/src/servicedesk_ai/migrations/versions/0001_initial_schema.py) → [0002升级](../../desk-ai/src/servicedesk_ai/migrations/versions/0002_office_it_support.py) → [0003原件摘要](../../desk-ai/src/servicedesk_ai/migrations/versions/0003_restore_original_digest.py) | desk_ai_test |

MySQL 8.4 LTS必须先有目标数据库。使用IDEA的root数据源在`192.168.23.66:3306`执行以下语句只创建空库，再填写应用配置并启动各应用：

```sql
CREATE DATABASE IF NOT EXISTS desk_user_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE IF NOT EXISTS desk_ticket_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE IF NOT EXISTS desk_knowledge_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE IF NOT EXISTS desk_ai_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
```

生产使用对应的prod库，由管理员准备数据库及独立运行、迁移账号。应用迁移不创建其他服务数据库，不跨库建立外键或写入数据。当前迁移链安装31张自有业务表和2张Spring Session表，user为7表、ticket为10表、knowledge为5表、AI为11表；Flyway历史表及Alembic版本表另计。

## 2. 开关与凭据

| 环境 | Java三个MVC服务 | AI API |
| --- | --- | --- |
| 开发 | application-local.yml中DESK_MIGRATIONS_ENABLED=true；MIGRATION_USERNAME/PASSWORD引用本服务DB_USERNAME/PASSWORD，当前使用root | .env中DESK_AI_MIGRATIONS_ENABLED=true；MYSQL__MIGRATION_USERNAME/PASSWORD留空时复用MYSQL__USERNAME/PASSWORD，当前使用root |
| 生产 | application-prod.yml要求显式提供DESK_*_MIGRATION_USERNAME/PASSWORD，不读取开发文件 | 进程环境必须提供DESK_AI_MYSQL__MIGRATION_USERNAME/PASSWORD，不读取开发.env |
| 自动化测试 | application-test.yml关闭Flyway，按用例在专用MySQL实例中另验迁移 | 关闭外部基础设施，迁移用例使用隔离MySQL，不访问共享业务库 |

Java与AI的正常启动默认启用迁移。Java通过Flyway的数据库锁串行执行同一库的迁移；AI启动和Alembic维护命令共用MySQL GET_LOCK，等待上限30秒，失败拒绝启动。数据库会话退出时释放该锁。迁移先于业务资源就绪；最终部署必须先完成AI API迁移，再启动receiver/worker，不让消费者读写旧结构。

密码仅填所属模块本机真实配置或生产受控secret，不填SQL、不放命令行参数、不上传Git。现有本机MySQL密码保持原值；Java开发迁移字段引用该密码即可。

## 3. 已经手工建表的数据库

已执行手工DDL的库不能当作空库重新初始化。Java的baseline-on-migrate保持false；AI发现已有表且没有Alembic历史也会明确拒绝启动，不自动stamp。

接管时先暂停该库的业务写入并备份，在可销毁MySQL实例执行同一初始迁移，逐表比对已有库与初始版本的字段、默认值、字符集、注释、索引、生成列、CHECK及外键。仅表数相同不足以证明结构一致。有任何差异先明确修复方案，不通过删表、IF NOT EXISTS、忽略错误或跳过版本掩盖差异。

完整比对一致后，管理员针对准确的所属库显式执行一次原生工具操作：Java使用与应用同版的Flyway 11.20.3，将该库baseline到版本1；AI在desk-ai目录执行以下命令：

```shell
uv run --locked alembic stamp 0001
uv run --locked alembic current
```

Java的原生Flyway维护入口在已通过环境或受限配置文件指定准确URL及迁移凭据后执行`flyway -baselineVersion=1 baseline`，三个库分别操作，不把密码放在命令行。baseline/stamp只登记已验证的初始版本，不重跑建表，也不会验证结构相同，因此必须先完成上述比对。禁止手工INSERT迁移历史或默认开启baseline-on-migrate。若已经有正确迁移历史，直接校验，不重复baseline/stamp。

完成后正常启动：Java校验已有迁移校验和，跳过V1并执行后续版本；AI从0001之后升级到head。开发和生产各有自己的历史，不能复制另一环境历史表。

## 4. 后续业务改表

Java普通DDL在所属服务db/migration新增递增版本；需要前置数据核验时，按ticket/knowledge V2模式在所属migration包使用Flyway JavaMigration，并将受控SQL放resources/db/upgrade，避免同版本被SQL与Java重复发现。当前下一版本ticket为V3、knowledge为V4，user为V2，AI为0004，不能覆盖已有迁移；AI在desk-ai目录创建新revision，再填写明确的upgrade和数据保留策略：

```shell
uv run --locked alembic revision -m "add_business_field"
```

字段、索引及约束先同步数据库设计和真实消费者，再编写迁移。已在共享环境应用的脚本保持不变，修复使用新版本。普通业务请求只执行DML，DDL由下一次应用启动或明确的发布迁移执行，不在每次请求中运行。

每次变更在独立MySQL 8.4验证首次安装、已有版本增量升级、旧数据保留、重复执行不重跑及失败后的实际结构。MySQL DDL通常逐条隐式提交，不能假定整版本失败可ROLLBACK；先核查已生效内容再按明确步骤修复，不默认clean、repair、删卷或downgrade。初始AI版本拒绝自动删表回退，代码回退不等于数据恢复。

## 5. 手工SQL快照

| 文件 | 用途 | 执行账号 |
| --- | --- | --- |
| [mysql-test.sql](mysql-test.sql) | 初始版本的四个test库建库建表快照，供IDEA查阅或受控手工初始化 | MySQL管理员，如root |
| [mysql-prod.sql](mysql-prod.sql) | 相同初始结构的prod快照，与测试数据隔离 | MySQL管理员，如root |

两个快照已在隔离MySQL 8.4.13验证，不随V2等增量版本改写。正常开发优先空库加自动迁移；执行手工快照后必须走第3节接管，不能随后直接启动并期望工具自动认领现有表。脚本含CREATE DATABASE和USE，已有同名表会失败，不应反复执行整份文件。

快照不含删库删表、业务示例数据、账号密码或默认管理员，也不创建Flyway/Alembic历史。此前的service_desk_test、service_desk_prod不属于四服务分库结构，脚本不访问或删除它们。Spring Session表采用3.5.7官方MySQL结构，XXL-JOB独立技术库使用组件同版官方SQL，不纳入业务迁移。

## 6. 办公IT支持目标的增量迁移

[数据库设计](../设计/数据库设计.md)的31表435字段已经由新迁移链实现；V1/0001和本目录SQL继续保留原24表基线。当前V2/0002面向经核验为空的开发骨架：先核验所属库名和全部旧业务表，再执行明确的ALTER/CREATE/RENAME。knowledge的旧型号关联表仅在空库前置条件下移除；不关闭外键检查、不删除业务记录、不伪造回填或迁移历史。

Java V2通过SQL资源CRC32参与Flyway验证，SQL位于所属resources/db/upgrade，Java入口位于所属migration包并显式加入locations。AI 0002及其migrations/sql资源随wheel发布。在线启动／原生维护入口才包含前置检查；Alembic --sql输出仅供审阅，不能将离线SQL直接提交执行来绕过保护。MySQL DDL可能逐条提交；任何非预期中途失败都须停止、备份现场并核对已生效DDL，不自动repair或重跑。

V3/0003补回V2/0002遗漏的file_sha256，knowledge原件表或AI索引任务非空时在任何DDL前拒绝，须先从可信原件或备份制定独立修复。字典解析现已覆盖含数字的字段名，435字段包含这两个摘要列；旧433统计不能作为完整字典证据。本次只升级独占验收实例，共享test库未执行V3/0003。

2026-11-13，现有四个test库的全部业务/Session表为空，逐表SHOW CREATE与独占实例的不可变初始迁移一致，备份结构和原生历史后完成升级：user=1、ticket=2、knowledge=2、AI=0002。生产未执行。非空旧库会在首条DDL前被拦截；以下映射矩阵属于真实旧数据出现时的实施前提，不表示自动迁移已支持任意历史数据。

| 所属库 | 目标差异 | 数据保留和切换要求 |
| --- | --- | --- |
| user | 仍为5张自有表及Session技术表 | 身份/客户人员范围不迁到ticket，不新增全权角色 |
| ticket | 7→10张；新增it_service_item、it_service_entitlement、it_service_event；工单项目/环境/现场信息与结单原因，设备关联可空；事件草稿采用唯一 | 新建迁移版本，先建目录、单id服务资格及其变更历史，再补快照/可空字段、约束及索引；旧工单/资产/预约不得重建丢失；旧first_response_at/response_due_at/response_overdue_recorded_at按原“接单/分派”含义迁为first_accepted_at/acceptance_due_at/acceptance_overdue_recorded_at |
| knowledge | 仍5张；kb_document_product_model替换为kb_document_applicability；PDF/MARKDOWN格式与匹配条件 | 旧applicable_systems按原文保留为applicability_note，不从说明文本推导机器规则；先建新适用规则并映射原型号，再校验每发布修订至少一规则；旧任务/修订/原件ID及引用保留 |
| AI | 7→11张；新增ai_diagnostic_fact、ai_model_call、ai_tool_call、ai_source_usage；ai_ticket_draft迁为ai_action_draft，调整会话/运行/引用及文本定位 | 显式rename/copy保留旧ID、外键及回执；已存在旧引用需先补实际来源再切新FK；旧历史不伪造逐次调用账目 |

已有业务数据时先完成下面的数据映射与恢复方案，再设计独立的迁移版本；不能为通过当前空库保护而清空数据：

1. 盘点真实部署版本、四库迁移历史及已有数据；业务入口尚未实现不等于库一定为空。保存基线备份并比较新字典，确定准确的下一版本号。代码、网关白名单、HTTP/RPC/MQ合同及所有消费者同时纳入实施范围。
2. 显式建立真实支持项目及客户开通范围。旧设备截止日不能无依据合并成客户级全部服务权益，须服务商确认映射和日期；未确认时禁止新范围业务，既有工单按原设备资格事实保存legacy受理快照并继续人工履约。现有旧事件RESPONSE_OVERDUE保留不可变原值，读取端解释为受理超期；新写使用ACCEPTANCE_OVERDUE，不就地重写历史事件。旧工单的原区域/地址从原设备快照或已确认预约回填，缺失保留待核对，不用当前设备地点伪造历史。
3. 原型号规则只映射到人工确认的服务项目，保留原型号限制和真实PDF定位；缺映射的修订阻止新发布/召回并列出待核对项，不能自动变为通用知识。旧PDF任务格式可明确回填PDF，Markdown新任务为NOT_REQUIRED；不新建远端解析任务或重算已发布向量。
4. 旧retrieval_query保留为context_snapshot.legacy_retrieval_query及旧来源标记，不编造成完整工具执行过程。旧会话映射CUSTOMER_SELF_SERVICE并保留原owner/customer/device/model；服务项目须有明确映射，否则保留历史、阻止新轮。旧运行的旧sources能完整回查时生成知识来源记录，缺失则标历史不可用，不用最终引用冒充全部实际上下文。来源的first_model_call_number可空，纯规则或旧明细未知不伪造模型请求。旧运行没有逐次模型回执时只保留既有汇总，明确标记来源为旧汇总、不编造ai_model_call明细。
5. 旧草稿保留ID/请求键/期限并赋CREATE_TICKET，核对sd_ticket.source_draft_id历史唯一关系；旧CLOSED无法证明客户是否确认、旧处理类型无法可靠判定时，closure_reason/resolution_type分别保留LEGACY_UNKNOWN，service_scope_snapshot.origin=LEGACY_MIGRATION并保留原事件及迁移依据，不猜成客户确认或维修成功。该值仅迁移可写、新业务API拒绝，前端明确显示未知。旧会话/运行以可重建的实际事实确定初始state_version，原历史可为0；structured_result缺失时保留空且不得创建技术处理草稿。
6. 若已实际使用旧MQ v1，先盘点在途和可重放任务，保留其原摘要及PDF解码，新增v2采用contentFormat且不重写旧jobId摘要。退出旧兼容前须旧任务/墓碑、Broker保留窗口和重放需求全部核验；未实际落地的业务RPC按新目标实现，不预建双协议。
7. 按依赖先增加列/表及回填、后切消费者、再收紧FK/CHECK和移除旧模型列。循环FK通过分步建表/ALTER安装，不能关闭外键检查。旧service_expires_on/旧关联表及旧草稿结构只有全部消费者、历史读取和恢复验证完成后才能移除，禁止直接删表重建。

验证分别覆盖空库从原基线升级、已有业务数据增量升级、幂等回执/旧引用保留、重复启动不重跑、失败后的实际结构及隔离恢复。未完成数据映射时阻止对应新功能，保留安全人工链；当前空基线升级已在独占MySQL8.4.13验证首次安装、原生增量与重复执行、3类非空保护、31表435字段及19项合法/非法写入；不等同于非空历史映射或生产恢复验证。生产迁移须在独立实施任务按实际版本执行，代码回退不能代替数据恢复。

持续验证入口为.github/scripts/verify_database.py，CI使用独占MySQL8.4.13服务及desk_*_ci库。构建Java制品并生成/安装AI合同后，从desk-ai执行 `uv run --locked python ../.github/scripts/verify_database.py --work-dir ../.local/ci/database`；连接参数取DESK_VERIFY_HOST/PORT/USERNAME/PASSWORD，必须显式DESK_VERIFY_ISOLATED=true。只允许专用可销毁实例，拒绝接管已有同名CI库。测试会重建自身创建的CI库、写入并回滚固定夹具；非空保护失败案例确认结构与行未变后仅用原生repair清除CI失败记录，不手改成功历史。该入口不能用于共享业务库。
