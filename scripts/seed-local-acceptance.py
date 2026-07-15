#!/usr/bin/env python3
"""Idempotent synthetic fixtures through normal setup/project/member/resource APIs.

Only the fixed loopback acceptance stack is used. SQL seeds business targets,
never approvals, workflow runs, or successful task outcomes. Existing rows,
credentials and member roles are retained. Private files are chmod 600.
"""
from pathlib import Path
import argparse
import json
import os
import secrets
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / "deploy/.acceptance-private"
ENV = ROOT / "deploy/.env.acceptance"
BASE = "http://127.0.0.1:18089"
PROJECTS = {"a": "ops-acceptance-a", "b": "ops-acceptance-b"}


def private_json(name, factory):
    path = PRIVATE / name
    if path.exists():
        return json.loads(path.read_text())
    PRIVATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    value = factory()
    with os.fdopen(os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "w") as stream:
        json.dump(value, stream)
    return value


def request(path, method="GET", body=None, token=None, denied=False, timeout=40):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, json.dumps(body).encode() if body is not None else None, headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status, payload = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        status, payload = error.code, json.loads(error.read() or b"{}")
    if denied:
        if status not in (401, 403) and payload.get("code") in (None, "0000"):
            raise RuntimeError("Expected permission rejection: " + path)
        return {"httpStatus": status, "code": payload.get("code")}
    if status != 200 or payload.get("code") != "0000":
        raise RuntimeError(f"API failed: {method} {path}, HTTP {status}, code={payload.get('code')}")
    return payload.get("data")


def login(credentials):
    return request("/api/v1/admin/admin-user/login", "POST", credentials)["token"]


def normalized_members(members):
    return [{"userId": member.get("userId", member.get("user_id", "")), "username": member.get("username", ""),
             "memberRole": member.get("memberRole", member.get("member_role", "MEMBER"))} for member in members]


def prepare_business():
    values = dict(line.split("=", 1) for line in ENV.read_text().splitlines() if line and not line.startswith("#"))
    new = {}
    for key in ("OPS_ACCEPTANCE_DB_A_PASSWORD", "OPS_ACCEPTANCE_DB_B_PASSWORD"):
        if not values.get(key):
            new[key] = secrets.token_urlsafe(30)
    if new:
        with ENV.open("a") as stream:
            stream.write("\n" + "\n".join(f"{key}={value}" for key, value in new.items()) + "\n")
        values.update(new)
    for suffix in PROJECTS:
        database = "ops_acceptance_business_" + suffix
        user = "ops_acceptance_ro_" + suffix
        password = values[f"OPS_ACCEPTANCE_DB_{suffix.upper()}_PASSWORD"]
        if not all(c.isalnum() or c in "_-" for c in password):
            raise ValueError("Unexpected generated fixture credential format")
        sql = f"""
CREATE DATABASE IF NOT EXISTS {database} CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS '{user}'@'%' IDENTIFIED BY '{password}';
GRANT SELECT, SHOW VIEW ON {database}.* TO '{user}'@'%';
USE {database};
CREATE TABLE IF NOT EXISTS acceptance_service (
 service_id VARCHAR(80) PRIMARY KEY, project_id VARCHAR(80) NOT NULL,
 name VARCHAR(80) NOT NULL, scenario VARCHAR(40) NOT NULL, version VARCHAR(20) NOT NULL);
CREATE TABLE IF NOT EXISTS acceptance_customer (
 customer_id VARCHAR(80) PRIMARY KEY, display_name VARCHAR(80) NOT NULL, tier VARCHAR(20) NOT NULL);
CREATE TABLE IF NOT EXISTS acceptance_order (
 order_id VARCHAR(80) PRIMARY KEY, customer_id VARCHAR(80) NOT NULL, service_id VARCHAR(80) NOT NULL,
 request_key VARCHAR(80) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL, amount DECIMAL(12,2) NOT NULL,
 created_at DATETIME NOT NULL, FOREIGN KEY(customer_id) REFERENCES acceptance_customer(customer_id),
 FOREIGN KEY(service_id) REFERENCES acceptance_service(service_id));
"""
        for i, scenario in enumerate(("HEALTHY", "FAULT", "INSUFFICIENT_DATA"), 1):
            sql += f"INSERT INTO acceptance_service VALUES ('ops-acc-{suffix}-service-{i}', '{PROJECTS[suffix]}', '验收服务 {suffix.upper()}{i}', '{scenario}', 'fixture-1') ON DUPLICATE KEY UPDATE service_id=service_id;\n"
        for i in range(1, 7):
            sql += f"INSERT INTO acceptance_customer VALUES ('ops-acc-{suffix}-customer-{i}', '虚构客户 {i}', '{'VIP' if i % 2 else 'STANDARD'}') ON DUPLICATE KEY UPDATE customer_id=customer_id;\n"
        for i in range(1, 13):
            status = ("PENDING", "PAID", "CANCELED", "FAILED")[(i - 1) % 4]
            amount = (0, 0.01, 19.99, 9999.99)[(i - 1) % 4]
            sql += f"INSERT INTO acceptance_order VALUES ('ops-acc-{suffix}-order-{i}', 'ops-acc-{suffix}-customer-{(i - 1) % 6 + 1}', 'ops-acc-{suffix}-service-{(i - 1) % 3 + 1}', 'ops-acc-{suffix}-request-{i}', '{status}', {amount}, '2026-09-08 12:00:00') ON DUPLICATE KEY UPDATE order_id=order_id;\n"
        subprocess.run(["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
                        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'], input=sql, text=True, check=True)
    # Refresh only the isolated backend's environment. Never rebuild here or remove a volume.
    subprocess.run(["docker", "compose", "-p", "orbisops-acceptance", "--env-file", str(ENV),
                    "-f", "compose.yml", "-f", "deploy/compose.acceptance.yml", "up", "-d", "--no-build", "backend"], cwd=ROOT, check=True)
    for _ in range(60):
        try:
            request("/api/v1/setup/status")
            return
        except (OSError, RuntimeError):
            time.sleep(1)
    raise RuntimeError("Acceptance API did not become ready")


def seed():
    prepare_business()
    admin = private_json("admin.json", lambda: {"username": "ops_acceptance_admin", "password": "Aa9" + secrets.token_urlsafe(24)})
    if request("/api/v1/setup/status"):
        # The same public first-time setup entry as the browser; no bootstrap DB injection.
        request("/api/v1/setup", "POST", admin)
    token = login(admin)
    users = request("/api/v1/admin/admin-user/query-all", token=token)
    accounts = private_json("users.json", lambda: {name: {"username": name, "password": "Aa9" + secrets.token_urlsafe(24)}
                                                  for name in ("ops_acceptance_operator", "ops_acceptance_viewer", "ops_acceptance_b_member")})
    for username, credentials in accounts.items():
        if not any(user["username"] == username for user in users):
            request("/api/v1/admin/admin-user/create", "POST", {**credentials, "userId": username, "userRole": "user", "status": 1}, token)
    users = {user["username"]: user for user in request("/api/v1/admin/admin-user/query-all", token=token)}
    snapshot = request("/api/v1/admin/ops-projects/snapshot", token=token)
    projects = {project["projectId"]: project for project in snapshot["projects"]}
    for suffix, project_id in PROJECTS.items():
        if project_id not in projects:
            projects[project_id] = request("/api/v1/admin/ops-projects/projects", "POST", {
                "projectId": project_id, "name": "隔离验收项目 " + suffix.upper(), "owner": admin["username"],
                "description": "synthetic-OPS-acceptance；仅本机测试；场景事实不代表已验收业务工作流。", "environments": ["dev", "test"]}, token)
        desired = [("ops_acceptance_operator", "MAINTAINER"), ("ops_acceptance_viewer", "VIEWER")] if suffix == "a" else [("ops_acceptance_b_member", "MEMBER")]
        path = f"/api/v1/admin/ops-projects/projects/{project_id}/members"
        members = normalized_members(request(path, token=token))
        additions = [{"userId": users[name]["userId"], "username": name, "memberRole": role} for name, role in desired
                     if not any(member.get("userId") == users[name]["userId"] for member in members)]
        if additions:
            request(path, "PUT", {"members": members + additions}, token)
        resource_id = project_id + "-mysql"
        if not any(resource["resourceId"] == resource_id for resource in projects[project_id].get("resources", [])):
            request("/api/v1/admin/ops-projects/resources", "POST", {
                "projectId": project_id, "resourceId": resource_id, "name": "验收业务只读库 " + suffix.upper(), "type": "mysql", "environment": "test",
                "endpoint": "jdbc:mysql://mysql:3306/ops_acceptance_business_" + suffix + "?allowPublicKeyRetrieval=true&useSSL=false",
                "username": "ops_acceptance_ro_" + suffix, "passwordRef": "${env:OPS_ACCEPTANCE_DB_" + suffix.upper() + "_PASSWORD}"}, token)
    verify(token, accounts)


def verify(token, accounts):
    snapshot = request("/api/v1/admin/ops-projects/snapshot", token=token)
    result = []
    for project in snapshot["projects"]:
        if project["projectId"] not in PROJECTS.values():
            continue
        members = normalized_members(request(f"/api/v1/admin/ops-projects/projects/{project['projectId']}/members", token=token))
        for resource in project.get("resources", []):
            if resource["resourceId"] == project["projectId"] + "-mysql" and resource.get("status") != "SCANNED":
                raise RuntimeError("Business resource did not pass live schema scan")
        result.append({"projectId": project["projectId"], "members": [{key: m.get(key) for key in ("username", "memberRole")} for m in members],
                       "resources": [{key: r.get(key) for key in ("resourceId", "status")} for r in project.get("resources", [])]})
    for username, credentials in accounts.items():
        user_token = login(credentials)
        visible = request("/api/v1/user/chat/catalog/projects", token=user_token)
        ids = {project["projectId"] for project in visible}
        expected = {PROJECTS["b"]} if username.endswith("b_member") else {PROJECTS["a"]}
        if ids.intersection(PROJECTS.values()) != expected:
            raise RuntimeError("Project isolation failed for " + username)
        rejection = request("/api/v1/admin/ops-projects/snapshot", token=user_token, denied=True)
        other = PROJECTS["a"] if username.endswith("b_member") else PROJECTS["b"]
        cross_project = request(f"/api/v1/user/chat/catalog/projects/{other}/agents", token=user_token, denied=True)
        print(json.dumps({"user": username, "visibleProjects": sorted(ids), "adminEndpointDenied": rejection,
                          "crossProjectDenied": cross_project}, ensure_ascii=False))
    for project in result:
        expected = {"ops_acceptance_operator": "MAINTAINER", "ops_acceptance_viewer": "VIEWER"} if project["projectId"] == PROJECTS["a"] else {"ops_acceptance_b_member": "MEMBER"}
        actual = {member["username"]: member["memberRole"] for member in project["members"]}
        if any(actual.get(user) != role for user, role in expected.items()):
            raise RuntimeError("Fixture member role changed; retained existing roles for review")
    values = dict(line.split("=", 1) for line in ENV.read_text().splitlines() if line and not line.startswith("#"))
    for suffix in PROJECTS:
        env = dict(os.environ, MYSQL_PWD=values[f"OPS_ACCEPTANCE_DB_{suffix.upper()}_PASSWORD"])
        database = "ops_acceptance_business_" + suffix
        base = ["docker", "exec", "-i", "-e", "MYSQL_PWD", "orbisops-acceptance-mysql-1", "mysql", "-uops_acceptance_ro_" + suffix, "-N", "-B", database]
        # Later acceptance workflows may legitimately add services and orders.
        # Verify this seed's exact identities and their relationships, preserving
        # and reporting additional business rows instead of demanding an empty DB.
        def identities(kind, count):
            return ','.join(f"'ops-acc-{suffix}-{kind}-{i}'" for i in range(1, count + 1))
        service_ids = identities('service', 3)
        customer_ids = identities('customer', 6)
        order_ids = identities('order', 12)
        sql = f"""SELECT
          (SELECT COUNT(*) FROM acceptance_service WHERE service_id IN ({service_ids}) AND project_id='{PROJECTS[suffix]}'),
          (SELECT COUNT(*) FROM acceptance_customer WHERE customer_id IN ({customer_ids})),
          (SELECT COUNT(*) FROM acceptance_order WHERE order_id IN ({order_ids})),
          (SELECT COUNT(*) FROM acceptance_order o
             JOIN acceptance_customer c ON c.customer_id=o.customer_id
             JOIN acceptance_service s ON s.service_id=o.service_id
             WHERE o.order_id IN ({order_ids}) AND c.customer_id IN ({customer_ids})
               AND s.service_id IN ({service_ids}) AND s.project_id='{PROJECTS[suffix]}'),
          (SELECT COUNT(*) FROM acceptance_service),
          (SELECT COUNT(*) FROM acceptance_customer),
          (SELECT COUNT(*) FROM acceptance_order);"""
        counts = subprocess.run(base, input=sql, text=True, env=env, capture_output=True, check=True).stdout.strip()
        actual = [int(value) for value in counts.split()]
        if actual[:4] != [3, 6, 12, 12]:
            raise RuntimeError("Unexpected retained fixture rows: " + counts)
        denied = subprocess.run(base, input="BEGIN; INSERT INTO acceptance_customer VALUES ('ops-acc-permission-probe', '虚构权限测试', 'TEST'); ROLLBACK;", text=True, env=env, capture_output=True)
        if denied.returncode == 0 or "1142" not in denied.stderr:
            raise RuntimeError("Read-only SQL write rejection was not verified")
        print(json.dumps({"schema": database, "seedCountsAndJoinedOrders": actual[:4],
                          "retainedTotalCounts": actual[4:], "writeRejected": "MySQL 1142"}))
    print(json.dumps({"fixture": "synthetic-OPS-acceptance", "projects": result, "businessSchemas": 2,
                      "expectedRowsPerSchema": {"services": 3, "customers": 6, "orders": 12},
                      "modelAcceptance": "NOT_RUN_BY_SEED"}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    if args.verify_only:
        verify(login(json.loads((PRIVATE / "admin.json").read_text())), json.loads((PRIVATE / "users.json").read_text()))
    else:
        seed()
