#!/usr/bin/env python3
"""Actual HTTP fault peer for frozen MCP cases; facts-only, no reference access.

Every repetition uses a fresh attemptId. Only an append-only SQLite call ledger
changes; business resources are immutable. The optional redirect sink refuses all
requests and records any violation without retaining credentials.
"""
import argparse
import gzip
import hashlib
import json
import os
import secrets
import socket
import sqlite3
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlparse

args = argparse.ArgumentParser()
args.add_argument('--facts', type=Path, required=True)
args.add_argument('--database', required=True)
args.add_argument('--port', type=int, default=8781)
args.add_argument('--sink-port', type=int, default=8782)
options = args.parse_args()
token = os.environ['MCP_ACCEPTANCE_TOKEN']
source_sha = hashlib.sha256(options.facts.read_bytes()).hexdigest()
cases = {c['caseId']: c for c in json.loads(options.facts.read_text())}
projects = sorted({c['projectId'] for c in cases.values()})
sessions = set()
lock = threading.Lock()


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def connection():
    result = sqlite3.connect(options.database, timeout=15)
    result.row_factory = sqlite3.Row
    return result


with connection() as db:
    db.executescript('''
        CREATE TABLE IF NOT EXISTS calls(id INTEGER PRIMARY KEY, rpc_id TEXT, case_id TEXT,
          project_id TEXT, attempt_id TEXT, physical_attempt INTEGER, status TEXT,
          payload_sha256 TEXT, source_sha256 TEXT, received_at REAL,
          resource_project_id TEXT, response_project_id TEXT);
        CREATE TABLE IF NOT EXISTS redirects(id INTEGER PRIMARY KEY, case_id TEXT,
          attempt_id TEXT, method TEXT, received_at REAL);
        CREATE TABLE IF NOT EXISTS denied_reads(id INTEGER PRIMARY KEY, case_id TEXT,
          requested_project_id TEXT, attempt_id TEXT, received_at REAL);
    ''')


schema = {'type': 'object', 'required': ['caseId', 'scope', 'resourceId', 'state', 'requestCount', 'note'],
    'properties': {'caseId': {'type': 'string'}, 'resourceId': {'const': 'frozen-order-observation'},
        'state': {'enum': ['HEALTHY', 'DEGRADED']}, 'requestCount': {'type': 'integer', 'minimum': 0},
        'note': {'type': 'string'}, 'scope': {'type': 'object',
            'required': ['projectId', 'environment', 'serviceId', 'endTime', 'windowMinutes'],
            'properties': {'projectId': {'enum': projects}, 'environment': {'const': 'sandbox'},
                'serviceId': {'const': 'eval-order-reader'}, 'endTime': {'const': '2026-10-02T12:00:00Z'},
                'windowMinutes': {'const': 5}}, 'additionalProperties': False}},
    'additionalProperties': False}
tool = {'name': 'read_snapshot', 'description': 'Read the current project\'s immutable isolated order observation and retain its real wire receipt.',
    'inputSchema': {'type': 'object', 'required': ['caseId', 'projectId', 'attemptId'],
        'properties': {'caseId': {'enum': sorted(cases)}, 'projectId': {'enum': projects},
            'attemptId': {'type': 'string', 'pattern': '^[a-zA-Z0-9_-]{1,96}$'}}, 'additionalProperties': False},
    'outputSchema': schema, 'annotations': {'readOnlyHint': True, 'destructiveHint': False, 'idempotentHint': True, 'openWorldHint': False}}


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def setup(self):
        super().setup()
        # Fresh integrations must not retain one idle thread per old client.
        self.connection.settimeout(30)

    def log_message(self, *unused):
        pass

    def send(self, status, value=None, headers=None, raw=None):
        body = raw if raw is not None else canonical(value).encode() if value is not None else b''
        headers = dict(headers or {})
        self.send_response(status)
        self.send_header('Content-Type', headers.pop('Content-Type', 'application/json'))
        self.send_header('Content-Length', str(len(body)))
        for key, item in headers.items(): self.send_header(key, item)
        self.end_headers()
        try: self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError): pass

    def authorized(self):
        return secrets.compare_digest(self.headers.get('Authorization', ''), 'Bearer '+token)

    def redirect_record(self, value, method):
        with connection() as db:
            db.execute('INSERT INTO redirects(case_id,attempt_id,method,received_at) VALUES(?,?,?,?)',
                (value.get('caseId', ''), value.get('attemptId', ''), method, time.time()))
        self.send(403, {'error': 'Redirect destination has no MCP authority'})

    def do_GET(self):
        uri = urlparse(self.path)
        if uri.path == '/redirected-get' or self.server.server_port == options.sink_port:
            return self.redirect_record({k: v[0] for k, v in parse_qs(uri.query).items()}, 'GET')
        if uri.path == '/ready':
            return self.send(200, {'caseCount': len(cases), 'sourceSha256': source_sha,
                'businessWrites': 0, 'referenceReadable': False})
        if uri.path == '/evidence' and self.authorized():
            attempt = parse_qs(uri.query).get('attemptId', [None])[0]
            where, parameters = (' WHERE attempt_id=?', (attempt,)) if attempt else ('', ())
            with connection() as db:
                return self.send(200, {'calls': [dict(row) for row in db.execute('SELECT * FROM calls'+where+' ORDER BY id', parameters)],
                    'redirects': [dict(row) for row in db.execute('SELECT * FROM redirects'+where+' ORDER BY id', parameters)],
                    'deniedReads': [dict(row) for row in db.execute('SELECT * FROM denied_reads'+where+' ORDER BY id', parameters)],
                    'businessWrites': 0, 'sourceSha256': source_sha})
        # This peer supports POST responses only. MCP clients distinguish a
        # missing GET/SSE channel (405) from an expired session (404).
        if uri.path == '/mcp': return self.send(405)
        self.send(404, {'error': 'Not available'})

    def do_DELETE(self):
        if not self.authorized(): return self.send(401, {'error': 'Unauthorized'})
        with lock: sessions.discard(self.headers.get('Mcp-Session-Id'))
        self.send(200)

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        if length > 100_000: return self.send(413, {'error': 'Request too large'})
        try: request = json.loads(self.rfile.read(length))
        except ValueError: return self.send(400, {'error': 'Invalid JSON'})
        if self.server.server_port == options.sink_port:
            return self.redirect_record(request.get('params', {}).get('arguments', {}), 'POST')
        if not self.authorized(): return self.send(401, {'error': 'Unauthorized'})
        if hashlib.sha256(options.facts.read_bytes()).hexdigest() != source_sha:
            return self.send(503, {'error': 'Frozen facts changed'})
        method, identity = request.get('method'), request.get('id')
        envelope = {'jsonrpc': '2.0', 'id': identity}
        if method == 'initialize':
            session = secrets.token_hex(24)
            with lock: sessions.add(session)
            version = request.get('params', {}).get('protocolVersion', '2024-11-05')
            envelope['result'] = {'protocolVersion': version, 'capabilities': {'tools': {}},
                'serverInfo': {'name': 'frozen-contract-peer', 'version': '1.0.0'}}
            return self.send(200, envelope, {'Mcp-Session-Id': session})
        with lock: valid_session = self.headers.get('Mcp-Session-Id') in sessions
        if not valid_session: return self.send(404, {'error': 'Session expired'})
        if identity is None: return self.send(202)
        if method == 'tools/list':
            envelope['result'] = {'tools': [tool]}
            return self.send(200, envelope)
        if method != 'tools/call':
            envelope['error'] = {'code': -32601, 'message': 'Method not found'}
            return self.send(200, envelope)
        params = request.get('params', {})
        inputs = params.get('arguments', {})
        case = cases.get(inputs.get('caseId'))
        if params.get('name') != 'read_snapshot' or not case or inputs.get('projectId') != case['projectId'] or not inputs.get('attemptId'):
            with connection() as db:
                db.execute('INSERT INTO denied_reads(case_id,requested_project_id,attempt_id,received_at) VALUES(?,?,?,?)',
                    (inputs.get('caseId', ''), inputs.get('projectId', ''), inputs.get('attemptId', ''), time.time()))
            envelope['error'] = {'code': -32602, 'message': 'Case, project and attempt identity required'}
            return self.send(200, envelope)
        profile = case['wireProfile']; mode = profile['mode']; configuration = profile['parameters']
        payload = profile['payload']; payload_sha = hashlib.sha256(canonical(payload).encode()).hexdigest()
        with lock, connection() as db:
            count = db.execute('SELECT COUNT(*) FROM calls WHERE case_id=? AND attempt_id=?',
                (case['caseId'], inputs['attemptId'])).fetchone()[0]+1
            status = 'RESPONSE_UNKNOWN' if mode in ('response-timeout', 'invalid-json', 'partial-stream', 'redirect') else 'SUCCEEDED'
            if mode in ('rpc-error', 'business-error'): status = 'BUSINESS_ERROR'
            if mode == 'drop-after-read' and count <= configuration['dropAttempts']: status = 'RESPONSE_UNKNOWN'
            response_scope = payload.get('scope', {})
            db.execute('INSERT INTO calls(rpc_id,case_id,project_id,attempt_id,physical_attempt,status,payload_sha256,source_sha256,received_at,resource_project_id,response_project_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)',
                (str(identity), case['caseId'], case['projectId'], inputs['attemptId'], count, status, payload_sha, source_sha, time.time(),
                 case['initialSnapshot']['resource']['scope']['projectId'], response_scope.get('projectId', '') if isinstance(response_scope, dict) else ''))
        if mode == 'drop-after-read' and count <= configuration['dropAttempts']:
            self.close_connection = True
            self.connection.shutdown(socket.SHUT_RDWR)
            return
        if mode == 'redirect':
            location = configuration['location']+'?'+urlencode({'caseId': case['caseId'], 'attemptId': inputs['attemptId']})
            return self.send(configuration['httpStatus'], headers={'Location': location})
        result = {'isError': False, 'content': [], 'structuredContent': payload}
        text = {'type': 'text', 'text': canonical(payload)}
        if mode in ('text-json', 'text-json-and-prose', 'mixed-image-json') or configuration.get('content') == 'text-json':
            result.pop('structuredContent'); result['content'] = [text]
        if mode == 'structured-with-conflicting-text':
            conflicting = json.loads(canonical(payload))
            conflicting['resourceId' if configuration['textConflict'] == 'resource' else 'state'] = 'other-resource' if configuration['textConflict'] == 'resource' else 'DEGRADED'
            result['content'] = [{'type': 'text', 'text': canonical(conflicting)}]
        if mode == 'text-json-and-prose':
            prose = {'type': 'text', 'text': 'Display note only; not structured observation evidence.'}
            result['content'] = [text, prose] if configuration['jsonPosition'] == 'first' else [prose, text]
        if mode == 'mixed-image-json':
            picture = {'type': 'image', 'mimeType': 'image/png', 'data': 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='}
            result['content'] = [picture, text] if configuration['imagePosition'] == 'first' else [text, picture]
        if mode == 'rpc-error':
            envelope['error'] = {'code': configuration['rpcErrorCode'], 'message': configuration['rpcErrorMessage']}
        elif mode == 'business-error':
            envelope['result'] = {'isError': True, 'content': [{'type': 'text', 'text': configuration['businessError']}],
                'structuredContent': {'code': configuration['businessError']}}
        else: envelope['result'] = result
        body = canonical(envelope).encode()
        if mode == 'invalid-json':
            body = body[:-3] if configuration['corruption'] == 'unterminated-object' else b'{"jsonrpc":"2.0","bad":"line\nbreak"}'
        if mode == 'gzip-json': return self.send(200, headers={'Content-Encoding': 'gzip'}, raw=gzip.compress(body))
        if mode == 'duplicate-sse':
            second = json.loads(canonical(envelope))
            if configuration['duplicate'] == 'contradictory-after-first': second['result']['structuredContent']['state'] = 'DEGRADED'
            frames = ('event: message\ndata: '+canonical(envelope)+'\n\nevent: message\ndata: '+canonical(second)+'\n\n').encode()
            return self.send(200, headers={'Content-Type': 'text/event-stream'}, raw=frames)
        if mode == 'partial-stream' or mode == 'response-timeout' and configuration['delayPoint'] == 'during-body':
            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.send_header('Content-Length', str(len(body))); self.end_headers()
            cut = len(body)//2
            if mode == 'partial-stream' and configuration['cutPoint'] == 'multibyte-character':
                cut = next(i+1 for i, value in enumerate(body) if value >= 0xE0)
            try: self.wfile.write(body[:cut]); self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError): return
            if mode == 'response-timeout': time.sleep(min(18, configuration['delaySeconds']))
            self.close_connection = True
            try: self.connection.shutdown(socket.SHUT_RDWR)
            except OSError: pass
            return
        if mode == 'response-timeout': time.sleep(min(18, configuration['delaySeconds']))
        self.send(200, raw=body)


servers = [ThreadingHTTPServer(('127.0.0.1', port), Handler) for port in (options.port, options.sink_port)]
for server in servers:
    server.daemon_threads = True
threading.Thread(target=servers[1].serve_forever, daemon=True).start()
servers[0].serve_forever()
