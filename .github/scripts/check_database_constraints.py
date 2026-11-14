"""在迁移后的专用CI库验证业务不变量，全部夹具随事务回滚。"""

import json
from collections.abc import Callable

import pymysql


def verify_constraints(connection: dict) -> int:
    """验证跨客户资格、SQL空值、事实来源与同运行引用，禁止用于业务库。"""
    db = pymysql.connect(**connection, charset="utf8mb4", autocommit=False)
    checks = 0
    version = json.dumps({"schema_version": 1})
    ids = {
        key: format(number, "032x")
        for number, key in enumerate(
            [
                "service",
                "other_service",
                "customer",
                "other_customer",
                "entitlement",
                "actor",
                "ticket",
                "conversation",
                "generation",
                "other_generation",
                "fact",
                "model",
                "source",
            ],
            start=1,
        )
    }
    try:
        with db.cursor() as cursor:

            def insert(table: str, values: dict) -> None:
                """只插入代码内固定表与字段，动态值使用绑定参数。"""
                columns = ",".join(f"`{key}`" for key in values)
                placeholders = ",".join("%s" for _ in values)
                cursor.execute(
                    f"INSERT INTO `{table}` ({columns}) VALUES ({placeholders})",
                    tuple(values.values()),
                )

            def rejects(action: Callable[[], None], code: int) -> None:
                """错误必须来自目标约束，语法或连接错误不能算通过。"""
                nonlocal checks
                try:
                    action()
                except pymysql.MySQLError as error:
                    assert error.args[0] == code, error.args
                else:
                    raise AssertionError("非法组合被数据库接受")
                checks += 1

            db.select_db("desk_ticket_ci")
            item = dict(
                id=ids["service"],
                service_code="OFFICE_NETWORK",
                category="NETWORK",
                service_name="办公网络",
                support_scope="指定办公网络连接问题",
                created_by=ids["actor"],
                updated_by=ids["actor"],
                change_reason="CI夹具",
            )
            insert("it_service_item", item)
            insert(
                "it_service_item",
                item
                | {
                    "id": ids["other_service"],
                    "service_code": "OFFICE_SOFTWARE",
                    "category": "OFFICE_SOFTWARE",
                },
            )
            rejects(
                lambda: cursor.execute(
                    "UPDATE it_service_item SET category='UNKNOWN' WHERE id=%s", (ids["service"],)
                ),
                3819,
            )
            entitlement = dict(
                id=ids["entitlement"],
                customer_id=ids["customer"],
                service_item_id=ids["service"],
                starts_on="2026-01-01",
                ends_on="2026-12-31",
                granted_by=ids["actor"],
                updated_by=ids["actor"],
                change_reason="CI夹具",
            )
            insert("it_service_entitlement", entitlement)
            rejects(lambda: insert("it_service_entitlement", entitlement | {"id": "f" * 32}), 1062)
            rejects(
                lambda: cursor.execute("UPDATE it_service_entitlement SET ends_on='2025-01-01'"),
                3819,
            )
            ticket = dict(
                id=ids["ticket"],
                ticket_no="CI-1",
                customer_id=ids["customer"],
                requester_id=ids["actor"],
                client_request_id="a" * 32,
                request_hash="a" * 64,
                service_item_id=ids["service"],
                entitlement_id=ids["entitlement"],
                service_scope_snapshot=json.dumps(
                    {"schema_version": 1, "admission_type": "COVERED"}
                ),
                support_context=version,
                title="网络连接问题",
                symptom="办公网络无法连接",
                acceptance_due_at="2099-01-01 00:00:00",
            )
            insert("sd_ticket", ticket)
            rejects(
                lambda: cursor.execute(
                    "UPDATE sd_ticket SET customer_id=%s", (ids["other_customer"],)
                ),
                1452,
            )
            rejects(
                lambda: cursor.execute(
                    "UPDATE sd_ticket SET service_scope_snapshot=%s", (version,)
                ),
                3819,
            )
            rejects(lambda: cursor.execute("UPDATE sd_ticket SET entitlement_id=NULL"), 3819)
            event = dict(
                id="e" * 32,
                service_item_id=ids["other_service"],
                entitlement_id=ids["entitlement"],
                actor_id=ids["actor"],
                event_type="ENTITLEMENT_GRANTED",
                client_action_id="e" * 32,
                request_hash="e" * 64,
                details=version,
            )
            rejects(lambda: insert("it_service_event", event), 1452)
            rejects(
                lambda: insert(
                    "it_service_event",
                    event | {"service_item_id": ids["service"], "entitlement_id": None},
                ),
                3819,
            )

            db.select_db("desk_ai_ci")
            conversation = dict(
                id=ids["conversation"],
                owner_id=ids["actor"],
                customer_id=ids["customer"],
                mode="CUSTOMER_SELF_SERVICE",
                service_item_id=ids["service"],
                support_context=version,
                title="连接问题",
            )
            insert("ai_conversation", conversation)
            generation = dict(
                id=ids["generation"],
                conversation_id=ids["conversation"],
                owner_id=ids["actor"],
                round_number=1,
                input_state_version=1,
                question_text="网络无法连接",
                client_request_id="b" * 32,
                request_hash="b" * 64,
                session_ref="12345678-1234-1234-1234-123456789012",
                auth_version=1,
                pipeline_config=version,
                context_snapshot=version,
            )
            insert("ai_generation", generation)
            rejects(
                lambda: insert(
                    "ai_generation",
                    generation
                    | {
                        "id": ids["other_generation"],
                        "round_number": 2,
                        "client_request_id": "c" * 32,
                    },
                ),
                1062,
            )
            fact = dict(
                id=ids["fact"],
                conversation_id=ids["conversation"],
                state_version=1,
                fact_kind="OBSERVATION",
                operation="ASSERT",
                source_type="USER_REPORT",
                reported_by=ids["actor"],
                statement="用户报告网络无法连接",
                source_locator=version,
            )
            insert("ai_diagnostic_fact", fact)
            rejects(
                lambda: cursor.execute(
                    "UPDATE ai_diagnostic_fact SET source_type='ASSISTANT_PROPOSAL',"
                    "reported_by=NULL,source_generation_id=%s,fact_kind='PERFORMED_ACTION'",
                    (ids["generation"],),
                ),
                3819,
            )
            rejects(
                lambda: cursor.execute("UPDATE ai_diagnostic_fact SET operation='RETRACT'"), 3819
            )
            source = dict(
                id=ids["source"],
                generation_id=ids["generation"],
                conversation_id=ids["conversation"],
                source_number=1,
                source_type="DIAGNOSTIC_FACT",
                fact_id=ids["fact"],
                source_version=version,
                locator_snapshot=version,
                excerpt_snapshot="用户报告无法联网",
            )
            insert("ai_source_usage", source)
            rejects(
                lambda: cursor.execute("UPDATE ai_source_usage SET first_model_call_number=1"), 1452
            )
            insert(
                "ai_model_call",
                dict(
                    id=ids["model"],
                    generation_id=ids["generation"],
                    call_number=1,
                    purpose="DECISION",
                    provider="CI",
                    requested_model="CI",
                    input_manifest=version,
                    started_at="2026-01-01 00:00:00",
                ),
            )
            cursor.execute("UPDATE ai_source_usage SET first_model_call_number=1")
            rejects(
                lambda: cursor.execute(
                    "UPDATE ai_model_call SET input_tokens=3,cached_input_tokens=4"
                ),
                3819,
            )
            rejects(lambda: cursor.execute("UPDATE ai_model_call SET status='SUCCEEDED'"), 3819)
            checks += 5  # 资格、无设备工单、用户事实、无模型来源及真实模型关联的合法写入。
        return checks
    finally:
        db.rollback()
        db.close()
