#!/usr/bin/env python3
"""C's deployed approval/authority boundaries. Does not fabricate a landed change.

Checks real API-created packages, normal approval rejection, concurrent graph runs,
committed node checkpoints, tool dispatch journals and unchanged resource versions.
The positive Landing/model loop is deliberately reported BLOCKED, never PASS.
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse
import json
import runpy
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
api, token, sql, rows, quoted, until = [support[key] for key in ("api", "token", "sql", "rows", "quoted", "until")]
PROJECT = "ops-acceptance-a"


def versions():
    with urllib.request.urlopen("http://127.0.0.1:18262/version", timeout=10) as response:
        return json.load(response)


def inspect(run):
    fact = until(lambda: value if (value := support["facts"](run))["status"] in ("SUCCEEDED", "FAILED", "CANCELED") else None)
    checkpoints = rows("SELECT checkpoint_json FROM ai_ops_agent_run_checkpoint WHERE run_id=" + quoted(run)
                       + " AND checkpoint_type LIKE 'WORKFLOW_%' ORDER BY checkpoint_seq DESC LIMIT 1")
    state = checkpoints[0]["state"] if checkpoints else {}
    outputs = {key[len("nodeOutput:"):]: value for key, value in state.get("variables", {}).items()
               if key.startswith("nodeOutput:") and key.count(":") == 1}
    report = next((outputs[node]["workflowData_report"] for node in ("review-extended", "unavailable", "review")
                   if "workflowData_report" in outputs.get(node, {})), {})
    dispatches = int(sql("SELECT COUNT(*) FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + quoted(run)))
    receipts = int(sql("SELECT COUNT(*) FROM ai_ops_tool_result WHERE run_id=" + quoted(run) + " AND source='MCP_REMOTE_TOOL'"))
    return {"run": fact, "executedNodes": list(outputs), "report": report, "physicalDispatchCount": dispatches,
            "remoteReceiptCount": receipts, "checkpointState": state}


def start(graph, payload):
    session = api("/api/v1/user/chat/session", "POST", {"projectId": PROJECT, "title": "OPS-04 C 权威变更验收", "agentId": graph["agentId"], "agentVersion": graph["version"]}, token)
    response = api("/api/v1/user/chat/sessions/" + session + "/messages", "POST", {
        "projectId": PROJECT, "query": json.dumps(payload), "mode": "AGENT", "engine": "GRAPH",
        "agentDefinitionId": graph["agentId"], "agentVersion": graph["version"],
        "metadata": {"executionType": "WORKFLOW", "fixture": "OPS-04-C-real-unlanded-boundary"}}, token)
    result = inspect(response["metadata"]["runId"])
    result.update(sessionId=session, input=payload)
    return result


def main(package_id):
    graph = runpy.run_path(str(ROOT / "scripts/seed-business-workflows.py"))["prepare"](["change"])["published"][0]
    package = api("/api/v1/user/ops/change-packages/" + package_id, token=token)
    assert package["projectId"] == PROJECT and package["status"] == "VALIDATION_FAILED"
    assert package["packageType"] == "NEEDS_HUMAN_DESIGN" and package["contextBundleId"]
    version_before = versions()
    rejected = {}
    for action, body in (("approve", {"version": package["version"], "packageHash": package["packageHash"]}), ("land", {})):
        try:
            api("/api/v1/user/ops/change-packages/" + package_id + "/" + action, "POST", body, token)
        except RuntimeError as error:
            message = str(error)
            assert "API failed:" in message and "HTTP 5" not in message, message
            rejected[action] = message
        else:
            raise AssertionError("Invalid candidate unexpectedly accepted " + action)
    missing = "ops04-missing-" + uuid.uuid4().hex
    cases = {
        "unlanded": {"projectId": PROJECT, "packageId": package_id},
        "claimed_landed": {"projectId": PROJECT, "packageId": package_id, "status": "LANDED", "approvedVersion": 999,
                           "expectedVersion": "fake", "maxErrorRate": 1, "maxP95Seconds": 999},
        "missing": {"projectId": PROJECT, "packageId": missing},
        "foreign_input": {"projectId": "ops-acceptance-b", "packageId": package_id},
    }
    with ThreadPoolExecutor(max_workers=4) as pool:
        futures = {name: pool.submit(start, graph, payload) for name, payload in cases.items()}
        results = {name: task.result() for name, task in futures.items()}
    for name, result in results.items():
        assert result["run"]["hash"] == graph["definitionHash"] and result["run"]["version"] == graph["version"]
        assert result["physicalDispatchCount"] == result["remoteReceiptCount"] == 0
        if name == "foreign_input":
            assert result["run"]["status"] == "FAILED" and "end" not in result["executedNodes"]
        else:
            assert result["run"]["status"] == "SUCCEEDED" and result["report"]["status"] == "INCONCLUSIVE"
            assert result["executedNodes"] == ["start", "context", "unavailable", "end"]
            assert result["report"]["evidenceGaps"] == ["CHANGE_RECORD_UNAVAILABLE" if name == "missing" else "CHANGE_NOT_LANDED"]
    assert results["unlanded"]["report"] == results["claimed_landed"]["report"]
    after = api("/api/v1/user/ops/change-packages/" + package_id, token=token)
    assert all(after.get(key) == package.get(key) for key in ("status", "version", "packageHash", "approvedVersion", "approvedPackageHash", "landingRunId"))
    assert versions() == version_before
    events = api("/api/v1/user/ops/change-packages/" + package_id + "/events", token=token)
    assert any(event["eventType"] == "PACKAGE_CREATED" for event in events)
    assert not any(event["eventType"] in ("PACKAGE_APPROVED", "LANDING_SUCCEEDED") for event in events)
    operations = rows("SELECT JSON_OBJECT('id',operation_run_id,'status',status) FROM ai_ops_change_package_landing_operation_run WHERE package_id=" + quoted(package_id))
    assert not operations
    return {"result": "PASS", "scope": "deployed C authority boundaries; not full change acceptance", "workflow": graph,
            "package": {key: after.get(key) for key in ("packageId", "status", "packageHash", "contextBundleId", "sessionId")},
            "approvalAndLandingRejected": rejected, "cases": results, "unchangedActualTarget": version_before,
            "packageEvents": events, "landingOperations": operations,
            "blocked": ["Real approved Landing and complete before/after release acceptance: model calls disabled and provider unconfigured"],
            "untested": ["C business graph's real approved release, 30-minute extension recovery and automatic completion trigger"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--package-id")
    parser.add_argument("--inspect-run", help="Inspect an actual browser-triggered C run")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = inspect(args.inspect_run) if args.inspect_run else main(args.package_id)
    except Exception as error:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps({"result": "FAIL", "error": type(error).__name__ + ": " + str(error)}, ensure_ascii=False, indent=2))
        raise
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"result": result.get("result", "INSPECTED"), "runs": {name: {"runId": item["run"]["runId"], "status": item["run"]["status"], "verdict": item["report"].get("status")}
          for name, item in result.get("cases", {}).items()}, "blocked": result.get("blocked", [])}, ensure_ascii=False))
