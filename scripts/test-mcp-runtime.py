#!/usr/bin/env python3
"""OPS-03 deployed-stack acceptance: normal APIs, real MCP process, MySQL and SQLite.

Creates uniquely named protocol fixtures. Never patches approvals/run outcomes in SQL.
Restarts only the named acceptance backend/MCP containers; all volumes are retained.
Requires the probe Tool Policy to have been reviewed through the normal UI/API.
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse
import copy
import json
import runpy
import subprocess
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"
api_module = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))
api = api_module["request"]
token = api_module["login"](json.loads((ROOT / "deploy/.acceptance-private/admin.json").read_text()))
values = dict(line.split("=", 1) for line in (ROOT / "deploy/.env.acceptance").read_text().splitlines()
              if line and not line.startswith("#"))
run_tag = uuid.uuid4().hex[:10]
results = []
sql_queries = []
mcp_name = "OPS-03 acceptance MCP"


def sql(query):
    sql_queries.append(query.rstrip(';') + ';')
    result = subprocess.run(["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
                             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B --raw orbisops_acceptance'],
                            input=query, text=True, capture_output=True, check=True)
    return result.stdout.strip()


def quoted(value):
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def rows(query):
    output = sql(query)
    return [json.loads(line) for line in output.splitlines()] if output else []


def peer_evidence():
    request = urllib.request.Request("http://127.0.0.1:18861/evidence",
                                     headers={"Authorization": "Bearer " + values["OPS_ACCEPTANCE_MCP_TOKEN"]})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)


def until(condition, seconds=80):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        result = condition()
        if result:
            return result
        time.sleep(0.25)
    raise TimeoutError("Deployed MCP acceptance condition timed out")


def ready():
    try:
        api("/api/v1/setup/status")
        peer_evidence()
        return True
    except (OSError, RuntimeError):
        return False


def waiting_approval(run, seconds=900):
    """Observe durable state before reading the approval API; don't flood it while models run."""
    def pending():
        state = facts(run)
        if state['status'] in ('FAILED', 'SUCCEEDED', 'CANCELED'):
            raise AssertionError('Expected approval wait, got ' + json.dumps(state))
        if state['status'] != 'WAITING_APPROVAL':
            return None
        value = api(f'/api/v1/agent/chat/runs/{run}/workflow-approval?projectId={PROJECT}', token=token)
        return value if value and value.get('status') == 'WAITING' else None
    return until(pending, seconds=seconds)


def facts(run):
    return rows("SELECT JSON_OBJECT('runId',run_id,'status',status,'version',agent_version,'hash',agent_definition_hash,"
                "'epoch',fencing_token,'error',error_message) FROM ai_ops_agent_run WHERE run_id=" + quoted(run))[0]


def integration(name=None):
    fixture_name = name if name is not None else mcp_name
    matches = [item for item in api(f"/api/v1/admin/ops/projects/{PROJECT}/tools", token=token)
               if item["mcpName"] == fixture_name]
    if len(matches) != 1:
        raise RuntimeError("Fixture integration is missing or ambiguous; nothing changed")
    tool = matches[0]
    if tool["status"] != "ENABLED" or "probe" not in tool["allowedActions"]:
        raise RuntimeError("Fixture probe is not enabled; initialize this named fixture without replacing disabled integrations")
    if tool["transportConfig"].get("endpoint") != "http://127.0.0.1:8181/mcp":
        raise RuntimeError("Fixture endpoint differs from the fixed local peer; retained")
    return tool


def publish(case, modes, budget=12, approval=False):
    tool = integration()
    definition = json.loads((ROOT / "scripts/fixtures/workflow-mcp-read.json").read_text()
                            .replace("$acceptanceMcpId", tool["mcpId"]))
    definition.update(agentId=f"ops-acceptance-mcp-{case}-{run_tag}", name=f"OPS-03 协议回归 · {case} · {run_tag}")
    definition["nodes"][0]["config"]["maxRealToolCalls"] = budget
    original = definition["nodes"][1]
    test_ids = []
    actions = []
    for index, mode in enumerate(modes):
        test_id = f"ops03-{run_tag}-{case}-{index}"
        test_ids.append(test_id)
        action = copy.deepcopy(original["config"]["actions"][0])
        action["arguments"].update(testId=test_id, mode=mode)
        actions.append(action)
    original["config"]["actions"] = actions
    if approval:
        second = copy.deepcopy(original)
        second.update(nodeId="after-approval", outputKey="afterApproval")
        second["config"]["actions"] = [copy.deepcopy(actions[-1])]
        original["config"]["actions"] = actions[:-1]
        definition["nodes"] = [definition["nodes"][0], original,
                               {"nodeId": "review", "type": "HUMAN_APPROVAL", "description": "批准继续隔离只读验收；用于验证恢复时预算不重置", "config": {"timeoutSeconds": 600}},
                               second, definition["nodes"][-1]]
        ids = [node["nodeId"] for node in definition["nodes"]]
        definition["edges"] = [{"from": a, "to": b, "conditionType": "always", "condition": "always"} for a, b in zip(ids, ids[1:])]
    saved = api("/api/v1/admin/ops-agents/drafts", "POST", definition, token)
    path = "/api/v1/admin/ops-agents/" + saved["agentId"] + "/versions/" + str(saved["version"])
    for action in ("validate", "publish"):
        saved = api(path + "/" + action, "POST", {}, token)
    return saved, test_ids


def start(definition, case):
    session = api("/api/v1/user/chat/session", "POST", {"projectId": PROJECT, "agentId": definition["agentId"],
                  "agentVersion": definition["version"], "title": "OPS-03 实际协议回归 · " + case}, token)
    response = api(f"/api/v1/user/chat/sessions/{session}/messages", "POST", {"projectId": PROJECT,
        "query": "执行已发布的隔离 MCP 协议验收：" + case, "mode": "AGENT", "engine": "GRAPH",
        "agentDefinitionId": definition["agentId"], "agentVersion": definition["version"],
        "metadata": {"executionType": "WORKFLOW", "fixture": "OPS-03-protocol-real-peer-direct-actions"}}, token, timeout=240)
    return response["metadata"]["runId"]


def verify(case, run, test_ids, expected_status, expected_calls, budget=None):
    final = until(lambda: (value if value["status"] in ("SUCCEEDED", "FAILED", "CANCELED") else None) if (value := facts(run)) else None)
    assert final["status"] == expected_status, final
    if expected_status == "FAILED":
        category = {"empty": "CONTRACT_INVALID", "shared-retry": "CONTRACT_INVALID", "budget-twelve": "AUTHORITY_DENIED"}.get(case, "TOOL_ERROR")
        assert final["error"] == "MCP_" + category, final
    remote = [row for row in peer_evidence()["requests"] if row["method"] == "tools/call" and row["test_id"] in test_ids]
    assert len(remote) == expected_calls, (case, len(remote), expected_calls)
    calls = api(f"/api/v1/admin/ops/projects/{PROJECT}/mcp-tool-calls", token=token)
    calls = [call for call in calls if call["runId"] == run]
    reservations = rows("SELECT JSON_OBJECT('logicalId',logical_call_id,'attempt',physical_attempt,'rpcId',request_id,'limit',budget_limit) "
                        "FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + quoted(run) + " ORDER BY id")
    assert len(reservations) == expected_calls, (case, reservations, remote)
    assert sorted(item["rpcId"] for item in reservations) == sorted(item["rpc_id"] for item in remote)
    if budget is not None:
        assert all(item["limit"] == budget for item in reservations)
    tool_results = rows("SELECT JSON_OBJECT('resultId',result_id,'source',source,'status',status,'output',full_output,'hash',output_hash) "
                        "FROM ai_ops_tool_result WHERE run_id=" + quoted(run) + " AND source='MCP_REMOTE_TOOL' ORDER BY id")
    if expected_status == "SUCCEEDED":
        assert tool_results and all(item["status"] == "SUCCEEDED" for item in tool_results)
        for item in tool_results:
            envelope = json.loads(item["output"])
            assert envelope["orbisopsResultVersion"] == 1 and envelope["isError"] is False
            assert isinstance(envelope["normalizedContent"]["count"], int)
        response = rows("SELECT response_json FROM ai_ops_agent_run WHERE run_id=" + quoted(run))[0]
        rendered = json.dumps(response, ensure_ascii=False)
        assert '"$ref"' not in rendered, "Provider objects must remain complete JSON in graph outputs"
    result = {"case": case, "result": "PASS", "run": final, "actualRemoteCalls": len(remote),
              "physicalReservations": reservations, "callStatuses": [item["status"] for item in calls],
              "failures": [json.loads(item["errorMessage"]) for item in calls if item.get("errorMessage")],
              "resultIds": [item["resultId"] for item in tool_results], "testIds": test_ids}
    results.append(result)
    print(json.dumps(result, ensure_ascii=False), flush=True)
    return result


def main():
    until(ready)
    admission = runpy.run_path(str(ROOT / 'scripts/deploy-tested-acceptance.py'))['require_idle_runs']
    initial_admission = admission()
    tool = integration()
    baseline = peer_evidence()
    accounts = json.loads((ROOT / "deploy/.acceptance-private/users.json").read_text())
    viewer = api_module["login"](accounts["ops_acceptance_viewer"])
    outsider = api_module["login"](accounts["ops_acceptance_b_member"])
    policy = next(item for item in api(f"/api/v1/admin/ops/projects/{PROJECT}/mcp-tool-policies", token=token)
                  if item["mcpId"] == tool["mcpId"] and item["toolName"] == "probe" and item["status"] == "ACTIVE")
    permission_checks = {
        "viewerCannotApprove": api(f"/api/v1/admin/ops/projects/{PROJECT}/mcp-tool-policies/{policy['policyId']}/approve",
                                   "POST", {}, viewer, denied=True),
        "otherProjectCannotReadCalls": api(f"/api/v1/admin/ops/projects/{PROJECT}/mcp-tool-calls",
                                           token=outsider, denied=True),
    }
    assert all(item["httpStatus"] == 403 for item in permission_checks.values()), permission_checks
    results.append({"case": "project-permissions", "result": "PASS", "checks": permission_checks})
    for case, modes, status, count in [
        ("structured", ["structured"], "SUCCEEDED", 1),
        ("multi", ["multiple"], "SUCCEEDED", 1),
        ("reconnect", ["disconnect_once"], "SUCCEEDED", 2),
        ("empty", ["empty"], "FAILED", 1),
        ("shared-retry", ["contract_then_disconnect"], "FAILED", 1),
        ("budget-twelve", ["structured"] * 11 + ["disconnect_once"], "FAILED", 12),
    ]:
        definition, ids = publish(case, modes)
        verify(case, start(definition, case), ids, status, count, 12)
    # Four tool/business failures must not trip the dependency circuit.
    for index in range(4):
        case = "business-error-" + str(index)
        definition, ids = publish(case, ["tool_error"])
        verify(case, start(definition, case), ids, "FAILED", 1, 12)
    definition, ids = publish("after-business-errors", ["structured"])
    verify("after-business-errors", start(definition, "after-business-errors"), ids, "SUCCEEDED", 1, 12)

    # Concurrent real published workflows retain separate runs and their independent budgets.
    concurrent = [publish("parallel-" + str(index), ["structured"]) for index in range(3)]
    with ThreadPoolExecutor(max_workers=3) as pool:
        runs = list(pool.map(lambda item: start(item[0], "parallel"), concurrent))
    for index, ((definition, ids), run) in enumerate(zip(concurrent, runs)):
        verify("parallel-" + str(index), run, ids, "SUCCEEDED", 1, 12)

    # Actual waiting item, process restart, approval command and resume, with the same durable budget.
    definition, ids = publish("restart-wait", ["disconnect_once", "structured"], 3, approval=True)
    run = start(definition, "restart-wait")
    approval_path = f"/api/v1/agent/chat/runs/{run}/workflow-approval?projectId={PROJECT}"
    pending = until(lambda: (value if value.get("status") == "WAITING" else None)
                    if (value := api(approval_path, token=token)) else None)
    assert int(sql("SELECT COUNT(*) FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + quoted(run))) == 2
    # The test's durable approval wait is allowed, but other active work/owned leases are not.
    restart_admission = admission()
    namespace_recovery = runpy.run_path(str(ROOT / 'scripts/backend-namespace-lifecycle.py'))['restart_backend']()
    until(ready)
    assert api(approval_path, token=token)["approvalId"] == pending["approvalId"]
    assert int(sql("SELECT COUNT(*) FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + quoted(run))) == 2
    api(f"/api/v1/agent/chat/runs/{run}/workflow-approval/decision?projectId={PROJECT}", "POST",
        {"approvalId": pending["approvalId"], "decision": "APPROVE"}, token)
    verify("restart-wait", run, ids, "SUCCEEDED", 3, 3)
    approval_rows = rows("SELECT JSON_OBJECT('id',approval_id,'status',status,'node',node_id) FROM ai_ops_workflow_approval WHERE run_id=" + quoted(run))
    assert approval_rows[0]["status"] == "APPROVED"
    results[-1]["approvals"] = approval_rows
    results[-1]["namespaceRecovery"] = namespace_recovery

    current = peer_evidence()
    assert current["services"] == baseline["services"] and current["receipts"] == baseline["receipts"], "Read-only tests must preserve target data"
    return {"fixture": "OPS-03-protocol-real-peer-direct-actions", "mcpId": tool["mcpId"], "mcpName": mcp_name,
            "runTag": run_tag, "results": results,
            "servicesUnchanged": True, "writeReceiptsUnchanged": True,
            "initialAdmission": initial_admission, "restartAdmission": restart_admission,
            "limitations": ["Not a model evaluation or OPS-04 business graph", "Approved Landing write workflow remains separate acceptance"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--mcp-name", default=mcp_name, help="Exact reviewed fixture identity on the fixed local peer")
    args = parser.parse_args()
    mcp_name = args.mcp_name
    if args.output.exists() or args.output.with_suffix('.sql').exists():
        parser.error('Choose fresh output paths; retain earlier results and SQL')
    try:
        report = main()
    except Exception as error:
        report = {"runTag": run_tag, "results": results, "failure": str(error)}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        args.output.with_suffix('.sql').write_text('-- Actual read-only verification queries; all runtime actions use normal APIs.\n' + '\n\n'.join(sql_queries) + '\n')
        raise
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    args.output.with_suffix('.sql').write_text('-- Actual read-only verification queries; all runtime actions use normal APIs.\n' + '\n\n'.join(sql_queries) + '\n')
