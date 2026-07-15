#!/usr/bin/env python3
"""Read-only cross-check of a real OPS-05 browser/API session and its MCP receipts.

Retains the exact inspection SQL and its tabular result beside --output.
This fixture verifies the model-disabled queue; it does not claim semantic model quality.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import runpy
import subprocess
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def main(session, output):
    if not re.fullmatch(r"chat-session-[a-z0-9-]+", session):
        raise ValueError("Expected an actual acceptance chat-session ID")
    support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token, rows, q = [support[k] for k in ("api", "token", "rows", "quoted")]
    if support["values"].get("ORBISOPS_AI_MODEL_CALLS_ENABLED") != "false":
        raise RuntimeError("This checker only asserts model-disabled behavior")
    project = "ops-acceptance-a"
    endpoint = "/api/v1/user/ops/task-episodes?" + urllib.parse.urlencode({"projectId": project, "sessionId": session})
    view = api(endpoint, token=token)
    assert view["turns"] and view["turns"][0]["status"] == "WAITING_MODEL", view
    assert all(t["status"] == "WAITING_TURN" for t in view["turns"][1:]), view
    assert all(t["attempts"] == 0 and not t["episodeId"] for t in view["turns"])
    assert not view["episodes"] and not view["artifacts"] and not view["jobs"]
    request = urllib.request.Request("http://127.0.0.1:18862/evidence", headers={
        "Authorization": "Bearer " + support["values"]["OPS_ACCEPTANCE_OBSERVABILITY_TOKEN"]})
    with urllib.request.urlopen(request, timeout=10) as response:
        peer = json.load(response)
    with urllib.request.urlopen("http://127.0.0.1:18262/version", timeout=10) as response:
        target = json.load(response)
    assert target["projectId"] == project
    versions = {item["serviceId"]: item["version"] for item in target["services"]}
    runs = []
    for turn in view["turns"]:
        run = turn["sourceRunRef"]
        fact = support["facts"](run)
        assert fact["status"] == "SUCCEEDED" and fact["version"] == 2, fact
        receipts = rows("SELECT JSON_OBJECT('resultId',result_id,'hash',output_hash,'output',full_output,'status',status) "
                        "FROM ai_ops_tool_result WHERE run_id=" + q(run) + " AND project_id=" + q(project)
                        + " AND source='MCP_REMOTE_TOOL' ORDER BY id")
        dispatches = rows("SELECT JSON_OBJECT('rpcId',request_id,'nodeId',node_id,'tool',tool_name,'attempt',physical_attempt,'limit',budget_limit) "
                          "FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + q(run) + " ORDER BY id")
        rpc_ids = {d["rpcId"] for d in dispatches}
        calls = [c for c in peer["rpcCalls"] if c["rpc_id"] in rpc_ids]
        queries = [c for c in peer["queries"] if c["rpc_id"] in rpc_ids]
        assert len(receipts) == len(dispatches) == len(calls) == len(queries) == 1
        assert dispatches[0]["limit"] == 2 and dispatches[0]["attempt"] == 1
        receipt, query = receipts[0], queries[0]
        assert receipt["status"] == "SUCCEEDED"
        assert hashlib.sha256(receipt["output"].encode()).hexdigest() == receipt["hash"]
        envelope = json.loads(receipt["output"])
        observed = envelope["normalizedContent"]
        assert envelope["isError"] is False and envelope["structuredContent"] == observed
        assert observed["kind"] == "target_version" and observed["status"] == query["status"] == "AVAILABLE"
        assert observed["queryId"] == query["query_id"] and observed["queryFingerprint"] == query["fingerprint"]
        assert observed["scope"] == json.loads(query["scope_json"])
        assert hashlib.sha256(json.dumps(observed, sort_keys=True).encode()).hexdigest() == query["response_sha256"]
        assert observed["version"] == versions[observed["scope"]["serviceId"]]
        runs.append({"run": fact, "receipt": receipt, "dispatches": dispatches, "remoteCalls": calls, "upstreamQueries": queries})
    snapshots = rows("SELECT JSON_OBJECT('runId',run_id,'hash',snapshot_hash,'snapshot',snapshot_json) "
                     "FROM ai_ops_task_episode_context WHERE session_id=" + q(session) + " ORDER BY run_id")
    assert len(snapshots) == len(runs)
    for item in snapshots:
        assert hashlib.sha256(item.pop("snapshot").encode()).hexdigest() == item["hash"]
    statements = (ROOT / "scripts/fixtures/ops05-task-episodes-inspect.sql").read_text().replace("'REPLACE_WITH_SESSION_ID'", q(session))
    result = subprocess.run(["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -B --raw orbisops_acceptance'],
        input=statements, text=True, capture_output=True, check=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_name(output.stem + "-inspect.sql").write_text(statements)
    output.with_name(output.stem + "-inspect.tsv").write_text(result.stdout)
    evidence = {"status": "PASS", "scope": "actual read-only workflows and durable pending prefix; semantic Luna loop BLOCKED",
                "sessionId": session, "view": view, "runs": runs, "contextSnapshots": snapshots, "actualTarget": target,
                "checks": {"databaseReceiptHashMatches": True, "dispatchMatchesRemoteLedger": True,
                           "upstreamResultHashMatches": True, "targetVersionMatches": True,
                           "sourceContextHashMatches": True, "noInventedEpisodeOrSuccess": True}}
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"status": "PASS", "sessionId": session, "realReadRuns": len(runs), "checks": evidence["checks"]}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--session", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    main(args.session, args.output)
