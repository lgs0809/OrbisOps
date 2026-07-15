#!/usr/bin/env python3
"""Verify deployed OPS-06 rejection paths against real prior UI/API runs and MCP receipts.

This model-disabled fixture cannot invent task boundaries or positive acceptance.
It saves SQL, receipt/peer checks, and separate BLOCKED positive-model status.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse
import json
import re
import runpy
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]


def main(session, output):
    if not re.fullmatch(r"chat-session-[a-z0-9-]+", session):
        raise ValueError("Use a session ID created through the actual chat entry point")
    output.parent.mkdir(parents=True, exist_ok=True)
    runpy.run_path(str(ROOT / "scripts/inspect-task-episode-acceptance.py"))["main"](
        session, output.with_name(output.stem + "-receipts.json"))
    support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token, sql, q, rows = [support[k] for k in ("api", "token", "sql", "quoted", "rows")]
    project = "ops-acceptance-a"
    if support["values"].get("ORBISOPS_AI_MODEL_CALLS_ENABLED") != "false":
        raise RuntimeError("This fixture only verifies model-disabled rejection paths")
    receipt = rows("SELECT JSON_OBJECT('id',r.result_id,'hash',r.output_hash) FROM ai_ops_tool_result r "
                   "JOIN ai_ops_agent_run a ON a.run_id=r.run_id AND a.project_id=r.project_id "
                   "WHERE a.session_id=" + q(session) + " AND a.project_id=" + q(project)
                   + " AND r.source='MCP_REMOTE_TOOL' AND r.status='SUCCEEDED' ORDER BY r.id LIMIT 1")[0]
    missing = "unassigned-task-" + uuid.uuid4().hex[:16]
    endpoint = "/api/v1/user/ops/task-acceptance/" + missing + "?projectId=" + project
    request = {"requestId": "runtime-" + uuid.uuid4().hex, "revision": 1,
               "goalReview": "Verify the real read-only target version; no task boundary has been assigned.",
               "criteria": [{"resultId": receipt["id"], "outputHash": receipt["hash"], "pointer": "/version", "operator": "EQ", "expected": "fixture-1"}]}
    before = sql("SELECT COUNT(*) FROM ai_ops_task_acceptance WHERE project_id=" + q(project))
    checks = {}
    for method in ("GET", "POST"):
        result = api(endpoint, method, request if method == "POST" else None, token, denied=True)
        assert result["httpStatus"] == 403, result
        checks["unassignedTask" + method] = result
    with ThreadPoolExecutor(max_workers=8) as pool:
        repeated = list(pool.map(lambda _: api(endpoint, "POST", request, token, denied=True), range(8)))
    assert all(r["httpStatus"] == 403 for r in repeated)
    checks["eightRejectedRequests"] = repeated
    users = json.loads((ROOT / "deploy/.acceptance-private/users.json").read_text())
    auth = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))
    for name in ("ops_acceptance_viewer", "ops_acceptance_b_member"):
        credentials = next(u for u in users if u["username"] == name) if isinstance(users, list) else users[name]
        denied = api(endpoint, "POST", request, auth["login"](credentials), denied=True)
        assert denied["httpStatus"] == 403, denied
        checks[name] = denied
    invalid = dict(request, criteria=[])
    result = api(endpoint, "POST", invalid, token, denied=True)
    assert result["httpStatus"] == 400, result
    checks["noAssertionsRejected"] = result
    assert sql("SELECT COUNT(*) FROM ai_ops_task_acceptance WHERE project_id=" + q(project)) == before
    assert sql("SELECT COUNT(*) FROM ai_ops_skill_verified_contribution WHERE project_id=" + q(project)) == "0"
    statements = (ROOT / "scripts/fixtures/ops06-task-acceptance-inspect.sql").read_text().replace("'REPLACE_WITH_SESSION_ID'", q(session))
    table = subprocess.run(["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -B --raw orbisops_acceptance'],
        input=statements, text=True, capture_output=True, check=True)
    output.with_name(output.stem + "-inspect.sql").write_text(statements)
    output.with_name(output.stem + "-inspect.tsv").write_text(table.stdout)
    evidence = {"status": "PASS", "scope": "deployed rejection paths, real retained receipts and zero fabricated acceptance",
                "positiveSemanticAndAcceptanceLoop": "BLOCKED_NO_CONFIGURED_LUNA", "sessionId": session,
                "acceptanceCountBeforeAndAfter": int(before), "checks": checks}
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(evidence, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--session", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    main(args.session, args.output)
