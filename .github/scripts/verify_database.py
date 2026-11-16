"""在独占MySQL实例验证原生迁移、字典和非空旧库保护，不访问共享业务库。"""

import argparse
import json
import os
import re
import subprocess
import zipfile
from pathlib import Path

import pymysql
from alembic import command
from check_database_constraints import verify_constraints
from pydantic import SecretStr
from verify_ai_invariants import verify_ai_invariants

from servicedesk_ai.bootstrap.migrations import (
    migrate_database,
    migration_config,
    migration_connection,
)
from servicedesk_ai.bootstrap.settings import Settings

ROOT = Path(__file__).resolve().parents[2]
SERVICES = ("user", "ticket", "knowledge", "ai")


def java_migrate(
    service: str, database: str, connection: dict, work: Path, target: str = ""
) -> None:
    """运行真实Flyway及已构建迁移类，密码不进入命令行或输出。"""
    libraries = work / "flyway-libraries" / service
    libraries.mkdir(parents=True, exist_ok=True)
    jar = next((ROOT / f"desk-{service}/target").glob("desk-*-SNAPSHOT.jar"))
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.startswith("BOOT-INF/lib/") and name.endswith(".jar"):
                output = libraries / Path(name).name
                content = archive.read(name)
                if not output.exists() or output.read_bytes() != content:
                    output.write_bytes(content)
    env = os.environ | {
        "DESK_VERIFY_SERVICE": service,
        "DESK_VERIFY_JDBC_URL": (
            f"jdbc:mysql://{connection['host']}:{connection['port']}/{database}"
            "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
            "&characterEncoding=UTF-8&connectTimeout=3000&socketTimeout=60000"
        ),
        "DESK_VERIFY_USERNAME": connection["user"],
        "DESK_VERIFY_PASSWORD": connection["password"],
        "DESK_VERIFY_TARGET": target,
    }
    classpath = os.pathsep.join(
        [str(libraries / "*"), str(ROOT / f"desk-{service}/target/classes")]
    )
    result = subprocess.run(
        [
            "java",
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8",
            "-cp",
            classpath,
            str(ROOT / ".github/scripts/VerifyFlyway.java"),
        ],
        env=env,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    (work / f"{database}-flyway.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"{database}原生Flyway失败，详见所属验证日志")


def ai_migrate(database: str, connection: dict, target: str = "head") -> None:
    """复用API启动使用的Alembic与MySQL命名锁。"""
    settings = Settings(
        infrastructure_enabled=False,
        resource_env="ci" if database.endswith("_ci") else "test",
        mysql={
            "host": connection["host"],
            "port": connection["port"],
            "username": connection["user"],
            "password": SecretStr(connection["password"]),
            "database": database,
        },
    )
    if target == "head":
        migrate_database(settings)
    else:
        with migration_connection(settings) as resource:
            command.upgrade(migration_config(resource), target)


def dictionary() -> dict:
    """从正式设计字典读取表、字段、空值和类型合同。"""
    doc = (ROOT / "docs/设计/数据库设计.md").read_text(encoding="utf-8")
    tables = {}
    pattern = r"^## \d+\. `(desk_\w+)\.(\w+)` [^\n]+\n(.*?)(?=^## |\Z)"
    for match in re.finditer(pattern, doc, re.M | re.S):
        owner, table, body = match.groups()
        fields = re.findall(
            r"^\| `([a-z_][a-z0-9_]*)` \| ([^|]+) \| ([^|]+) \| [^|]+ \|$", body, re.M
        )
        tables[table] = (owner, {name: (typ.strip(), null) for name, typ, null in fields})
    if len(tables) != 31:
        raise RuntimeError("正式数据库字典表数异常")
    return tables


def verify_contract(connection: dict, suffix: str) -> dict:
    """逐字段比较真实information_schema，额外的框架表另计。"""
    contract = dictionary()
    db = pymysql.connect(**connection, charset="utf8mb4", autocommit=True)
    try:
        with db.cursor() as cursor:
            seen = set()
            for service in SERVICES:
                database = f"desk_{service}_{suffix}"
                cursor.execute(
                    "SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA=%s",
                    (database,),
                )
                actual_tables = {row[0] for row in cursor.fetchall()}
                owned = {
                    name for name, (owner, _) in contract.items() if owner == f"desk_{service}"
                }
                framework = {
                    "flyway_schema_history",
                    "alembic_version",
                    "SPRING_SESSION",
                    "SPRING_SESSION_ATTRIBUTES",
                }
                assert actual_tables - framework == owned, database
                for table in owned:
                    cursor.execute(
                        "SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,EXTRA "
                        "FROM information_schema.columns "
                        "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION",
                        (database, table),
                    )
                    rows = cursor.fetchall()
                    expected = contract[table][1]
                    assert {row[0] for row in rows} == set(expected), table
                    for name, typ, nullable, extra in rows:
                        declared, null = expected[name]
                        normalized = declared.replace("integer", "int").replace(
                            "boolean", "tinyint(1)"
                        )
                        assert typ == normalized, (table, name, typ, normalized)
                        if "STORED" in null:
                            assert "STORED GENERATED" in extra, (table, name)
                        else:
                            assert (nullable == "YES") == null.startswith("可空"), (table, name)
                    seen.add(table)
            cursor.execute("SELECT @@version")
            version = cursor.fetchone()[0]
        return {
            "mysql": version,
            "tables": len(seen),
            "fields": sum(len(v[1]) for v in contract.values()),
        }
    finally:
        db.close()


def main() -> None:
    """只允许显式标记的独占实例，已有CI库也拒绝接管或清空。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get("DESK_VERIFY_ISOLATED") != "true":
        raise RuntimeError("必须显式声明独占可销毁实例")
    connection = {
        "host": os.environ.get("DESK_VERIFY_HOST", "127.0.0.1"),
        "port": int(os.environ.get("DESK_VERIFY_PORT", "3306")),
        "user": os.environ.get("DESK_VERIFY_USERNAME", "root"),
        "password": os.environ["DESK_VERIFY_PASSWORD"],
    }
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)
    db = pymysql.connect(**connection, charset="utf8mb4", autocommit=True)
    try:
        with db.cursor() as cursor:
            for service in SERVICES:
                cursor.execute(
                    f"CREATE DATABASE `desk_{service}_ci` "
                    "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin"
                )
        for service in SERVICES:
            database = f"desk_{service}_ci"
            if service == "ai":
                ai_migrate(database, connection, "0001")
            else:
                java_migrate(service, database, connection, work, "1")
        # 一个可空元信息行已经足以阻止新版本，必须在任何DDL前拒绝。
        guards = {
            "ticket": (
                "it_product_model",
                "INSERT INTO it_product_model (id,category,brand,model_name,created_by) "
                "VALUES (%s,'COMPUTER','test','test',%s)",
                ("a" * 32, "b" * 32),
            ),
            "knowledge": (
                "kb_document_series",
                "INSERT INTO kb_document_series (id,source_type,source_namespace,source_code,"
                "source_organization,language_code,audience,created_by) "
                "VALUES (%s,'OFFICIAL_MANUAL','test','test','test','zh-CN','CUSTOMER',%s)",
                ("a" * 32, "b" * 32),
            ),
            "ai": (
                "ai_conversation",
                "INSERT INTO ai_conversation "
                "(id,owner_id,customer_id,device_id,product_model_id,title) "
                "VALUES (%s,%s,%s,%s,%s,'test')",
                tuple(x * 32 for x in "abcde"),
            ),
        }
        for service, (table, statement, parameters) in guards.items():
            database = f"desk_{service}_ci"
            db.select_db(database)
            with db.cursor() as cursor:
                cursor.execute(statement, parameters)
                cursor.execute(f"SHOW CREATE TABLE `{table}`")
                before = cursor.fetchone()[1]
            try:
                if service == "ai":
                    ai_migrate(database, connection)
                else:
                    java_migrate(service, database, connection, work)
            except Exception as error:
                # 必须核对确实是空库保护触发，而不是无关连接错误。
                evidence = (
                    str(error)
                    if service == "ai"
                    else (work / f"{database}-flyway.log").read_text(encoding="utf-8")
                )
                assert "旧业务表非空" in evidence, evidence
            else:
                raise AssertionError(f"{database}未阻止非空旧表")
            with db.cursor() as cursor:
                cursor.execute(f"SHOW CREATE TABLE `{table}`")
                assert cursor.fetchone()[1] == before
                cursor.execute(f"SELECT COUNT(*) FROM `{table}`")
                assert cursor.fetchone()[0] == 1
                # 仅删除刚刚写入独占CI库的固定夹具，未删除其他数据。
                cursor.execute(f"DELETE FROM `{table}` WHERE id=%s", ("a" * 32,))
                if service != "ai":
                    # MySQL非事务迁移失败有原生失败记录，repair只能用于确认尚未执行DDL的CI案例。
                    cursor.execute("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0")
                    assert cursor.fetchone()[0] == 1
            if service != "ai":
                # 原生repair在确认结构不变后清除失败版本；不手改成功历史。
                repair_flyway(service, database, connection, work)
        for service in SERVICES:
            for _ in range(2):
                if service == "ai":
                    ai_migrate("desk_ai_ci", connection)
                else:
                    java_migrate(service, f"desk_{service}_ci", connection, work)
        result = verify_contract(connection, "ci") | {
            "native_migration": "passed",
            "rerun": "passed",
            "nonempty_guards": 3,
            "business_constraint_checks": verify_constraints(connection),
            "ai_invariants": verify_ai_invariants(connection),
        }
        # 全部库均由本函数在独占实例内创建，验证从全空环境一次升至head。
        with db.cursor() as cursor:
            for service in SERVICES:
                cursor.execute(f"DROP DATABASE `desk_{service}_ci`")
                cursor.execute(
                    f"CREATE DATABASE `desk_{service}_ci` "
                    "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin"
                )
        for service in SERVICES:
            if service == "ai":
                ai_migrate("desk_ai_ci", connection)
            else:
                java_migrate(service, f"desk_{service}_ci", connection, work)
        assert verify_contract(connection, "ci") == {
            key: result[key] for key in ("mysql", "tables", "fields")
        }
        result["fresh_install"] = "passed"
        (work / "database-verification.json").write_text(
            json.dumps(result, indent=2), encoding="utf-8"
        )
        print(json.dumps(result))
    finally:
        db.close()


def repair_flyway(service: str, database: str, connection: dict, work: Path) -> None:
    """仅对已证明未变更结构的独占CI失败案例调用Flyway repair。"""
    old = os.environ.get("DESK_VERIFY_REPAIR")
    os.environ["DESK_VERIFY_REPAIR"] = "true"
    try:
        java_migrate(service, database, connection, work, "1")
    finally:
        if old is None:
            os.environ.pop("DESK_VERIFY_REPAIR", None)
        else:
            os.environ["DESK_VERIFY_REPAIR"] = old


if __name__ == "__main__":
    main()
