"""启动独占应用，执行HTTP、双向TLS和期限竞争验收，结束时关闭自有进程。"""

import argparse
import ipaddress
import json
import os
import secrets
import socket
import subprocess
import sys
import time
from datetime import UTC, datetime, timedelta
from pathlib import Path

import httpx
import pymysql
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID
from verify_database import ROOT, java_migrate
from verify_manual_workflow import Browser


def certificates(directory: Path) -> None:
    """临时CA只在内存持有签发私钥，叶子密钥只写入忽略的验收目录。"""
    directory.mkdir(parents=True, exist_ok=True)
    now = datetime.now(UTC)
    ca_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    ca_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "desk-isolated-ci")])
    authority = (
        x509.CertificateBuilder()
        .subject_name(ca_name)
        .issuer_name(ca_name)
        .public_key(ca_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=1))
        .not_valid_after(now + timedelta(days=2))
        .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
        .sign(ca_key, hashes.SHA256())
    )
    (directory / "ca.pem").write_bytes(authority.public_bytes(serialization.Encoding.PEM))
    for name, usage in (
        ("user", ExtendedKeyUsageOID.SERVER_AUTH),
        ("ai", ExtendedKeyUsageOID.CLIENT_AUTH),
    ):
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        cert = (
            x509.CertificateBuilder()
            .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, name)]))
            .issuer_name(ca_name)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(minutes=1))
            .not_valid_after(now + timedelta(days=1))
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .add_extension(x509.ExtendedKeyUsage([usage]), critical=False)
            .add_extension(
                x509.SubjectAlternativeName(
                    [
                        x509.DNSName("localhost"),
                        x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
                    ]
                ),
                critical=False,
            )
            .sign(ca_key, hashes.SHA256())
        )
        (directory / f"{name}.pem").write_bytes(cert.public_bytes(serialization.Encoding.PEM))
        key_file = directory / f"{name}-key.pem"
        key_file.write_bytes(
            key.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.NoEncryption(),
            )
        )
        key_file.chmod(0o600)


def free_port() -> int:
    """临时监听仅选择回环空闲端口；启动失败不接管已有进程。"""
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def ready(process: subprocess.Popen, url: str) -> None:
    """等待本次拥有的进程健康，超时和提前退出均失败。"""
    deadline = time.monotonic() + 90
    with httpx.Client(timeout=2, trust_env=False) as client:
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError("验收应用提前退出，检查所属日志")
            try:
                response = client.get(url + "/actuator/health")
                if response.is_success:
                    return
            except httpx.HTTPError:
                pass
            time.sleep(0.5)
    raise TimeoutError("验收应用未在90秒内就绪")


def main() -> None:
    """拒绝共享地址和非空库，保留证据，凭据不进入命令行。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", required=True, type=Path)
    args = parser.parse_args()
    if os.environ.get("DESK_VERIFY_ISOLATED") != "true":
        raise RuntimeError("必须显式声明独占实例")
    connection = {
        "host": os.environ.get("DESK_VERIFY_HOST", "127.0.0.1"),
        "port": int(os.environ["DESK_VERIFY_PORT"]),
        "user": os.environ.get("DESK_VERIFY_USERNAME", "root"),
        "password": os.environ["DESK_VERIFY_PASSWORD"],
    }
    if connection["host"] not in {"127.0.0.1", "localhost"}:
        raise RuntimeError("应用验收只接受本机专用数据库")
    work = args.work_dir.resolve()
    if not work.is_relative_to(ROOT / ".local"):
        raise RuntimeError("验收产物必须归项目.local目录")
    work.mkdir(parents=True, exist_ok=True)
    with pymysql.connect(**connection, autocommit=True) as database:
        with database.cursor() as cursor:
            for service in ("user", "ticket"):
                name = f"desk_{service}_ci"
                cursor.execute(
                    f"CREATE DATABASE IF NOT EXISTS {name} "
                    "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin"
                )
                cursor.execute(
                    "SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA=%s",
                    (name,),
                )
                tables = [row[0] for row in cursor.fetchall() if row[0] != "flyway_schema_history"]
                for table in tables:
                    if not table.replace("_", "").isalnum():
                        raise RuntimeError("未知测试表名")
                    cursor.execute(f"SELECT COUNT(*) FROM `{name}`.`{table}`")
                    if cursor.fetchone()[0]:
                        raise RuntimeError("应用验收拒绝接管非空数据库")
                java_migrate(service, name, connection, work)
    tls = work / "tls"
    certificates(tls)
    user_port, ticket_port, rpc_port, gateway_port = (
        free_port(),
        free_port(),
        free_port(),
        free_port(),
    )
    password, ticket_token, ai_token = (secrets.token_hex(24) + "Ab9" for _ in range(3))
    base = {
        "desk.support.rpc.enabled": False,
        "desk.support.ai-token": secrets.token_hex(32),
        "desk.support.knowledge-token": secrets.token_hex(32),
        "desk.ai.rpc.target": "localhost:1",
        "desk.ai.rpc.token": secrets.token_hex(32),
        "desk.ai.rpc.ca-file": str(tls / "ca.pem"),
        "desk.ai.rpc.certificate-file": str(tls / "ai.pem"),
        "desk.ai.rpc.private-key-file": str(tls / "ai-key.pem"),
        "spring.profiles.active": "dev,test",
        "spring.config.location": "classpath:/application.yml",
        "spring.cloud.nacos.discovery.enabled": "false",
        "spring.cloud.nacos.config.enabled": "false",
        "spring.cloud.nacos.config.import-check.enabled": "false",
        "spring.cloud.sentinel.enabled": "false",
        "desk.infrastructure.enabled": "false",
        "desk.infrastructure.resource-env": "ci",
        "management.tracing.enabled": "false",
        "spring.flyway.enabled": "false",
        "spring.datasource.username": connection["user"],
        "spring.datasource.password": connection["password"],
        "spring.data.redis.host": "127.0.0.1",
        "spring.data.redis.port": os.environ["DESK_VERIFY_REDIS_PORT"],
        "spring.data.redis.password": os.environ.get("DESK_VERIFY_REDIS_PASSWORD", ""),
        "desk.redis-key-prefix": "desk:ci:identity:" + secrets.token_hex(8) + ":",
        "desk.identity.csrf-secret": secrets.token_hex(32),
        "desk.identity.ticket-token": ticket_token,
        "desk.identity.knowledge-token": secrets.token_hex(32),
        "desk.identity.ai-token": ai_token,
        "desk.identity.user-token": ticket_token,
        "desk.identity.user-url": "",
        "spring.cloud.discovery.client.simple.instances.desk-user[0].uri": f"http://127.0.0.1:{user_port}",
        "spring.cloud.discovery.client.simple.instances.desk-ticket[0].uri": f"http://127.0.0.1:{ticket_port}",
        "desk.downstream.ai": "http://127.0.0.1:1",
        "desk.downstream.knowledge": "http://127.0.0.1:1",
        "desk.identity.rpc.port": rpc_port,
        "desk.identity.rpc.ca-file": str(tls / "ca.pem"),
        "desk.identity.rpc.certificate-file": str(tls / "user.pem"),
        "desk.identity.rpc.private-key-file": str(tls / "user-key.pem"),
        "desk.bootstrap.organization-code": "ci-provider",
        "desk.bootstrap.organization-name": "隔离验收服务商",
        "desk.bootstrap.username": "ci-admin",
        "desk.bootstrap.password": password,
        "spring.autoconfigure.exclude": (
            "org.springframework.boot.autoconfigure.security.servlet."
            "UserDetailsServiceAutoConfiguration"
        ),
    }
    environment = os.environ | {
        "DESK_RESOURCE_ENV": "ci",
        "PYTHONUTF8": "1",
        "DESK_VERIFY_USER_URL": f"http://127.0.0.1:{user_port}",
        "DESK_VERIFY_TICKET_URL": f"http://127.0.0.1:{ticket_port}",
        "DESK_VERIFY_ADMIN": "ci-admin",
        "DESK_VERIFY_ADMIN_PASSWORD": password,
        "DESK_VERIFY_TLS_DIR": str(tls),
        "DESK_VERIFY_AI_TOKEN": ai_token,
        "DESK_VERIFY_RPC_TARGET": f"localhost:{rpc_port}",
    }
    processes, logs = [], []
    service_environments = {}
    try:
        for service, port in (
            ("user", user_port),
            ("ticket", ticket_port),
            ("gateway", gateway_port),
        ):
            config = base | {
                "server.port": port,
                "server.address": "127.0.0.1",
                "spring.datasource.url": f"jdbc:mysql://127.0.0.1:{connection['port']}/desk_{service}_ci?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8",
                "desk.bootstrap.enabled": str(service == "user").lower(),
                "logging.file.name": str(work / f"{service}.json"),
            }
            env = environment | {"SPRING_APPLICATION_JSON": json.dumps(config)}
            service_environments[service] = env
            log = (work / f"{service}.log").open("wb")
            logs.append(log)
            process = subprocess.Popen(
                [
                    "java",
                    f"-Dcsp.sentinel.log.dir={work / 'sentinel'}",
                    "-jar",
                    str(ROOT / f"desk-{service}/target/desk-{service}-0.1.0-SNAPSHOT.jar"),
                ],
                cwd=ROOT,
                env=env,
                stdout=log,
                stderr=subprocess.STDOUT,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
            )
            processes.append(process)
            ready(process, f"http://127.0.0.1:{port}")
        # 验收经真实Gateway路由；服务实例列表替代Nacos，不伪称注册中心已验证。
        environment["DESK_VERIFY_USER_URL"] = f"http://127.0.0.1:{gateway_port}"
        environment["DESK_VERIFY_TICKET_URL"] = f"http://127.0.0.1:{gateway_port}"
        browser = Browser(
            environment["DESK_VERIFY_USER_URL"], environment["DESK_VERIFY_TICKET_URL"]
        )
        try:
            browser.login("ci-admin", password)
            changed = secrets.token_hex(24) + "Ab9"
            browser.change("ci-admin", password, changed)
            environment["DESK_VERIFY_ADMIN_PASSWORD"] = changed
        finally:
            browser.client.close()
        for script in ("verify_manual_workflow", "verify_identity_rpc"):
            command = [sys.executable, str(ROOT / f".github/scripts/{script}.py")]
            if script == "verify_manual_workflow":
                command += ["--output", str(work / "manual-workflow.json")]
            with (work / f"{script}.log").open("wb") as log:
                subprocess.run(
                    command,
                    env=environment,
                    stdout=log,
                    stderr=subprocess.STDOUT,
                    check=True,
                    timeout=180,
                )
            print("PASS", script, flush=True)
        classpath = os.pathsep.join(
            [
                str(work / "flyway-libraries/ticket/*"),
                str(ROOT / "desk-ticket/target/classes"),
            ]
        )
        with (work / "deadline.log").open("wb") as log:
            subprocess.run(
                [
                    "java",
                    f"-Dcsp.sentinel.log.dir={work / 'sentinel-deadline'}",
                    "-cp",
                    classpath,
                    str(ROOT / ".github/scripts/VerifyTicketDeadline.java"),
                ],
                env=service_environments["ticket"],
                stdout=log,
                stderr=subprocess.STDOUT,
                check=True,
                timeout=90,
            )
        print("PASS MySQL期限任务与关闭退回竞争", flush=True)
        with (work / "onsite.log").open("wb") as log:
            subprocess.run(
                [
                    "java",
                    f"-Dcsp.sentinel.log.dir={work / 'sentinel-onsite'}",
                    "-cp",
                    classpath,
                    str(ROOT / ".github/scripts/VerifyOnsite.java"),
                ],
                env=service_environments["ticket"],
                stdout=log,
                stderr=subprocess.STDOUT,
                check=True,
                timeout=90,
            )
        print("PASS MySQL预约容量与取消履约", flush=True)
        (work / "application-verification.json").write_text(
            json.dumps(
                {
                    "http": "passed",
                    "mtls_rpc": "passed",
                    "deadline_race_rounds": 10,
                    "onsite_transactions": "passed",
                    "gateway": "passed_with_static_discovery",
                    "deployment": "not_tested",
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
        for log in logs:
            log.close()


if __name__ == "__main__":
    main()
