"""Two local target connections with separately reviewed read/write tools and durable RPC evidence."""
import datetime
import hashlib
import hmac
import json
import os
import sqlite3
import threading
import time
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from order_request_observation import observe_order_requests

TOKEN = os.environ["LANDING_MCP_TOKEN"]
TARGETS = {"test": ("http://prepare-target:8280", os.environ["PREPARE_CONTROL_TOKEN"]),
           "prod": ("http://workflow-target:8280", os.environ["PROD_CONTROL_TOKEN"])}
PROJECT, SERVICE = "ops-acceptance-a", "ops-acc-a-service-2"
SESSIONS, LOCK = set(), threading.Lock()
if min(len(TOKEN), *(len(value[1]) for value in TARGETS.values())) < 30:
    raise RuntimeError("ISOLATED_CONTROL_CREDENTIALS_REQUIRED")


def database():
    connection = sqlite3.connect("/state/landing-mcp.sqlite", timeout=10)
    connection.row_factory = sqlite3.Row
    return connection


with database() as connection:
    connection.executescript("""PRAGMA journal_mode=WAL;
      CREATE TABLE IF NOT EXISTS calls(id INTEGER PRIMARY KEY AUTOINCREMENT,rpc_id TEXT,
      tool TEXT,input_hash TEXT,received_at REAL,status TEXT,result_hash TEXT);""")


def tools():
    result = []
    for environment in TARGETS:
        for action in ("read_state", "check_orders", "apply_configuration", *(["validate_configuration"] if environment == "test" else [])):
            write = action == "apply_configuration"
            properties = {"projectId": {"type": "string", "enum": [PROJECT]},
                          "service": {"type": "string", "enum": [SERVICE], "description": "验收服务 A2，订单查询服务"},
                          "actor": {"type": "string", "minLength": 1, "maxLength": 128}}
            required = ["projectId", "service"]
            if write:
                properties.update({key: {"type": "string", "minLength": 1, "maxLength": 256}
                                   for key in ("expectedVersion", "version", "executionKey", "deadline")})
                properties["scenario"] = {"type": "string", "enum": ["HEALTHY", "FAULT", "SLOW_SQL"]}
                required += ["expectedVersion", "version", "scenario"]
                if environment == "prod":
                    required += ["actor", "executionKey", "deadline"]
            if action == "validate_configuration":
                properties.update({"expectedVersion": {"type": "string", "minLength": 1},
                                   "scenario": {"type": "string", "enum": ["HEALTHY"]}})
                required += ["expectedVersion", "scenario"]
            result.append({"name": environment + "_" + action,
                "description": f"Local isolated {environment} connection: {action}. Resource identity is service://{SERVICE}/{environment}. "
                    + ("Changes actual order-service configuration with expected-version CAS and an atomic receipt."
                       if write else "Reads the actual target. validate_configuration verifies the expected HEALTHY test version before and after twenty real order GETs; it never rewrites a repaired target. check_orders only observes requests."),
                "inputSchema": {"type": "object", "additionalProperties": False, "properties": properties, "required": required},
                "outputSchema": {"type": "object", "required": ["status", "resourceKey", "environment", "projectId"],
                                 "properties": {k: {"type": "string"} for k in ("status", "resourceKey", "environment", "projectId")}},
                "annotations": {"readOnlyHint": not write, "destructiveHint": write, "idempotentHint": action not in ("check_orders", "validate_configuration")}})
    return result


def execute(name, args):
    environment, action = name.split("_", 1)
    if environment not in TARGETS or action not in ("read_state", "check_orders", "apply_configuration", "validate_configuration"):
        raise ValueError("UNKNOWN_TOOL")
    if args.get("projectId") != PROJECT or args.get("service") != SERVICE:
        raise ValueError("TARGET_SCOPE_MISMATCH")
    if action == "validate_configuration":
        if environment != "test" or not args.get("expectedVersion") or args.get("scenario") != "HEALTHY":
            raise ValueError("TEST_VALIDATION_CONTRACT_REQUIRED")
        observed_args = {"projectId": PROJECT, "service": SERVICE}
        before = execute("test_read_state", observed_args)
        orders = execute("test_check_orders", observed_args)
        after = execute("test_read_state", observed_args)
        expected = args["expectedVersion"]
        passed = (all(state.get("version") == expected and state.get("scenario") == "HEALTHY"
                      for state in (before, after))
                  and orders["requestCount"] == 20
                  and all(row["httpStatus"] == 200 and row["version"] == expected and row["traceId"]
                          for row in orders["requests"])
                  and len({row["traceId"] for row in orders["requests"]}) == 20)
        return {**orders, "status": "PASSED" if passed else "FAILED", "verified": passed,
                "beforeState": before, "afterState": after, "expectedVersion": expected,
                "reason": "REAL_TEST_REQUESTS_VERIFIED" if passed else "TEST_STATE_OR_REQUEST_MISMATCH"}
    base, token = TARGETS[environment]
    if action == "check_orders":
        data = observe_order_requests(base + "/orders/ops-acc-a-order-2")
    else:
        headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
        if action == "apply_configuration":
            body = dict(args)
            if environment == "test":
                body.setdefault("actor", "isolated-prepare-runtime")
                body.setdefault("executionKey", "prepare-" + uuid.uuid4().hex)
                body.setdefault("deadline", (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(seconds=60)).isoformat())
            request = urllib.request.Request(base + "/control/deploy", json.dumps(body).encode(), headers, method="POST")
        else:
            request = urllib.request.Request(base + "/control/state/" + SERVICE, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                data = json.load(response)
        except urllib.error.HTTPError as error:
            if error.code in (400, 403, 409):
                raise ValueError(json.load(error).get("error", "TARGET_REJECTED")) from None
            raise
    return {**data, "projectId": PROJECT, "environment": environment,
            "resourceKey": f"service://{SERVICE}/{environment}",
            "observedAt": datetime.datetime.now(datetime.timezone.utc).isoformat()}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_):
        pass

    def send(self, code, value=None, headers=None):
        body = json.dumps(value, ensure_ascii=False).encode() if value is not None else b""
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        if hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + TOKEN):
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
                return self.send(200, [dict(row) for row in connection.execute("SELECT * FROM calls ORDER BY id")])
        self.send(405)

    def do_DELETE(self):
        if self.authorized():
            with LOCK:
                SESSIONS.discard(self.headers.get("Mcp-Session-Id"))
            self.send(200)

    def do_POST(self):
        if not self.authorized():
            return
        call_id = None
        try:
            size = int(self.headers.get("Content-Length", 0))
            if self.path != "/mcp" or not 0 < size <= 64000:
                return self.send(400)
            request = json.loads(self.rfile.read(size))
            method, params = request.get("method"), request.get("params") or {}
            if method != "initialize" and self.headers.get("Mcp-Session-Id") not in SESSIONS:
                return self.send(404, {"error": "session expired"})
            if "id" not in request:
                return self.send(202)
            headers = {}
            if method == "initialize":
                session = str(uuid.uuid4())
                with LOCK:
                    SESSIONS.add(session)
                headers["Mcp-Session-Id"] = session
                result = {"protocolVersion": params.get("protocolVersion", "2024-11-05"),
                          "capabilities": {"tools": {"listChanged": False}},
                          "serverInfo": {"name": "orbisops-isolated-dual-target", "version": "1.0.0"}}
            elif method == "tools/list":
                result = {"tools": tools()}
            elif method == "tools/call":
                with database() as connection:
                    call_id = connection.execute("INSERT INTO calls(rpc_id,tool,input_hash,received_at,status) VALUES(?,?,?,?,?)",
                        (str(request["id"]), params["name"], hashlib.sha256(json.dumps(params, sort_keys=True).encode()).hexdigest(),
                         time.time(), "RECEIVED")).lastrowid
                try:
                    data = execute(params["name"], params.get("arguments") or {})
                    result = {"isError": data.get("status") == "FAILED", "content": [], "structuredContent": data}
                except ValueError as error:
                    result = {"isError": True, "content": [{"type": "text", "text": str(error)}]}
                with database() as connection:
                    connection.execute("UPDATE calls SET status=?,result_hash=? WHERE id=?",
                        ("REJECTED" if result["isError"] else "COMPLETED",
                         hashlib.sha256(json.dumps(result, sort_keys=True).encode()).hexdigest(), call_id))
            elif method == "ping":
                result = {}
            else:
                return self.send(200, {"jsonrpc": "2.0", "id": request["id"], "error": {"code": -32601, "message": "unknown method"}})
            self.send(200, {"jsonrpc": "2.0", "id": request["id"], "result": result}, headers)
        except (OSError, TimeoutError):
            if call_id is not None:
                with database() as connection:
                    connection.execute("UPDATE calls SET status='UNKNOWN' WHERE id=?", (call_id,))
            # The target may have committed: never manufacture a negative execution acknowledgement.
            self.close_connection = True
        except (ValueError, TypeError, KeyError):
            self.send(400, {"error": "invalid_request"})


ThreadingHTTPServer(("0.0.0.0", 8381), Handler).serve_forever()
