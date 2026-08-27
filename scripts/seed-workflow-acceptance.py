#!/usr/bin/env python3
"""Create/publish the synthetic OPS-02 fixture through normal APIs, without replacing existing versions.

No approvals or task outcomes are manufactured. Use --revision 2 only to publish
the changed topology during a frozen-v1 recovery test. Repeating a published
revision does not move the current pointer back to an older version.
"""
from pathlib import Path
import argparse
import json
import runpy

ROOT = Path(__file__).resolve().parents[1]


def prepare(revision=1, agent_id=None):
    api = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))
    token = api["login"](json.loads((ROOT / "deploy/.acceptance-private/admin.json").read_text()))
    request = api["request"]
    fixture = ROOT / "scripts/fixtures" / ("workflow-approval.json" if revision == 1 else "workflow-approval-v2.json")
    definition = json.loads(fixture.read_text())
    if agent_id:
        if not agent_id.startswith("ops-acceptance-") or not agent_id.replace("-", "").isalnum():
            raise ValueError("Only explicit synthetic acceptance agent IDs are allowed")
        definition["agentId"] = agent_id
        definition["name"] += " · " + agent_id.removeprefix("ops-acceptance-")
    path = "/api/v1/admin/ops-agents/" + definition["agentId"]
    versions = request(path + "/versions", token=token)
    matches = [item for item in versions if item.get("description") == definition["description"]
               and [(node["nodeId"], node["type"]) for node in item["nodes"]]
               == [(node["nodeId"], node["type"]) for node in definition["nodes"]]]
    if matches:
        saved = matches[0]
        if saved["lifecycle"] == "PUBLISHED":
            return saved
        if saved["lifecycle"] not in ("DRAFT", "VALIDATED"):
            raise RuntimeError("Existing fixture was disabled/changed; retained without reactivation")
    else:
        if len(versions) >= revision:
            raise RuntimeError("Existing workflow differs from fixture; retained without replacement")
        saved = request("/api/v1/admin/ops-agents/drafts", "POST", definition, token)
    for action in ("validate", "publish"):
        saved = request(path + "/versions/" + str(saved["version"]) + "/" + action, "POST", {}, token)
    return saved


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--revision", type=int, choices=(1, 2), default=1)
    parser.add_argument("--agent-id", help="Optional separate fixture identity for an independent replay")
    args = parser.parse_args()
    result = prepare(args.revision, args.agent_id)
    print(json.dumps({key: result.get(key) for key in ("agentId", "version", "lifecycle", "definitionHash")}, ensure_ascii=False))
