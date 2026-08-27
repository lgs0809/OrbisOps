#!/usr/bin/env python3
"""Operate the isolated, synthetic local workspace without touching acceptance data.

The Compose project, API port, database and container labels are fixed safety
boundaries. init/plan are offline. up uses only already present tested images;
stop/restart retain every volume. Seeded histories are presentation fixtures,
never evidence of real model calls, approvals, deployments or acceptance.
"""
from pathlib import Path
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
import os
import re
import secrets
import subprocess
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / "deploy/.showcase-private-v2"
ENV = PRIVATE / "runtime.env"
COMPOSE = ROOT / "deploy/compose.showcase.yml"
PROJECT = "orbisops-showcase-v2"
DATABASE = "orbisops_showcase"
BASE = "http://127.0.0.1:18099"
CATALOG = ROOT / "scripts/fixtures/daily-workspace/catalog.json"
ACCOUNTS = [
    ("lead", "管理员", "19900000001", "admin"),
    ("operator", "值班成员", "19900000002", "user"),
    ("reviewer", "复核成员", "19900000003", "user"),
]


def history_dates():
    # Freeze the last day on initialization so rerunning cannot rewrite an
    # existing deterministic timeline. A new snapshot uses its own volumes.
    values = dict(line.split("=", 1) for line in ENV.read_text().splitlines() if "=" in line) if ENV.is_file() else {}
    start = dt.date.fromisoformat(values.get("SHOWCASE_HISTORY_START", "2026-06-30"))
    end = dt.date.fromisoformat(values.get("SHOWCASE_HISTORY_END", dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date().isoformat()))
    if start > end: raise ValueError("Invalid history date window")
    return [start + dt.timedelta(days=n) for n in range((end - start).days + 1)]


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def private_write(path, value, *, replace=False):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(path.parent, 0o700)
    mode = os.O_WRONLY | os.O_CREAT | (os.O_TRUNC if replace else os.O_EXCL)
    with os.fdopen(os.open(path, mode, 0o600), "w") as stream:
        stream.write(value)
    os.chmod(path, 0o600)


def command(args, **kwargs):
    # Never include credentials or Compose's expanded environment in evidence.
    result = subprocess.run(args, text=True, capture_output=True, **kwargs)
    if result.returncode:
        evidence = PRIVATE / ("command-failure-" + secrets.token_hex(6) + ".json")
        output = result.stdout + "\n" + result.stderr
        if ENV.is_file():
            for line in ENV.read_text().splitlines():
                if "=" in line:
                    key, value = line.split("=", 1)
                    if ("PASSWORD" in key or "SECRET" in key or "API_KEY" in key) and value:
                        output = output.replace(value, "[redacted]")
        private_write(evidence, canonical({"exitCode": result.returncode, "executable": args[0], "output": output}) + "\n")
        raise RuntimeError(f"Command failed (exit {result.returncode}): {args[0]}; private details: {evidence.name}")
    return result.stdout


def compose(*args):
    if not ENV.is_file():
        raise ValueError("Initialize this workspace first")
    return command(["docker", "compose", "-p", PROJECT, "--env-file", str(ENV),
                    "-f", str(COMPOSE), *args], cwd=ROOT)


def labels(service):
    container = PROJECT + "-" + service + "-1"
    value = json.loads(command(["docker", "inspect", container,
                               "--format", "{{json .Config.Labels}}"] ))
    if value.get("com.docker.compose.project") != PROJECT or value.get("com.docker.compose.service") != service:
        raise ValueError("Refusing a container outside the showcase project")
    return container


def sql(statement):
    container = labels("mysql")
    # The credential is resolved inside only the dedicated container.
    return command(["docker", "exec", "-i", container, "sh", "-c",
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B --raw orbisops_showcase'],
                   input="SET SESSION time_zone='+00:00';\n" + statement)


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, (dict, list)):
        value = canonical(value)
    # Empty 0x is an identifier in MySQL, not a valid literal.
    if str(value) == "": return "CONVERT(X'' USING utf8mb4)"
    # SQL hex literals do not depend on MySQL escape/sql modes.
    return "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4)"


def api(path, method="GET", body=None, token=None, timeout=45):
    if not path.startswith("/api/v1/") or ".." in path:
        raise ValueError("Invalid API path")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path,
          canonical(body).encode() if body is not None else None, headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status, payload = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        status, payload = error.code, json.loads(error.read() or b"{}")
    if status != 200 or payload.get("code") != "0000":
        # Provider errors can contain secrets; retain code/status only here.
        detail = str(payload.get("info", ""))
        for item in accounts().values() if (PRIVATE / "accounts.json").is_file() else []:
            detail = detail.replace(item["password"], "[redacted]")
        evidence = PRIVATE / ("api-failure-" + secrets.token_hex(6) + ".json")
        private_write(evidence, canonical({"method": method, "path": path, "httpStatus": status, "code": payload.get("code"), "info": detail}) + "\n")
        raise RuntimeError(f"API {method} {path}: HTTP {status}, code={payload.get('code')}; private details: {evidence.name}")
    return payload.get("data")


def accounts():
    return json.loads((PRIVATE / "accounts.json").read_text())


def login(key="lead"):
    item = accounts()[key]
    maintenance = PRIVATE / "maintenance-login.json"
    password = json.loads(maintenance.read_text())["password"] if key == "lead" and maintenance.is_file() else item["password"]
    return api("/api/v1/admin/admin-user/login", "POST",
               {"phone": item["phone"], "password": password})["token"]


def init(server_image, web_image, display_names=None):
    names = json.loads(display_names) if display_names else [a[1] for a in ACCOUNTS]
    if not isinstance(names, list) or len(names) != 3 or any(not isinstance(n, str) or not n.strip() or len(n) > 50 for n in names):
        raise ValueError("Provide exactly three display names")
    for value in (server_image, web_image):
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", value or ""):
            raise ValueError("Use exact local tested image IDs, never floating tags")
    if ENV.exists():
        values = dict(line.split("=", 1) for line in ENV.read_text().splitlines() if "=" in line)
        if values.get("SHOWCASE_SERVER_IMAGE") != server_image or values.get("SHOWCASE_WEB_IMAGE") != web_image:
            raise ValueError("Existing image pins retained; use the retained environment until an explicitly reviewed image refresh")
        if not (PRIVATE / "accounts.json").is_file():
            raise ValueError("Incomplete private configuration; retained for recovery without regenerating passwords")
        return {"status": "REUSED_PRIVATE_CONFIGURATION", "passwordsPrinted": False}
    values = {name: secrets.token_urlsafe(36) for name in (
        "SHOWCASE_MYSQL_PASSWORD", "SHOWCASE_MYSQL_ROOT_PASSWORD", "SHOWCASE_POSTGRES_PASSWORD",
        "SHOWCASE_REDIS_PASSWORD", "SHOWCASE_JWT_SECRET",
        "SHOWCASE_ORDERS_RO_PASSWORD", "SHOWCASE_PAYMENTS_RO_PASSWORD", "SHOWCASE_FULFILMENT_RO_PASSWORD", "SHOWCASE_WEBHOOK_SECRET")}
    values.update(SHOWCASE_SERVER_IMAGE=server_image, SHOWCASE_WEB_IMAGE=web_image, SHOWCASE_MODEL_CALLS_ENABLED="false",
                  SHOWCASE_HISTORY_START="2026-06-30", SHOWCASE_HISTORY_END=history_dates()[-1].isoformat())
    private_write(ENV, "\n".join(f"{key}={value}" for key, value in values.items()) + "\n")
    values = {key: {"userId": "showcase-" + key, "username": name, "phone": phone,
                    "userRole": role, "status": 0 if key == "disabled" else 1,
                    "password": "Aa9" + secrets.token_urlsafe(18)}
              for key, name, phone, role in ACCOUNTS}
    for key, name in zip((a[0] for a in ACCOUNTS), names): values[key]["username"] = name.strip()
    private_write(PRIVATE / "accounts.json", canonical(values) + "\n")
    return {"status": "PRIVATE_CONFIGURATION_CREATED", "passwordsPrinted": False,
            "secretFileMode": "0600", "privateDirectoryMode": "0700"}


def refresh_images(server_image, web_image):
    for value in (server_image, web_image):
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", value or ""):
            raise ValueError("Use exact local tested image IDs, never floating tags")
        actual = command(["docker", "image", "inspect", value, "--format", "{{.Id}}"] ).strip()
        if actual != value: raise ValueError("Requested exact image is unavailable locally")
    for service in ("mysql", "pgvector", "redis", "backend", "web"): labels(service)
    if sql("SELECT DATABASE();").strip() != DATABASE: raise ValueError("Wrong showcase database")
    counts = sql("SELECT (SELECT COUNT(*) FROM ai_ops_agent_run WHERE status NOT IN ('SUCCEEDED','FAILED','CANCELED')) + (SELECT COUNT(*) FROM ai_ops_task_episode_turn WHERE status NOT IN ('ASSIGNED','SOURCE_BLOCKED')) + (SELECT COUNT(*) FROM ai_ops_episode_consolidation_job WHERE status NOT IN ('SUCCEEDED','SOURCE_BLOCKED')) + (SELECT COUNT(*) FROM ai_ops_skill_evolution_job WHERE status NOT IN ('COMPLETED','FAILED','SKIPPED','NO_CHANGE')); ").strip()
    if counts != "0": raise ValueError("Active runs or jobs exist; image refresh retained without interruption")
    old_text = ENV.read_text()
    values = dict(line.split("=",1) for line in old_text.splitlines() if "=" in line)
    before = {key: values[key] for key in ("SHOWCASE_SERVER_IMAGE", "SHOWCASE_WEB_IMAGE")}
    values.update(SHOWCASE_SERVER_IMAGE=server_image, SHOWCASE_WEB_IMAGE=web_image)
    new_text = "\n".join(f"{key}={value}" for key, value in values.items()) + "\n"
    unchanged = {k: v for k, v in values.items() if k not in before}
    old_unchanged = {k: v for k, v in dict(line.split("=",1) for line in old_text.splitlines() if "=" in line).items() if k not in before}
    if unchanged != old_unchanged: raise ValueError("Secret/configuration changed unexpectedly")
    private_write(ENV, new_text, replace=True)
    compose("up", "-d", "--no-build", "--pull", "never", "--no-deps", "--wait", "--wait-timeout", "180", "backend", "web")
    deployed = {service: command(["docker", "inspect", labels(service), "--format", "{{.Image}}"] ).strip() for service in ("backend", "web")}
    if deployed != {"backend": server_image, "web": web_image}: raise ValueError("Running image ID does not match reviewed pin")
    return {"status": "EXACT_LOCAL_IMAGES_DEPLOYED", "beforeImages": before, "afterImages": deployed,
            "secretValuesRetained": True, "volumesRetained": True, "activeRunsAndJobs": 0,
            "beforeConfigSha256": hashlib.sha256(old_text.encode()).hexdigest(), "afterConfigSha256": hashlib.sha256(new_text.encode()).hexdigest()}


def seed_accounts():
    labels("backend"); labels("mysql")
    values = accounts()
    if api("/api/v1/setup/status"):
        lead = values["lead"]
        created = api("/api/v1/setup", "POST", {key: lead[key] for key in ("username", "phone", "password")})
        # The setup service owns user identity; never rewrite it directly.
        lead["userId"] = created["userId"]
        private_write(PRIVATE / "accounts.json", canonical(values) + "\n", replace=True)
    token = login()
    profile = api("/api/v1/user/account", token=token)
    if profile.get("passwordChangeRequired"):
        if (PRIVATE / "sealed.json").exists():
            raise ValueError("Workspace is sealed for personal first login; set your own password before maintenance")
        password = "Aa9" + secrets.token_urlsafe(24)
        changed = api("/api/v1/user/account/password", "POST", {"currentPassword": values["lead"]["password"], "newPassword": password}, token)
        private_write(PRIVATE / "maintenance-login.json", canonical({"password": password, "purpose": "temporary normal-API initialization; reset before handover"}) + "\n")
        token = changed["token"]
    current = {item["phone"]: item for item in api("/api/v1/admin/admin-user/query-all", token=token) if item.get("phone")}
    for key, item in values.items():
        if item["phone"] not in current:
            api("/api/v1/admin/admin-user/create", "POST", item, token)
        elif any(current[item["phone"]].get(field) != item[field]
                 for field in ("userId", "username", "userRole", "status")):
            raise ValueError("Existing showcase account differs; preserved: " + key)
    return {"accounts": len(values), "createdThrough": "setup + authenticated administrator create",
            "phonesAndPasswords": "deploy/.showcase-private-v2/accounts.json", "publicSelfSignup": False}


def seed_catalog():
    token = login()
    values = accounts()
    spec = json.loads(CATALOG.read_text())
    current = {item["projectId"]: item for item in api("/api/v1/admin/ops-projects/snapshot", token=token)["projects"]}
    results = []
    for project in spec["projects"]:
        key, pid = project["key"], project["projectId"]
        desired = {"projectId": pid, "name": project["name"], "description": project["description"] + "\n" + spec["provenance"],
                   "owner": values["lead"]["username"], "environments": ["test"]}
        if pid not in current:
            api("/api/v1/admin/ops-projects/projects", "POST", desired, token)
        elif any(current[pid].get(field) != desired[field] for field in ("name", "description", "owner")):
            raise ValueError("Existing project differs; retained: " + pid)
        path = "/api/v1/admin/ops-projects/projects/" + pid + "/members"
        members = api(path, token=token)
        wanted = [("operator", "MAINTAINER"), ("reviewer", "APPROVER")]
        additions = []
        for user, role in wanted:
            old = next((m for m in members if m["userId"] == values[user]["userId"]), None)
            if old is not None and old["memberRole"] != role:
                raise ValueError("Existing membership differs; retained")
            if old is None:
                additions.append({"userId": values[user]["userId"], "username": values[user]["username"], "memberRole": role})
        if additions:
            api(path, "PUT", {"members": members + additions}, token)
        kb_path = "/api/v1/admin/ai-client-rag-order/projects/" + pid + "/knowledge-bases"
        kb_id = "showcase-" + key + "-runbooks"
        if not any(kb["kbId"] == kb_id for kb in api(kb_path, token=token)):
            api(kb_path, "POST", {"kbId": kb_id, "name": project["name"] + "值班手册",
                                 "description": "服务契约、交接步骤与排障方法。合成展示资料，向量检索未在展示库验收。",
                                 "sourceType": "SHOWCASE_MANUAL", "status": "ENABLED"}, token)
        results.append({"projectId": pid, "membersAdded": len(additions), "kbId": kb_id})
    return results


def plan():
    return {"project": PROJECT, "scope": "ISOLATED_SYNTHETIC_SHOWCASE_NOT_TEST_ACCEPTANCE",
            "addresses": {"web": "http://127.0.0.1:3304", "api": BASE},
            "volumes": [PROJECT + "_" + name for name in ("mysql-data", "pgvector-data", "redis-data", "repair-data", "app-data")],
            "projects": 3, "accounts": 3, "historySource": "synthetic demonstration",
            "historyStart": history_dates()[0].isoformat(), "historyEnd": history_dates()[-1].isoformat(),
            "dailyInvestigationsPerProject": 1, "dailyInvestigationCount": len(history_dates()) * 3,
            "modelCallsEnabledByDefault": False, "externalSecretsCopied": False,
            "seedingOverwritesExistingRows": False, "acceptanceDatabaseUsed": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("plan", "init", "refresh-images", "up", "stop", "restart", "accounts", "catalog", "history", "verify", "seal"))
    parser.add_argument("--server-image"); parser.add_argument("--web-image")
    parser.add_argument("--display-names", help="Private local display-name override as a JSON array of three names")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.output and args.output.exists():
        raise ValueError("Preserve previous evidence; choose a new output")
    if args.action == "plan": result = plan()
    elif args.action == "init": result = init(args.server_image, args.web_image, args.display_names)
    elif args.action == "refresh-images": result = refresh_images(args.server_image, args.web_image)
    elif args.action in ("up", "restart"):
        if args.action == "restart":
            for service in ("mysql", "pgvector", "redis", "backend", "web"): labels(service)
            compose("stop", "backend", "web")
        compose("up", "-d", "--no-build", "--pull", "never", "--wait", "--wait-timeout", "180", "mysql", "pgvector", "redis")
        pg = labels("pgvector")
        database = command(["docker", "exec", pg, "psql", "-U", "showcase_app", "-d", DATABASE, "-tAc", "SELECT current_database();"]).strip()
        if database != DATABASE: raise ValueError("Wrong showcase vector database")
        command(["docker", "exec", "-i", pg, "psql", "-v", "ON_ERROR_STOP=1", "-U", "showcase_app", "-d", DATABASE], input=(ROOT / "deploy/acceptance/showcase-vector-init.sql").read_text())
        compose("up", "-d", "--no-build", "--pull", "never", "--wait", "--wait-timeout", "180")
        result = {"status": "RUNNING", **plan(), "setupAvailable": api("/api/v1/setup/status")}
    elif args.action == "stop":
        compose("stop"); result = {"status": "STOPPED_VOLUMES_RETAINED", "project": PROJECT}
    elif args.action == "accounts": result = seed_accounts()
    elif args.action == "catalog": result = seed_catalog()
    elif args.action == "seal":
        token = login()
        for key in ("operator", "reviewer", "lead"):
            if api("/api/v1/admin/admin-user/" + accounts()[key]["userId"] + "/reset-password", "POST", token=token) is not True:
                raise ValueError("Initial password reset was not acknowledged")
        result = {"accounts": 3, "firstLoginRequiresPasswordChange": True, "oldTokensInvalidated": True}
        private_write(PRIVATE / "sealed.json", canonical(result) + "\n")
    else:
        location = ROOT / "scripts/showcase-history.py"
        module_spec = importlib.util.spec_from_file_location("showcase_history", location)
        module = importlib.util.module_from_spec(module_spec); module_spec.loader.exec_module(module)
        result = module.run(globals(), apply=args.action == "history")
    report = {"action": args.action, "at": dt.datetime.now(dt.timezone.utc).isoformat(), "result": result,
              "scriptSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    if args.output: private_write(args.output, json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        import traceback
        private_write(PRIVATE / ("cli-failure-" + secrets.token_hex(6) + ".json"), canonical({
            "at": dt.datetime.now(dt.timezone.utc).isoformat(), "type": type(error).__name__,
            "message": str(error), "traceback": traceback.format_exc(),
            "scriptSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}) + "\n")
        raise
