#!/usr/bin/env python3
"""Run the published business graph and cross-check durable state, RPC ledger and actual DB facts.

No fixture verdicts are injected. --expected is a test assertion, never sent to the workflow.
"""
from pathlib import Path
import argparse
import datetime as dt
import json
import math
import runpy
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
api, token, quoted, until = [support[key] for key in ("api", "token", "quoted", "until")]
PROJECT = "ops-acceptance-a"
sql_queries = []
started_runs = []


def sql(query):
    sql_queries.append(query.rstrip(';') + ';')
    return support['sql'](query)


def rows(query):
    output = sql(query)
    return [json.loads(line) for line in output.splitlines()] if output else []


def inspect(run):
    # This is an observation deadline, not an extension of the application's
    # model or workflow budget. Native retries can legitimately exceed 80 s.
    fact = until(lambda: item if (item := support["facts"](run))["status"] in ("SUCCEEDED", "FAILED", "CANCELED") else None,
                 seconds=900)
    states = rows("SELECT checkpoint_json FROM ai_ops_agent_run_checkpoint WHERE run_id=" + quoted(run)
                  + " AND checkpoint_type LIKE 'WORKFLOW_%' ORDER BY checkpoint_seq DESC LIMIT 1")
    state = states[0]["state"] if states else {}
    variables = state.get("variables", {})
    outputs = {key[len("nodeOutput:"):]: value for key, value in variables.items()
               if key.startswith("nodeOutput:") and key.count(":") == 1}
    report = next((outputs[node]["workflowData_report"] for node in ("summary", "report", "sql-review", "review")
                   if node in outputs and "workflowData_report" in outputs[node]), {})
    reservations = rows("SELECT JSON_OBJECT('rpcId',request_id,'nodeId',node_id,'tool',tool_name,'attempt',physical_attempt,'limit',budget_limit) "
                        "FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + quoted(run) + " ORDER BY id")
    values = support["values"]
    request = urllib.request.Request("http://127.0.0.1:18862/evidence", headers={"Authorization": "Bearer " + values["OPS_ACCEPTANCE_OBSERVABILITY_TOKEN"]})
    with urllib.request.urlopen(request, timeout=10) as response:
        peer = json.load(response)
    rpc_ids = {item["rpcId"] for item in reservations}
    calls = [item for item in peer["rpcCalls"] if item["rpc_id"] in rpc_ids]
    queries = [item for item in peer["queries"] if item["rpc_id"] in rpc_ids]
    assert len(reservations) == len(calls) <= 12, (reservations, calls)
    assert {item["rpc_id"] for item in calls} == rpc_ids
    assert all(item["limit"] == 12 for item in reservations)
    assert len({item["fingerprint"] for item in queries}) == len(queries), "Supplement queries must not repeat the same request"
    actual = {"run": fact, "report": report, "executedNodes": list(outputs), "routeHistory": state.get("routeHistory"),
              "physicalDispatches": reservations, "remoteCalls": calls, "upstreamQueries": queries, "checks": {"dispatchLedgerMatchesRemote": True}}
    events = api('/api/v1/admin/ops-agent-runs/' + run + '/events/list', token=token)
    actual['modelIdentities'] = [{key: event.get('payload', {}).get(key) for key in ('requestedModel', 'responseModel')}
                                 for event in events if event.get('eventType') == 'MODEL_RESPONSE_VERIFIED']
    actual['modelRetryCount'] = sum(event.get('eventType') == 'MODEL_CALL_RETRYING' for event in events)
    actual['checks']['configuredModelsVerified'] = bool(actual['modelIdentities']) and all(
        item['requestedModel'] == item['responseModel'] and item['responseModel'] in ('gpt-5.6-luna', 'gpt-5.6-terra')
        for item in actual['modelIdentities'])
    if fact["status"] != "SUCCEEDED":
        return actual
    assert "end" in outputs, "Incomplete graph incorrectly reported success: terminal node was not executed"
    assert report, "A completed graph must persist its business report"
    assert not report.get("needsSql"), "Required database branch did not reach its final review"
    if report.get("slowSqlFinding") == "FOUND":
        assert "explain" in outputs and "report" in outputs, "Slow-SQL path must execute its bounded supplement and final report"
    preserved = []
    for output in outputs.values():
        for key, value in output.items():
            if key.startswith("workflowData_") and isinstance(value,dict) and value.get("outputMode") == "MCP_EVIDENCE_REFERENCE":
                recorded = rows("SELECT JSON_OBJECT('source',source,'hash',output_hash,'output',full_output) FROM ai_ops_tool_result WHERE result_id="
                                + quoted(value["providerResultId"]))[0]
                assert recorded["source"] == "MCP_REMOTE_TOOL" and recorded["hash"] == value["providerOutputHash"]
                envelope = json.loads(recorded["output"])
                assert envelope["orbisopsResultVersion"] == 1 and envelope["isError"] is False
                assert envelope["normalizedContent"] == value["normalizedContent"]
                preserved.append(value["providerResultId"])
    actual["checks"]["completeToolEnvelopesPreserved"] = preserved
    window = report["window"]
    start, end = [dt.datetime.fromtimestamp(window[key], dt.timezone.utc).strftime("%Y-%m-%d %H:%M:%S") for key in ("startEpoch", "endEpoch")]
    reference = rows("SELECT JSON_OBJECT('sampleCount',COUNT(*),'uniqueRequests',COUNT(DISTINCT event_id),'errorCount',COALESCE(SUM(http_status>=500),0),"
                     "'slowSqlCount',COALESCE(SUM(sql_duration_ms>1000),0)) FROM ops_acceptance_business_a.ops04_request WHERE service_id="
                     + quoted(window["serviceId"]) + " AND observed_at >= " + quoted(start) + " AND observed_at < " + quoted(end))[0]
    assert reference["sampleCount"] == reference["uniqueRequests"]
    measured = rows("SELECT JSON_OBJECT('seconds',duration_ms/1000,'status',http_status) FROM ops_acceptance_business_a.ops04_request "
                    "WHERE service_id=" + quoted(window["serviceId"])
                    + " AND TIMESTAMPADD(MICROSECOND,CAST(duration_ms*1000 AS SIGNED),observed_at) >= " + quoted(start)
                    + " AND TIMESTAMPADD(MICROSECOND,CAST(duration_ms*1000 AS SIGNED),observed_at) < " + quoted(end))
    # The HTTP ledger records request start + monotonic elapsed duration; this is an
    # independent derived completion-window check, labelled separately from scrape facts.
    if measured:
        durations = sorted(float(item["seconds"]) for item in measured)
        error_rate = sum(item["status"] >= 500 for item in measured) / len(measured)
        p95 = durations[math.ceil(.95 * len(durations)) - 1]
        actual["derivedCompletionWindowCheck"] = {"sampleCount":len(measured),"errorRate":error_rate,"rawSampleP95Seconds":p95,
            "timestampMethod":"stored request start plus measured elapsed duration; not rewritten request facts"}
        assert report["metrics"]["sampleCountLowerBound"] <= len(measured)
        if report["status"] in ("HEALTHY","NO_OBSERVED_ANOMALY"):
            assert len(measured) >= 100 and error_rate <= .01 and p95 <= 1
        if report["status"] in ("UNHEALTHY","OBSERVED_ANOMALY"):
            assert len(measured) >= 100 and (error_rate > .01 or p95 > 1)
    if "metrics" in outputs:
        native = outputs["metrics"]["workflowData_metrics"]["normalizedContent"]
        if native["status"] == "AVAILABLE":
            assert native["collectionDefinition"] == "ops04-completed-http-raw-scrapes-v1"
            actual["checks"]["nativeScrapeTimestamps"] = True
    if "logs" in outputs:
        logs = outputs["logs"]["workflowData_logs"]["normalizedContent"]
        if logs["status"] == "AVAILABLE":
            assert logs["sampleCount"] <= reference["sampleCount"], (logs["sampleCount"], reference)
            checked = []
            for event in logs["samples"]:
                row = rows("SELECT JSON_OBJECT('traceId',event_id,'orderId',order_id,'status',http_status,'durationMs',duration_ms,'sqlDurationMs',sql_duration_ms) "
                           "FROM ops_acceptance_business_a.ops04_request WHERE event_id=" + quoted(event["trace_id"]))[0]
                assert row["orderId"] == event["order_id"] and row["status"] == event["http_status"]
                assert row["durationMs"] == event["duration_ms"] and row["sqlDurationMs"] == event["sql_duration_ms"]
                checked.append(row["traceId"])
            actual["checks"]["logsMatchActualMySqlRequests"] = checked
            actual["checks"]["logsCapturedCount"] = logs["sampleCount"]
    actual["actualMySqlWindow"] = reference
    if "sql" in outputs:
        observed = outputs["sql"]["workflowData_sql"]["normalizedContent"]
        if observed["status"] == "AVAILABLE":
            assert observed["sampleCount"] <= reference["sampleCount"] and observed["slowSqlCount"] <= reference["slowSqlCount"]
            assert reference["slowSqlCount"] > 0, "SQL branch must have real linked slow-query evidence"
    assert report["rootCauseConfirmed"] is False
    assert report.get("supplementRoundsUsed", 0) <= 2
    raw_response = sql("SELECT response_json FROM ai_ops_agent_run WHERE run_id=" + quoted(run))
    assert '"$ref"' not in raw_response, "Evidence must survive actual serialized responses"
    actual["checks"]["responseCompleteJson"] = True
    if "investigation" in report:
        reference = report["investigation"]
        assert reference["executionStatus"] == "SUCCEEDED" and reference["workflowId"] == "ops-business-alert-b"
        assert reference["childRunId"] != run
        child = inspect(reference["childRunId"])
        assert child["run"]["status"] == "SUCCEEDED"
        assert child["run"]["hash"] == reference["workflowDefinitionHash"] and child["run"]["version"] == reference["workflowVersion"]
        child_window = child["report"]["window"]
        assert child_window["projectId"] == window["projectId"] and child_window["serviceId"] == window["serviceId"]
        assert child_window["startEpoch"] == window["endEpoch"] - 900
        assert child_window["endEpoch"] <= window["endEpoch"] + 900
        prior = outputs["handoff"]["workflowData_alertInput"]["priorEvidence"]
        assert prior["sourceRunId"] == run and prior["inspectionReport"]["window"] == window
        assert prior["inspectionReport"]["evidenceReferences"] == report["evidenceReferences"]
        child_request = json.loads(sql("SELECT request_json FROM ai_ops_agent_run WHERE run_id=" + quoted(reference["childRunId"])))
        assert json.loads(child_request["query"]) == outputs["handoff"]["workflowData_alertInput"]
        assert child_request["metadata"]["parentRunId"] == run
        assert child_request["metadata"]["subWorkflowDefinitionHash"] == reference["workflowDefinitionHash"]
        assert "summary" in outputs and report["status"] in ("UNHEALTHY", "UNREACHABLE")
        actual["childInvestigation"] = child
        actual["checks"]["childCommittedSuccessAndFrozenVersionVerified"] = True
        actual["checks"]["inspectionEvidenceCarriedWithDistinctInvestigationWindow"] = True
        actual["totalPhysicalCallsIncludingChild"] = len(calls) + len(child["remoteCalls"])
    return actual


def run_case(service, alert_time, expected, workflow="alert"):
    agent = "ops-business-alert-b" if workflow == "alert" else "ops-business-inspection-a"
    versions = api("/api/v1/admin/ops-agents/" + agent + "/versions", token=token)
    definition = max((version for version in versions if version["lifecycle"] == "PUBLISHED"), key=lambda version: version["version"])
    session = api("/api/v1/user/chat/session", "POST", {"projectId": PROJECT, "agentId": agent,"agentVersion": definition["version"],
                  "title": "OPS-04 真实业务证据 · " + service}, token)
    inputs = {"projectId": PROJECT, "environment": "acceptance", "serviceId": service}
    if workflow == "alert":
        inputs.update(alertTime=alert_time, alertContent="隔离订单目标告警关联验收；数据来自实际 HTTP 请求")
    query = (f"请调查隔离验收环境中服务 {service} 的订单延迟告警，发生时间是 {alert_time}。"
             "请按告警前后各十五分钟关联指标、日志和慢查询，只做只读调查；证据缺失时明确说明。"
             if workflow == "alert" else f"请只读巡检隔离验收环境中服务 {service} 最近五分钟的健康情况。")
    started = {'sessionId': session, 'userQuery': query}
    started_runs.append(started)
    response = api(f"/api/v1/user/chat/sessions/{session}/messages", "POST", {"projectId": PROJECT,"query": query,
        "mode": "AGENT", "engine": "GRAPH", "agentDefinitionId": agent,"agentVersion": definition["version"],
        "metadata": {"executionType": "WORKFLOW", "acceptanceScenario": "OPS-04-real-business-natural-language"}}, token, timeout=900)
    started['runId'] = response['metadata']['runId']
    result = inspect(response["metadata"]["runId"])
    result.update(sessionId=session, input=inputs, userQuery=query, expectation=expected, workflow=workflow, result="FAIL")
    if (result["run"]["status"] == "SUCCEEDED" and result["report"].get("status") == expected
            and result['checks']['configuredModelsVerified']):
        result["result"] = "PASS"
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--service", required=True)
    parser.add_argument("--workflow", choices=["alert", "inspection"], default="alert")
    parser.add_argument("--alert-time", default=dt.datetime.now(dt.timezone.utc).isoformat())
    parser.add_argument("--expected", required=True)
    parser.add_argument("--output", type=Path, required=True)
    options = parser.parse_args()
    if options.output.exists() or options.output.with_suffix('.sql').exists():
        parser.error('Preserve existing evidence; choose a new output path')
    try:
        report = run_case(options.service, options.alert_time, options.expected, options.workflow)
    except Exception as error:
        options.output.parent.mkdir(parents=True, exist_ok=True)
        options.output.write_text(json.dumps({"result":"FAIL","error":type(error).__name__+": "+str(error),
                                             'startedRuns': started_runs},ensure_ascii=False,indent=2))
        raise
    finally:
        options.output.parent.mkdir(parents=True, exist_ok=True)
        options.output.with_suffix('.sql').write_text('\n'.join(sql_queries) + '\n')
    options.output.parent.mkdir(parents=True, exist_ok=True)
    options.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps({"result":report["result"],"runId":report["run"]["runId"],"runStatus":report["run"]["status"],
                      "verdict":report["report"].get("status"),"realToolCalls":len(report["remoteCalls"]),
                      "totalPhysicalCallsIncludingChild":report.get("totalPhysicalCallsIncludingChild",len(report["remoteCalls"])),
                      "childRunId":report.get("childInvestigation",{}).get("run",{}).get("runId"),"nodes":report["executedNodes"]},ensure_ascii=False))
    raise SystemExit(0 if report["result"] == "PASS" else 1)
