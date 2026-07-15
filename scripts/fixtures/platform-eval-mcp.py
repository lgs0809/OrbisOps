#!/usr/bin/env python3
"""Read-only frozen facts MCP peer; persistent SQLite is the independent dispatch ledger.

Reference answers are neither loaded nor mounted. Only new evaluation facts and
request receipts are used. No live business databases or production resources are
contacted; this is the sandbox permitted by the platform evaluation design.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--facts', type=Path, required=True)
parser.add_argument('--database', required=True)
parser.add_argument('--host', default='127.0.0.1')
parser.add_argument('--port', type=int, default=8581)
parser.add_argument('--kind', choices=['inspection', 'investigation', 'skill'], default='inspection')
args = parser.parse_args()
TOKEN = os.environ['MCP_ACCEPTANCE_TOKEN']
if len(TOKEN) < 32:
    raise RuntimeError('A initialized local acceptance token is required')
SOURCE_BYTES = args.facts.read_bytes()
SOURCE_HASH = hashlib.sha256(SOURCE_BYTES).hexdigest()
FACTS = {item['caseId']: item for item in json.loads(SOURCE_BYTES)}
SESSIONS = set()


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def db():
    connection = sqlite3.connect(args.database, timeout=10)
    connection.row_factory = sqlite3.Row
    return connection


with db() as connection:
    connection.executescript('''PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS calls(id INTEGER PRIMARY KEY AUTOINCREMENT,
          rpc_id TEXT NOT NULL, tool TEXT NOT NULL, case_id TEXT NOT NULL,
          project_id TEXT NOT NULL, received_at REAL NOT NULL, status TEXT NOT NULL,
          facts_sha256 TEXT, source_sha256 TEXT NOT NULL);''')


def tool_definition():
    inspection = args.kind == 'inspection'
    return {'name': args.kind+'_snapshot',
            'description': ({'inspection': '只读读取隔离巡检评测场景 inspection-001 至 inspection-050 的冻结指标与对象/窗口标识；',
                'investigation': '只读读取隔离开放调查场景 investigation-001 至 investigation-050 的冻结指标、日志、执行计划、连接归属、依赖协议和资源观测；',
                'skill': '只读读取合成Skill结构审核场景 skill-001 至 skill-040 的完整来源输入或包文件；不代表实际任务验收或Skill发布；'}[args.kind])+
                '这是合成沙箱观测，不是生产事实，不返回判分答案。开发与留出项目各只能读取自己的场景。',
            'inputSchema': {'type': 'object', 'required': ['caseId', 'projectId'],
                'properties': {'caseId': {'type': 'string', 'pattern': '^'+args.kind+'-[0-9]{3}$'},
                               'projectId': {'type': 'string', 'enum': sorted({record['projectId'] for record in FACTS.values()})}},
                'additionalProperties': False},
            'outputSchema': {'type': 'object', 'required': ['caseId', 'source']+(['target', 'observation'] if inspection else ['scope', 'observations']),
                'properties': {'caseId': {'type': 'string'}, 'source': {'type': 'string'},
                               **({'target': {'type': 'object'}, 'observation': {'type': 'object'}} if inspection else
                                  {'scope': {'type': 'object'}, 'observations': {'type': 'object'}})}},
            'annotations': {'readOnlyHint': True, 'destructiveHint': False}}


def response(request):
    method, params = request.get('method'), request.get('params') or {}
    if 'id' not in request:
        return None
    if method == 'initialize':
        result = {'protocolVersion': params.get('protocolVersion', '2024-11-05'),
                  'capabilities': {'tools': {'listChanged': False}},
                  'serverInfo': {'name': 'orbisops-frozen-platform-evaluation', 'version': '1.0.0'}}
    elif method == 'tools/list':
        result = {'tools': [tool_definition()]}
    elif method == 'ping':
        result = {}
    elif method == 'tools/call':
        inputs = params.get('arguments') or {}
        case_id, project_id = inputs.get('caseId', ''), inputs.get('projectId', '')
        record = FACTS.get(case_id)
        allowed = (params.get('name') == args.kind+'_snapshot' and set(inputs) == {'caseId', 'projectId'}
                   and record is not None and record['projectId'] == project_id
                   and hashlib.sha256(args.facts.read_bytes()).hexdigest() == SOURCE_HASH)
        with db() as connection:
            connection.execute('INSERT INTO calls(rpc_id,tool,case_id,project_id,received_at,status,facts_sha256,source_sha256) VALUES(?,?,?,?,?,?,?,?)',
                (str(request['id']), params.get('name', ''), case_id, project_id, time.time(),
                 'SUCCEEDED' if allowed else 'DENIED',
                 hashlib.sha256(canonical(record['facts']).encode()).hexdigest() if allowed else None, SOURCE_HASH))
        if not allowed:
            return {'jsonrpc': '2.0', 'id': request['id'], 'error': {'code': -32602, 'message': 'FROZEN_CASE_OR_PROJECT_NOT_AUTHORIZED'}}
        result = {'content': [], 'isError': False, 'structuredContent': record['facts']}
    else:
        return {'jsonrpc': '2.0', 'id': request['id'], 'error': {'code': -32601, 'message': 'UNKNOWN_TOOL'}}
    return {'jsonrpc': '2.0', 'id': request['id'], 'result': result}


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def log_message(self, *_):
        pass

    def send(self, status, value=None, headers=None):
        body = json.dumps(value, ensure_ascii=False).encode() if value is not None else b''
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        for name, value in (headers or {}).items():
            self.send_header(name, value)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def authorized(self):
        if self.headers.get('Authorization') != 'Bearer ' + TOKEN:
            self.send(401, {'error': 'UNAUTHORIZED'})
            return False
        return True

    def do_GET(self):
        if self.path == '/ready':
            return self.send(200, {'sourceSha256': SOURCE_HASH, 'cases': len(FACTS)})
        if not self.authorized():
            return
        if self.path == '/evidence':
            with db() as connection:
                return self.send(200, {'sourceSha256': SOURCE_HASH, 'calls': [dict(row) for row in connection.execute('SELECT * FROM calls ORDER BY id')]})
        self.send(405)

    def do_DELETE(self):
        if self.authorized():
            self.send(200)

    def do_POST(self):
        if not self.authorized():
            return
        length = int(self.headers.get('Content-Length', 0))
        if not 0 < length < 16384:
            return self.send(413)
        request = json.loads(self.rfile.read(length))
        if request.get('method') != 'initialize' and self.headers.get('Mcp-Session-Id') not in SESSIONS:
            return self.send(404, {'error': 'INITIALIZE_REQUIRED'})
        result, headers = response(request), {}
        if request.get('method') == 'initialize':
            headers['Mcp-Session-Id'] = str(uuid.uuid4())
            SESSIONS.add(headers['Mcp-Session-Id'])
        self.send(202 if result is None else 200, result, headers)


ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()
