#!/usr/bin/env python3
"""Loopback fault injection followed by unmodified responses from the authorized model provider.

Only for the isolated acceptance stack. Never manufactures a successful model response.
Records request hashes and transport outcomes, never prompts, keys or response bodies.
"""
import datetime as dt
import hashlib
import hmac
import json
import os
import sqlite3
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE = os.environ['MODEL_FAULT_UPSTREAM'].rstrip('/')
KEY = os.environ['MODEL_FAULT_API_KEY']
STATE = os.environ.get('MODEL_FAULT_STATE', '/state/transport.sqlite')
FAILURES = int(os.environ.get('MODEL_FAULT_FIRST_FAILURES', '2'))
MAX_REQUESTS = 24
LOCK = threading.Lock()
PARTS = urllib.parse.urlsplit(BASE)
if PARTS.scheme != 'https' or not PARTS.hostname or PARTS.username or not KEY or not 0 <= FAILURES <= 2:
    raise ValueError('Expected the existing HTTPS provider and at most two injected failures')


def database():
    connection = sqlite3.connect(STATE, timeout=10)
    connection.row_factory = sqlite3.Row
    return connection


with database() as conn:
    conn.execute('''CREATE TABLE IF NOT EXISTS transport (
        attempt INTEGER PRIMARY KEY AUTOINCREMENT, started_at TEXT NOT NULL,
        request_sha256 TEXT NOT NULL, model TEXT NOT NULL, injected INTEGER NOT NULL,
        upstream_status INTEGER, status TEXT NOT NULL, duration_ms INTEGER)''')
    columns = {r[1] for r in conn.execute('PRAGMA table_info(transport)')}
    for name, kind in [('response_bytes', 'INTEGER'), ('response_sha256', 'TEXT'), ('content_type', 'TEXT')]:
        if name not in columns: conn.execute('ALTER TABLE transport ADD COLUMN ' + name + ' ' + kind)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.0'

    def log_message(self, *_args):
        pass

    def respond(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == '/ready':
            return self.respond(200, {'ready': True, 'scope': 'isolated-model-transport-test'})
        self.respond(404, {'error': 'not_found'})

    def do_POST(self):
        if self.path != '/v1/chat/completions':
            return self.respond(404, {'error': 'not_found'})
        if not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + KEY):
            return self.respond(401, {'error': {'code': 'invalid_api_key'}})
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 3_000_000:
                return self.respond(413, {'error': 'request_size'})
            body = self.rfile.read(length)
            model = json.loads(body).get('model')
            if model not in ('gpt-5.6-luna', 'gpt-5.6-terra'):
                return self.respond(400, {'error': 'model_not_authorized'})
        except (ValueError, TypeError):
            return self.respond(400, {'error': 'invalid_request'})
        started = time.monotonic()
        with LOCK, database() as conn:
            count = conn.execute('SELECT count(*) FROM transport').fetchone()[0]
            if count >= MAX_REQUESTS:
                return self.respond(400, {'error': 'acceptance_request_budget_exhausted'})
            injected = count < FAILURES
            attempt = conn.execute('''INSERT INTO transport
                (started_at,request_sha256,model,injected,status) VALUES (?,?,?,?,?)''',
                (dt.datetime.now(dt.timezone.utc).isoformat(), hashlib.sha256(body).hexdigest(), model,
                 int(injected), 'INJECTED_503' if injected else 'FORWARDING')).lastrowid
        if injected:
            self.respond(503, {'error': {'code': 'temporarily_unavailable', 'message': 'Acceptance transport fault'}})
            with database() as conn:
                conn.execute('UPDATE transport SET duration_ms=? WHERE attempt=?',
                             (int((time.monotonic()-started)*1000), attempt))
            return
        request = urllib.request.Request(BASE + self.path, data=body,
            headers={'Authorization': 'Bearer ' + KEY, 'Content-Type': 'application/json',
                     'Accept': self.headers.get('Accept', 'application/json')})
        status, outcome, headers_sent = None, 'TRANSPORT_FAILED', False
        size, content_type, response_hash = 0, '', hashlib.sha256()
        try:
            try:
                upstream = urllib.request.build_opener(NoRedirect()).open(request, timeout=180)
            except urllib.error.HTTPError as error:
                upstream = error
            with upstream:
                status = upstream.status
                self.send_response(status)
                content_type = upstream.headers.get('Content-Type', 'application/json')
                self.send_header('Content-Type', content_type)
                self.send_header('Cache-Control', 'no-store')
                retry_after = upstream.headers.get('Retry-After')
                if retry_after: self.send_header('Retry-After', retry_after)
                self.end_headers()
                headers_sent = True
                while True:
                    chunk = upstream.read1(4096)
                    if not chunk: break
                    size += len(chunk)
                    response_hash.update(chunk)
                    if size > 8_000_000 or time.monotonic()-started > 240:
                        raise TimeoutError('acceptance_response_budget')
                    self.wfile.write(chunk)
                    self.wfile.flush()
                outcome = 'FORWARDED_COMPLETE'
        except (OSError, TimeoutError):
            if not headers_sent:
                try: self.respond(502, {'error': {'code': 'temporarily_unavailable'}})
                except OSError: pass
        finally:
            with database() as conn:
                conn.execute('''UPDATE transport SET upstream_status=?,status=?,duration_ms=?,
                    response_bytes=?,response_sha256=?,content_type=? WHERE attempt=?''',
                    (status, outcome, int((time.monotonic()-started)*1000), size, response_hash.hexdigest(),
                     content_type[:128], attempt))


if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1', 8382), Handler).serve_forever()
