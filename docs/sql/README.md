# 数据库初始化与迁移

**状态边界（2026-11-17）：现有真实开发MySQL的四个test库已执行正式迁移，37张自有表、585字段的名称、类型及空值与字典无差异。迁移前旧业务表为空；该结果不能证明非空旧业务可自动映射。字段齐全不代表全部业务、模型效果或生产恢复验收完成。历史SQL快照不能视为最终系统安装脚本。**

三个Java业务服务使用Flyway，AI使用Alembic；各服务只管理自己的MySQL库。三个MVC应用和AI API在启动就绪前自动执行未应用的版本，迁移失败则中止启动。Gateway不连接数据库，AI receiver/worker不执行DDL。自动迁移执行明确的版本脚本，不根据Entity或Python模型自动推断表结构。

ticket V5在任何DDL前要求旧工单、事件及预约为空，因为旧记录不能推断原提交版本、现场负责人或草稿根采用事实；非空库需要显式映射方案，不能删数据绕过。AI 0005同样在DDL前拒绝非空旧草稿，单条ALTER增加不可变修订、根、摘要及状态。两个迁移均需在独占库先验收，不能用空库验证结果推断非空业务库可自动升级。

工单V4要求旧WAITING_CUSTOMER记录先由人工核对并结束旧等待；缺少期限会使首条原子DDL失败，不能默认补一个历史期限。部署前先以只读方式盘点，不能在应用启动失败后自动改变业务状态。AI 0004要求旧反馈表为空，因为旧共享评价没有实际采纳事实且可能含人工处理记录；非空时在DDL前拒绝，不能丢弃这些记录或推断采纳情况。其单条ALTER允许在DDL已提交而Alembic版本尚未提交的中断后核对新结构再重入。知识V4保留旧文档与事件，默认访问允许只表达升级前的既有访问状态。

## 1. 迁移源与首次启动

| 所属服务 | 当前版本 | 唯一迁移源 | 测试库 |
| --- | --- | --- | --- |
| user | 1 | [Flyway V1](../../desk-user/src/main/resources/db/migration/V1__initial_schema.sql) | desk_user_test |
| ticket | 7 | [V1基线](../../desk-ticket/src/main/resources/db/migration/V1__initial_schema.sql) → [V2受控升级](../../desk-ticket/src/main/java/com/yangaobo/servicedesk/ticket/migration/V2__office_it_support.java) → [V3独立版本](../../desk-ticket/src/main/resources/db/migration/V3__diagnosis_versions.sql) → [V4等待期限](../../desk-ticket/src/main/resources/db/migration/V4__ticket_waiting.sql) → [V5回执与预约](../../desk-ticket/src/main/java/com/yangaobo/servicedesk/ticket/migration/V5__action_receipts.java) → [V6诊断附件](../../desk-ticket/src/main/resources/db/migration/V6__diagnostic_attachments.sql) → [V7补充请求草稿](../../desk-ticket/src/main/resources/db/migration/V7__information_draft_event.sql) | desk_ticket_test |
| knowledge | 5 | [V1基线](../../desk-knowledge/src/main/resources/db/migration/V1__initial_schema.sql) → [V2受控升级](../../desk-knowledge/src/main/java/com/yangaobo/servicedesk/knowledge/migration/V2__office_it_support.java) → [V3原件摘要](../../desk-knowledge/src/main/java/com/yangaobo/servicedesk/knowledge/migration/V3__restore_original_digest.java) → [V4资料安全](../../desk-knowledge/src/main/resources/db/migration/V4__document_safety.sql) → [V5审核规程](../../desk-knowledge/src/main/resources/db/migration/V5__reviewed_procedure.sql) | desk_knowledge_test |
| AI | 0009 | [0001基线](../../desk-ai/src/servicedesk_ai/migrations/versions/0001_initial_schema.py) → [0002升级](../../desk-ai/src/servicedesk_ai/migrations/versions/0002_office_it_support.py) → [0003原件摘要](../../desk-ai/src/servicedesk_ai/migrations/versions/0003_restore_original_digest.py) → [0004私有评价](../../desk-ai/src/servicedesk_ai/migrations/versions/0004_private_feedback.py) → [0005草稿修订](../../desk-ai/src/servicedesk_ai/migrations/versions/0005_draft_revisions.py) → [0006任务与Run](../../desk-ai/src/servicedesk_ai/migrations/versions/0006_collaborative_agent.py) → [0007父子证据](../../desk-ai/src/servicedesk_ai/migrations/versions/0007_evidence_sections.py) → [0008草稿确认](../../desk-ai/src/servicedesk_ai/migrations/versions/0008_draft_task_events.py) → [0009草稿对账](../../desk-ai/src/servicedesk_ai/migrations/versions/0009_draft_reconciliation.py) | desk_ai_test |

MySQL 8.4 LTS必须先有目标数据库。使用IDEA的root数据源在`192.168.23.66:3306`执行以下语句只创建空库，再填写应用配置并启动各应用：

```sql
CREATE DATABASE IF NOT EXISTS desk_user_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE IF NOT EXISTS desk_ticket_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE IF NOT EXISTS desk_knowledge_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE IF NOT EXISTS desk_ai_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
```

生产使用对应的prod库，由管理员准备数据库及独立运行、迁移账号。应用迁移不创建其他服务数据库，不跨库建立外键或写入数据。当前完整迁移链安装37张自有业务表和2张Spring Session表，user为7表（含Session两表）、ticket为12表、knowledge为5表、AI为15表；Flyway历史表及Alembic版本表另计。

AI 0006在DDL前拒绝非空旧诊断核心，不能从旧Generation推断任务和来源，索引任务及片段保留。0007对既有叶片补正文摘要和Token估算，不更换片段ID或重建既有索引；新任务生成SECTION/LEAF清单。AI0008/0009扩展草稿等待、确认、撤回和释放事件；ticket V7允许补充请求保存人工确认修订，不修改历史迁移或增加表字段。MySQL多条DDL不整体回滚，中断后须核对备份和实际结构再恢复，不能强改Alembic版本跳过。knowledge V5为旧资料保留空规程，不自动生成或伪造已审核标记。

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

Java普通DDL在所属服务db/migration新增递增版本；需要前置数据核验时，按ticket/knowledge V2模式在所属migration包使用Flyway JavaMigration，并将受控SQL放resources/db/upgrade，避免同版本被SQL与Java重复发现。当前下一版本ticket为V8、knowledge为V6，user为V2，AI为0010，不能覆盖已有迁移；AI在desk-ai目录创建新revision，再填写明确的upgrade和数据保留策略：

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

两个快照属于历史初始结构，不随增量版本改写，也不代表当前目标字典。正常开发优先空库加自动迁移；执行手工快照后必须走第3节接管，不能随后直接启动并期望工具自动认领现有表。脚本含CREATE DATABASE和USE，已有同名表会失败，不应反复执行整份文件。

快照不含删库删表、业务示例数据、账号密码或默认管理员，也不创建Flyway/Alembic历史。此前的service_desk_test、service_desk_prod不属于四服务分库结构，脚本不访问或删除它们。Spring Session表采用3.5.7官方MySQL结构，XXL-JOB独立技术库使用组件同版官方SQL，不纳入业务迁移。

## 6. 有历史数据的迁移评估

字段及约束以[数据库设计](../设计/数据库设计.md)为准，已发布迁移保持不可变。对触发非空保护的旧库，先依据真实数据与迁移历史制定专用映射方案，随后在隔离副本演练。

| 所属库 | 需评估的变化 | 保留与核对 |
| --- | --- | --- |
| user | 身份/组织/范围边界保持，审计与Session独立 | 不迁移私聊所有者、不授予管理员默认全文权限 |
| ticket | 工单环境版本/事件序号/等待语义，统一动作回执、附件，预约出勤责任 | 原动作键、草稿来源及事件映射为回执；不能重复正式动作，不伪造旧出勤人或跟进时间 |
| knowledge | 发布态与访问态分开，修订内有限规程、人工Markdown知识发布 | 原件版本/摘要、修订/任务ID保留；旧文本规程须人工核对，不自动变可执行定义 |
| AI | 会话/消息/任务/运行分离，合并检查反馈、任务内假设、Run摘要/引用与独立来源和调用账目 | 盘点旧运行、来源及其可核对关系；缺失明细保持未知 |

实施前盘点四库实际版本、数据量、索引空间和在途调用。专用迁移方案须说明扩展、回填、核对和切换步骤，并在隔离副本验证任一时刻只有一个运行器可写同一任务。新建空库直接执行第 1 节的完整迁移链。

旧会话仍保留owner/customer/ticket；明确范围的一段故障映射为任务，无法识别的历史保留只读并禁止自动续行。旧建议不转成已执行检查；事实前驱链、实际来源和已确认交接可靠时才迁移。旧缺来源历史标受限，不能用最终引用冒充全部上下文。旧消息按已有顺序确定序号，不猜时间顺序补缺失输入。不能满足新表必填/来源约束的旧历史保留受控只读归档及明确索引，不伪造成功结构化结果或呼叫账目塞进新表；归档访问仍受原权限，不进入新Agent上下文。

旧工单row_version不能恢复精确环境变更历史，以迁移时刻建立新的diagnosis_context_version基线；事件序号据真实事件校验回填。旧回执从已持久建单/处理/预约记录核对唯一关系后写入，保留旧请求键与摘要；跨用途/操作者冲突阻止切换，不随意换键。旧预约缺实际执行人时保留迁移来源并限制自动新操作，专门迁移字段/约束须依据真实样本设计，不能凭当前处理人伪造。

旧共享反馈不继承为知识发布授权：迁移后默认私人保留，知识复用须人工重新脱敏确认后编写Markdown原件。旧knowledge DISABLED/RETIRED的历史访问政策按原语义确认后映射；不因新历史阅读能力意外扩大已撤回资料访问。已采用草稿迁移为首版根并关联原ticket回执；旧过期草稿不延长有效期。

切换前暂停新受理，等待已知在途收敛，将未知外调保留为UNKNOWN；新运行器只接收新身份或明确续行。跨语言方法采用新方法/字段编号，已发布字段不改义/复用，确有破坏变更新建v2及短迁移窗口；旧generations接口退出须核验全部消费者。索引MQ信封及原任务摘要不因诊断改造重写，SUBMIT/CANCEL仍沿原事务事实。

验证空库安装、旧数据迁移、幂等和来源保留、重复启动、失败中间DDL及恢复。检查所有字段/默认/注释/索引/FK/CHECK/生成列与字典一致，再验证当前业务；表数相等不足以通过。全字段ER与字典一致性属于文档检查，不能替代真实MySQL验证。

当前.github/scripts/verify_database.py是旧实现验证入口，后续需按新字典及行为更新后才能验收目标。仅允许显式隔离的可销毁实例，禁止运行在共享业务库。历史本机检查数字不作为本次目标实现证据。
