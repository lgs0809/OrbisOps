#!/usr/bin/env python3
"""OPS-02 isolated API/restart acceptance. Uses normal publication, approval and cancellation commands.

Each execution creates explicitly synthetic agent/session IDs; no existing business
data is replaced. The acceptance backend and its previously running shared-network
peers recover together. Database access is read-only evidence collection. These
deterministic fixtures are not OPS-04 business graphs or model-quality acceptance.
"""
from pathlib import Path
import argparse
import json
import runpy
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"
BASE = "http://127.0.0.1:18089"
seed = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))
api = seed["request"]
token = seed["login"](json.loads((ROOT / "deploy/.acceptance-private/admin.json").read_text()))
sql_queries = []
results = []


def record(value):
    results.append(value)
    print(json.dumps(value, ensure_ascii=False), flush=True)


def sql(statement):
    sql_queries.append(statement.rstrip(';') + ';')
    result = subprocess.run(["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
                             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B orbisops_acceptance'],
                            input=statement, text=True, capture_output=True, check=True)
    return result.stdout.strip()


def until(predicate, seconds=90):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(1)
    raise RuntimeError("Acceptance condition timed out")


def ready():
    try:
        api("/api/v1/setup/status")
        return True
    except (OSError, RuntimeError):
        return False


def facts(run):
    return json.loads(sql("SELECT JSON_OBJECT('status',status,'version',agent_version,'definitionHash',agent_definition_hash,"
                          "'epoch',fencing_token,'cancelRequested',cancel_requested,'error',error_message) "
                          "FROM ai_ops_agent_run WHERE run_id='" + run + "';"))


def start(agent, query):
    session = api("/api/v1/user/chat/session", "POST", {"projectId": PROJECT,
                  "agentId": agent, "agentVersion": 1, "title": "OPS-02 合成引擎自动回归"}, token)
    response = api(f"/api/v1/user/chat/sessions/{session}/messages", "POST", {
        "projectId": PROJECT, "query": query, "mode": "AGENT", "engine": "GRAPH",
        "agentDefinitionId": agent, "agentVersion": 1,
        "metadata": {"executionType": "WORKFLOW", "fixture": "synthetic-OPS-02-engine-contract"}}, token, timeout=240)
    return response["metadata"]["runId"]


def approval_path(run, action=""):
    return f"/api/v1/agent/chat/runs/{run}/workflow-approval{action}?projectId={PROJECT}"


def approval(run, node):
    def current():
        value = api(approval_path(run), token=token)
        state = facts(run)["status"]
        if state in ("FAILED", "CANCELED", "SUCCEEDED"):
            raise RuntimeError("Expected approval wait, got " + json.dumps(facts(run)))
        return value if value.get("nodeId") == node and value.get("status") == "WAITING" else None
    return until(current)


def rejected(path, body, statuses=(400, 403, 409)):
    request = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method="POST",
                                    headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
    try:
        urllib.request.urlopen(request, timeout=45)
        raise AssertionError("Negative request was accepted")
    except urllib.error.HTTPError as error:
        assert error.code in statuses, error.code
        payload = json.loads(error.read())
        print(json.dumps({"expectedRejection": error.code, "reason": payload.get("info")}, ensure_ascii=False), flush=True)


def decide(run, record):
    api(approval_path(run, "/decision"), "POST", {"approvalId": record["approvalId"], "decision": "APPROVE"}, token)


def checkpoints(run):
    return [json.loads(line) for line in sql("SELECT checkpoint_json FROM ai_ops_agent_run_checkpoint "
                                            f"WHERE run_id='{run}' AND checkpoint_type LIKE 'WORKFLOW_%' ORDER BY checkpoint_seq;").splitlines()]


def frozen_approval_and_cancel():
    prepare = runpy.run_path(str(ROOT / "scripts/seed-workflow-acceptance.py"))["prepare"]
    agent = "ops-acceptance-runtime-" + uuid.uuid4().hex[:10]
    v1 = prepare(1, agent)
    run = start(agent, "OPS-02 自动合成审批：两个独立确认，无业务写入。")
    first = approval(run, "review-one")
    before = facts(run)
    v2 = prepare(2, agent)
    assert before["version"] == 1 and before["definitionHash"] == v1["definitionHash"]
    assert v2["definitionHash"] != v1["definitionHash"]
    rejected(approval_path(run, "/decision"), {"decision": "APPROVE"}, (400,))
    rejected(approval_path(run, "/decision"), {"decision": "APPROVE", "approvalId": "not-reviewed"}, (403,))
    recovery = runpy.run_path(str(ROOT / 'scripts/backend-namespace-lifecycle.py'))['restart_backend']()
    until(ready)
    assert api(approval_path(run), token=token) == first
    assert facts(run) == before
    decide(run, first)
    second = approval(run, "review-two")
    assert second["approvalId"] != first["approvalId"]
    rejected(approval_path(run, "/decision"), {"decision": "APPROVE", "approvalId": first["approvalId"]}, (403,))
    assert api(approval_path(run), token=token) == second
    decide(run, second)
    until(lambda: facts(run)["status"] in ("SUCCEEDED", "FAILED"))
    final = facts(run)
    assert final["status"] == "SUCCEEDED", final
    assert final["version"] == 1 and final["definitionHash"] == before["definitionHash"] and final["epoch"] == 3
    decisions = sql(f"SELECT node_id,status FROM ai_ops_workflow_approval WHERE run_id='{run}' ORDER BY requested_at;")
    assert decisions.splitlines() == ["review-one\tAPPROVED", "review-two\tAPPROVED"]
    states = [item["state"] for item in checkpoints(run) if "state" in item]
    assert len({item["planHash"] for item in states}) == 1
    assert len({item["contextBundleHash"] for item in states}) == 1
    assert states[-1]["status"] == "SUCCEEDED"
    assert "nodeValidation:review-one:2" in states[-1]["variables"]
    assert "nodeValidation:review-two:2" in states[-1]["variables"]
    record({"case": "restart-frozen-version-stale-approval", "status": "PASS", "run": run,
            "facts": final, "decisions": decisions, "planHash": states[-1]["planHash"], "namespaceRecovery": recovery})

    canceled = start(agent, "OPS-02 自动合成取消：等待审批时取消，无业务写入。")
    pending = approval(canceled, "review-one")
    assert api(f"/api/v1/agent/chat/runs/{canceled}/cancel?projectId={PROJECT}", "POST", {}, token)
    until(lambda: facts(canceled)["status"] == "CANCELED")
    rejected(approval_path(canceled, "/decision"), {"decision": "APPROVE", "approvalId": pending["approvalId"]})
    assert facts(canceled)["status"] == "CANCELED" and facts(canceled)["cancelRequested"] == 1
    record({"case": "cancel-wait-without-worker", "status": "PASS", "run": canceled,
            "facts": facts(canceled)})


def output_contracts():
    definition = json.loads((ROOT / "scripts/fixtures/workflow-approval.json").read_text())
    agent = "ops-acceptance-contract-" + uuid.uuid4().hex[:10]
    definition.update(agentId=agent, name="OPS-02 输出契约合成回归", description="纯确定性输出校验，不是业务健康检查。")
    start_node, end_node = definition["nodes"][0], definition["nodes"][-1]
    start_node["config"]["outputContract"] = {
        "format": "JSON", "schema": {"type": "object", "required": ["result", "sampleCount", "p95ms", "errorRate"],
        "properties": {"result": {"enum": ["HEALTHY", "UNHEALTHY", "INSUFFICIENT_DATA"]},
                       "sampleCount": {"type": "integer", "minimum": 0}, "p95ms": {"type": "number", "minimum": 0},
                       "errorRate": {"type": "number", "minimum": 0, "maximum": 1}}},
        "rule": "nodeOutput.result != 'HEALTHY' || nodeOutput.sampleCount >= 100 && nodeOutput.p95ms <= 1000 && nodeOutput.errorRate <= 0.01"}
    definition["nodes"] = [start_node, end_node]
    definition["edges"] = [{"from": "start", "to": "end", "conditionType": "always", "condition": "always"}]
    saved = api("/api/v1/admin/ops-agents/drafts", "POST", definition, token)
    path = f"/api/v1/admin/ops-agents/{agent}/versions/{saved['version']}"
    for action in ("validate", "publish"):
        api(path + "/" + action, "POST", {}, token)
    healthy = {"result": "HEALTHY", "sampleCount": 100, "p95ms": 999, "errorRate": 0.01}
    cases = [("valid", healthy, True), ("insufficient-claimed-healthy", dict(healthy, sampleCount=99), False),
             ("schema-drift", dict(healthy, sampleCount="100"), False),
             ("cross-project-output", dict(healthy, projectId="ops-acceptance-b"), False),
             ("version-drift", dict(healthy, agentVersion=2), False), ("malformed", "success without evidence", False)]
    for name, payload, passes in cases:
        run = start(agent, json.dumps(payload) if isinstance(payload, dict) else payload)
        state = facts(run)
        assert state["status"] == ("SUCCEEDED" if passes else "FAILED"), (name, state)
        after_count = sql(f"SELECT COUNT(*) FROM ai_ops_agent_run_checkpoint WHERE run_id='{run}' AND checkpoint_type='WORKFLOW_NODE_AFTER';")
        assert after_count == ("2" if passes else "0"), (name, after_count)
        record({"case": name, "status": "PASS", "expectedRunStatus": state["status"], "run": run,
                "completedNodeCheckpoints": after_count})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('Use a fresh evidence path')
    report = {"status": "FAIL", "fixture": "synthetic-engine-contract-not-business-or-model-quality", "results": results}
    try:
        frozen_approval_and_cancel()
        output_contracts()
        report['status'] = 'PASS'
    except Exception as error:
        report['error'] = type(error).__name__ + ': ' + str(error)
        raise
    finally:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        args.output.with_suffix('.sql').write_text('-- Actual read-only run, approval and checkpoint queries.\n' + '\n'.join(sql_queries) + '\n')
