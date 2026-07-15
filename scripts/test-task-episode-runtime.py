#!/usr/bin/env python3
"""Real API -> persisted chat -> MCP -> durable pending episode queue; restart and ACL verification.

Requires the isolated model-disabled stack. Three real read-only workflows run
in one new session; missing Luna keeps classification pending and ordered.
This is NOT a positive semantic model-classification test.
"""
from pathlib import Path
import argparse
import hashlib
import json
import runpy
import subprocess
import time
import uuid
import urllib.parse

ROOT = Path(__file__).resolve().parents[1]


def main(output, restart=True):
    support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token, sql, q, rows = [support[k] for k in ("api", "token", "sql", "quoted", "rows")]
    seed = runpy.run_path(str(ROOT / "scripts/seed-task-episode-acceptance.py"))["prepare"]
    first, second = seed(), seed()
    assert first == second, "Seed changed existing workflow/session"
    project = first["projectId"]
    if support["values"].get("ORBISOPS_AI_MODEL_CALLS_ENABLED") != "false":
        raise RuntimeError("This fixture only asserts model-disabled behavior; do not disable a user's configured model")
    result = {"status": "FAIL", "modelSemanticLoop": "BLOCKED_NO_CONFIGURED_LUNA", "seed": first, "runs": [], "checks": {}}

    def save():
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")

    def wait(check, seconds=90):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            value = check()
            if value:
                return value
            time.sleep(1)
        raise AssertionError("OPS-05 condition timed out")

    session = api("/api/v1/user/chat/session", "POST", {"projectId": project, "agentId": first["agentId"],
        "agentVersion": first["agentVersion"], "title": "OPS-05 实际跨轮验收 · " + uuid.uuid4().hex[:8]}, token)
    result["sessionId"] = session
    result["url"] = f"http://127.0.0.1:3302/chat?projectId={project}&sessionId={session}"
    save()
    queries = ["读取当前目标版本，保留后续核对依据。", "继续上一轮，只读复核同一目标版本。", "另一个任务：读取目标版本，记录一次独立核对。"]
    for question in queries:
        end = int(time.time()) - 2
        query = json.dumps({"question": question, "window": {"projectId": project, "environment": "acceptance",
            "serviceId": "ops-acc-a-service-1", "startEpoch": end - 600, "endEpoch": end}}, ensure_ascii=False)
        response = api(f"/api/v1/user/chat/sessions/{session}/messages", "POST", {"projectId": project,
            "query": query, "mode": "AGENT", "engine": "GRAPH", "agentDefinitionId": first["agentId"],
            "agentVersion": first["agentVersion"], "metadata": {"executionType": "WORKFLOW", "fixture": "OPS-05-real-read-model-disabled"}}, token)
        run = response["metadata"]["runId"]
        status = wait(lambda: (r if r["status"] in ("SUCCEEDED", "FAILED", "CANCELED") else None)
                      if (r := support["facts"](run)) else None)
        assert status["status"] == "SUCCEEDED", status
        receipts = rows("SELECT JSON_OBJECT('resultId',result_id,'hash',output_hash,'source',source,'status',status,'output',full_output) "
                        "FROM ai_ops_tool_result WHERE project_id=" + q(project) + " AND run_id=" + q(run) + " AND source='MCP_REMOTE_TOOL' ORDER BY id")
        assert len(receipts) == 1 and receipts[0]["status"] == "SUCCEEDED", receipts
        assert hashlib.sha256(receipts[0]["output"].encode()).hexdigest() == receipts[0]["hash"]
        assert "fixture-1" in receipts[0]["output"], "The real target version must be present in the persisted MCP receipt"
        result["runs"].append({"runId": run, "status": status, "receipts": receipts})
        save()
    endpoint = "/api/v1/user/ops/task-episodes?" + urllib.parse.urlencode({"projectId": project, "sessionId": session})

    def pending():
        value = api(endpoint, token=token)
        return value if len(value["turns"]) == 3 and value["turns"][0]["status"] == "WAITING_MODEL" else None

    view = wait(pending, 150)
    assert [t["status"] for t in view["turns"]] == ["WAITING_MODEL", "WAITING_TURN", "WAITING_TURN"], view
    assert all(t["attempts"] == 0 and not t["episodeId"] for t in view["turns"])
    assert not view["episodes"] and not view["artifacts"] and not view["jobs"]
    assert view["turns"][0]["contextFidelity"] == "FROZEN_MAIN_CONTEXT"
    result["beforeRestart"] = view
    context = rows("SELECT JSON_OBJECT('runId',run_id,'hash',snapshot_hash,'snapshot',snapshot_json) FROM ai_ops_task_episode_context "
                   "WHERE session_id=" + q(session) + " ORDER BY run_id")
    assert len(context) == 3
    for item in context:
        assert hashlib.sha256(item["snapshot"].encode()).hexdigest() == item["hash"]
    result["contextSnapshots"] = [{k: item[k] for k in ("runId", "hash")} for item in context]
    chat_query = "SELECT message_seq,role,content,capture_key FROM ai_ops_chat_message WHERE session_id=" + q(session) + " ORDER BY message_seq"
    chat_before = sql(chat_query)
    assert sql("SELECT COUNT(*) FROM ai_ops_chat_message WHERE session_id=" + q(session)) == "6"
    result["checks"]["threeRealReadRunsAndImmutableMainContext"] = True
    result["checks"]["noModelCallsNoInventedBoundariesAndNoSkipping"] = True
    result["checks"]["seedRepeatIsIdempotent"] = True
    save()

    users = json.loads((ROOT / "deploy/.acceptance-private/users.json").read_text())
    user_api = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))
    result["access"] = []
    for name in ("ops_acceptance_viewer", "ops_acceptance_b_member"):
        credentials = next(u for u in users if u["username"] == name) if isinstance(users, list) else users[name]
        user_token = user_api["login"](credentials)
        denied = api(endpoint, token=user_token, denied=True)
        assert denied["httpStatus"] == 403, denied
        result["access"].append({"username": name, "result": denied})
    result["checks"]["otherUserAndOtherProjectCannotReadSession"] = True
    assert api("/api/v1/admin/ops/task-episodes/sweep?projectId=" + project, "POST", {}, token) >= 0
    assert sql(chat_query) == chat_before

    if restart:
        compose = runpy.run_path(str(ROOT / "scripts/local-acceptance.py"))["compose"]
        before = subprocess.check_output(["docker", "inspect", "orbisops-acceptance-backend-1", "--format", "{{.State.StartedAt}}"], text=True).strip()
        try:
            compose("--profile", "business", "stop", "mcp-acceptance", "observability-mcp")
            compose("restart", "backend")
        finally:
            try:
                compose("start", "backend")
                def ready():
                    try:
                        return api("/api/v1/setup/status") is not None
                    except (RuntimeError, OSError):
                        return False
                wait(ready)
            finally:
                compose("--profile", "business", "up", "-d", "--no-deps", "--no-build", "mcp-acceptance", "observability-mcp")
        after = subprocess.check_output(["docker", "inspect", "orbisops-acceptance-backend-1", "--format", "{{.State.StartedAt}}"], text=True).strip()
        assert before != after
        time.sleep(12)
        result["restart"] = {"before": before, "after": after}
        result["afterRestart"] = api(endpoint, token=token)
        stable = lambda value: [(t["turnSeq"], t["sourceRunRef"], t["episodeId"], t["inputHash"], t["attempts"]) for t in value["turns"]]
        assert stable(view) == stable(result["afterRestart"])
        assert sql(chat_query) == chat_before, "The classifier or restart changed normal chat"
        result["checks"]["restartPreservesPendingPrefixSourcesAndChat"] = True
    result["status"] = "PASS"
    save()
    print(json.dumps({"status": result["status"], "modelSemanticLoop": result["modelSemanticLoop"], "checks": result["checks"], "url": result["url"]}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--no-restart", action="store_true")
    args = parser.parse_args()
    try:
        main(args.output, not args.no_restart)
    except Exception as error:
        value = json.loads(args.output.read_text()) if args.output.exists() else {}
        value.update(status="FAIL", error=str(error))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
        raise
