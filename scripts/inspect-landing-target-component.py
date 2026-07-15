#!/usr/bin/env python3
"""Read-only verification of retained component HTTP evidence against actual MySQL facts."""
import argparse
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(source, output):
    evidence = json.loads(source.read_text())
    if evidence.get("kind") != "REAL_COMPONENT_TEST_ONLY" or evidence.get("status") != "PASS":
        raise ValueError("A completed component result is required")
    h = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    q = h["quoted"]
    orders = evidence["ordersBefore"] + evidence["ordersAfter"]
    ids = [r["traceId"] for r in orders]
    assert len(ids) == len(set(ids)) == 40
    key = evidence["request"]["executionKey"]
    queries = {
        "requests": "SELECT JSON_OBJECT('traceId',event_id,'status',http_status,'version',version) "
                    "FROM ops_acceptance_business_prepare.ops04_request WHERE event_id IN ("
                    + ",".join(map(q, ids)) + ") ORDER BY event_id",
        "receipts": "SELECT JSON_OBJECT('key',execution_key,'result',CAST(result_json AS JSON)) "
                    "FROM ops_acceptance_business_prepare.ops08_deployment_receipt WHERE execution_key IN ("
                    + q(key) + "," + q(key + "-restore") + ") ORDER BY created_at",
        "production": "SELECT JSON_OBJECT('receipts',COUNT(*)) "
                      "FROM ops_acceptance_business_a.ops08_deployment_receipt WHERE execution_key IN ("
                      + q(key) + "," + q(key + "-restore") + ")",
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix(".sql").write_text(";\n".join(queries.values()) + ";\n")
    result = {"status": "NOT_PASSED", "source": source.name, "executionKey": key,
              "kind": "REAL_COMPONENT_SQL_CROSSCHECK_ONLY"}
    try:
        result.update({name: h["rows"](query) for name, query in queries.items()})
        actual = {r["traceId"]: r for r in result["requests"]}
        assert len(actual) == 40
        for response in orders:
            row = actual[response["traceId"]]
            assert row["status"] == response["httpStatus"]
            expected_version = (evidence["before"]["data"]["version"] if response in evidence["ordersBefore"]
                                else evidence["request"]["version"])
            assert row["version"] == expected_version
        receipts = {r["key"]: r["result"] for r in result["receipts"]}
        assert len(receipts) == 2
        assert receipts[key] == evidence["concurrentResponses"][0]["data"]
        assert receipts[key + "-restore"] == evidence["restore"]["data"]
        assert result["production"] == [{"receipts": 0}]
        result["status"] = "PASS"
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"status": result["status"], "actualRequests": len(result["requests"]),
                      "changeReceipts": len(result["receipts"]), "productionWrites": 0}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    inspect(args.source, args.output)
