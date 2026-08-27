#!/usr/bin/env python3
"""OPS-01 real API + JVM crash/restart acceptance, only in orbisops-acceptance.

Model calls stay disabled: FAILED model runs are expected and never count as a
successful investigation. Fault triggers delay job transitions for one fresh
synthetic session; the script kills only the acceptance backend, removes each
trigger, and waits for the normal persisted lease/replay (no state fabrication).
"""
from pathlib import Path
import json
import runpy
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
seed = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))
api = seed["request"]
token = seed["login"](json.loads((seed["PRIVATE"] / "admin.json").read_text()))
PROJECT = "ops-acceptance-a"
AGENT = PROJECT + "-ops-agent"
PREFIX = "ops01_crash_"


def sql(statement):
    result = subprocess.run(["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
                             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B orbisops_acceptance'],
                            input=statement, text=True, capture_output=True, check=True)
    return result.stdout.strip()


def until(predicate, seconds=45):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(1)
    raise RuntimeError("Acceptance condition did not become true")


def ready():
    try:
        api("/api/v1/setup/status")
        return True
    except (OSError, RuntimeError):
        return False


def new_session(title):
    return api("/api/v1/user/chat/session", "POST", {"projectId": PROJECT, "agentId": AGENT, "title": title}, token)


def chat(session, query):
    response = api(f"/api/v1/user/chat/sessions/{session}/messages", "POST", {
        "projectId": PROJECT, "agentId": AGENT, "query": query,
        "metadata": {"fixture": "synthetic-OPS-01-model-disabled"}}, token)
    if not response.get("metadata", {}).get("failed"):
        raise RuntimeError("Expected disabled-model failure; real model acceptance has not been authorized/configured")
    return response["metadata"]["runId"]


def jobs(session):
    return sql(f"SELECT task_type,status,attempts,result_kind FROM ai_ops_memory_post_processing WHERE session_id='{session}' ORDER BY id;")


def complete(session):
    return sql(f"SELECT COUNT(*) FROM ai_ops_memory_post_processing WHERE session_id='{session}' AND status<>'COMPLETED';") == "0"


def crash(phase):
    session = new_session("OPS-01 合成故障验收 · " + phase)
    trigger = PREFIX + phase
    if sql(f"SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='orbisops_acceptance' AND trigger_name='{trigger}';") != "0":
        raise RuntimeError("Existing fault trigger requires inspection before reuse: " + trigger)
    transition = "OLD.status='PENDING' AND NEW.status='RUNNING'" if phase == "before_claim" else "OLD.status='RUNNING' AND NEW.status='COMPLETED'"
    sql(f"""DELIMITER //
CREATE TRIGGER {trigger} BEFORE UPDATE ON ai_ops_memory_post_processing FOR EACH ROW
BEGIN
 IF NEW.session_id='{session}' AND {transition} THEN
  SET @ops01_fault_pause = SLEEP(30);
 END IF;
END//
DELIMITER ;
""")
    try:
        run = chat(session, "OPS-01 合成宕机验收：我通常偏好先给结论，再列查询证据。")
        until(lambda: int(sql("SELECT COUNT(*) FROM information_schema.processlist WHERE DB='orbisops_acceptance' AND STATE='User sleep';")) > 0)
        before = jobs(session)
        if phase == "before_claim" and "PENDING" not in before:
            raise RuntimeError("Pending capture was not observed before the crash")
        if phase == "after_claim" and "RUNNING" not in before:
            raise RuntimeError("Committed lease was not observed before the crash")
        print(json.dumps({"phase": phase, "session": session, "run": run, "beforeCrash": before}, ensure_ascii=False), flush=True)
        subprocess.run(["docker", "kill", "orbisops-acceptance-backend-1"], check=True, stdout=subprocess.DEVNULL)
    finally:
        sql(f"DROP TRIGGER {trigger};")
        subprocess.run(["docker", "start", "orbisops-acceptance-backend-1"], check=True, stdout=subprocess.DEVNULL)
        until(ready, 90)
    # Five-minute production lease expires naturally. Other acceptance work can proceed while this waits.
    until(lambda: complete(session), 360)
    after = jobs(session)
    if "EXTRACTED" not in after:
        raise RuntimeError("Extraction replay did not persist its actual result")
    sources = sql(f"SELECT COUNT(*) FROM ai_ops_memory_item WHERE session_id='{session}';")
    if sources != "1":
        raise RuntimeError("Extraction replay was lost or duplicated: " + sources)
    print(json.dumps({"phase": phase, "status": "PASS", "session": session, "afterReplay": after, "coldItemCount": 1}, ensure_ascii=False), flush=True)


def conversation_restart():
    session = new_session("OPS-01 连续摘要与重启验收（模型关闭）")
    first = "禁止重启订单服务，只允许读取本机验收库；连接池等待原因尚未确认。"
    chat(session, first)
    for turn in range(2, 23):
        chat(session, f"OPS-01 合成记忆第 {turn} 轮，仅验证历史持久化；本轮模型关闭。")
    until(lambda: complete(session))
    row = sql(f"SELECT last_seq,covered_seq,summary_revision FROM ai_ops_conversation_memory_state WHERE session_id='{session}';")
    before = [int(value) for value in row.split()]
    if before[0] != 44 or before[2] < 2:
        raise RuntimeError("Two successive summaries were not observed: " + row)
    constraint = sql(f"SELECT INSTR(summary_content,'禁止重启订单服务')>0,INSTR(protected_messages_json,'连接池等待原因尚未确认')>0 FROM ai_ops_conversation_memory_state WHERE session_id='{session}';")
    if constraint != "1\t1":
        raise RuntimeError("An early summary/source was lost")
    runpy.run_path(str(ROOT / 'scripts/backend-namespace-lifecycle.py'))['restart_backend']()
    until(ready, 90)
    run = chat(session, "OPS-01 重启后续轮，请保留最初的边界与尚未完成事项。")
    until(lambda: complete(session))
    after = sql(f"SELECT last_seq,covered_seq,summary_revision FROM ai_ops_conversation_memory_state WHERE session_id='{session}';")
    if int(after.split()[0]) != 46:
        raise RuntimeError("Message order reset after a real backend restart")
    bundle = sql(f"SELECT INSTR(bundle_json,'禁止重启订单服务')>0 FROM ai_ops_runtime_context_bundle WHERE session_id='{session}' AND run_id='{run}';")
    if bundle != "1":
        raise RuntimeError("Restart did not reconstruct the actual runtime context from durable sources")
    print(json.dumps({"phase": "conversation_restart", "status": "PASS", "session": session, "run": run,
                      "before": before, "after": after, "runtimeContextContainsEarlyConstraint": True,
                      "modelRuns": "EXPECTED_FAILED_NOT_MODEL_ACCEPTANCE"}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    settings = (ROOT / "deploy/.env.acceptance").read_text()
    if "ORBISOPS_AI_MODEL_CALLS_ENABLED=false" not in settings:
        raise RuntimeError("This fault suite requires disabled model calls")
    crash("before_claim")
    crash("after_claim")
    conversation_restart()
