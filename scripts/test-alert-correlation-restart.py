#!/usr/bin/env python3
"""Verify durable incident groups across a restart of only the local acceptance backend.

Retains every data volume. It also verifies historical MCP receipts from the
Incident API against their database records, and rejects malformed topology
updates through the normal API. No alert/approval state is changed in SQL.
"""
from pathlib import Path
import argparse
import json
import runpy
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"


def main(output):
    support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token, sql, q = [support[key] for key in ("api", "token", "sql", "quoted")]
    result = {"status": "FAIL", "checks": {}}

    def save():
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
        output.with_suffix('.sql').write_text('-- Actual read-only correlation and receipt evidence queries.\n' + '\n'.join(support['sql_queries']) + '\n')

    def snapshot():
        groups = api("/api/v1/admin/ops/alert-correlations?projectId=" + PROJECT, token=token)
        return {"groups": sorted(groups, key=lambda group: group["groupId"]),
                "decisions": sql("SELECT event_id,group_id,decision_json FROM ai_ops_alert_correlation_decision ORDER BY event_id"),
                "revisions": sql("SELECT id,group_id,action_type,detail_json FROM ai_ops_alert_correlation_revision ORDER BY id")}

    def started():
        return subprocess.check_output(["docker", "inspect", "orbisops-acceptance-backend-1", "--format", "{{.State.StartedAt}}"], text=True).strip()

    def ready():
        for _ in range(90):
            try:
                if api("/api/v1/setup/status") is not None:
                    return
            except (RuntimeError, OSError):
                pass
            time.sleep(1)
        raise AssertionError("Backend did not recover")

    result["before"] = snapshot()
    assert any(len(group["members"]) >= 3 for group in result["before"]["groups"]), "Run the real correlation fault fixture first"
    result["startedBefore"] = started()
    save()
    result['namespaceRecovery'] = runpy.run_path(str(ROOT / 'scripts/backend-namespace-lifecycle.py'))['restart_backend']()
    result["startedAfter"] = started()
    assert result["startedBefore"] != result["startedAfter"]
    # Allow two reconciliation polls; already recorded source events must not be counted again.
    time.sleep(12)
    result["after"] = snapshot()
    assert result["before"] == result["after"], "Restart changed persisted grouping decisions or counted events twice"
    result["checks"]["restartPreservesGroupMembersCountsAndAuditHistory"] = True

    topology_path = "/api/v1/admin/ops/alert-correlations/topology"
    topology_before = api(topology_path + "?projectId=" + PROJECT + "&environment=acceptance", token=token)
    invalid = {"projectId": PROJECT, "environment": "acceptance", "edges": [
        {"source": "a", "target": "b", "evidenceRef": "invalid-acceptance-input", "observedAt": "not-a-time", "expiresAt": "also-invalid"}]}
    result["invalidTopology"] = api(topology_path, "PUT", invalid, token, denied=True)
    assert result["invalidTopology"]["httpStatus"] == 400
    assert topology_before == api(topology_path + "?projectId=" + PROJECT + "&environment=acceptance", token=token)
    result["checks"]["invalidTopologyRejectedWithoutMutation"] = True

    result["incidents"] = []
    group = next(g for g in result["after"]["groups"] if len(g["members"]) == 3)
    for member in group["members"]:
        incident = member["signal"]["incidentId"]
        detail = api("/api/v1/admin/ops/incidents/" + incident + "/detail", token=token)
        diagnosis = detail["diagnosis"]
        mcp_sources = [source for source in diagnosis["sourceStatus"] if source["sourceId"].startswith("mcp.")]
        assert len(mcp_sources) >= 2 and all(source["queryStatus"] == "SUCCEEDED" and source["assessment"] == "UNKNOWN" for source in mcp_sources)
        assert all("尚未形成数据源查询结果" not in value for value in diagnosis["unknowns"])
        assert diagnosis["evidenceCompleteness"] == "PARTIAL"
        run_ids = [run["run_id"] for run in detail["runs"]]
        for fact in diagnosis["facts"]:
            for ref in fact["evidenceRefs"]:
                saved = support["rows"]("SELECT JSON_OBJECT('runId',run_id,'hash',output_hash) FROM ai_ops_tool_result WHERE result_id=" + q(ref["resultId"]))[0]
                assert saved["runId"] in run_ids and saved["hash"] == ref["outputHash"]
        result["incidents"].append({"incidentId": incident, "runs": run_ids, "diagnosis": diagnosis})
    result["checks"]["historicalReceiptProjectionMatchesDatabaseWithoutHealthyClaim"] = True
    result["status"] = "PASS"
    save()
    print(json.dumps({"status": "PASS", "checks": result["checks"]}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('Use a fresh evidence path')
    try:
        main(args.output)
    except Exception as error:
        value = json.loads(args.output.read_text()) if args.output.exists() else {}
        value.update(status="FAIL", error=str(error))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
        raise
