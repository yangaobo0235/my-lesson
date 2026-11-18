"""对隔离user/ticket进程执行真实会话、权限、并发及人工工单合同验收。"""

import argparse
import json
import os
import secrets
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime, timedelta
from pathlib import Path
from urllib.parse import urlparse

import httpx


def key() -> str:
    """每次独立业务意图使用32位标识。"""
    return uuid.uuid4().hex


class Browser:
    """模拟同源浏览器Cookie及独立CSRF，不伪造服务端身份。"""

    def __init__(self, user_url: str, ticket_url: str) -> None:
        self.user_url = user_url
        self.ticket_url = ticket_url
        self.client = httpx.Client(timeout=15, trust_env=False)
        self.csrf = ""

    def raw(self, path: str, body: dict | None = None, *, ticket: bool = False) -> dict:
        """不输出口令或请求体，错误诊断只包含安全响应。"""
        response = self.client.request(
            "GET" if body is None else "POST",
            (self.ticket_url if ticket else self.user_url) + "/api/v1/" + path,
            json=body,
            headers={"X-CSRF-TOKEN": self.csrf} if self.csrf else {},
        )
        value = response.json()
        assert isinstance(value, dict) and isinstance(value.get("code"), int), path
        assert value.get("traceId"), (path, "缺少追踪号")
        assert (value["code"] == 0) == response.is_success, (path, response.status_code)
        return value

    def call(self, path: str, body: dict | None = None, *, ticket=False, code=0):
        """核对明确错误码后返回数据，不能将跳过计为通过。"""
        value = self.raw(path, body, ticket=ticket)
        assert value["code"] == code, (path, value)
        return value["data"]

    def login(self, username: str, password: str) -> dict:
        """登录前后分别取得当前会话令牌。"""
        self.csrf = self.call("users/auth/csrf")["token"]
        identity = self.call("users/auth/login", {"username": username, "password": password})
        self.csrf = self.call("users/auth/csrf")["token"]
        return identity

    def change(self, username: str, old: str, new: str) -> dict:
        """改密必须使旧会话失效，随后重新登录。"""
        self.call("users/auth/password", {"currentPassword": old, "newPassword": new})
        self.call("users/auth/me", code=2001)
        return self.login(username, new)


class Workflow:
    """每次创建独立命名测试数据，不删除或接管已有业务数据。"""

    def __init__(self, user_url: str, ticket_url: str, username: str, password: str) -> None:
        self.urls = user_url, ticket_url
        self.browsers: list[Browser] = []
        self.passed: list[str] = []
        self.admin = self.browser()
        self.me = self.admin.login(username, password)
        if self.me["mustChangePassword"]:
            changed = secrets.token_hex(20) + "Ab9"
            self.me = self.admin.change(username, password, changed)
            password = changed
        self.admin_credentials = username, password
        self.suffix = key()[:10]

    def browser(self) -> Browser:
        """统一关闭测试拥有的HTTP连接池。"""
        browser = Browser(*self.urls)
        self.browsers.append(browser)
        return browser

    def passed_case(self, name: str) -> None:
        """只在全部断言通过后登记证据。"""
        self.passed.append(name)
        print("PASS", name, flush=True)

    def organization(self, name: str) -> dict:
        """通过真实管理入口建立客户。"""
        return self.admin.call(
            "admin/organization/add",
            {
                "organizationCode": name + self.suffix,
                "organizationName": "隔离验证" + name,
            },
        )

    def account(self, name: str, organization: str, roles: list[str], scopes=()) -> tuple:
        """创建账号、设置范围、强制首次改密，不直接写身份表。"""
        password = secrets.token_hex(20) + "Ab9"
        username = name + self.suffix
        row = self.admin.call(
            "admin/account/add",
            {
                "organizationId": organization,
                "username": username,
                "displayName": name,
                "initialPassword": password,
                "roles": roles,
            },
        )
        if scopes:
            row = self.admin.call(
                "admin/account/set-customer-scopes",
                {
                    "id": row["id"],
                    "rowVersion": row["rowVersion"],
                    "customerIds": list(scopes),
                    "reason": "隔离验证人员范围",
                },
            )
        browser = self.browser()
        assert browser.login(username, password)["mustChangePassword"]
        browser.call("tickets", ticket=True, code=2004)
        new_password = secrets.token_hex(20) + "Ab9"
        browser.change(username, password, new_password)
        return browser, row, username, new_password

    def create_form(self, service: str | None) -> dict:
        """构造设备为空的直接建单，人工链不需要AI。"""
        return {
            "clientRequestId": key(),
            "serviceItemId": service,
            "deviceId": None,
            "title": "Windows终端问题",
            "symptom": "登录后黑屏，尚未确认原因",
            "attemptedActions": "尚未操作",
            "sourceDraftId": None,
            "supportContext": {
                "osName": "Windows",
                "osVersion": "11",
                "connectionType": "UNKNOWN",
                "affectedScope": "UNKNOWN",
            },
        }

    def action(self, browser: Browser, ticket: dict, name: str, **fields) -> dict:
        """新动作取当前版本，回放场景单独保留原表单。"""
        return browser.call(
            f"tickets/{ticket['id']}/{name}",
            {"clientActionId": key(), "rowVersion": ticket["rowVersion"], **fields},
            ticket=True,
        )

    def current(self, browser: Browser, ticket: dict) -> dict:
        """重新读取当前授权投影，避免沿用旧版本。"""
        return browser.call("tickets/" + ticket["id"], ticket=True)["ticket"]

    def run(self) -> None:
        """验证身份、安全、资格、竞争、通知与人工闭环。"""
        a, b = self.organization("customer-a-"), self.organization("customer-b-")
        customer, _, _, _ = self.account("customer-", a["id"], ["CUSTOMER_USER"])
        outsider, _, _, _ = self.account("outsider-", b["id"], ["CUSTOMER_USER"])
        staff, staff_row, staff_name, staff_password = self.account(
            "staff-",
            self.me["organizationId"],
            ["SERVICE_AGENT", "TECHNICIAN"],
            [a["id"]],
        )
        second, second_row, _, _ = self.account(
            "second-", self.me["organizationId"], ["TECHNICIAN"], [a["id"]]
        )
        self.passed_case("首次改密与角色客户范围")

        token = customer.csrf
        customer.csrf = "wrong-csrf"
        customer.call("tickets", self.create_form(None), ticket=True, code=1003)
        customer.csrf = token
        anonymous = self.browser()
        anonymous.client.headers.update(
            {"X-User-Id": self.me["ownerId"], "X-Roles": "SYSTEM_ADMIN"}
        )
        anonymous.call("tickets", ticket=True, code=2001)
        customer.call("admin/organization/list", {}, code=1003)
        self.admin.call("tickets", ticket=True, code=1003)
        self.passed_case("CSRF、伪造身份头及管理员正文隔离")

        item_form = {
            "clientActionId": key(),
            "serviceCode": "terminal_" + self.suffix,
            "category": "TERMINAL",
            "serviceName": "Windows办公终端",
            "supportScope": "Windows 11用户侧低风险排查",
            "onsiteSupported": False,
            "reason": "隔离案例",
        }
        item = self.admin.call("admin/service-item/add", item_form, ticket=True)
        assert self.admin.call("admin/service-item/add", item_form, ticket=True)["id"] == item["id"]
        self.admin.call(
            "admin/service-item/add",
            item_form | {"serviceName": "异义"},
            ticket=True,
            code=1004,
        )
        grant_form = {
            "clientActionId": key(),
            "customerId": a["id"],
            "serviceItemId": item["id"],
            "startsOn": str((datetime.now(UTC) + timedelta(hours=8)).date() - timedelta(days=1)),
            "endsOn": str((datetime.now(UTC) + timedelta(hours=8)).date() + timedelta(days=365)),
            "reason": "验证开通",
        }
        entitlement = staff.call("admin/service-entitlement/grant", grant_form, ticket=True)
        assert (
            staff.call("admin/service-entitlement/grant", grant_form, ticket=True)["id"]
            == entitlement["id"]
        )
        staff.call("admin/service-item/list", {}, ticket=True)
        outsider.call("tickets", self.create_form(item["id"]), ticket=True, code=3001)
        self.passed_case("目录资格幂等与未开通拒绝")

        form = self.create_form(item["id"])
        with ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(customer.raw, "tickets", form, ticket=True) for _ in range(2)]
            concurrent = [future.result() for future in futures]
        assert all(result["code"] == 0 for result in concurrent), concurrent
        ticket = concurrent[0]["data"]
        assert ticket["id"] == concurrent[1]["data"]["id"]
        customer.call("tickets", form | {"title": "同键不同问题"}, ticket=True, code=1004)
        outsider.call("tickets/" + ticket["id"], ticket=True, code=1002)
        assert outsider.call("tickets", ticket=True)["total"] == "0"
        self.passed_case("并发建单唯一回执、异义冲突与跨客户隔离")

        status_form = {
            "id": entitlement["id"],
            "rowVersion": entitlement["rowVersion"],
            "clientActionId": key(),
            "reason": "暂停新受理",
        }
        entitlement = staff.call("admin/service-entitlement/suspend", status_form, ticket=True)
        assert customer.call("tickets", form, ticket=True)["id"] == ticket["id"]
        customer.call("tickets", self.create_form(item["id"]), ticket=True, code=3001)
        entitlement = staff.call(
            "admin/service-entitlement/resume",
            status_form | {"rowVersion": entitlement["rowVersion"], "clientActionId": key()},
            ticket=True,
        )
        assert entitlement["id"] == status_form["id"]
        self.passed_case("暂停阻止新受理且原回执优先，恢复沿原资格")

        accept = {"clientActionId": key(), "rowVersion": ticket["rowVersion"]}
        other_accept = accept | {"clientActionId": key()}
        path = "tickets/" + ticket["id"]
        with ThreadPoolExecutor(max_workers=2) as pool:
            left = pool.submit(staff.raw, path + "/accept", accept, ticket=True)
            right = pool.submit(second.raw, path + "/accept", other_accept, ticket=True)
            results = left.result(), right.result()
        assert sorted(result["code"] for result in results) == [0, 1004], results
        winner, loser = (staff, second) if results[0]["code"] == 0 else (second, staff)
        original = accept if winner is staff else other_accept
        receipt = winner.call(path + "/accept", original, ticket=True)
        assert winner.call(path + "/actions/" + original["clientActionId"], ticket=True) == receipt
        ticket = self.current(customer, ticket)
        loser.call(
            path + "/resolve",
            {
                "rowVersion": ticket["rowVersion"],
                "clientActionId": key(),
                "content": "越权结案",
                "resolutionType": "GUIDANCE",
            },
            ticket=True,
            code=1003,
        )
        self.passed_case("双人接单仅一方成功、原动作回执与当前处理人保护")

        self.action(winner, ticket, "comments", content="内部定位信息", visibility="INTERNAL")
        ticket = self.current(winner, ticket)
        assert all(
            event["visibility"] == "PUBLIC"
            for event in customer.call(path + "/events", ticket=True)["records"]
        )
        assert any(
            event["visibility"] == "INTERNAL"
            for event in winner.call(path + "/events", ticket=True)["records"]
        )
        information = {
            "clientActionId": key(),
            "rowVersion": ticket["rowVersion"],
            "content": "请确认是否仅当前用户受影响",
        }
        winner.call(path + "/request-information", information, ticket=True, code=1001)
        follow_up = datetime.now(UTC).replace(microsecond=0) + timedelta(hours=1)
        winner.call(
            path + "/request-information",
            information | {"followUpAt": follow_up.isoformat()},
            ticket=True,
        )
        ticket = self.current(customer, ticket)
        assert ticket["status"] == "WAITING_CUSTOMER"
        assert datetime.fromisoformat(ticket["followUpAt"]) == follow_up
        self.action(customer, ticket, "supplements", content="其他用户正常，仅当前用户")
        ticket = self.current(winner, ticket)
        assert ticket["status"] == "PROCESSING"
        self.passed_case("内部记录隔离及反馈驱动人工处理分支")

        self.action(
            winner,
            ticket,
            "resolve",
            content="提供显示驱动恢复检查指引",
            resolutionType="GUIDANCE",
        )
        ticket = self.current(customer, ticket)
        assert ticket["confirmationDueAt"] is not None
        self.action(customer, ticket, "unresolved", content="按指引检查后仍未解决")
        ticket = self.current(winner, ticket)
        assert ticket["resolutionType"] is None and ticket["confirmationDueAt"] is None
        self.action(
            winner,
            ticket,
            "resolve",
            content="已确认需要终端维护团队现场检查",
            resolutionType="REFERRED",
            referralTarget="客户终端维护团队",
        )
        ticket = self.current(customer, ticket)
        self.action(customer, ticket, "confirm")
        ticket = self.current(customer, ticket)
        assert ticket["status"] == "CLOSED" and ticket["closureReason"] == "CUSTOMER_CONFIRMED"
        assert ticket["resolutionType"] == "REFERRED"
        notes = customer.call("tickets/notifications", ticket=True)
        assert int(notes["total"]) > 0
        note = notes["records"][0]
        customer.call("tickets/notifications/" + note["id"] + "/read", {}, ticket=True)
        outsider.call("tickets/notifications/" + note["id"] + "/read", {}, ticket=True, code=1002)
        customer.call("tickets/overview", ticket=True)
        self.passed_case("退回清空本轮结论、转介确认、通知权限与工作台")

        triage = customer.call("tickets", self.create_form(None), ticket=True)
        assert triage["entitlementId"] is None and triage["serviceItemId"] is None
        assert triage["serviceScopeSnapshot"]["admissionType"] == "TRIAGE"
        self.action(staff, triage, "accept")
        triage = self.current(staff, triage)
        staff.call(
            "tickets/" + triage["id"] + "/resolve",
            {
                "clientActionId": key(),
                "rowVersion": triage["rowVersion"],
                "content": "未分类不能承诺已恢复",
                "resolutionType": "RESTORED",
            },
            ticket=True,
            code=1001,
        )
        self.action(
            staff,
            triage,
            "context",
            serviceItemId=item["id"],
            supportContext={"osName": "Windows", "osVersion": "11"},
            reason="人工核对后分类",
        )
        triage = self.current(staff, triage)
        assert triage["serviceItemId"] == item["id"]
        self.passed_case("不确定分类入场、未分类结果限制及人工补分类")

        # 相同日期覆盖首日和末日；资格修改不重写已有工单的受理历史。
        today = (datetime.now(UTC) + timedelta(hours=8)).date()
        entitlement = staff.call(
            "admin/service-entitlement/query", {"id": entitlement["id"]}, ticket=True
        )
        change = {
            "id": entitlement["id"],
            "clientActionId": key(),
            "rowVersion": entitlement["rowVersion"],
            "startsOn": str(today),
            "endsOn": str(today),
            "reason": "首尾日期边界",
        }
        entitlement = staff.call("admin/service-entitlement/update", change, ticket=True)
        customer.call("tickets", self.create_form(item["id"]), ticket=True)
        expired = change | {
            "clientActionId": key(),
            "rowVersion": entitlement["rowVersion"],
            "startsOn": str(today - timedelta(days=2)),
            "endsOn": str(today - timedelta(days=1)),
        }
        entitlement = staff.call("admin/service-entitlement/update", expired, ticket=True)
        customer.call("tickets", self.create_form(item["id"]), ticket=True, code=3001)
        customer.call("tickets/" + ticket["id"], ticket=True)
        restore = change | {"clientActionId": key(), "rowVersion": entitlement["rowVersion"]}
        staff.call("admin/service-entitlement/update", restore, ticket=True)
        events = staff.call(
            "admin/service-entitlement/events", {"id": entitlement["id"]}, ticket=True
        )
        assert int(events["total"]) >= 6
        self.passed_case("资格首末日有效、过期拒绝新请求、旧履约保留及不可变审计")

        model = staff.call(
            "admin/product-model/add",
            {
                "category": "COMPUTER",
                "brand": "隔离验证",
                "modelName": self.suffix,
                "supportedSystems": "Windows 11",
            },
            ticket=True,
        )
        asset = staff.call(
            "admin/device/add",
            {
                "customerId": a["id"],
                "productModelId": model["id"],
                "assetCode": "TEST-" + self.suffix,
            },
            ticket=True,
        )
        own = self.create_form(item["id"]) | {"deviceId": asset["id"]}
        created = customer.call("tickets", own, ticket=True)
        assert created["deviceId"] == asset["id"]
        # 另一客户先有自己有效项目，确保失败来自设备归属而非无服务。
        other_staff, _, _, _ = self.account(
            "asset-agent-", self.me["organizationId"], ["SERVICE_AGENT"], [b["id"]]
        )
        other_staff.call(
            "admin/service-entitlement/grant",
            grant_form
            | {
                "clientActionId": key(),
                "customerId": b["id"],
            },
            ticket=True,
        )
        denied = outsider.raw(
            "tickets", self.create_form(item["id"]) | {"deviceId": asset["id"]}, ticket=True
        )
        assert denied["code"] == 3101, denied
        staff.call(
            "admin/device/retire",
            {"id": asset["id"], "rowVersion": asset["rowVersion"]},
            ticket=True,
        )
        denied = customer.raw(
            "tickets", self.create_form(item["id"]) | {"deviceId": asset["id"]}, ticket=True
        )
        assert denied["code"] == 3101, denied
        assert customer.call("tickets", own, ticket=True)["id"] == created["id"]
        self.passed_case("可选设备、跨客户及退役拒绝、退役后原建单回执保留")

        third, third_row, _, _ = self.account(
            "third-", self.me["organizationId"], ["TECHNICIAN"], [a["id"]]
        )
        assigned = customer.call("tickets", self.create_form(item["id"]), ticket=True)
        self.action(staff, assigned, "accept")
        assigned = self.current(staff, assigned)
        with ThreadPoolExecutor(max_workers=2) as pool:
            futures = [
                pool.submit(
                    staff.raw,
                    "tickets/" + assigned["id"] + "/reassign",
                    {
                        "clientActionId": key(),
                        "rowVersion": assigned["rowVersion"],
                        "assigneeId": target["id"],
                        "reason": "验证并发转派",
                    },
                    ticket=True,
                )
                for target in (second_row, third_row)
            ]
            outcomes = [future.result()["code"] for future in futures]
        assert sorted(outcomes) == [0, 1004], outcomes
        assigned = self.current(staff, assigned)
        staff.call(
            "tickets/" + assigned["id"] + "/resolve",
            {
                "clientActionId": key(),
                "rowVersion": assigned["rowVersion"],
                "content": "原处理人不得继续处置",
                "resolutionType": "GUIDANCE",
            },
            ticket=True,
            code=1003,
        )
        self.passed_case("并发转派仅一个成功，原处理人立即失去处理权限")

        current_staff = self.admin.call("admin/account/query", {"id": staff_row["id"]})
        self.admin.call(
            "admin/account/set-customer-scopes",
            {
                "id": current_staff["id"],
                "rowVersion": current_staff["rowVersion"],
                "customerIds": [],
                "reason": "验证撤权",
            },
        )
        staff.call(path, ticket=True, code=2002)
        staff.login(staff_name, staff_password)
        staff.call(path, ticket=True, code=1002)
        assert staff.call("tickets/notifications", ticket=True)["total"] == "0"
        self.passed_case("撤权使旧会话失效，新登录不能读取原工单与通知")

        admin_row = self.admin.call("admin/account/query", {"id": self.me["ownerId"]})
        self.admin.call(
            "admin/account/disable",
            {
                "id": admin_row["id"],
                "rowVersion": admin_row["rowVersion"],
                "reason": "验证最后管理员保护",
            },
            code=2102,
        )
        self.admin.call(
            "admin/audit-event/list",
            {
                "targetUserId": second_row["id"],
                "targetOrganizationId": self.me["organizationId"],
            },
            code=1001,
        )
        self.admin.call("admin/audit-event/list", {"targetUserId": second_row["id"]})
        self.passed_case("最后管理员保护及审计目标互斥校验")

        backup, backup_row, backup_name, backup_password = self.account(
            "backup-admin-", self.me["organizationId"], ["SYSTEM_ADMIN"]
        )
        original_row = self.admin.call("admin/account/query", {"id": self.me["ownerId"]})
        backup_row = self.admin.call("admin/account/query", {"id": backup_row["id"]})
        # 两名管理员同时停用自己：共用服务商锁后必有一笔因最后管理员保护回滚。
        with ThreadPoolExecutor(max_workers=2) as pool:
            futures = [
                pool.submit(
                    browser.raw,
                    "admin/account/disable",
                    {
                        "id": row["id"],
                        "rowVersion": row["rowVersion"],
                        "reason": "最后管理员竞争验收",
                    },
                )
                for browser, row in ((self.admin, original_row), (backup, backup_row))
            ]
            results = [future.result()["code"] for future in futures]
        assert sorted(results) == [0, 2102], results
        survivor = backup if results[0] == 0 else self.admin
        disabled_id = original_row["id"] if results[0] == 0 else backup_row["id"]
        disabled = survivor.call("admin/account/query", {"id": disabled_id})
        survivor.call(
            "admin/account/enable",
            {
                "id": disabled_id,
                "rowVersion": disabled["rowVersion"],
                "reason": "恢复隔离验收初始管理员",
            },
        )
        self.admin.login(*self.admin_credentials)
        backup.login(backup_name, backup_password)
        # 恢复为仅初始管理员有效，以便重复执行验收仍有明确的最后管理员基线。
        backup_row = self.admin.call("admin/account/query", {"id": backup_row["id"]})
        self.admin.call(
            "admin/account/disable",
            {
                "id": backup_row["id"],
                "rowVersion": backup_row["rowVersion"],
                "reason": "结束隔离竞争夹具",
            },
        )
        self.passed_case("最后管理员并发停用只允许一个提交，失败事务完整回滚")

    def close(self) -> None:
        """只关闭此测试创建的客户端，不清理共享服务。"""
        for browser in self.browsers:
            browser.client.close()


def main() -> None:
    """必须显式声明独占环境，并只允许回环地址。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if os.environ.get("DESK_VERIFY_ISOLATED") != "true":
        raise RuntimeError("仅允许显式声明的独占测试环境")
    urls = tuple(os.environ[name] for name in ("DESK_VERIFY_USER_URL", "DESK_VERIFY_TICKET_URL"))
    if any(urlparse(url).hostname not in {"127.0.0.1", "localhost"} for url in urls):
        raise RuntimeError("验收只允许回环隔离服务")
    workflow = Workflow(
        *urls, os.environ["DESK_VERIFY_ADMIN"], os.environ["DESK_VERIFY_ADMIN_PASSWORD"]
    )
    try:
        workflow.run()
    finally:
        workflow.close()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps({"passed": workflow.passed}, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
