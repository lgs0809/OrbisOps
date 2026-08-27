#!/usr/bin/env python3
"""Discover/review the actual isolated read-only connectors using normal authenticated APIs.

--approve-read-only executes auditable policy approval as the configured acceptance admin.
Disabled/rejected integrations and changed contracts are preserved, never silently replaced.
"""
from pathlib import Path
import argparse
import json
import runpy
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"
NAME = "OPS-04 real observability"
TOOLS = {"metrics_window", "logs_window", "sql_window", "sql_explain", "target_version"}
if any(line.startswith('OPS_ACCEPTANCE_NATIVE_PACKAGE_BINDING=') and line.split('=',1)[1].strip()
       for line in (ROOT/'deploy/.env.acceptance').read_text().splitlines()):
    TOOLS.add('change_package_evidence')


def prepare(approve=False, refresh_catalog=False):
    support = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))
    credentials = json.loads((ROOT / "deploy/.acceptance-private/admin.json").read_text())
    token, api = support["login"](credentials), support["request"]
    path = f"/api/v1/admin/ops/projects/{PROJECT}"
    def integration():
        matches = [item for item in api(path + "/tools", token=token) if item.get("mcpName") == NAME]
        if len(matches) > 1:
            raise RuntimeError("Ambiguous integration identity; preserved")
        return matches[0] if matches else None
    mcp = integration()
    preserved = []
    if mcp and refresh_catalog:
        preserved = [item for item in api(path + "/mcp-tool-policies", token=token) if item["mcpId"] == mcp["mcpId"]]
        # A reviewed connector may return to pending review after an explicit
        # discovery attempt. Retry through the native importer, preserving all
        # previous human-reviewed contracts and refusing disabled integrations.
        recoverable_discovery = (mcp.get("status") == "PENDING_REVIEW"
            and bool(preserved) and bool(mcp.get("remoteTools"))
            and all(item.get("reviewStatus") == "HUMAN_REVIEWED" for item in preserved))
        if (mcp.get("status") != "ENABLED" and not recoverable_discovery) or mcp["transportConfig"].get("endpoint") != "http://127.0.0.1:8281/mcp":
            raise RuntimeError("Only the reviewed isolated endpoint may be explicitly rediscovered")
        if any(item["status"] not in ("ACTIVE", "STALE") or item.get("reviewStatus") != "HUMAN_REVIEWED" for item in preserved):
            raise RuntimeError("Existing disabled/unreviewed policy preserved; catalog refresh refused")
    if not mcp or refresh_catalog:
        actor = next(user for user in api("/api/v1/admin/admin-user/query-all", token=token) if user["username"] == credentials["username"])
        api("/api/v1/admin/ops/tool-executions", "POST", {"projectId": PROJECT, "toolsetId": "capability.manage",
            "toolName": "mcp_server_import", "userId": actor["userId"], "authenticatedUsername": credentials["username"],
            "executionScope": "PRE_APPROVAL_WORKFLOW", "runId": "ops04-observability-import-" + uuid.uuid4().hex,
            "arguments": {"sourceUrl": "http://127.0.0.1:8281/mcp", "capabilityName": NAME,
                          "credentialRef": "${env:OPS_ACCEPTANCE_OBSERVABILITY_TOKEN}", "transportType": "streamable-http"}}, token)
        for _ in range(30):
            mcp = integration()
            if mcp:
                break
            time.sleep(1)
    if not mcp or mcp.get("status") not in ("ENABLED", "PENDING_REVIEW"):
        raise RuntimeError("Integration missing or deliberately disabled; preserved")
    if mcp["transportConfig"].get("endpoint") != "http://127.0.0.1:8281/mcp" or {x["toolName"] for x in mcp["remoteTools"]} != TOOLS:
        raise RuntimeError("Integration endpoint/catalog changed; preserved")
    policies = [item for item in api(path + "/mcp-tool-policies", token=token) if item["mcpId"] == mcp["mcpId"]]
    for prior in preserved:
        current = next((item for item in policies if item["policyId"] == prior["policyId"]), None)
        fields = ("schemaHash", "reviewStatus", "readOnly", "allowedActions", "reviewedBy", "reviewedAt")
        superseded = any(tool["toolName"] == prior["toolName"] and tool["schemaHash"] != prior["schemaHash"] for tool in mcp["remoteTools"])
        status_preserved = current and (current["status"] == prior["status"] or (prior["status"] == "ACTIVE" and current["status"] == "STALE" and superseded))
        if not status_preserved or any(current.get(key) != prior.get(key) for key in fields):
            raise RuntimeError("An existing reviewed contract changed during discovery; no automatic reapproval")
    reviewed = []
    for tool in mcp["remoteTools"]:
        policy = next((item for item in policies if item["toolName"] == tool["toolName"] and item["schemaHash"] == tool["schemaHash"]), None)
        if not policy:
            raise RuntimeError("Discovered contract has no matching review record")
        if policy["status"] not in ("ACTIVE", "PENDING_REVIEW"):
            raise RuntimeError("Policy disabled/rejected; preserved")
        if policy.get("reviewStatus") != "HUMAN_REVIEWED":
            if not approve:
                return {"state": "REVIEW_REQUIRED", "mcpId": mcp["mcpId"]}
            policy = api(path + "/mcp-tool-policies/" + policy["policyId"] + "/approve", "POST", {
                "effectType": "READ_EXTERNAL_STATE", "effectScope": "TARGET_RESOURCE_READ", "mutability": "READ_ONLY",
                "capability": "READ_ONLY", "allowedActions": [tool["toolName"].upper()], "riskLevel": "LOW", "readOnly": True,
                "investigateAllowed": True, "prepareAllowed": False, "landAllowed": False, "requiresApprovedPackage": False,
                "requiresHumanApproval": False, "requiresDryRun": False, "requiresRollbackPlan": False, "disclosureTier": "CORE",
                "reason": "用户已授权本机隔离验收；固定服务/窗口只读查询；native包证据仅复用既有A viewer四个GET和固定批准包身份，不授予业务写入或审批权限"}, token)
        if not (policy["status"] == "ACTIVE" and policy["readOnly"] is True and policy["landAllowed"] is False):
            raise RuntimeError("Existing reviewed policy differs; preserved")
        reviewed.append({key: policy.get(key) for key in ("policyId", "toolName", "schemaHash", "reviewedBy", "reviewedAt", "status", "readOnly")})
    if mcp["status"] != "ENABLED":
        api(path + "/tools/" + mcp["mcpId"] + "/status", "PATCH", {"status": "ENABLED"}, token)
    return {"state": "READY", "mcpId": mcp["mcpId"], "projectId": PROJECT, "reviewedPolicies": reviewed}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--approve-read-only", action="store_true")
    parser.add_argument("--refresh-catalog", action="store_true", help="Explicitly rediscover the enabled acceptance endpoint while retaining prior reviewed contracts")
    options = parser.parse_args()
    print(json.dumps(prepare(options.approve_read_only, options.refresh_catalog), ensure_ascii=False, indent=2))
