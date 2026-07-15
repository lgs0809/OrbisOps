#!/usr/bin/env python3
"""Stop only the isolated target, derive three distinct alerts, and verify automatic grouping.

The test monitor emits signed alerts after checking actual Prometheus up=0.
Earlier downstream alerts first investigate independently. A later upstream alert
supplies the anomalous common resource; grouping never waits for human approval.
All child workflows still perform their own bounded read-only evidence collection.
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse
import datetime as dt
import json
import runpy
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "ops-acceptance-a"


def main(output):
    runtime = runpy.run_path(str(ROOT / "scripts/test-workflow-triggers-runtime.py"))
    support = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    fault = runpy.run_path(str(ROOT / "scripts/test-observability-fault-runtime.py"))
    api, token, sql, rows, q = [support[k] for k in ("api", "token", "sql", "rows", "quoted")]
    until, webhook, outbox, inspect = [runtime[k] for k in ("until", "webhook", "outbox", "inspect_completed")]
    seed = runpy.run_path(str(ROOT / "scripts/seed-alert-correlation.py"))["prepare"]()
    again = runpy.run_path(str(ROOT / "scripts/seed-alert-correlation.py"))["prepare"]()
    assert seed == again, "Seed import changed a saved fixture"
    secret = json.loads((ROOT / "deploy/.acceptance-private/triggers.json").read_text())["webhookSecret"]
    rule_path = "/api/v1/admin/ops/alert-triggers/rules/" + str(seed["rule"]["id"]) + "/status?status="
    result = {"status": "FAIL", "seed": seed, "checks": {"idempotentSeed": True}, "groupingIsNotVerifiedRootCause": True}
    accounts = json.loads((ROOT / "deploy/.acceptance-private/users.json").read_text())
    login = runpy.run_path(str(ROOT / "scripts/seed-local-acceptance.py"))["login"]
    viewer, outsider = login(accounts["ops_acceptance_viewer"]), login(accounts["ops_acceptance_b_member"])
    result["checks"]["projectBReadDenied"] = api("/api/v1/user/ops/alert-correlations?projectId=" + PROJECT,
        token=outsider, denied=True)
    assert result["checks"]["projectBReadDenied"]["httpStatus"] == 403
    result["checks"]["viewerTopologyWriteDenied"] = api("/api/v1/admin/ops/alert-correlations/topology", "PUT",
        {"projectId": PROJECT, "environment": "acceptance", "edges": []}, viewer, denied=True)
    assert result["checks"]["viewerTopologyWriteDenied"]["httpStatus"] == 403
    result["beforeResourceState"] = {"outbox": fault["outbox"](), "counters": fault["counters"]()}
    def save():
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    suffix = uuid.uuid4().hex[:10]
    def groups():
        return api("/api/v1/user/ops/alert-correlations?projectId=" + PROJECT + "&environment=acceptance", token=token)
    result["marker"] = suffix
    payloads = []
    try:
        api(rule_path + "1", "PUT", token=token)
        fault["compose"]("--profile", "business", "stop", "workflow-target")
        fault["wait_for"](lambda: fault["up"](0), 30)
        result["targetStopped"] = json.loads(subprocess.check_output(["docker", "inspect", "orbisops-acceptance-workflow-target-1",
                                                "--format", "{{json .State}}"], text=True))
        assert result["targetStopped"]["Running"] is False
        result["prometheusUp"] = 0
        start = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()
        payloads = []
        for service, name, entity in ((2, "OrdersUnavailable", "ops-acc-a-service-2"),
                                     (4, "LatencyCollectionUnavailable", "ops-acc-a-service-4"),
                                     (1, "SharedTargetDown", seed["resource"])):
            # The rule matches the stable prefix; the full title retains a unique test marker.
            payloads.append({"alerts": [{"fingerprint": "correlation-" + suffix + "-" + str(service), "status": "firing", "startsAt": start,
                "labels": {"alertname": "OPS04Correlation" + name, "severity": "warning", "service": "ops-acc-a-service-" + str(service),
                           "entity_id": entity, "fixture": "ops04-correlation", "environment": "acceptance", "test_marker": suffix},
                "annotations": {"summary": "Actual isolated target stopped; monitor up=0; " + name + " " + suffix}}]})
        # Incident titles come from alertname, not the annotation. Identify by source event IDs below.
        event_ids = []
        result["webhooks"] = []
        for payload in payloads[:2]:
            response = webhook(payload, secret); result["webhooks"].append(response)
            event_ids.extend(event["id"] for event in response["events"])
        def selected_groups():
            assigned = rows("SELECT JSON_OBJECT('groupId',d.group_id) FROM ai_ops_alert_correlation_decision d WHERE d.event_id IN ("
                            + ",".join(str(i) for i in event_ids) + ")")
            ids = {item["groupId"] for item in assigned}
            return [group for group in groups() if group["groupId"] in ids or any(
                member["signal"]["eventId"] in event_ids for member in group["members"])]
        initial = until(lambda: value if len(value := selected_groups()) == 2 else None)
        result["beforeUpstreamSignal"] = initial
        response = webhook(payloads[2], secret); result["webhooks"].append(response)
        event_ids.extend(event["id"] for event in response["events"])
        merged = until(lambda: value[0] if len(value := selected_groups()) == 1 and len(value[0]["members"]) == 3 else None)
        result["group"] = merged
        assert merged["rootCauseConfirmed"] is False
        result["checks"]["differentSymptomsRegroupAutomaticallyAfterUpstreamEvidence"] = True
        result["runs"] = []
        for payload in payloads:
            fp = payload["alerts"][0]["fingerprint"]
            entry = until(lambda: value[0] if (value := outbox(fp)) and value[0].get("runId") else None)
            verified = inspect(entry["runId"])
            assert verified["report"]["status"] == "UNREACHABLE"
            result["runs"].append(verified)
        with ThreadPoolExecutor(max_workers=6) as pool:
            result["duplicates"] = list(pool.map(lambda i: webhook(payloads[i % 3], secret), range(6)))
        assert all(len(outbox(payload["alerts"][0]["fingerprint"])) == 1 for payload in payloads)
        result["checks"]["duplicateFirstRunsSuppressed"] = True
        save()
    finally:
        try:
            fault["compose"]("--profile", "business", "start", "workflow-target")
            fault["wait_for"](lambda: fault["get"]("http://127.0.0.1:18262/ready")["status"] == "ready")
            fault["wait_for"](lambda: fault["up"](1), 30)
            result["targetRestored"] = True
            result["recoveryWebhooks"] = []
            for payload in payloads:
                recovered = json.loads(json.dumps(payload))
                recovered["alerts"][0]["status"] = "resolved"
                recovered["alerts"][0]["endsAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
                result["recoveryWebhooks"].append(webhook(recovered, secret))
        finally:
            runtime["restore"](rule_path + str(seed["rule"]["status"]))
            result["ruleRestoredStatus"] = seed["rule"]["status"]
            save()
    final_group = until(lambda: group if (group := next((g for g in groups() if g["groupId"] == result["group"]["groupId"]), None))
                        and all(member["signal"]["recovery"] for member in group["members"]) else None)
    result["groupAfterRecovery"] = final_group
    assert len(final_group["members"]) == 3
    assert all(member["occurrenceCount"] == 4 for member in final_group["members"])
    incident_ids = [member["signal"]["incidentId"] for member in final_group["members"]]
    result["incidentsAfterRecovery"] = rows("SELECT JSON_OBJECT('incidentId',incident_id,'status',status) FROM ai_ops_incident WHERE incident_id IN ("
                                           + ",".join(q(value) for value in incident_ids) + ")")
    assert all(item["status"] not in ("RESOLVED", "CLOSED") for item in result["incidentsAfterRecovery"])
    result["afterResourceState"] = {"outbox": fault["outbox"](), "counters": fault["counters"]()}
    assert result["beforeResourceState"] == result["afterResourceState"]
    result["checks"]["resourceFactsSurvivedAndRecoveryDidNotFabricateResolution"] = True
    result["status"] = "PASS"
    save()
    print(json.dumps({"status": "PASS", "groupId": result["group"]["groupId"], "checks": result["checks"]}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        main(args.output)
    except Exception as error:
        result = json.loads(args.output.read_text()) if args.output.exists() else {}
        result.update(status="FAIL", error=str(error))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
        raise
