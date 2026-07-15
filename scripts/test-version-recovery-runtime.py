#!/usr/bin/env python3
"""Actual version endpoint outage/recovery plus A -> B unreachable handoff.

Stops only the isolated target, always restarts in finally, and verifies counters
and business facts survive. Version queries are governed published graph actions.
"""
from pathlib import Path
import argparse
import datetime as dt
import json
import runpy
import uuid

ROOT = Path(__file__).resolve().parents[1]


def main():
    m = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    recovery = runpy.run_path(str(ROOT / "scripts/test-observability-fault-runtime.py"))
    c = runpy.run_path(str(ROOT / "scripts/test-change-workflow-runtime.py"))
    b = runpy.run_path(str(ROOT / "scripts/test-business-workflow-runtime.py"))
    api, token = m["api"], m["token"]
    integration = runpy.run_path(str(ROOT / "scripts/seed-observability-mcp.py"))["prepare"]()
    definition = json.loads((ROOT / "scripts/fixtures/workflow-business-change.json").read_text().replace("$observabilityMcpId", integration["mcpId"]))
    definition.update(agentId="ops04-version-recovery-" + uuid.uuid4().hex[:10], name="OPS-04 实际版本接口恢复（只读组件）",
                      description="只读真实目标版本；不创建变更，不宣称 Landing 完成。")
    version = next(n for n in definition["nodes"] if n["nodeId"] == "version")
    version["config"]["actions"][0]["argumentBindings"] = {"window": "input.window"}
    definition["nodes"] = [definition["nodes"][0], version, {"nodeId": "end", "type": "END", "config": {"outputKeys": ["workflowData_version"]}}]
    definition["edges"] = [{"from": a, "to": z, "conditionType": "always", "condition": "always"} for a, z in (("start", "version"), ("version", "end"))]
    graph = api("/api/v1/admin/ops-agents/drafts", "POST", definition, token)
    for action in ("validate", "publish"):
        graph = api("/api/v1/admin/ops-agents/" + graph["agentId"] + "/versions/" + str(graph["version"]) + "/" + action, "POST", {}, token)
    def version_query():
        end = int(dt.datetime.now(dt.timezone.utc).timestamp())
        result = c["start"](graph, {"window": {"projectId": "ops-acceptance-a", "environment": "acceptance", "serviceId": "ops-acc-a-service-4", "startEpoch": end - 60, "endEpoch": end}})
        assert result["run"]["status"] == "SUCCEEDED" and 1 <= result["physicalDispatchCount"] <= 2
        evidence = result["checkpointState"]["variables"]["nodeOutput:version"]["workflowData_version"]
        stored = m["rows"]("SELECT JSON_OBJECT('hash',output_hash,'output',full_output) FROM ai_ops_tool_result WHERE result_id=" + m["quoted"](evidence["providerResultId"]))[0]
        assert stored["hash"] == evidence["providerOutputHash"]
        assert json.loads(stored["output"])["normalizedContent"] == evidence["normalizedContent"]
        return {"run": result["run"], "evidence": evidence, "dispatches": result["physicalDispatchCount"]}
    before = {"outbox": recovery["outbox"](), "counters": recovery["counters"](), "version": c["versions"]()}
    initial = version_query()
    assert initial["evidence"]["normalizedContent"]["status"] == "AVAILABLE"
    recovery["compose"]("--profile", "business", "stop", "workflow-target")
    try:
        recovery["wait_for"](lambda: recovery["up"](0), 30)
        unavailable = version_query()
        assert unavailable["evidence"]["normalizedContent"]["status"] == "UNAVAILABLE"
        assert "version" not in unavailable["evidence"]["normalizedContent"]
        handoff = b["run_case"]("ops-acc-a-service-3", dt.datetime.now(dt.timezone.utc).isoformat(), "UNREACHABLE", "inspection")
        assert handoff["result"] == "PASS" and handoff["childInvestigation"]["report"]["status"] == "UNREACHABLE"
    finally:
        recovery["compose"]("--profile", "business", "start", "workflow-target")
    recovery["wait_for"](lambda: recovery["get"]("http://127.0.0.1:18262/ready")["status"] == "ready")
    recovery["wait_for"](lambda: recovery["up"](1), 30)
    recovered = version_query()
    assert recovered["evidence"]["normalizedContent"]["status"] == "AVAILABLE"
    after = {"outbox": recovery["outbox"](), "counters": recovery["counters"](), "version": c["versions"]()}
    assert after == before
    return {"result": "PASS", "scope": "real target recovery, read-only version interface and A unreachable handoff; no release",
            "before": before, "after": after, "initial": initial, "unavailable": unavailable, "recovered": recovered, "inspectionHandoff": handoff}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = main()
    except Exception as error:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps({"result": "FAIL", "error": type(error).__name__ + ": " + str(error)}, ensure_ascii=False, indent=2))
        raise
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"result": "PASS", "unreachableInspectionRun": result["inspectionHandoff"]["run"]["runId"],
                      "actualVersionRecovered": True, "countersAndOutboxUnchanged": True}, ensure_ascii=False))
