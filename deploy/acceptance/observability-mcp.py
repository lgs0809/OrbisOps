#!/usr/bin/env python3
"""Read-only MCP adapter for the isolated Prometheus, Elasticsearch and MySQL targets.

Ledger/metrics calls each perform one upstream query. The explicitly configured
native evidence read performs four existing viewer GETs and archives each response.
The separate SQLite ledger records received RPCs and upstream outcomes for cross-checking.
"""
from observability_scope import scope_mapping, scoped_metric_series
from observability_ledger import ledger_query, ledger_summary_sql, window_summary, compare_windows
import native_change_evidence
import datetime as dt
import hashlib
import json
import math
import os
import re
import sqlite3
import subprocess
import threading
import time
import urllib.parse
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PROJECT = "ops-acceptance-a"
SERVICES = {"ops-acc-a-service-" + str(i) for i in range(1, 5)}
TOKEN = os.environ["OBSERVABILITY_MCP_TOKEN"]
PASSWORD = os.environ["OBSERVABILITY_MYSQL_PASSWORD"]
if len(TOKEN) < 32 or not PASSWORD:
    raise RuntimeError("Acceptance credentials must be initialized before starting the connector")
SESSIONS = set()
LOCK = threading.Lock()
MAX_BYTES = 900_000


def database():
    connection = sqlite3.connect("/state/observability.sqlite", timeout=10)
    connection.row_factory = sqlite3.Row
    return connection


with database() as connection:
    connection.executescript("""
      PRAGMA journal_mode=WAL;
      CREATE TABLE IF NOT EXISTS queries(
        query_id TEXT PRIMARY KEY, rpc_id TEXT NOT NULL, tool TEXT NOT NULL, scope_json TEXT NOT NULL,
        fingerprint TEXT NOT NULL, received_at REAL NOT NULL, completed_at REAL,
        status TEXT NOT NULL, upstream_query TEXT NOT NULL, response_sha256 TEXT);
      CREATE TABLE IF NOT EXISTS rpc_calls(
        id INTEGER PRIMARY KEY AUTOINCREMENT, rpc_id TEXT NOT NULL, tool TEXT NOT NULL,
        input_sha256 TEXT NOT NULL, received_at REAL NOT NULL, status TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS native_responses(
        native_read_id TEXT NOT NULL, rpc_id TEXT NOT NULL, path TEXT NOT NULL,
        http_status INTEGER NOT NULL, response_hash TEXT NOT NULL, response_json TEXT NOT NULL,
        received_at REAL NOT NULL, PRIMARY KEY(native_read_id,path));
    """)


def scope(value):
    if not isinstance(value, dict) or value.get("projectId") != PROJECT or value.get("environment") not in ("acceptance", "prod"):
        raise ValueError("PROJECT_OR_ENVIRONMENT_NOT_AUTHORIZED")
    if value.get("serviceId") not in SERVICES:
        raise ValueError("SERVICE_NOT_AUTHORIZED")
    start, end = value.get("startEpoch"), value.get("endEpoch")
    if any(isinstance(x, bool) or not isinstance(x, (float, int)) or not math.isfinite(x) for x in (start, end)):
        raise ValueError("INVALID_WINDOW")
    now = time.time()
    if not 0 < end - start <= 3600 or end > now + 1 or start < now - 7 * 86400:
        raise ValueError("WINDOW_OUT_OF_BOUNDS: startEpoch/endEpoch are Unix seconds; "
                         "require 0 < endEpoch-startEpoch <= 3600, endEpoch <= now, "
                         "startEpoch within the last 7 days; serverNowEpoch=" + str(int(now)))
    return {key: value[key] for key in ("projectId", "environment", "serviceId", "startEpoch", "endEpoch")}


def http_json(url, body=None):
    request = urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=8) as response:
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("UPSTREAM_RESULT_TOO_LARGE")
    return json.loads(data)


def mysql(query):
    result = subprocess.run(["mysql", "-hmysql", "-uops_acceptance_ro_a", "-N", "-B", "--raw",
                             "--default-character-set=utf8mb4", "ops_acceptance_business_a"],
                            input=query, text=True, capture_output=True, timeout=8,
                            env={**os.environ, "MYSQL_PWD": PASSWORD})
    if result.returncode:
        raise OSError("MYSQL_QUERY_FAILED")
    if len(result.stdout.encode()) > MAX_BYTES:
        raise ValueError("UPSTREAM_RESULT_TOO_LARGE")
    return result.stdout


def query_plan(name, selected, arguments, rpc_id=None):
    service = selected["serviceId"]  # Exact allowlist, never free-form SQL/PromQL.
    start, end = selected["startEpoch"], selected["endEpoch"]
    if name == 'change_package_evidence':
        binding = json.loads(os.environ['OBSERVABILITY_NATIVE_PACKAGE_BINDING'])
        credentials = {'username':os.environ['OBSERVABILITY_NATIVE_VIEWER_USERNAME'],
                       'password':os.environ['OBSERVABILITY_NATIVE_VIEWER_PASSWORD']}
        native_change_evidence.authorize(binding, credentials, selected)
        def execute():
            native_read_id = str(uuid.uuid4())
            def archive(path,status,digest,raw):
                with database() as connection:
                    connection.execute('INSERT INTO native_responses VALUES(?,?,?,?,?,?,?)',
                        (native_read_id,str(rpc_id),path,status,digest,raw,time.time()))
            return {**native_change_evidence.read(binding,credentials,selected,archive),
                    'nativeReadArchiveId':native_read_id}
        return 'existing viewer GET detail/versions/events/operations; '+json.dumps(binding,sort_keys=True),execute
    if name == "target_version":
        url = "http://workflow-target:8280/version"
        def execute():
            result = http_json(url)
            if result.get("projectId") != PROJECT:
                raise ValueError("TARGET_PROJECT_MISMATCH")
            matches = [item for item in result.get("services", []) if item.get("serviceId") == service]
            if len(matches) != 1:
                raise ValueError("TARGET_SERVICE_NOT_FOUND")
            return {"version": matches[0]["version"], "source": "actual order target /version", "serviceId": service,
                    "resourceIdentity": scope_mapping(selected["environment"], service)["resourceIdentity"], "routeDefinition": "/orders/id",
                    "collectionDefinition": "ops04-completed-http-raw-scrapes-v1"}
        return url + "#serviceId=" + service, execute
    if name == "metrics_window":
        selector = ('{job="ops-workflow-target",instance="workflow-target:8280",project_id=~"' + PROJECT
                    + '|",environment=~"acceptance|",service_id=~"' + service
                    + '|",__name__=~"ops04_http_requests_total|ops04_http_errors_total|ops04_http_request_duration_seconds_bucket|up"}')
        # Instant range-vector queries retain actual scrape timestamps. query_range
        # evaluates on a grid and can carry a pre-window sample into the left edge.
        query = selector + '[' + str(int((end - start) * 1000)) + 'ms]'
        url = "http://prometheus-acceptance:9090/api/v1/query?" + urllib.parse.urlencode({"query": query, "time": end})
        def execute():
            result = http_json(url)
            if result.get("status") != "success" or result.get("data", {}).get("resultType") != "matrix":
                raise ValueError("PROMETHEUS_RESPONSE_INVALID")
            return {"series": scoped_metric_series(result["data"]["result"], selected), "stepSeconds": 5,
                    "source": "Prometheus 2.53 /api/v1/query range vector; actual scrape timestamps",
                    "collectionDefinition": "ops04-completed-http-raw-scrapes-v1", "quantileMethod": "compatible classic histogram"}
        return query, execute
    if name == "logs_window":
        query = {"size": 12, "track_total_hits": True, "query": {"bool": {"filter": [
            {"term": {"project_id": PROJECT}}, {"term": {"environment": "acceptance"}},
            {"term": {"service_id": service}}, {"range": {"@timestamp": {"gte": int(start * 1000), "lt": int(end * 1000)}}}]}},
            "sort": [{"sql_duration_ms": "desc"}, {"@timestamp": "desc"}],
            "aggs": {"errors": {"filter": {"range": {"http_status": {"gte": 500}}}},
                     "slowSql": {"filter": {"range": {"sql_duration_ms": {"gt": 1000}}}},
                     "versions": {"terms": {"field": "version", "size": 20}}}}
        def execute():
            result = http_json("http://elasticsearch-acceptance:9200/ops04-orders/_search", query)
            if result.get("timed_out") or result.get("_shards", {}).get("failed", 0):
                raise ValueError("ELASTICSEARCH_PARTIAL_RESULT")
            return {"sampleCount": result["hits"]["total"]["value"], "errorCount": result["aggregations"]["errors"]["doc_count"],
                    "slowSqlCount": result["aggregations"]["slowSql"]["doc_count"],
                    "versions": result["aggregations"]["versions"]["buckets"],
                    "samples": [hit["_source"] for hit in result["hits"]["hits"]],
                    "source": "Elasticsearch 7.17 ops04-orders", "complete": result["hits"]["total"]["relation"] == "eq"}
        return json.dumps(query, sort_keys=True), execute
    if name == "sql_window":
        comparison = scope(arguments['comparisonWindow']) if 'comparisonWindow' in arguments else None
        if comparison and (any(comparison[key] != selected[key]
                for key in ('projectId', 'environment', 'serviceId'))
                or comparison['endEpoch'] > start):
            raise ValueError('LEDGER_COMPARISON_SCOPE_OR_WINDOW_INVALID')
        selects = [ledger_query(PROJECT, service, selected, 'after')]
        if comparison:
            selects.append(ledger_query(PROJECT, service, comparison, 'before'))
        query = ledger_summary_sql(selects)
        def execute():
            summaries = {item['label']: item for line in mysql(query).splitlines()
                         for item in [json.loads(line)]}
            summary = summaries['after']
            window_facts = window_summary(summary, selected)
            result = {**summary, **window_facts, "rows": summary["rows"] or [], "sampleRowsLimit": 12,
                    "source": "MySQL actual HTTP request ledger; measured SQL durations; not server slow-log export"}
            if comparison:
                result['comparison'] = compare_windows(window_summary(summaries['before'], comparison), window_facts)
            return result
        return query, execute
    if name == "sql_explain":
        order = arguments.get("orderId", "")
        if not isinstance(order, str) or not re.fullmatch(r"(?:ops-acc-a-order-[0-9]{1,3}|ops04-slow-order-1)", order):
            raise ValueError("ORDER_NOT_AUTHORIZED")
        query = ("EXPLAIN FORMAT=JSON SELECT o.order_id,c.tier,s.version FROM acceptance_order o "
                 "JOIN acceptance_customer c ON c.customer_id=o.customer_id JOIN acceptance_service s ON s.service_id=o.service_id "
                 "WHERE s.project_id='" + PROJECT + "' AND s.service_id='" + service + "' AND o.order_id='" + order + "';")
        return query, lambda: {"plan": json.loads(mysql(query)), "orderId": order,
                              "source": "MySQL EXPLAIN FORMAT=JSON; fixed authorized order-read template"}
    raise ValueError("UNKNOWN_TOOL")


def execute_tool(rpc_id, name, arguments):
    selected = scope(arguments.get("window"))
    query, execute = query_plan(name, selected, arguments, rpc_id)
    identity = str(uuid.uuid4())
    fingerprint = hashlib.sha256((name + json.dumps(selected, sort_keys=True) + query).encode()).hexdigest()
    with database() as connection:
        connection.execute("INSERT INTO queries VALUES(?,?,?,?,?,?,NULL,'DISPATCHING',?,NULL)",
                           (identity, str(rpc_id), name, json.dumps(selected), fingerprint, time.time(), query))
    result = {"kind": name, "scope": selected, "queryId": identity, "queryFingerprint": fingerprint,
              "scopeMapping": scope_mapping(selected["environment"], selected["serviceId"])}
    try:
        result.update(execute(), status="AVAILABLE")
    except (OSError, ValueError, KeyError, subprocess.TimeoutExpired) as error:
        # A successful diagnostic can report an unavailable source, never a healthy substitute.
        result.update(status="UNAVAILABLE", reason=type(error).__name__, evidenceGap=name + " upstream query did not yield complete evidence")
    result["observedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
    digest = hashlib.sha256(json.dumps(result, sort_keys=True).encode()).hexdigest()
    with database() as connection:
        connection.execute("UPDATE queries SET status=?,completed_at=?,response_sha256=? WHERE query_id=?",
                           (result["status"], time.time(), digest, identity))
    return result


def service_names():
    # Names are discovery metadata from the same authorized business catalog, never health evidence.
    raw = mysql("SELECT JSON_OBJECT('serviceId',service_id,'name',name) FROM acceptance_service "
                "WHERE project_id='ops-acceptance-a' ORDER BY service_id")
    return {item['serviceId']: item['name'] for line in raw.splitlines() if line
            for item in [json.loads(line)] if item.get('serviceId') in SERVICES}


def tool(name, names=None):
    properties = {"window": {
        "type": "object",
        "description": "Authorized resource and observation window. Resource IDs are case-sensitive; timestamps are Unix seconds.",
        "properties": {
            "projectId": {"type": "string", "enum": [PROJECT]},
            "environment": {"type": "string", "enum": ["acceptance", "prod"],
                            "description": "prod is the logical name of the isolated workflow-target production target; its historical source label is acceptance. scopeMapping and sourceMetric retain that mapping and original labels. This endpoint never reads the independent TEST target."},
            "serviceId": {"type": "string", "enum": sorted(SERVICES),
                          "description": "Exact service ID; authorized catalog display names (discovery metadata, not live health): " + json.dumps(names or {}, ensure_ascii=False, sort_keys=True)},
            "startEpoch": {"type": "number", "description": "Unix seconds, within the last seven days."},
            "endEpoch": {"type": "number", "description": "Unix seconds, no later than server now; 0 < endEpoch - startEpoch <= 3600."},
        },
        "required": ["projectId", "environment", "serviceId", "startEpoch", "endEpoch"],
        "additionalProperties": False,
    }}
    required = ["window"]
    if name == 'sql_window':
        properties['comparisonWindow'] = {**properties['window'],
            'description': 'Optional preceding non-overlapping window for the same authorized resource; computes changes from complete actual HTTP ledger queries.'}
    if name == "sql_explain":
        properties["orderId"] = {"type": "string"}
        required.append("orderId")
    description = ('four fixed existing viewer GETs; original native bindings and response hashes retained; no digital signature or final SLO verdict'
                   if name == 'change_package_evidence' else 'one actual upstream query; reports unavailable sources explicitly')
    return {"name": name, "description": "Read-only isolated " + name + "; " + description,
            "inputSchema": {"type": "object", "properties": properties, "required": required, "additionalProperties": False},
            "outputSchema": {"type": "object", "required": ["status", "scope", "queryId", "queryFingerprint", "observedAt"],
                             "properties": {"status": {"enum": ["AVAILABLE", "UNAVAILABLE"]}, "scope": {"type": "object"},
                                            "queryId": {"type": "string"}, "queryFingerprint": {"type": "string"}, "observedAt": {"type": "string"}}},
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "idempotentHint": True, "openWorldHint": False}}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_):
        pass

    def send(self, status, value=None, headers=None):
        data = json.dumps(value, ensure_ascii=False).encode() if value is not None else b""
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        for name, value in (headers or {}).items():
            self.send_header(name, value)
        self.end_headers()
        if data:
            self.wfile.write(data)

    def authorized(self):
        if self.headers.get("Authorization") == "Bearer " + TOKEN:
            return True
        self.send(401, {"error": "unauthorized"})
        return False

    def do_GET(self):
        if self.path == "/ready":
            return self.send(200, {"status": "ready"})
        if not self.authorized():
            return
        if self.path == "/evidence":
            with database() as connection:
                return self.send(200, {"queries": [dict(row) for row in connection.execute("SELECT * FROM queries ORDER BY received_at")],
                                       "rpcCalls": [dict(row) for row in connection.execute("SELECT * FROM rpc_calls ORDER BY id")]})
        self.send(405)

    def do_DELETE(self):
        if self.authorized():
            with LOCK:
                SESSIONS.discard(self.headers.get("Mcp-Session-Id"))
            self.send(200)

    def do_POST(self):
        if not self.authorized():
            return
        size = int(self.headers.get("Content-Length", 0))
        if self.path != "/mcp" or not 0 < size <= 64_000:
            return self.send(400)
        try:
            request = json.loads(self.rfile.read(size))
            method, params = request.get("method"), request.get("params") or {}
            recorded = None
            if method == "tools/call" and "id" in request:
                with database() as connection:
                    recorded = connection.execute("INSERT INTO rpc_calls(rpc_id,tool,input_sha256,received_at,status) VALUES(?,?,?,?,?)",
                        (str(request["id"]), str(params.get("name")), hashlib.sha256(json.dumps(params, sort_keys=True).encode()).hexdigest(), time.time(), "RECEIVED")).lastrowid
            if method != "initialize" and self.headers.get("Mcp-Session-Id") not in SESSIONS:
                if recorded is not None:
                    with database() as connection:
                        connection.execute("UPDATE rpc_calls SET status='SESSION_REJECTED' WHERE id=?", (recorded,))
                return self.send(404, {"error": "session expired"})
            if "id" not in request:
                return self.send(202)
            headers = {}
            if method == "initialize":
                session = str(uuid.uuid4())
                with LOCK:
                    SESSIONS.add(session)
                headers["Mcp-Session-Id"] = session
                result = {"protocolVersion": params.get("protocolVersion", "2024-11-05"), "capabilities": {"tools": {"listChanged": False}},
                          "serverInfo": {"name": "orbisops-real-observability", "version": "1.0.0"}}
            elif method == "tools/list":
                names = service_names()
                tool_names = ['metrics_window','logs_window','sql_window','sql_explain','target_version']
                if os.environ.get('OBSERVABILITY_NATIVE_PACKAGE_BINDING'):
                    tool_names.append('change_package_evidence')
                result = {"tools": [tool(name, names) for name in tool_names]}
            elif method == "tools/call":
                try:
                    data = execute_tool(request["id"], params.get("name"), params.get("arguments") or {})
                    result = {"isError": False, "content": [], "structuredContent": data}
                    status = data["status"]
                except ValueError as error:
                    result = {"isError": True, "content": [{"type": "text", "text": str(error)}]}
                    status = "REJECTED"
                with database() as connection:
                    connection.execute("UPDATE rpc_calls SET status=? WHERE id=?", (status, recorded))
            elif method == "ping":
                result = {}
            else:
                return self.send(200, {"jsonrpc": "2.0", "id": request["id"], "error": {"code": -32601, "message": "unknown method"}})
            self.send(200, {"jsonrpc": "2.0", "id": request["id"], "result": result}, headers)
        except (ValueError, TypeError, KeyError):
            self.send(400, {"error": "invalid_request"})


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8281), Handler).serve_forever()
