#!/usr/bin/env python3
"""Deployed DIRECT data/branch/recovery contract; synthetic protocol evidence, not a business graph."""
from pathlib import Path
import argparse
import copy
import json
import runpy
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
api, token, sql, rows, quoted, until = [support[key] for key in ("api", "token", "sql", "rows", "quoted", "until")]
PROJECT = support["PROJECT"]
tag = uuid.uuid4().hex[:10]


def definition(mcp_name=None):
    mcp = support["integration"](mcp_name)
    graph = json.loads((ROOT / "scripts/fixtures/workflow-mcp-read.json").read_text().replace("$acceptanceMcpId", mcp["mcpId"]))
    graph.update(agentId="ops-acceptance-data-" + tag, name="OPS-04 参数传递机制验收 · " + tag)
    graph["nodes"][0]["config"]["maxRealToolCalls"] = 2
    first = graph["nodes"][1]
    action = first["config"]["actions"][0]
    action["arguments"] = {"mode": "structured"}
    action["argumentBindings"] = {"testId": "input.first", "service": "input.service"}
    action["structuredOutputKey"] = "first"
    second = copy.deepcopy(first)
    second.update(nodeId="followup", outputKey="secondOutput")
    second["config"]["actions"][0].update(argumentBindings={"testId": "input.second", "value": "nodeOutput.workflowData_first.normalizedContent.count"}, structuredOutputKey="second")
    graph["nodes"] = [graph["nodes"][0], first,
                      {"nodeId": "approval", "type": "HUMAN_APPROVAL", "config": {"timeoutSeconds": 600},
                       "description": "批准继续隔离只读数据传递测试；不涉及目标写入"},
                      {"nodeId": "route", "type": "ROUTER", "agent": "ops-acceptance-router", "config": {}},
                      second, graph["nodes"][-1]]
    graph["edges"] = [{"from": a, "to": b, "conditionType": "always", "condition": "always"}
                      for a, b in (("start", "mcp-read"), ("mcp-read", "approval"), ("approval", "route"), ("followup", "end"))]
    graph["edges"] += [{"from": "route", "to": "followup", "conditionType": "expression",
                        "condition": "nodeOutput.workflowData_first.normalizedContent.count == 1"},
                       {"from": "route", "to": "end", "conditionType": "default", "condition": "default"}]
    return graph


def save_and_publish(graph):
    saved = api("/api/v1/admin/ops-agents/drafts", "POST", graph, token)
    path = f"/api/v1/admin/ops-agents/{saved['agentId']}/versions/{saved['version']}"
    api(path + "/validate", "POST", {}, token)
    return api(path + "/publish", "POST", {}, token)


def start(graph, query):
    session = api("/api/v1/user/chat/session", "POST", {"projectId": PROJECT, "agentId": graph["agentId"],
                  "agentVersion": graph["version"], "title": "OPS-04 数据传递机制验收（非业务结论）"}, token)
    response = api(f"/api/v1/user/chat/sessions/{session}/messages", "POST", {"projectId": PROJECT,
                  "query": json.dumps(query), "mode": "AGENT", "engine": "GRAPH", "agentDefinitionId": graph["agentId"],
                  "agentVersion": graph["version"], "metadata": {"executionType": "WORKFLOW", "fixture": "OPS-04-data-binding-contract"}}, token, timeout=240)
    return response["metadata"]["runId"]


def terminal(run):
    return until(lambda: value if (value := support["facts"](run))["status"] in ("SUCCEEDED", "FAILED", "CANCELED") else None)


def main(mcp_name=None):
    graph = save_and_publish(definition(mcp_name))
    first, second = "ops04-first-" + tag, "ops04-second-" + tag
    run = start(graph, {"first": first, "second": second, "service": "normal"})
    path = f"/api/v1/agent/chat/runs/{run}/workflow-approval?projectId={PROJECT}"
    pending = support['waiting_approval'](run)
    assert int(sql("SELECT COUNT(*) FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + quoted(run))) == 1
    recovery = runpy.run_path(str(ROOT / 'scripts/backend-namespace-lifecycle.py'))['restart_backend']()
    until(support["ready"])
    assert api(path, token=token)["approvalId"] == pending["approvalId"]
    api(f"/api/v1/agent/chat/runs/{run}/workflow-approval/decision?projectId={PROJECT}", "POST",
        {"approvalId": pending["approvalId"], "decision": "APPROVE"}, token)
    final = terminal(run)
    assert final["status"] == "SUCCEEDED", final
    peer = [row for row in support["peer_evidence"]()["requests"] if row["method"] == "tools/call" and row["test_id"] in (first, second)]
    assert len(peer) == 2 and {item["test_id"] for item in peer} == {first, second}
    calls = api(f"/api/v1/admin/ops/projects/{PROJECT}/mcp-tool-calls", token=token)
    calls = [item for item in calls if item["runId"] == run]
    assert len(calls) == 2 and all(item["status"] == "SUCCEEDED" for item in calls)
    inputs = [json.loads(item["inputJson"]) for item in calls]
    # String arguments are deliberately summarized in audit; correlate by trusted node/run IDs.
    followup = json.loads(next(item for item in calls if item["nodeId"] == "followup")["inputJson"])
    assert followup["value"] == 1 and isinstance(followup["value"], int), inputs
    reservations = rows("SELECT JSON_OBJECT('rpcId',request_id,'node',node_id) FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + quoted(run))
    assert len(reservations) == 2 and sorted(item["rpcId"] for item in reservations) == sorted(item["rpc_id"] for item in peer)

    # Missing source data fails before any tools/call, without a fabricated default.
    missing_run = start(graph, {"second": "ops04-missing-" + tag, "service": "normal"})
    missing = terminal(missing_run)
    assert missing["status"] == "FAILED", missing
    assert int(sql("SELECT COUNT(*) FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + quoted(missing_run))) == 0

    # Published configuration cannot bind approval/runtime authority into tool arguments.
    invalid = definition(mcp_name)
    invalid["agentId"] += "-invalid"
    invalid["nodes"][1]["config"]["actions"][0]["argumentBindings"]["value"] = "runtime.landingApproved"
    rejected = False
    try:
        save_and_publish(invalid)
    except RuntimeError as error:
        assert "API failed:" in str(error) and "HTTP 5" not in str(error), error
        rejected = True
    assert rejected, "Authority binding must be rejected before publication"
    return {"result": "PASS", "fixture": "data-binding-mechanism-not-business-graph", "run": final,
            "actualRemoteRequests": peer, "toolInputs": inputs, "reservations": reservations, "approvalId": pending["approvalId"],
            "missingInputRun": missing, "authorityBindingPublicationRejected": True,
            "namespaceRecovery": recovery,
            "limitations": ["Business metrics/logs/SQL workflow acceptance remains separate",
                            "DIRECT actions do not call a model, but shared Skill context may use the configured model"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--mcp-name", default=support["mcp_name"], help="Exact enabled, reviewed local fixture identity")
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Use a fresh evidence path")
    try:
        report = main(args.mcp_name)
    except Exception as error:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps({"result": "FAIL", "runTag": tag, "error": type(error).__name__ + ": " + str(error)}, ensure_ascii=False, indent=2))
        raise
    finally:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.with_suffix('.sql').write_text('-- Actual read-only evidence queries; no outcome updates.\n' + '\n'.join(support['sql_queries']) + '\n')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"result": "PASS", "runId": report["run"]["runId"], "actualRemoteCalls": 2, "missingInputBlocked": True}))
