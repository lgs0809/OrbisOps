#!/usr/bin/env python3
"""Publish committed business definitions through draft -> validate -> publish APIs."""
from pathlib import Path
import argparse
import json
import runpy

ROOT = Path(__file__).resolve().parents[1]


def prepare(kinds, new_version=False):
    support = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))
    credentials = json.loads((ROOT / "deploy/.acceptance-private/admin.json").read_text())
    token, api = support["login"](credentials), support["request"]
    integration = runpy.run_path(str(ROOT / "scripts/seed-observability-mcp.py"))["prepare"]()
    if integration["state"] != "READY":
        return integration
    matches = runpy.run_path(str(ROOT / "scripts/seed-mcp-acceptance.py"))["matches_definition"]
    published = []
    for kind in kinds:
        source = ROOT / "scripts/fixtures" / ("workflow-business-" + kind + ".json")
        definition = json.loads(source.read_text().replace("$observabilityMcpId", integration["mcpId"]))
        if kind == "inspection":
            alert_source = json.loads((ROOT / "scripts/fixtures/workflow-business-alert.json").read_text().replace("$observabilityMcpId", integration["mcpId"]))
            alert_versions = api("/api/v1/admin/ops-agents/" + alert_source["agentId"] + "/versions", token=token)
            alert = next((version for version in alert_versions if version.get("lifecycle") == "PUBLISHED" and matches(alert_source, version)), None)
            if not alert:
                raise RuntimeError("Publish the committed alert graph first; inspection must pin its reviewed business definition")
            for node in definition["nodes"]:
                if node.get("config", {}).get("version") == "$alertWorkflowVersion":
                    node["config"]["version"] = alert["version"]
        path = "/api/v1/admin/ops-agents/" + definition["agentId"]
        versions = api(path + "/versions", token=token)
        saved = next((version for version in versions if matches(definition, version)), None)
        if versions and not saved and not new_version:
            raise RuntimeError("Existing business graph differs; preserve it and review a new version explicitly")
        if not saved:
            saved = api("/api/v1/admin/ops-agents/drafts", "POST", definition, token)
        if saved.get("lifecycle") != "PUBLISHED":
            if saved.get("lifecycle") not in ("DRAFT", "VALIDATED"):
                raise RuntimeError("Existing graph deliberately disabled; preserved")
            for action in ("validate", "publish"):
                saved = api(path + "/versions/" + str(saved["version"]) + "/" + action, "POST", {}, token)
        published.append({key: saved.get(key) for key in ("agentId", "version", "definitionHash", "lifecycle")})
    return {"state": "READY", "published": published}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("kinds", nargs="+", choices=["alert", "inspection", "change"])
    parser.add_argument("--new-version", action="store_true", help="Publish a new reviewed source version; preserve all earlier versions")
    args = parser.parse_args()
    print(json.dumps(prepare(args.kinds,args.new_version), ensure_ascii=False, indent=2))
