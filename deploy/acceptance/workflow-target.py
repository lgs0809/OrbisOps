#!/usr/bin/env python3
"""Isolated order read service with real MySQL calls, durable request facts and ES projection.

Scenario faults are explicit synthetic fixture behavior. All timestamps and measured latencies
come from actual HTTP requests; no health conclusions or fabricated request counts are seeded.
"""
import collections
import datetime
import json
import os
import re
import sqlite3
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from deployment_control import DeploymentControl

PROJECT = "ops-acceptance-a"
DATABASE = os.environ.get("TARGET_DATABASE", "ops_acceptance_business_a")
DB_USER = os.environ.get("TARGET_DATABASE_USER", "ops_workflow_target")
ENVIRONMENT = os.environ.get("TARGET_ENVIRONMENT", "acceptance")
LOG_INDEX = os.environ.get("TARGET_LOG_INDEX", "ops04-orders")
if DATABASE not in ("ops_acceptance_business_a", "ops_acceptance_business_prepare") or ENVIRONMENT not in ("acceptance", "test"):
    raise RuntimeError("ISOLATED_TARGET_SCOPE_REQUIRED")
STATE = "/state/target.sqlite"
LOCK = threading.Lock()
BUCKETS = (0.05, 0.1, 0.25, 0.5, 0.75, 1.0, 1.5, 2.5, 5.0)
COUNTERS = {}
ORDERS = {}


def sql(query):
    result = subprocess.run(["mysql", "-h", "mysql", "-u", DB_USER, "--default-character-set=utf8mb4",
                             "--batch", "--raw", "--skip-column-names", DATABASE], input=query, text=True,
                            capture_output=True, timeout=5, env={**os.environ, "MYSQL_PWD": os.environ["TARGET_MYSQL_PASSWORD"]})
    if result.returncode:
        raise RuntimeError("TARGET_DATABASE_QUERY_FAILED")
    return result.stdout.strip()


def quoted(value):
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def local():
    conn = sqlite3.connect(STATE, timeout=5)
    conn.row_factory = sqlite3.Row
    return conn


with local() as conn:
    conn.executescript("""PRAGMA journal_mode=WAL;
      CREATE TABLE IF NOT EXISTS events(id TEXT PRIMARY KEY, event_json TEXT NOT NULL,
      mysql_done INTEGER NOT NULL DEFAULT 0, es_done INTEGER NOT NULL DEFAULT 0);""")


def accumulate(event):
    key = (event["service_id"], event["version"])
    with LOCK:
        counter = COUNTERS.setdefault(key, {"count": 0, "errors": 0, "sum": 0., "buckets": [0] * len(BUCKETS)})
        counter["count"] += 1
        counter["errors"] += event["http_status"] >= 500
        seconds = event["duration_ms"] / 1000.
        counter["sum"] += seconds
        for index, upper in enumerate(BUCKETS):
            counter["buckets"][index] += seconds <= upper


with local() as conn:
    for row in conn.execute("SELECT event_json FROM events"):
        accumulate(json.loads(row["event_json"]))


def project_events():
    while True:
        with local() as conn:
            pending = conn.execute("SELECT * FROM events WHERE mysql_done=0 OR es_done=0 LIMIT 50").fetchall()
        for row in pending:
            event = json.loads(row["event_json"])
            if not row["mysql_done"]:
                try:
                    order = "NULL" if not event["order_id"] else quoted(event["order_id"])
                    sql("INSERT IGNORE INTO ops04_request(event_id,service_id,order_id,observed_at,http_status,duration_ms,sql_duration_ms,version,sql_text) VALUES(" +
                        ",".join((quoted(event["trace_id"]), quoted(event["service_id"]), order,
                                  quoted(event["@timestamp"].replace("T", " ").replace("Z", "")), str(event["http_status"]),
                                  str(event["duration_ms"]), str(event["sql_duration_ms"]), quoted(event["version"]), quoted(event["sql_text"]))) + ")")
                    with local() as conn:
                        conn.execute("UPDATE events SET mysql_done=1 WHERE id=?", (row["id"],))
                except (OSError, RuntimeError, subprocess.TimeoutExpired):
                    pass
            if not row["es_done"]:
                try:
                    request = urllib.request.Request("http://elasticsearch-acceptance:9200/" + LOG_INDEX + "/_create/" + row["id"],
                                                     data=row["event_json"].encode(), method="PUT", headers={"Content-Type": "application/json"})
                    with urllib.request.urlopen(request, timeout=3) as response:
                        response.read()
                    done = True
                except urllib.error.HTTPError as error:
                    done = error.code == 409
                except OSError:
                    done = False
                if done:
                    with local() as conn:
                        conn.execute("UPDATE events SET es_done=1 WHERE id=?", (row["id"],))
        time.sleep(0.25 if pending else 1)


def metrics():
    lines = ["# TYPE ops04_http_requests_total counter", "# TYPE ops04_http_errors_total counter",
             "# TYPE ops04_http_request_duration_seconds histogram"]
    with LOCK:
        for (service, version), value in sorted(COUNTERS.items()):
            labels = f'project_id="{PROJECT}",environment="{ENVIRONMENT}",service_id="{service}",version="{version}",route="/orders/id"'
            lines += [f'ops04_http_requests_total{{{labels}}} {value["count"]}',
                      f'ops04_http_errors_total{{{labels}}} {value["errors"]}',
                      f'ops04_http_request_duration_seconds_count{{{labels}}} {value["count"]}',
                      f'ops04_http_request_duration_seconds_sum{{{labels}}} {value["sum"]}']
            for index, upper in enumerate(BUCKETS):
                lines.append(f'ops04_http_request_duration_seconds_bucket{{{labels},le="{upper}"}} {value["buckets"][index]}')
            lines.append(f'ops04_http_request_duration_seconds_bucket{{{labels},le="+Inf"}} {value["count"]}')
    return "\n".join(lines) + "\n"


control = DeploymentControl(sql, quoted, PROJECT, os.environ.get("TARGET_CONTROL_TOKEN", ""),
                            os.environ.get("TARGET_CONTROL_SERVICES", "").split(","))


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def send(self, status, body, content_type="application/json", trace=""):
        data = body.encode() if isinstance(body, str) else json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        if trace:
            self.send_header("X-Trace-Id", trace)
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if self.path != "/control/deploy":
            return self.send(404, {"error": "route_not_found"})
        try:
            size = int(self.headers.get("Content-Length", 0))
            if not 0 < size <= 8192:
                raise ValueError("INVALID_BODY_SIZE")
            body = json.loads(self.rfile.read(size))
        except (ValueError, TypeError):
            return self.send(400, {"error": "invalid_request"})
        return control.handle(self, body)

    def do_GET(self):
        if self.path.startswith("/control/state/"):
            return control.handle(self)
        if self.path == "/metrics":
            return self.send(200, metrics(), "text/plain; version=0.0.4")
        if self.path == "/ready":
            try:
                sql("SELECT 1")
                return self.send(200, {"status": "ready", "projectId": PROJECT})
            except (OSError, RuntimeError, subprocess.TimeoutExpired):
                return self.send(503, {"status": "database_unavailable"})
        if self.path == "/version":
            try:
                data = sql("SELECT JSON_OBJECT('serviceId',service_id,'version',version,'scenario',scenario) FROM acceptance_service WHERE project_id=" + quoted(PROJECT))
                return self.send(200, {"projectId": PROJECT, "services": [json.loads(line) for line in data.splitlines()]})
            except (OSError, RuntimeError, subprocess.TimeoutExpired):
                return self.send(503, {"status": "database_unavailable"})
        match = re.fullmatch(r"/orders/([A-Za-z0-9_-]{1,80})", self.path)
        if not match:
            return self.send(404, {"error": "route_not_found"})
        order_id = match.group(1)
        started = time.monotonic()
        trace = str(uuid.uuid4())
        observed = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")
        query = ("SELECT JSON_OBJECT('orderId',o.order_id,'amount',o.amount,'status',o.status,'customerId',o.customer_id,"
                 "'customerTier',c.tier,'serviceId',s.service_id,'scenario',s.scenario,'version',s.version) "
                 "FROM acceptance_order o JOIN acceptance_customer c ON c.customer_id=o.customer_id "
                 "JOIN acceptance_service s ON s.service_id=o.service_id WHERE s.project_id=" + quoted(PROJECT) + " AND o.order_id=" + quoted(order_id))
        status, sql_ms, service, version, sql_text = 503, 0., "unresolved", "unknown", "SELECT order JOIN customer JOIN service"
        try:
            sql_started = time.monotonic()
            raw = sql(query)
            sql_ms = (time.monotonic() - sql_started) * 1000
            if not raw:
                return self.send(404, {"error": "order_not_found", "traceId": trace}, trace=trace)
            order = json.loads(raw)
            service, version = order["serviceId"], order["version"]
            with LOCK:
                count = COUNTERS.get((service, version), {}).get("count", 0) + 1
            if order["scenario"] == "SLOW_SQL" and count % 5 == 0:
                slow_started = time.monotonic()
                sql("SELECT SLEEP(1.15)")
                sql_ms += (time.monotonic() - slow_started) * 1000
                sql_text += "; SELECT SLEEP(1.15) /* explicit isolated slow-query fixture */"
            status = 503 if order["scenario"] == "FAULT" and count % 10 == 0 else 200
            body = {"order": order, "traceId": trace} if status == 200 else {"error": "synthetic_dependency_failure", "traceId": trace}
        except (OSError, RuntimeError, subprocess.TimeoutExpired):
            body = {"error": "database_unavailable", "traceId": trace}
            order_id = ""
        body["version"] = version  # The version observed by this request, including failures.
        duration = (time.monotonic() - started) * 1000
        event = {"trace_id": trace, "project_id": PROJECT, "environment": ENVIRONMENT, "service_id": service,
                 "version": version, "order_id": order_id, "@timestamp": observed, "http_status": status,
                 "duration_ms": round(duration, 3), "sql_duration_ms": round(sql_ms, 3), "sql_text": sql_text,
                 "level": "ERROR" if status >= 500 else "WARN" if sql_ms > 1000 else "INFO",
                 "message": "SQL slow query" if sql_ms > 1000 else "dependency failure" if status >= 500 else "order read completed",
                 "fixture": "OPS-04-real-http-requests-synthetic-orders"}
        with local() as conn:
            conn.execute("INSERT INTO events(id,event_json) VALUES(?,?)", (trace, json.dumps(event, ensure_ascii=False)))
        accumulate(event)
        self.send(status, body, trace=trace)


threading.Thread(target=project_events, daemon=True).start()
ThreadingHTTPServer(("0.0.0.0", 8280), Handler).serve_forever()
