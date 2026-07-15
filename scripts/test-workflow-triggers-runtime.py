#!/usr/bin/env python3
"""Actual signed alert -> B and internal scheduler -> A acceptance.

Uses normal APIs, published graphs, real MCP receipts and database checkpoints.
Enables only named acceptance fixtures during this bounded test and restores them.
Repeat webhook notifications follow the existing FIRST/SUMMARY/RECOVERY policy;
an outbox delivery success is never used as proof that its workflow succeeded.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse
import copy
import hashlib
import hmac
import json
import runpy
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"
BASE = "/api/v1/admin/task-schedule"
support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
api, token, sql, rows, quoted = [support[key] for key in ("api", "token", "sql", "rows", "quoted")]
inspect = runpy.run_path(str(ROOT / "scripts/test-business-workflow-runtime.py"))["inspect"]
result = {"scope": "local signed webhook and internal scheduler; no model or Landing", "checks": {}}


def until(condition, seconds=80):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            value = condition()
            if value:
                return value
        except RuntimeError as error:
            if "HTTP 429" not in str(error):
                raise
            time.sleep(5)
        time.sleep(2)
    raise AssertionError("Acceptance condition did not become true within the deadline")


def restore(path, body=None):
    # These PUTs are idempotent fixture restoration, never retry arbitrary submits.
    for attempt in range(10):
        try:
            return api(path, "PUT", body, token)
        except RuntimeError as error:
            if "HTTP 429" not in str(error) or attempt == 9:
                raise
            time.sleep(5)


def webhook(payload, secret, valid=True):
    body = json.dumps(payload, ensure_ascii=False).encode()
    timestamp = str(int(time.time()))
    signature = hmac.new(secret.encode(), timestamp.encode() + b"." + body, hashlib.sha256).hexdigest()
    request = urllib.request.Request("http://127.0.0.1:18089/api/v1/admin/ops/alert-triggers/webhook/alertmanager",
        data=body, headers={"Content-Type": "application/json", "X-Ops-Alert-Timestamp": timestamp,
                            "X-Ops-Alert-Signature": signature if valid else "0" * 64,
                            "Authorization": "Bearer " + token})
    with urllib.request.urlopen(request, timeout=30) as response:
        value = json.load(response)
    assert value["code"] == "0000", value
    return value["data"]


def outbox(fingerprint):
    return rows("SELECT JSON_OBJECT('id',id,'dispatchKey',dedup_key,'eventType',event_type,'status',status,"
                "'runId',run_id,'retryCount',retry_count,'request',CAST(request_json AS JSON),'error',error_message) "
                "FROM ai_ops_alert_trigger_outbox WHERE fingerprint=" + quoted(fingerprint) + " ORDER BY id")


def inspect_completed(run):
    until(lambda: sql("SELECT COUNT(*) FROM ai_ops_agent_run WHERE run_id=" + quoted(run)
                      + " AND status IN ('SUCCEEDED','FAILED','CANCELED')") == "1", seconds=150)
    evidence = inspect(run)
    assert evidence["run"]["status"] == "SUCCEEDED", evidence
    return evidence


def executions(schedule_id):
    return api(BASE + "/execution/list?projectId=" + PROJECT + "&scheduleId=" + str(schedule_id) + "&limit=100", token=token)


def check_execution(schedule_id, execution_id, expected_trigger, graph):
    record = until(lambda: item if (item := next((x for x in executions(schedule_id)
                    if x["id"] == execution_id and x.get("endedAt")), None)) else None, seconds=150)
    assert record["status"] == "SUCCESS", record
    assert record["triggerType"] == expected_trigger
    request = json.loads(record["input"])
    assert request["executionStyle"] == "WORKFLOW" and request["agentVersion"] == graph["version"]
    run = request["runId"]
    evidence = inspect_completed(run)
    assert evidence["run"]["hash"] == graph["definitionHash"]
    return {"execution": record, "evidence": evidence}


def main(alert_time, scheduled):
    seed = runpy.run_path(str(ROOT / "scripts/seed-workflow-triggers.py"))["prepare"]()
    result["fixtures"] = seed
    again = runpy.run_path(str(ROOT / "scripts/seed-workflow-triggers.py"))["prepare"]()
    assert seed == again, "Repeated seed changed existing fixture state"
    result["checks"]["idempotentSeed"] = True
    rule, schedule = seed["rule"], seed["schedule"]
    rule_path = "/api/v1/admin/ops/alert-triggers/rules/" + str(rule["id"]) + "/status?status="
    secret = json.loads((ROOT / "deploy/.acceptance-private/triggers.json").read_text())["webhookSecret"]
    fingerprint = "ops04-trigger-" + uuid.uuid4().hex
    incoming = {"fingerprint": fingerprint, "status": "firing", "startsAt": alert_time,
                "labels": {"alertname": "OPS04Acceptance", "severity": "warning", "service": "ops-acc-a-service-2",
                           "fixture": "ops04-trigger", "environment": "acceptance"},
                "annotations": {"summary": 'Actual local 5xx signal; quote " and ${projectId} stay literal'}}
    payload = {"alerts": [incoming]}
    result["fingerprint"] = fingerprint
    try:
        api(rule_path + "1", "PUT", token=token)
        result["rejectedSignature"] = webhook(payload, secret, valid=False)
        assert outbox(fingerprint) == [], "Invalid signature queued a workflow"
        foreign = copy.deepcopy(payload)
        foreign["alerts"][0]["labels"]["environment"] = "production"
        result["unmatchedEnvironment"] = webhook(foreign, secret)
        assert outbox(fingerprint) == []
        result["firstWebhook"] = webhook(payload, secret)
        first = until(lambda: entries[0] if (entries := outbox(fingerprint)) and entries[0].get("runId") else None)
        result["firstRun"] = inspect_completed(first["runId"])
        assert first["eventType"] == "FIRST"
        assert result["firstRun"]["run"]["hash"] == seed["definitions"][0]["definitionHash"]
        assert result["firstRun"]["report"]["status"] == "OBSERVED_ANOMALY"
        assert json.loads(first["request"]["question"])["alertTime"] == alert_time
        assert '${projectId}' in json.loads(first["request"]["question"])["summary"]
        print("PASS actual signed webhook -> published B -> MySQL/MCP evidence", flush=True)

        with ThreadPoolExecutor(max_workers=8) as pool:
            result["concurrentRepeats"] = list(pool.map(lambda _: webhook(payload, secret), range(8)))
        assert len(outbox(fingerprint)) == 1, "Duplicate notifications dispatched extra FIRST runs"
        result["aggregate"] = rows("SELECT JSON_OBJECT('count',occurrence_count,'pending',pending_summary_count,'state',current_state) "
                                   "FROM ai_ops_alert_aggregate WHERE fingerprint=" + quoted(fingerprint))[0]
        assert result["aggregate"]["count"] == 9 and result["aggregate"]["pending"] == 8
        result["checks"]["concurrentDuplicateFirstSuppressed"] = True

        task_id = schedule["id"]
        manual = api(BASE + "/run-now/" + str(task_id) + "?projectId=" + PROJECT, "POST", {}, token)
        result["manualScheduleRun"] = check_execution(task_id, manual, "MANUAL", seed["definitions"][1])
        assert result["manualScheduleRun"]["evidence"]["report"]["status"] == "HEALTHY"
        print("PASS normal manual schedule entry -> published A -> actual healthy metrics", flush=True)

        if scheduled:
            baseline = {item["id"] for item in executions(task_id)}
            enabled = dict(schedule, status=1, cronExpression="*/20 * * * * *")
            api(BASE + "/update-by-id", "PUT", enabled, token)
            ids = until(lambda: values if len(values := [item["id"] for item in executions(task_id)
                        if item["id"] not in baseline and item["triggerType"] == "SCHEDULED"]) >= 2 else None, seconds=70)
            api(BASE + "/status/" + str(task_id) + "?projectId=" + PROJECT + "&status=0", "PUT", token=token)
            result["scheduledRuns"] = [check_execution(task_id, value, "SCHEDULED", seed["definitions"][1]) for value in ids]
            assert all(item["evidence"]["report"]["status"] == "HEALTHY" for item in result["scheduledRuns"])
            print("PASS two actual internal Cron firings with committed workflow results", flush=True)

        last_scan = 0.0
        def summary_ready():
            nonlocal last_scan
            if time.monotonic() - last_scan < 5:
                return None
            last_scan = time.monotonic()
            api("/api/v1/admin/ops/alert-triggers/outbox/process?limit=20", "POST", {}, token)
            return next((item for item in outbox(fingerprint) if item["eventType"] == "SUMMARY" and item.get("runId")), None)
        summary = until(summary_ready, seconds=150)
        result["summaryRun"] = inspect_completed(summary["runId"])
        assert len(outbox(fingerprint)) == 2
        with ThreadPoolExecutor(max_workers=4) as pool:
            result["repeatedOutboxScans"] = list(pool.map(lambda _: api(
                "/api/v1/admin/ops/alert-triggers/outbox/process?limit=20", "POST", {}, token), range(4)))
        assert len(outbox(fingerprint)) == 2
        result["outbox"] = outbox(fingerprint)
        result["checks"]["oneDebouncedSummaryAndNoRedispatch"] = True
        print("PASS 8 repeats -> one durable summary; parallel outbox scans do not redispatch", flush=True)
    finally:
        try:
            restore(rule_path + str(rule["status"]))
        finally:
            restore(BASE + "/update-by-id", schedule)
        result["restored"] = {"ruleStatus": rule["status"], "scheduleStatus": schedule["status"], "cron": schedule["cronExpression"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--alert-time", required=True, help="Center of an actual retained 30-minute fault observation window")
    parser.add_argument("--scheduled", action="store_true", help="Temporarily enable 20-second internal Cron for two actual firings")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        main(args.alert_time, args.scheduled)
        result["status"] = "PASS"
    except Exception as error:
        result["status"] = "FAIL"
        result["error"] = str(error)
        raise
    finally:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
