#!/usr/bin/env python3
"""Exercise the unaccepted-task evolution boundary using a real Chat/Workbench run.

Eight repeat requests retain one job/patch. No synthetic task success or candidate is imported.
Reusing the same run is idempotent. This test does not exercise positive model authoring.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse
import json
import re
import runpy
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]


def main(run_id, output):
    if not re.fullmatch(r"chat-chat-session-[a-z0-9-]+", run_id):
        raise ValueError("Use a real canonical Chat/Workbench run ID")
    support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token, sql, q, rows = [support[k] for k in ("api", "token", "sql", "quoted", "rows")]
    run = rows("SELECT JSON_OBJECT('runId',run_id,'sessionId',session_id,'projectId',project_id,'agentId',agent_id,'status',status) "
               "FROM ai_ops_agent_run WHERE run_id=" + q(run_id))[0]
    assert run["projectId"] == "ops-acceptance-a" and run["status"] == "SUCCEEDED", run
    assert sql("SELECT COUNT(*) FROM ai_ops_task_acceptance WHERE project_id=" + q(run["projectId"])
               + " AND (source_run_id=" + q(run_id) + " OR episode_id IN (SELECT episode_id FROM ai_ops_task_episode_turn "
               + "WHERE source_run_ref=" + q(run_id) + " AND project_id=" + q(run["projectId"]) + "))") == "0", "This fixture requires an unaccepted real task"
    output.parent.mkdir(parents=True, exist_ok=True)
    runpy.run_path(str(ROOT / "scripts/inspect-task-episode-acceptance.py"))["main"](
        run["sessionId"], output.with_name(output.stem + "-receipts.json"))
    endpoint = "/api/v1/admin/ops/skill-evolver/jobs"
    request = {k: run[k] for k in ("runId", "sessionId", "projectId", "agentId")}
    request["triggerReason"] = "USER_EXPLICIT_REMEMBER"
    with ThreadPoolExecutor(max_workers=8) as pool:
        created = list(pool.map(lambda _: api(endpoint, "POST", request, token), range(8)))
    job_ids = {r["jobId"] for r in created}
    assert len(job_ids) == 1, created
    job_id = next(iter(job_ids))
    wrong_scope = api(endpoint, "POST", dict(request, projectId="ops-acceptance-b"), token, denied=True)
    assert wrong_scope["httpStatus"] == 400, wrong_scope
    users = json.loads((ROOT / "deploy/.acceptance-private/users.json").read_text())
    auth = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))
    credentials = next(u for u in users if u["username"] == "ops_acceptance_viewer") if isinstance(users, list) else users["ops_acceptance_viewer"]
    denied = api(endpoint, "POST", request, auth["login"](credentials), denied=True)
    assert denied["httpStatus"] == 403, denied
    with ThreadPoolExecutor(max_workers=8) as pool:
        scans = list(pool.map(lambda _: api(endpoint + "/run-once", "POST", {}, token), range(8)))
    assert not any(r.get("reason") == "WORKER_DISABLED" for scan in scans for r in scan), scans
    deadline = time.monotonic() + 20
    while True:
        job = api(endpoint + "/" + job_id, "GET", token=token)
        if job["status"] == "SKIPPED":
            break
        if time.monotonic() > deadline:
            raise AssertionError(job)
        time.sleep(0.5)
    patches = api("/api/v1/admin/ops/skill-evolver/patches?jobId=" + job_id, "GET", token=token)
    assert len(patches) == 1 and patches[0]["decision"] == "SKIP_TASK_OUTCOME_UNVERIFIED", patches
    assert job["attempts"] == 1 and job["acceptedSourceId"] == "" and job["leaseUntilMillis"] == 0, job
    assert sql("SELECT COUNT(*) FROM ai_ops_skill_evolution_source WHERE run_id=" + q(run_id)) == "0"
    assert sql("SELECT COUNT(*) FROM ai_ops_skill_patch_candidate WHERE source_run_id=" + q(run_id)) == "0"
    statements = (ROOT / "scripts/fixtures/ops06-evolution-inspect.sql").read_text().replace("'REPLACE_WITH_RUN_ID'", q(run_id))
    table = subprocess.run(["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -B --raw orbisops_acceptance'],
        input=statements, text=True, capture_output=True, check=True)
    output.with_name(output.stem + "-inspect.sql").write_text(statements)
    output.with_name(output.stem + "-inspect.tsv").write_text(table.stdout)
    result = {"status": "PASS", "scope": "real receipts, unaccepted-task boundary, eight-way enqueue/scan, ACL and durable audit",
              "positiveModelAuthoring": "NOT_EXERCISED_BY_THIS_TEST", "run": run, "job": job, "patches": patches,
              "duplicateRequests": len(created), "concurrentScans": scans, "wrongScope": wrong_scope, "viewerDenied": denied}
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"status": "PASS", "jobId": job_id, "patches": len(patches), "sourceCount": 0, "candidateCount": 0}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    main(args.run, args.output)
