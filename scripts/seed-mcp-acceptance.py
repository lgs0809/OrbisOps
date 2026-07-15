#!/usr/bin/env python3
"""Discover the isolated real MCP peer and publish a deterministic read workflow.

Never fabricates policy reviews, approvals, receipts or run outcomes. The first run
imports tools as PENDING_REVIEW; review probe through the UI and rerun. Existing
credentials, disabled integrations and changed workflow versions are retained.
"""
from pathlib import Path
import argparse
import json
import runpy
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"
NAME = "OPS-03 acceptance MCP"


def matches_definition(expected, actual):
    if isinstance(expected, dict):
        return isinstance(actual, dict) and all(matches_definition(value, actual.get(key)) for key, value in expected.items())
    if isinstance(expected, list):
        return isinstance(actual, list) and len(expected) == len(actual) and all(matches_definition(a, b) for a, b in zip(expected, actual))
    return expected == actual


def prepare(name=NAME, approve_read_only=False):
    api = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))
    credentials = json.loads((ROOT / "deploy/.acceptance-private/admin.json").read_text())
    token = api["login"](credentials)
    request = api["request"]
    path = f"/api/v1/admin/ops/projects/{PROJECT}"
    tools = request(path + "/tools", token=token)
    matches = [item for item in tools if item.get("mcpName") == name]
    if len(matches) > 1:
        raise RuntimeError("Ambiguous acceptance MCP identity; nothing replaced")
    if not matches:
        users = request("/api/v1/admin/admin-user/query-all", token=token)
        actor = next(user for user in users if user["username"] == credentials["username"])
        result = request("/api/v1/admin/ops/tool-executions", "POST", {
            "projectId": PROJECT, "toolsetId": "capability.manage", "toolName": "mcp_server_import",
            "userId": actor["userId"], "authenticatedUsername": credentials["username"],
            "executionScope": "PRE_APPROVAL_WORKFLOW", "runId": "ops-03-initialization-" + uuid.uuid4().hex,
            "arguments": {"sourceUrl": "http://127.0.0.1:8181/mcp", "capabilityName": name,
                          "credentialRef": "${env:OPS_ACCEPTANCE_MCP_TOKEN}", "transportType": "streamable-http"}}, token)
        matches = [item for item in request(path + "/tools", token=token) if item.get("mcpName") == name]
        if len(matches) != 1:
            raise RuntimeError("Imported fixture missing or ambiguous; nothing replaced")
    mcp = matches[0]
    if mcp.get("status") not in ("PENDING_REVIEW", "ENABLED"):
        raise RuntimeError("Existing acceptance MCP was disabled/changed; retained")
    if mcp["transportConfig"].get("endpoint") != "http://127.0.0.1:8181/mcp":
        raise RuntimeError("Existing acceptance MCP endpoint differs; retained")
    policies = request(path + "/mcp-tool-policies", token=token)
    probe_hash = next(item["schemaHash"] for item in mcp["remoteTools"] if item["toolName"] == "probe")
    reviewed = [item for item in policies if item.get("mcpId") == mcp["mcpId"]
                and item.get("toolName") == "probe" and item.get("schemaHash") == probe_hash
                and item.get("status") == "ACTIVE" and item.get("reviewStatus") == "HUMAN_REVIEWED"
                and item.get("readOnly") is True]
    if not reviewed and approve_read_only:
        pending = [item for item in policies if item.get("mcpId") == mcp["mcpId"]
                   and item.get("toolName") == "probe" and item.get("schemaHash") == probe_hash
                   and item.get("status") == "PENDING_REVIEW"]
        if len(pending) != 1:
            raise RuntimeError("Probe review is missing, changed or rejected; retained")
        # Only the inspected synthetic read-only probe is reviewed. Other discovered
        # tools, including append_record, remain unavailable to this fixture.
        reviewed = [request(path + "/mcp-tool-policies/" + pending[0]["policyId"] + "/approve", "POST", {
            "effectType": "READ_EXTERNAL_STATE", "effectScope": "TARGET_RESOURCE_READ", "mutability": "READ_ONLY",
            "capability": "READ_ONLY", "allowedActions": ["PROBE"], "riskLevel": "LOW", "readOnly": True,
            "investigateAllowed": True, "prepareAllowed": False, "landAllowed": False,
            "requiresApprovedPackage": False, "requiresHumanApproval": False, "requiresDryRun": False,
            "requiresRollbackPlan": False, "disclosureTier": "CORE",
            "reason": "本机隔离协议验收；已审阅夹具源码，仅允许 probe 读取合成服务状态，不调用 append_record 或生产资源。"
        }, token)]
    if not reviewed:
        return {"state": "REVIEW_REQUIRED", "mcpId": mcp["mcpId"]}
    if any(item.get("status") != "ACTIVE" or item.get("readOnly") is not True
           or item.get("landAllowed") is not False for item in reviewed):
        raise RuntimeError("Existing reviewed probe policy differs; retained")
    if mcp["status"] != "ENABLED":
        mcp = request(path + "/tools/" + mcp["mcpId"] + "/status", "PATCH", {"status": "ENABLED"}, token)
    definition = json.loads((ROOT / "scripts/fixtures/workflow-mcp-read.json").read_text().replace("$acceptanceMcpId", mcp["mcpId"]))
    if name != NAME:
        definition.update(agentId="ops-acceptance-protocol-" + mcp["mcpId"], name=name + " · 只读工作流")
    agent_path = "/api/v1/admin/ops-agents/" + definition["agentId"]
    versions = request(agent_path + "/versions", token=token)
    if versions:
        matching = [item for item in versions if matches_definition(definition, item)]
        if not matching:
            raise RuntimeError("Existing acceptance workflow differs; retained")
        saved = matching[0]
        if saved.get("lifecycle") == "PUBLISHED":
            return {"state": "READY", "mcpId": mcp["mcpId"], "agentId": saved["agentId"], "version": saved["version"]}
        if saved.get("lifecycle") not in ("DRAFT", "VALIDATED"):
            raise RuntimeError("Existing acceptance workflow disabled; retained")
    else:
        saved = request("/api/v1/admin/ops-agents/drafts", "POST", definition, token)
    for action in ("validate", "publish"):
        saved = request(agent_path + "/versions/" + str(saved["version"]) + "/" + action, "POST", {}, token)
    return {"state": "READY", "mcpId": mcp["mcpId"], "agentId": saved["agentId"], "version": saved["version"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mcp-name", default=NAME, help="Separate fixture identity on the fixed local peer; never replaces another name")
    parser.add_argument("--approve-read-only", action="store_true", help="Review only the inspected synthetic probe through the normal admin API")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.mcp_name.strip() or len(args.mcp_name) > 120:
        parser.error("Use a nonempty fixture name up to 120 characters")
    if args.output and args.output.exists():
        parser.error("Choose a fresh output path")
    report = prepare(args.mcp_name, args.approve_read_only)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False))
