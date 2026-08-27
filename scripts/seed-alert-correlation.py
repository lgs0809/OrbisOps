#!/usr/bin/env python3
"""Seed a disabled, signed alert rule and the actual isolated target's dependency inventory.

Service endpoints share the workflow-target process. This records that deployment
relationship, not a claim that every symptom has the same cause. Other topology
edges and existing rules are preserved. No approval or incident state is written.
"""
from pathlib import Path
import argparse
import datetime as dt
import json
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"
RESOURCE = "ops04-order-target:workflow-target:8280"
RULE_NAME = "OPS-04 自动事件归组 · 真实目标故障"


def prepare(refresh_expired=False, workflow_version=None, automatic=False):
    support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token = support["api"], support["token"]
    rule_name = RULE_NAME if workflow_version is None else f"{RULE_NAME} · v{workflow_version}"
    if automatic:
        if workflow_version is not None:
            raise ValueError("Choose the automated evidence workflow or a specific interactive version")
        rule_name = f"{RULE_NAME} · 自动取证"
    rule = next((item for item in api("/api/v1/admin/ops/alert-triggers/rules", token=token)
                 if item["projectId"] == PROJECT and item["ruleName"] == rule_name), None)
    helpers = runpy.run_path(str(ROOT / "scripts/seed-workflow-triggers.py"))
    if rule is None and workflow_version is None:
        kind = "alert-automation" if automatic else "alert"
        definitions = runpy.run_path(str(ROOT / "scripts/seed-business-workflows.py"))["prepare"]([kind])
        if definitions.get("state") != "READY":
            raise RuntimeError("Publish and review the real alert Workflow first")
        alert, = definitions["published"]
    else:
        version = workflow_version if workflow_version is not None else rule["agentVersion"]
        agent = "ops-business-alert-automation" if automatic else "ops-business-alert-b"
        versions = api(f"/api/v1/admin/ops-agents/{agent}/versions", token=token)
        alert = next((item for item in versions if item["version"] == version
                      and item.get("projectId") == PROJECT and item.get("lifecycle") == "PUBLISHED"), None)
        if alert is None:
            raise RuntimeError("Existing correlation rule has no matching published Workflow")
    desired = helpers["alert_rule"](alert, helpers["fixture_secret"]())
    desired.update(ruleName=rule_name, alertNameRegex="^OPS04Correlation[A-Za-z]+$",
                   matchLabelsJson=json.dumps({"fixture": "ops04-correlation", "environment": "acceptance"}))
    if rule is None:
        rule = api("/api/v1/admin/ops/alert-triggers/rules", "POST", desired, token)
    for key in ("agentDefinitionId", "agentVersion", "agentDefinitionHash", "alertNameRegex",
                "questionTemplate", "serviceRegex", "matchLabelsJson"):
        actual, expected = rule[key], desired[key]
        if key in ("questionTemplate", "matchLabelsJson"):
            actual, expected = json.loads(actual), json.loads(expected)
        if actual != expected:
            raise RuntimeError("Existing correlation fixture differs; preserved: " + key)
    if rule.get("notifyChannel") or not rule.get("webhookSecret"):
        raise RuntimeError("Correlation fixture must have a signature configured and notifications disabled")
    container = json.loads(subprocess.check_output(["docker", "inspect", "orbisops-acceptance-workflow-target-1"], text=True))[0]
    # Avoid exposing Docker Config.Env: retain only deployment identity evidence.
    # Docker emits nanoseconds; Python 3.9's ISO parser accepts only microseconds.
    labels = container["Config"]["Labels"]
    if (labels.get("com.docker.compose.project") != "orbisops-acceptance"
            or labels.get("com.docker.compose.service") != "workflow-target" or not container["State"]["Running"]):
        raise RuntimeError("Expected the running isolated workflow target")
    observed = dt.datetime.strptime(container["State"]["StartedAt"][:19], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=dt.timezone.utc)
    now = dt.datetime.now(dt.timezone.utc)
    evidence = "local-compose:workflow-target:" + container["Id"][:12]
    edges = [{"source": "ops-acc-a-service-" + str(i), "target": RESOURCE, "evidenceRef": evidence,
              "observedAt": observed.isoformat(), "expiresAt": (observed + dt.timedelta(days=30)).isoformat()} for i in (1, 2, 3, 4)]
    path = "/api/v1/admin/ops/alert-correlations/topology"
    existing = api(path + "?projectId=" + PROJECT + "&environment=acceptance", token=token)
    merged = {(edge["source"], edge["target"]): edge for edge in existing}
    for edge in edges:
        key = edge["source"], edge["target"]
        previous = merged.get(key)
        if previous:
            expires = dt.datetime.fromisoformat(previous["expiresAt"].replace("Z", "+00:00"))
            if previous["evidenceRef"] == evidence and expires > now:
                continue
            if (not refresh_expired or expires > now
                    or not previous["evidenceRef"].startswith("local-compose:workflow-target:")):
                raise RuntimeError("Existing topology source differs or expired; preserved: " + str(key))
        if observed + dt.timedelta(days=30) <= now:
            raise RuntimeError("The isolated target has no recent deployment evidence")
        merged[key] = edge
    api(path, "PUT", {"projectId": PROJECT, "environment": "acceptance", "edges": list(merged.values())}, token)
    stored = {(edge["source"], edge["target"]): edge for edge in
              api(path + "?projectId=" + PROJECT + "&environment=acceptance", token=token)}
    public_rule = {key: value for key, value in rule.items() if key != "webhookSecret"}
    return {"rule": public_rule, "topology": [stored[(edge["source"], edge["target"])] for edge in edges],
            "resource": RESOURCE, "containerId": container["Id"][:12],
            "scope": "actual local shared target; no production changes or model calls"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh-expired", action="store_true", help="Refresh expired local workflow-target fixture edges")
    parser.add_argument("--workflow-version", type=int, help="Use a separate fixture pinned to this published alert Workflow version")
    parser.add_argument("--automatic", action="store_true", help="Use the read-only Workflow for structured alert events")
    args = parser.parse_args()
    print(json.dumps(prepare(args.refresh_expired, args.workflow_version, args.automatic), ensure_ascii=False, indent=2))
