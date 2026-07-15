#!/usr/bin/env python3
"""Real HTTP/MySQL concurrency and restart test; writes only the isolated PREPARE target.

Restores its configuration through the same CAS API and retains both receipts.
This is a component test, not evidence of OrbisOps approval or a model-driven Landing.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
VALUES = dict(line.split("=", 1) for line in (ROOT / "deploy/.env.acceptance").read_text().splitlines()
              if line and not line.startswith("#"))


def http(environment, path, body=None, authorized=True):
    port, key = (18263, "OPS_ACCEPTANCE_PREPARE_CONTROL_TOKEN") if environment == "test" else (18262, "OPS_ACCEPTANCE_PROD_CONTROL_TOKEN")
    headers = {"Content-Type": "application/json"}
    if authorized:
        headers["Authorization"] = "Bearer " + VALUES[key]
    request = urllib.request.Request(f"http://127.0.0.1:{port}" + path,
        json.dumps(body).encode() if body is not None else None, headers)
    try:
        response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return {"httpStatus": response.code, "traceId": response.headers.get("X-Trace-Id", ""), "data": json.load(response)}


def run(output):
    output.parent.mkdir(parents=True, exist_ok=True)
    result = {"kind": "REAL_COMPONENT_TEST_ONLY", "status": "NOT_PASSED", "productionWrites": 0}
    state_path = "/control/state/ops-acc-a-service-2"
    before, prod_before = http("test", state_path), http("prod", state_path)
    assert before["httpStatus"] == prod_before["httpStatus"] == 200
    assert before["data"]["version"] == "fixture-1" and before["data"]["scenario"] == "FAULT", "Changed preparation data preserved"
    tag = uuid.uuid4().hex[:8]
    body = {"projectId": "ops-acceptance-a", "service": "ops-acc-a-service-2", "expectedVersion": "fixture-1",
            "version": "test-fixed-" + tag, "scenario": "HEALTHY", "executionKey": "ops08-component-" + tag,
            "deadline": (dt.datetime.now(dt.timezone.utc) + dt.timedelta(minutes=10)).isoformat(), "actor": "ops08-component-test"}
    result.update(before=before, productionBefore=prod_before, request=body)
    try:
        result["ordersBefore"] = [http("test", "/orders/ops-acc-a-order-2") for _ in range(20)]
        assert sum(r["httpStatus"] == 503 for r in result["ordersBefore"]) == 2
        with ThreadPoolExecutor(max_workers=8) as pool:
            responses = list(pool.map(lambda _: http("test", "/control/deploy", body), range(8)))
        result["concurrentResponses"] = responses
        assert all(r["httpStatus"] == 200 and r["data"] == responses[0]["data"] for r in responses)
        result["conflict"] = http("test", "/control/deploy", {**body, "scenario": "SLOW_SQL"})
        assert result["conflict"]["data"].get("error") == "IDEMPOTENCY_CONFLICT"
        result["staleVersion"] = http("test", "/control/deploy", {**body, "executionKey": body["executionKey"] + "-stale"})
        assert result["staleVersion"]["data"].get("error") == "VERSION_CONFLICT"
        result["expired"] = http("test", "/control/deploy", {**body, "deadline": "2000-01-01T00:00:00Z"})
        assert result["expired"]["data"].get("error") == "AUTHORITY_EXPIRED"
        assert http("test", state_path, authorized=False)["httpStatus"] == 403
        assert http("test", "/control/state/ops-acc-a-service-1")["httpStatus"] == 409
        result["ordersAfter"] = [http("test", "/orders/ops-acc-a-order-2") for _ in range(20)]
        assert all(r["httpStatus"] == 200 and r["data"]["order"]["version"] == body["version"] for r in result["ordersAfter"])
        assert all(r["traceId"] for r in result["ordersBefore"] + result["ordersAfter"])
        subprocess.run(["docker", "restart", "orbisops-acceptance-prepare-target-1"], check=True, capture_output=True)
        for attempt in range(30):
            try:
                state = http("test", state_path)
                if state["httpStatus"] == 200:
                    break
            except OSError:
                pass
            time.sleep(1)
        else:
            raise RuntimeError("PREPARE_TARGET_DID_NOT_RECOVER")
        result["replayAfterRestart"] = http("test", "/control/deploy", body)
        assert result["replayAfterRestart"]["data"] == responses[0]["data"]
        assert state["data"]["version"] == body["version"]
        prod_after = http("prod", state_path)
        assert all(prod_after["data"][key] == prod_before["data"][key] for key in ("version", "scenario", "resourceKey"))
        result["productionAfter"] = prod_after
        result["status"] = "PASS"
    finally:
        try:
            # A response can be lost after COMMIT. Restore only our uniquely named version,
            # even when the concurrent request collection raised before returning receipts.
            current = http("test", state_path)
            result["stateBeforeRestore"] = current
            if current["httpStatus"] != 200:
                result["status"] = "RESTORE_STATE_UNKNOWN"
            elif current["data"]["version"] == body["version"]:
                restore = {**body, "expectedVersion": body["version"], "version": before["data"]["version"],
                           "scenario": before["data"]["scenario"], "executionKey": body["executionKey"] + "-restore",
                           "deadline": (dt.datetime.now(dt.timezone.utc) + dt.timedelta(minutes=2)).isoformat()}
                result["restore"] = http("test", "/control/deploy", restore)
                if result["restore"]["httpStatus"] != 200:
                    result["status"] = "RESTORE_FAILED"
            elif any(current["data"][key] != before["data"][key] for key in ("version", "scenario")):
                result["status"] = "CONCURRENT_CHANGE_PRESERVED"
        except Exception as error:
            result["status"] = "RESTORE_STATE_UNKNOWN"
            result["restoreErrorType"] = type(error).__name__
        finally:
            output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"status": result["status"], "concurrentCalls": 8, "productionWrites": 0,
                      "executionKey": body["executionKey"], "restoredThroughApi": result.get("restore", {}).get("httpStatus") == 200}))
    if result["status"] != "PASS":
        raise RuntimeError("Component test incomplete; inspect the retained result")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    run(parser.parse_args().output)
