#!/usr/bin/env python3
"""Isolated, real JSON-RPC MCP peer. SQLite is the independent request/receipt authority.

Only synthetic test records are writable. No business database or external resource is contacted.
Both HTTP and stdio use the same persistent state, allowing process-exit/reinitialize tests.
"""
import argparse
import gzip
import json
import os
import sqlite3
import sys
import threading
import time
import uuid
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

parser = argparse.ArgumentParser()
parser.add_argument('--database', required=True)
parser.add_argument('--stdio', action='store_true')
parser.add_argument('--discovery-tools', type=int, default=0)
parser.add_argument('--host', default='127.0.0.1')
parser.add_argument('--port', type=int, default=0)
args = parser.parse_args()
if not 0 <= args.discovery_tools <= 10000:
    parser.error('--discovery-tools must be between 0 and 10000')
token = os.environ.get('MCP_ACCEPTANCE_TOKEN', '')
sessions = set()


def db():
    conn = sqlite3.connect(args.database, timeout=10)
    conn.row_factory = sqlite3.Row
    return conn


with db() as conn:
    conn.executescript('''
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS requests(
          seq INTEGER PRIMARY KEY AUTOINCREMENT, rpc_id TEXT, method TEXT, tool TEXT,
          test_id TEXT, mode TEXT, received_at REAL NOT NULL);
        CREATE TABLE IF NOT EXISTS services(service_key TEXT PRIMARY KEY, state TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS receipts(execution_key TEXT PRIMARY KEY, service_key TEXT NOT NULL
          REFERENCES services(service_key), value INTEGER NOT NULL, created_at REAL NOT NULL);
        INSERT OR IGNORE INTO services VALUES('normal','HEALTHY'),('fault','FAILED'),('boundary','INSUFFICIENT_DATA');
        CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT NOT NULL);
    ''')

    if args.discovery_tools:
        conn.execute('CREATE TABLE IF NOT EXISTS discovery_services(service_id TEXT PRIMARY KEY, version TEXT NOT NULL, state TEXT NOT NULL)')
        conn.executemany('INSERT OR IGNORE INTO discovery_services VALUES(?,?,?)',
                         [(f'discovery-service-{i:02}', f'discovery-v{i}', 'DEGRADED' if i % 7 == 0 else 'HEALTHY')
                          for i in range(1, args.discovery_tools + 1)])


def discovery_tool(index):
    service = f'discovery-service-{index:02}'
    return dict(name=f'service_{index:02}_status',
                description=f'只读查询目录验收服务 {service} 的当前部署版本 version 与健康状态 health status，实际来源是隔离 SQLite 服务记录。',
                inputSchema=dict(type='object', properties={'requestId': dict(type='string', minLength=1)},
                                 required=['requestId'], additionalProperties=False),
                outputSchema=dict(type='object', properties={'count': dict(type='integer'), 'serviceId': dict(type='string'),
                                  'version': dict(type='string'), 'state': dict(type='string')},
                                  required=['count','serviceId','version','state']),
                annotations=dict(readOnlyHint=True, destructiveHint=False))


def discovery_result(name, inputs, count):
    allowed = {f'service_{i:02}_status': f'discovery-service-{i:02}' for i in range(1, args.discovery_tools + 1)}
    if name not in allowed or set(inputs) != {'requestId'} or not isinstance(inputs['requestId'], str) or not inputs['requestId'].strip():
        raise ValueError('UNKNOWN_TOOL_OR_INVALID_READ_REQUEST')
    with db() as conn:
        row = conn.execute('SELECT * FROM discovery_services WHERE service_id=?', (allowed[name],)).fetchone()
    if row is None:
        raise ValueError('SERVICE_NOT_FOUND')
    return dict(content=[], isError=False, structuredContent=dict(count=count, serviceId=row['service_id'],
                                                                 version=row['version'], state=row['state']))


def tool(name, output=True):
    result = dict(name=name, description='Isolated MCP acceptance fixture: ' + name,
                  inputSchema=dict(type='object', properties=dict(
                      testId=dict(type='string'), mode=dict(type='string'), executionKey=dict(type='string'),
                      contentEncoding=dict(type='string', enum=['gzip', 'deflate']),
                      service=dict(type='string'), value=dict(type='integer'), delayMs=dict(type='integer', minimum=0)),
                      required=['testId', 'executionKey'] if name == 'append_record' else ['testId'], additionalProperties=False),
                  annotations=dict(readOnlyHint=name != 'append_record', destructiveHint=False))
    if output:
        result['outputSchema'] = dict(type='object', properties={'count': dict(type='integer')}, required=['count'])
    with db() as conn:
        revision = conn.execute("SELECT value FROM settings WHERE key='schemaRevision'").fetchone()
    if revision and revision['value'] == '2' and name == 'probe':
        result['outputSchema']['required'].append('revision')
    return result


def response(request):
    method = request.get('method', '')
    params = request.get('params') or {}
    name = params.get('name', '')
    inputs = params.get('arguments') or {}
    test_id, mode = inputs.get('testId', inputs.get('requestId', '')), inputs.get('mode', 'structured')
    with db() as conn:
        conn.execute('INSERT INTO requests(rpc_id,method,tool,test_id,mode,received_at) VALUES(?,?,?,?,?,?)',
                     (str(request.get('id', '')), method, name, test_id, mode, time.time()))
        count = conn.execute('SELECT COUNT(*) FROM requests WHERE method=? AND test_id=?',
                             ('tools/call', test_id)).fetchone()[0]
    if 'id' not in request:
        return None, ''
    result = {}
    if method == 'initialize':
        result = dict(protocolVersion=params.get('protocolVersion', '2024-11-05'), capabilities={'tools': {'listChanged': False}},
                      serverInfo=dict(name='orbisops-isolated-acceptance', version='1.0'))
    elif method == 'tools/list':
        result = {'tools': [discovery_tool(i) for i in range(1, args.discovery_tools + 1)] if args.discovery_tools else
                  [tool('probe'), tool('plain_text', False), tool('append_record'), tool('receipt')]}
        with db() as conn:
            revision = conn.execute("SELECT value FROM settings WHERE key='schemaRevision'").fetchone()
        if revision and revision['value'] == '2' and not args.discovery_tools:
            result['tools'].append(tool('new_catalog_read'))
        elif revision and revision['value'] == '3' and not args.discovery_tools:
            result['tools'][0]['inputSchema'] = {'type': 'string'}
    elif method == 'tools/call' and args.discovery_tools:
        try:
            result = discovery_result(name, inputs, count)
        except ValueError as error:
            return dict(jsonrpc='2.0', id=request['id'], error=dict(code=-32602, message=str(error))), ''
    elif method == 'tools/call':
        if mode == 'delay':
            time.sleep(min(65000, inputs.get('delayMs', 0)) / 1000)
        if mode in ('disconnect_once', 'session404_once', 'stdio_exit_once', 'sse_disconnect_once') and count == 1:
            return None, mode
        if mode in ('disconnect_always', 'session404_always'):
            return None, mode
        if mode == 'malformed':
            return None, mode
        if mode in ('http401', 'http403') or mode == 'http503_once' and count == 1:
            return None, mode
        if mode in ('rpc_error', 'rpc_internal'):
            return dict(jsonrpc='2.0', id=request['id'], error=dict(
                code=-32602 if mode == 'rpc_error' else -32603, message='synthetic RPC failure')), ''
        if mode == 'tool_error':
            result = dict(isError=True, content=[dict(type='text', text='synthetic invalid parameter or no data')],
                          structuredContent={'reason': 'NO_DATA'}, _meta={'fixture': True})
        elif mode == 'empty':
            result = dict(isError=False, content=[])
        elif mode == 'bad_json':
            result = dict(content=[dict(type='text', text='{not-json')], isError=False)
        elif mode == 'missing_field' or mode == 'contract_then_disconnect' and count == 1:
            result = dict(content=[], structuredContent={'wrong': True}, isError=False)
        elif mode == 'contract_then_disconnect' and count > 1:
            return None, 'disconnect_always'
        elif mode == 'oversize':
            result = dict(content=[dict(type='text', text='x' * (1024 * 1024 + 1))], isError=False)
        elif name == 'plain_text':
            result = dict(content=[dict(type='text', text='合法纯文本结果。'), dict(type='text', text='第二段内容。')], isError=False)
        elif name == 'append_record':
            key = inputs.get('executionKey', '')
            if not key:
                return dict(jsonrpc='2.0', id=request['id'], error=dict(code=-32602, message='executionKey required')), ''
            with db() as conn:
                conn.execute('PRAGMA foreign_keys=ON')
                old = conn.execute('SELECT * FROM receipts WHERE execution_key=?', (key,)).fetchone()
                if old and (old['service_key'] != inputs.get('service', 'normal') or old['value'] != inputs.get('value', 1)):
                    return dict(jsonrpc='2.0', id=request['id'], error=dict(code=-32602, message='execution identity conflict')), ''
                conn.execute('INSERT OR IGNORE INTO receipts VALUES(?,?,?,?)',
                             (key, inputs.get('service', 'normal'), inputs.get('value', 1), time.time()))
            if mode == 'disconnect_after_commit':
                return None, 'disconnect_always'
            result = dict(content=[], structuredContent=dict(count=1, executionKey=key, committed=True), isError=False)
        elif name == 'receipt':
            with db() as conn:
                row = conn.execute('SELECT * FROM receipts WHERE execution_key=?', (inputs.get('executionKey', ''),)).fetchone()
            result = dict(content=[], structuredContent=dict(count=1 if row else 0, receipt=dict(row) if row else None), isError=False)
        elif mode == 'multiple':
            result = dict(content=[dict(type='text', text='{"count":'), dict(type='text', text=str(count) + '}')], isError=False)
        elif mode == 'text_json':
            result = dict(content=[dict(type='text', text=json.dumps(dict(count=count)))], isError=False)
        else:
            result = dict(content=[], structuredContent=dict(count=count, service='normal'), isError=False, _meta={'fixture': True})
    elif method != 'ping':
        return dict(jsonrpc='2.0', id=request['id'], error=dict(code=-32601, message='unknown method')), ''
    return dict(jsonrpc='2.0', id=request['id'], result=result), ''


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def log_message(self, *_):
        pass

    def send(self, status, value=None, headers=None):
        body = json.dumps(value, ensure_ascii=False).encode() if value is not None else b''
        return self.send_bytes(status, body, headers)

    def send_bytes(self, status, body, headers=None):
        encoding = (headers or {}).get('Content-Encoding')
        if encoding == 'gzip': body = gzip.compress(body)
        elif encoding == 'deflate': body = zlib.compress(body)
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def authorized(self):
        if token and self.headers.get('Authorization') != 'Bearer ' + token:
            self.send(401, {'error': 'unauthorized'})
            return False
        return True

    def do_GET(self):
        if self.path == '/ready':
            return self.send(200, {'status': 'ready'})
        if not self.authorized():
            return
        if self.path == '/evidence':
            with db() as conn:
                return self.send(200, {table: [dict(row) for row in conn.execute('SELECT * FROM ' + table)]
                                       for table in ('requests', 'receipts', 'services')})
        self.send(405)

    def do_DELETE(self):
        self.send(200)

    def do_POST(self):
        if not self.authorized():
            return
        request = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))))
        if self.path == '/control':
            with db() as conn:
                conn.execute("INSERT INTO settings VALUES('schemaRevision',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                             (str(request.get('schemaRevision', 1)),))
            return self.send(200, {'applied': True})
        if request.get('method') != 'initialize' and self.headers.get('Mcp-Session-Id') not in sessions:
            return self.send(404, {'error': 'session no longer exists; initialize again'})
        result, fault = response(request)
        if fault.startswith('http'):
            return self.send(int(fault[4:7]), {'error': 'synthetic HTTP failure'})
        if fault.startswith('session404'):
            return self.send(404, {'error': 'session expired'})
        if fault.startswith('sse_disconnect'):
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Connection', 'close')
            self.end_headers()
            self.wfile.write(b': synthetic stream disconnected\n\n')
            self.wfile.flush()
        elif fault == 'malformed':
            encoding = (request.get('params') or {}).get('arguments', {}).get('contentEncoding')
            self.send_bytes(200, b'{not-json', {'Content-Encoding': encoding} if encoding else {})
        if fault:
            self.close_connection = True
            return
        headers = {}
        encoding = (request.get('params') or {}).get('arguments', {}).get('contentEncoding')
        if encoding: headers['Content-Encoding'] = encoding
        if request.get('method') == 'initialize':
            headers['Mcp-Session-Id'] = str(uuid.uuid4())
            sessions.add(headers['Mcp-Session-Id'])
        self.send(202 if result is None else 200, result, headers)


if args.stdio:
    for line in sys.stdin:
        result, fault = response(json.loads(line))
        if fault:
            os._exit(0)
        if result is not None:
            print(json.dumps(result, ensure_ascii=False), flush=True)
else:
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(json.dumps({'port': server.server_port}), flush=True)
    server.serve_forever()
