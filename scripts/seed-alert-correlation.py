#!/usr/bin/env python3
"""Seed a disabled, signed alert rule and the actual isolated target's dependency inventory.

Service endpoints share the workflow-target process. This records that deployment
relationship, not a claim that every symptom has the same cause. Other topology
edges and existing rules are preserved. No approval or incident state is written.
"""
from pathlib import Path
import datetime as dt
import json
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"
RESOURCE = "ops04-order-target:workflow-target:8280"
RULE_NAME = "OPS-04 自动事件归组 · 真实目标故障"


def prepare():
    support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token = support["api"], support["token"]
    base = runpy.run_path(str(ROOT / "scripts/seed-workflow-triggers.py"))["prepare"]()
    desired = dict(base["rule"])
    for key in ("id", "createTime", "updateTime"):
        desired.pop(key, None)
    desired.update(ruleName=RULE_NAME, status=0, alertNameRegex="^OPS04Correlation[A-Za-z]+$",
                   matchLabelsJson=json.dumps({"fixture": "ops04-correlation", "environment": "acceptance"}),
                   webhookSecret=json.loads((ROOT / "deploy/.acceptance-private/triggers.json").read_text())["webhookSecret"])
    rule = next((item for item in api("/api/v1/admin/ops/alert-triggers/rules", token=token)
                 if item["projectId"] == PROJECT and item["ruleName"] == RULE_NAME), None)
    if rule is None:
        rule = api("/api/v1/admin/ops/alert-triggers/rules", "POST", desired, token)
    for key in ("agentDefinitionId", "agentVersion", "agentDefinitionHash", "alertNameRegex", "questionTemplate"):
        actual, expected = rule[key], desired[key]
        if key == "questionTemplate":
            actual, expected = json.loads(actual), json.loads(expected)
        if actual != expected:
            raise RuntimeError("Existing correlation fixture differs; preserved: " + key)
    container = json.loads(subprocess.check_output(["docker", "inspect", "orbisops-acceptance-workflow-target-1"], text=True))[0]
    # Avoid exposing Docker Config.Env: retain only deployment identity evidence.
    # Docker emits nanoseconds; Python 3.9's ISO parser accepts only microseconds.
    created = dt.datetime.strptime(container["Created"][:19], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=dt.timezone.utc)
    observed = created.replace(microsecond=0)
    evidence = "local-compose:workflow-target:" + container["Id"][:12]
    edges = [{"source": "ops-acc-a-service-" + str(i), "target": RESOURCE, "evidenceRef": evidence,
              "observedAt": observed.isoformat(), "expiresAt": (observed + dt.timedelta(days=30)).isoformat()} for i in (1, 2, 3, 4)]
    path = "/api/v1/admin/ops/alert-correlations/topology"
    existing = api(path + "?projectId=" + PROJECT + "&environment=acceptance", token=token)
    merged = {(edge["source"], edge["target"]): edge for edge in existing}
    for edge in edges:
        key = edge["source"], edge["target"]
        if key in merged and merged[key]["evidenceRef"] != evidence:
            raise RuntimeError("Existing topology source differs; preserved: " + str(key))
        merged[key] = edge
    api(path, "PUT", {"projectId": PROJECT, "environment": "acceptance", "edges": list(merged.values())}, token)
    return {"rule": rule, "topology": edges, "resource": RESOURCE, "containerId": container["Id"][:12],
            "scope": "actual local shared target; no production changes or model calls"}


if __name__ == "__main__":
    print(json.dumps(prepare(), ensure_ascii=False, indent=2))
