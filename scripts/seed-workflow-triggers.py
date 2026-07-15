#!/usr/bin/env python3
"""Create disabled, repeatable local alert/schedule fixtures via normal APIs.

Existing records, statuses, credentials and pinned definitions are preserved.
No model provider, notification channel or production write capability is enabled.
"""
from pathlib import Path
import argparse
import json
import os
import runpy
import secrets

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"
RULE_NAME = "OPS-04 本地签名告警 → B"
TASK_NAME = "OPS-04 本地定时巡检 → A"
PRIVATE = ROOT / "deploy/.acceptance-private/triggers.json"


def prepare():
    support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token = support["api"], support["token"]
    definitions = runpy.run_path(str(ROOT / "scripts/seed-business-workflows.py"))["prepare"](["alert", "inspection"])
    if definitions.get("state") != "READY":
        raise RuntimeError("Publish and review the real observability workflows first")
    alert, inspection = definitions["published"]
    if not PRIVATE.exists():
        with os.fdopen(os.open(PRIVATE, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "w") as stream:
            json.dump({"webhookSecret": secrets.token_urlsafe(36)}, stream)
    secret = json.loads(PRIVATE.read_text())["webhookSecret"]
    desired = {
        "ruleName": RULE_NAME, "status": 0, "sourceType": "ALERTMANAGER",
        "projectId": PROJECT, "agentDefinitionId": alert["agentId"],
        "agentBindingMode": "PINNED_VERSION", "agentVersion": alert["version"],
        "agentDefinitionHash": alert["definitionHash"],
        "alertNameRegex": "^OPS04Acceptance$", "severityRegex": "^(warning|critical)$",
        "serviceRegex": "^ops-acc-a-service-[1-4]$",
        "matchLabelsJson": json.dumps({"fixture": "ops04-trigger", "environment": "acceptance"}),
        "webhookSecret": secret, "notifyChannel": False, "includeRecentLogs": True,
        "rangeMinutes": 30, "promWindow": "5m", "nodeTimeoutSeconds": 120,
        "maxEvidenceItems": 20, "subAgentMaxIterations": 3, "dedupWindowSeconds": 300,
        "questionTemplate": json.dumps({"projectId": "${projectId}", "serviceId": "${service}",
                                        "environment": "acceptance", "alertTime": "${startsAt}",
                                        "eventType": "${eventType}", "fingerprint": "${fingerprint}",
                                        "occurrenceCount": "${occurrenceCount}", "summary": "${summary}"}),
    }
    rule = next((item for item in api("/api/v1/admin/ops/alert-triggers/rules", token=token)
                 if item["ruleName"] == RULE_NAME and item["projectId"] == PROJECT), None)
    if rule is None:
        rule = api("/api/v1/admin/ops/alert-triggers/rules", "POST", desired, token)
    for key in ("agentDefinitionId", "agentVersion", "agentDefinitionHash", "questionTemplate", "serviceRegex", "matchLabelsJson"):
        actual, expected = rule.get(key), desired[key]
        if key in ("questionTemplate", "matchLabelsJson"):
            actual, expected = json.loads(actual), json.loads(expected)
        if actual != expected:
            raise RuntimeError("Existing alert fixture differs; preserved: " + key)
    if rule.get("notifyChannel") or not rule.get("webhookSecret"):
        raise RuntimeError("Alert fixture must use a private signature and have notifications disabled")

    schedule_body = {
        "projectId": PROJECT, "executionType": "WORKFLOW", "agentId": inspection["agentId"],
        "agentBindingMode": "PINNED_VERSION", "agentVersion": inspection["version"],
        "taskName": TASK_NAME, "description": "本地真实指标巡检，启用后每 5 分钟运行；可手动执行",
        "cronExpression": "0 */5 * * * *", "status": 0,
        "taskParam": json.dumps({"projectId": PROJECT, "serviceId": "ops-acc-a-service-1", "environment": "acceptance"}),
        "notifyChannel": False, "lightweightScreeningEnabled": False,
        "rangeMinutes": 5, "promWindow": "5m", "nodeTimeoutSeconds": 120,
        "maxEvidenceItems": 20, "subAgentMaxIterations": 3,
    }
    path = "/api/v1/admin/task-schedule"
    tasks = api(path + "/list?projectId=" + PROJECT, token=token)
    schedule = next((item for item in tasks if item["taskName"] == TASK_NAME), None)
    if schedule is None:
        api(path + "/create", "POST", schedule_body, token)
        schedule = next(item for item in api(path + "/list?projectId=" + PROJECT, token=token) if item["taskName"] == TASK_NAME)
    for key in ("agentId", "agentVersion", "taskParam", "executionType"):
        if schedule.get(key) != schedule_body[key]:
            raise RuntimeError("Existing schedule fixture differs; preserved: " + key)
    if schedule.get("notifyChannel"):
        raise RuntimeError("Schedule fixture must have notifications disabled")
    return {"projectId": PROJECT, "rule": rule, "schedule": schedule,
            "definitions": definitions["published"], "secretLocation": "deploy/.acceptance-private/triggers.json",
            "modelCallsEnabled": False, "productionWritesEnabled": False}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = prepare()
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))
