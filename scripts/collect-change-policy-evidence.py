#!/usr/bin/env python3
"""Collect real governed MCP evidence for C's rule-component integration tests.

The two windows are observations of an unchanged target. This mechanism graph
does not create/approve/land a change and supplies no business acceptance verdict.
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse
import copy
import datetime as dt
import json
import math
import runpy
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


def main(center):
    helper = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token, rows, sql, quoted = [helper[key] for key in ("api", "token", "rows", "sql", "quoted")]
    integration = runpy.run_path(str(ROOT / "scripts/seed-observability-mcp.py"))["prepare"]()
    assert integration["state"] == "READY"
    mcp = integration["mcpId"]
    definition = json.loads((ROOT / "scripts/fixtures/workflow-business-change.json").read_text().replace("$observabilityMcpId", mcp))
    definition.update(agentId="ops04-c-real-evidence-" + uuid.uuid4().hex[:10], name="OPS-04 C 规则真实数据采集（未发生变更）",
                      description="仅采集实际版本和两个真实指标窗口供规则组件测试；没有实际变更，不是发布验收成功证据。")
    read = copy.deepcopy(next(node for node in definition["nodes"] if node["nodeId"] == "version"))
    read.update(nodeId="collect", description="按调用方指定窗口读取已授权的实际指标与资源版本")
    read["config"]["actions"] = [{"mcpId": mcp, "remoteToolName": tool, "argumentBindings": {"window": "input." + window},
                                  "structuredOutputKey": output, "outputMode": "MCP_EVIDENCE_REFERENCE"}
                                 for tool, window, output in (("target_version", "afterWindow", "version"),
                                                              ("metrics_window", "beforeWindow", "before"), ("metrics_window", "afterWindow", "after"))]
    definition["nodes"] = [definition["nodes"][0], read, {"nodeId": "end", "type": "END", "config": {"outputKeys": ["workflowData_version", "workflowData_before", "workflowData_after"]}}]
    definition["edges"] = [{"from": a, "to": b, "condition": "always", "conditionType": "always"} for a, b in (("start", "collect"), ("collect", "end"))]
    graph = api("/api/v1/admin/ops-agents/drafts", "POST", definition, token)
    for action in ("validate", "publish"):
        graph = api("/api/v1/admin/ops-agents/" + graph["agentId"] + "/versions/" + str(graph["version"]) + "/" + action, "POST", {}, token)

    def collect(service):
        def window(a, b):
            return {"projectId": "ops-acceptance-a", "serviceId": "ops-acc-a-service-" + str(service), "environment": "acceptance",
                    "startEpoch": a, "endEpoch": b, "complete": True}
        payload = {"beforeWindow": window(center - 900, center), "afterWindow": window(center, center + 900)}
        session = api("/api/v1/user/chat/session", "POST", {"projectId": "ops-acceptance-a", "title": "C 真实数据组件测试：未发生变更", "agentId": graph["agentId"], "agentVersion": graph["version"]}, token)
        response = api("/api/v1/user/chat/sessions/" + session + "/messages", "POST", {"projectId": "ops-acceptance-a", "query": json.dumps(payload),
                       "mode": "AGENT", "engine": "GRAPH", "agentDefinitionId": graph["agentId"], "agentVersion": graph["version"],
                       "metadata": {"executionType": "WORKFLOW", "fixture": "real-data-policy-component-not-landed-change"}}, token)
        run = response["metadata"]["runId"]
        fact = helper["until"](lambda: value if (value := helper["facts"](run))["status"] in ("SUCCEEDED", "FAILED") else None)
        assert fact["status"] == "SUCCEEDED", fact
        checkpoint = rows("SELECT checkpoint_json FROM ai_ops_agent_run_checkpoint WHERE run_id=" + quoted(run)
                          + " AND checkpoint_type LIKE 'WORKFLOW_%' ORDER BY checkpoint_seq DESC LIMIT 1")[0]["state"]
        output = checkpoint["variables"]["nodeOutput:collect"]
        result = {"run": fact, "scope": "real evidence / synthetic comparison boundary; no actual release", **payload,
                  "service": service, "references": [], "database": {}}
        for key in ("version", "before", "after"):
            value = output["workflowData_" + key]
            recorded = rows("SELECT JSON_OBJECT('hash',output_hash,'output',full_output) FROM ai_ops_tool_result WHERE result_id=" + quoted(value["providerResultId"]))[0]
            assert recorded["hash"] == value["providerOutputHash"]
            envelope = json.loads(recorded["output"])
            assert envelope["normalizedContent"] == value["normalizedContent"] and envelope["isError"] is False
            result[key] = envelope["normalizedContent"]
            assert result[key]["status"] == "AVAILABLE", result[key]
            result["references"].append(value["providerResultId"])
        actual_version = rows("SELECT JSON_OBJECT('version',version) FROM ops_acceptance_business_a.acceptance_service WHERE service_id=" + quoted(payload["afterWindow"]["serviceId"]))[0]["version"]
        assert result["version"]["version"] == actual_version
        for stage in ("before", "after"):
            w = payload[stage + "Window"]
            dates = [dt.datetime.fromtimestamp(w[k], dt.timezone.utc).strftime("%Y-%m-%d %H:%M:%S") for k in ("startEpoch", "endEpoch")]
            measured = rows("SELECT JSON_OBJECT('seconds',duration_ms/1000,'status',http_status,'version',version) FROM ops_acceptance_business_a.ops04_request WHERE service_id="
                            + quoted(w["serviceId"]) + " AND TIMESTAMPADD(MICROSECOND,CAST(duration_ms*1000 AS SIGNED),observed_at)>=" + quoted(dates[0])
                            + " AND TIMESTAMPADD(MICROSECOND,CAST(duration_ms*1000 AS SIGNED),observed_at)<" + quoted(dates[1]))
            assert measured, "This component test must use actual associated request data"
            durations = sorted(item["seconds"] for item in measured)
            result["database"][stage] = {"samples": len(measured), "errorRate": sum(item["status"] >= 500 for item in measured) / len(measured),
                                        "p95Seconds": durations[math.ceil(len(durations) * .95) - 1], "versions": sorted({item["version"] for item in measured})}
        result["dispatches"] = rows("SELECT JSON_OBJECT('rpcId',request_id,'tool',tool_name) FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + quoted(run) + " ORDER BY id")
        assert 3 <= len(result["dispatches"]) <= 6
        return result

    with ThreadPoolExecutor(max_workers=4) as pool:
        samples = list(pool.map(collect, (1, 2, 3, 4)))
    request = urllib.request.Request("http://127.0.0.1:18862/evidence", headers={"Authorization": "Bearer " + helper["values"]["OPS_ACCEPTANCE_OBSERVABILITY_TOKEN"]})
    with urllib.request.urlopen(request, timeout=10) as response:
        peer = json.load(response)
    for sample in samples:
        ids = {row["rpcId"] for row in sample["dispatches"]}
        sample["remoteCalls"] = [item for item in peer["rpcCalls"] if item["rpc_id"] in ids]
        sample["queries"] = [item for item in peer["queries"] if item["rpc_id"] in ids]
        assert len(sample["remoteCalls"]) == len(sample["dispatches"]) and len(sample["queries"]) == 3
        assert {q["query_id"] for q in sample["queries"]} == {sample[k]["queryId"] for k in ("version", "before", "after")}
    return {"result": "PASS", "scope": "real read components; no actual release or approved Landing", "graph": {k: graph[k] for k in ("agentId", "version", "definitionHash")}, "samples": samples}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--center", required=True, help="UTC midpoint inside an existing complete 30-minute traffic window")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = main(int(dt.datetime.fromisoformat(args.center.replace("Z", "+00:00")).timestamp()))
    except Exception as error:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps({"result": "FAIL", "error": type(error).__name__ + ": " + str(error)}, ensure_ascii=False))
        raise
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"result": "PASS", "scope": result["scope"], "samples": [{"service": item["service"], "runId": item["run"]["runId"], "database": item["database"]} for item in result["samples"]]}, ensure_ascii=False))
