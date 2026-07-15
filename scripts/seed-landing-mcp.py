#!/usr/bin/env python3
"""Normally import and review the seven bounded local dual-target MCP tools; preserve existing reviews."""
import argparse
import json
from pathlib import Path
import runpy
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT, NAME = "ops-acceptance-a", "OPS-08 isolated dual target"
ENDPOINT = "http://127.0.0.1:8381/mcp"
TOOLS = {env + "_" + action for env in ("test", "prod")
         for action in ("read_state", "check_orders", "apply_configuration")} | {"test_validate_configuration"}


def seed(output, refresh_catalog=False):
    h = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token = h["api"], h["token"]
    path = "/api/v1/admin/ops/projects/" + PROJECT
    matches = [m for m in api(path + "/tools", token=token) if m.get("mcpName") == NAME]
    if len(matches) > 1:
        raise RuntimeError("Ambiguous integration; preserved")
    if not matches or refresh_catalog:
        if matches and (matches[0]["transportConfig"].get("endpoint") != ENDPOINT or matches[0]["status"] != "ENABLED"):
            raise RuntimeError("Changed or disabled connection preserved")
        if matches:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.with_suffix(".before.json").write_text(json.dumps(matches[0], ensure_ascii=False, indent=2))
        actor = next(u for u in api("/api/v1/admin/admin-user/query-all", token=token)
                     if u["username"] == "ops_acceptance_admin")
        api("/api/v1/admin/ops/tool-executions", "POST", {"projectId": PROJECT,
            "toolsetId": "capability.manage", "toolName": "mcp_server_import", "userId": actor["userId"],
            "authenticatedUsername": actor["username"], "executionScope": "PRE_APPROVAL_WORKFLOW",
            "runId": "ops08-import-" + uuid.uuid4().hex,
            "arguments": {"sourceUrl": ENDPOINT, "capabilityName": NAME,
                          "credentialRef": "${env:OPS_ACCEPTANCE_LANDING_MCP_TOKEN}", "transportType": "streamable-http"}}, token)
        matches = [m for m in api(path + "/tools", token=token) if m.get("mcpName") == NAME]
    if len(matches) != 1:
        raise RuntimeError("Integration did not become available")
    mcp = matches[0]
    if mcp["transportConfig"].get("endpoint") != ENDPOINT or mcp["status"] not in ("PENDING_REVIEW", "ENABLED"):
        raise RuntimeError("Existing integration changed or disabled; preserved")
    if {t["toolName"] for t in mcp["remoteTools"]} != TOOLS:
        raise RuntimeError("Remote catalog differs from reviewed local source")
    policies = [p for p in api(path + "/mcp-tool-policies", token=token) if p["mcpId"] == mcp["mcpId"]]
    reviewed = []
    for tool in mcp["remoteTools"]:
        policy = next(p for p in policies if p["toolName"] == tool["toolName"] and p["schemaHash"] == tool["schemaHash"])
        write = tool["toolName"].endswith("apply_configuration")
        validation = tool["toolName"] == "test_validate_configuration"
        production = write and tool["toolName"].startswith("prod_")
        desired = {"readOnly": not write, "riskLevel": "HIGH" if production else "MEDIUM" if write else "LOW",
            "effectType": "MUTATE_TARGET_RESOURCE" if production else "MUTATE_TEST_RESOURCE" if write else "VALIDATE_ONLY" if validation else "READ_EXTERNAL_STATE",
            "effectScope": "TARGET_RESOURCE_WRITE" if production else "SANDBOX" if write or validation else "TARGET_RESOURCE_READ",
            "mutability": "PROD_MUTATING" if production else "TEST_MUTATING" if write else "READ_ONLY",
            "capability": "MUTATING" if write else "READ_ONLY", "allowedActions": ["TEST_VALIDATE" if validation else tool["toolName"].upper()],
            "investigateAllowed": not write, "prepareAllowed": (write and not production) or validation,
            "landAllowed": production or not write, "requiresApprovedPackage": production,
            "requiresHumanApproval": production, "requiresDryRun": production, "requiresRollbackPlan": production,
            "disclosureTier": "EXTENSION" if write or validation else "CORE"}
        if policy.get("reviewStatus") != "HUMAN_REVIEWED":
            if policy["status"] != "PENDING_REVIEW":
                raise RuntimeError("Existing non-pending policy preserved")
            policy = api(path + "/mcp-tool-policies/" + policy["policyId"] + "/approve", "POST", {
                **desired, "reason": "用户授权本机隔离验收；已审阅独立凭据、固定服务、实际配置 CAS 与持久化回执。测试写仅准备阶段，模拟生产写必须经 ChangePackage 审批与 Landing。"}, token)
        for key in ("readOnly", "riskLevel", "effectType", "effectScope", "mutability", "allowedActions",
                    "investigateAllowed", "prepareAllowed", "landAllowed", "requiresApprovedPackage",
                    "requiresHumanApproval", "requiresDryRun", "requiresRollbackPlan", "disclosureTier"):
            if policy.get(key) != desired[key] or policy["status"] != "ACTIVE":
                raise RuntimeError("Existing review differs; no automatic replacement")
        environment = tool["toolName"].split("_", 1)[0]
        binding = {"resourceScope": f"service://ops-acc-a-service-2/{environment}",
                   "targetEnvironment": environment}
        argument_policy = policy.get("argumentPolicy") or {}
        prior_binding = argument_policy.get("resourceBinding")
        if prior_binding is not None and prior_binding != binding:
            raise RuntimeError("Existing resource binding differs; preserved")
        if prior_binding is None:
            policy = api(path + "/mcp-tool-policies/" + policy["policyId"] + "/approve", "POST", {
                "argumentPolicy": {**argument_policy, "resourceBinding": binding},
                "reason": "本机双环境 MCP 代码固定项目、A2 服务和环境；补充已核对的逐工具资源绑定，保留已有参数限制及审批边界。"}, token)
            if policy.get("argumentPolicy", {}).get("resourceBinding") != binding:
                raise RuntimeError("Reviewed binding was not persisted")
        reviewed.append({key: policy.get(key) for key in ("policyId", "toolName", "schemaHash", "readOnly",
                         "effectType", "prepareAllowed", "landAllowed", "requiresApprovedPackage", "argumentPolicy", "reviewedBy", "reviewedAt")})
    if mcp["status"] != "ENABLED":
        api(path + "/tools/" + mcp["mcpId"] + "/status", "PATCH", {"status": "ENABLED"}, token)
    result = {"status": "READY", "mcpId": mcp["mcpId"], "projectId": PROJECT, "policies": reviewed}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"status": "READY", "mcpId": mcp["mcpId"], "reviewedTools": len(reviewed), "productionExecuted": False}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--refresh-catalog", action="store_true", help="Re-import this isolated connection and retain its previous definition beside the result")
    args = parser.parse_args()
    seed(args.output, args.refresh_catalog)
