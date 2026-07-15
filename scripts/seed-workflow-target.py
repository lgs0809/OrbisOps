#!/usr/bin/env python3
"""Repeatable linked data and least-privilege credentials for the real OPS-04 HTTP target."""
from pathlib import Path
import json
import runpy
import secrets
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ENV = ROOT / "deploy/.env.acceptance"
values = dict(line.split("=", 1) for line in ENV.read_text().splitlines() if line and not line.startswith("#"))
if not values.get("OPS_ACCEPTANCE_TARGET_DB_PASSWORD"):
    values["OPS_ACCEPTANCE_TARGET_DB_PASSWORD"] = secrets.token_urlsafe(36)
    with ENV.open("a") as stream:
        stream.write("\nOPS_ACCEPTANCE_TARGET_DB_PASSWORD=" + values["OPS_ACCEPTANCE_TARGET_DB_PASSWORD"] + "\n")
password = values["OPS_ACCEPTANCE_TARGET_DB_PASSWORD"]
assert all(ch.isalnum() or ch in "_-" for ch in password)
query = f"""
USE ops_acceptance_business_a;
CREATE TABLE IF NOT EXISTS ops04_request (
 event_id VARCHAR(36) PRIMARY KEY, service_id VARCHAR(80) NOT NULL, order_id VARCHAR(80),
 observed_at DATETIME(6) NOT NULL, http_status SMALLINT NOT NULL,
 duration_ms DECIMAL(12,3) NOT NULL, sql_duration_ms DECIMAL(12,3) NOT NULL,
 version VARCHAR(40) NOT NULL, sql_text VARCHAR(1000) NOT NULL,
 KEY ix_service_time(service_id,observed_at), FOREIGN KEY(order_id) REFERENCES acceptance_order(order_id));
INSERT INTO acceptance_service VALUES ('ops-acc-a-service-4','ops-acceptance-a','验收慢查询服务 A4','SLOW_SQL','fixture-1')
 ON DUPLICATE KEY UPDATE service_id=service_id;
INSERT INTO acceptance_order VALUES ('ops04-slow-order-1','ops-acc-a-customer-1','ops-acc-a-service-4',
 'ops04-slow-request-1','PAID',19.99,'2026-09-08 12:00:00') ON DUPLICATE KEY UPDATE order_id=order_id;
CREATE USER IF NOT EXISTS 'ops_workflow_target'@'%' IDENTIFIED BY '{password}';
GRANT SELECT ON ops_acceptance_business_a.acceptance_service TO 'ops_workflow_target'@'%';
GRANT SELECT ON ops_acceptance_business_a.acceptance_customer TO 'ops_workflow_target'@'%';
GRANT SELECT ON ops_acceptance_business_a.acceptance_order TO 'ops_workflow_target'@'%';
GRANT INSERT ON ops_acceptance_business_a.ops04_request TO 'ops_workflow_target'@'%';
SELECT JSON_OBJECT('services',(SELECT COUNT(*) FROM acceptance_service),'customers',(SELECT COUNT(*) FROM acceptance_customer),
 'orders',(SELECT COUNT(*) FROM acceptance_order),'slowScenario',(SELECT scenario FROM acceptance_service WHERE service_id='ops-acc-a-service-4'));
"""
result = subprocess.run(["docker", "exec", "-i", "orbisops-acceptance-mysql-1", "sh", "-c",
                         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B --raw'],
                        input=query, text=True, capture_output=True, check=True)
counts = json.loads(result.stdout.strip())
assert counts["slowScenario"] == "SLOW_SQL", "Existing service changed; preserved for review"

mapping = {"settings": {"number_of_shards": 1, "number_of_replicas": 0}, "mappings": {"properties": {
    **{key: {"type": "keyword"} for key in ("trace_id", "project_id", "environment", "service_id", "version", "order_id", "level", "fixture")},
    "@timestamp": {"type": "date"}, "http_status": {"type": "integer"}, "duration_ms": {"type": "double"},
    "sql_duration_ms": {"type": "double"}, "sql_text": {"type": "text"}, "message": {"type": "text"}}}}
for attempt in range(30):
    try:
        with urllib.request.urlopen("http://127.0.0.1:19262/_cluster/health", timeout=2):
            break
    except OSError:
        if attempt == 29:
            raise
        time.sleep(1)
try:
    with urllib.request.urlopen("http://127.0.0.1:19262/ops04-orders/_mapping", timeout=5) as response:
        actual = json.load(response)["ops04-orders"]["mappings"]["properties"]
    assert all(actual.get(key) == value for key, value in mapping["mappings"]["properties"].items()), "Existing ES schema changed; retained"
except urllib.error.HTTPError as error:
    if error.code != 404:
        raise
    request = urllib.request.Request("http://127.0.0.1:19262/ops04-orders", data=json.dumps(mapping).encode(), method="PUT",
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=5) as response:
        assert json.load(response)["acknowledged"]

runpy.run_path(str(ROOT / "scripts/local-acceptance.py"))["compose"]("--profile", "business", "up", "-d", "--build", "workflow-target")
for attempt in range(30):
    try:
        with urllib.request.urlopen("http://127.0.0.1:18262/ready", timeout=2) as response:
            assert json.load(response)["status"] == "ready"
        break
    except OSError:
        if attempt == 29:
            raise
        time.sleep(1)
print(json.dumps({"state": "READY", "counts": counts, "target": "http://127.0.0.1:18262", "metricSource": "real HTTP request measurements",
                  "logsIndex": "ops04-orders", "existingDataPreserved": True}, ensure_ascii=False))
