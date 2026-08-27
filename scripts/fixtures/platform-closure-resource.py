#!/usr/bin/env python3
"""Actual isolated HTTP resources with atomic SQLite CAS/receipts and fault injection.

No reference answers, model, platform approvals or task success are implemented
here. An acknowledged resource change alone is never a platform acceptance.
"""
import argparse
import datetime as dt
import hashlib
import hmac
import json
import os
import socket
import sqlite3
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

parser = argparse.ArgumentParser()
parser.add_argument('--environment', choices=['test', 'prod'], required=True)
parser.add_argument('--port', type=int, required=True)
parser.add_argument('--database', required=True)
args = parser.parse_args()
TOKEN = os.environ['CLOSURE_CONTROL_TOKEN']
PROJECTS = {'ops-platform-closure-dev', 'ops-platform-closure-holdout', 'ops-closure-business-a', 'ops-acceptance-a'}
if len(TOKEN) < 30: raise RuntimeError('LOCAL_CONTROL_CREDENTIAL_REQUIRED')


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def digest(value): return hashlib.sha256(canonical(value).encode()).hexdigest()

def valid_configuration(value):
    return isinstance(value, dict) and set(value)=={'delayMs','failEvery'} and all(
        type(value[key]) is int and 0 <= value[key] <= maximum
        for key,maximum in [('delayMs',500),('failEvery',20)])


def database():
    connection = sqlite3.connect(args.database, timeout=15)
    connection.row_factory = sqlite3.Row
    return connection


with database() as db:
    db.executescript('''PRAGMA journal_mode=WAL;
      CREATE TABLE IF NOT EXISTS resource(resource_key TEXT PRIMARY KEY, project_id TEXT NOT NULL,
        seed_hash TEXT NOT NULL, version TEXT NOT NULL, configuration_json TEXT NOT NULL, epoch INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS receipt(execution_key TEXT PRIMARY KEY, resource_key TEXT NOT NULL,
        command_hash TEXT NOT NULL, body_json TEXT NOT NULL, created_at REAL NOT NULL);
      CREATE TABLE IF NOT EXISTS request(trace_id TEXT PRIMARY KEY, resource_key TEXT NOT NULL,
        version TEXT NOT NULL, status INTEGER NOT NULL, duration_ms REAL NOT NULL, observed_at REAL NOT NULL);
      CREATE TABLE IF NOT EXISTS fault(resource_key TEXT PRIMARY KEY, profile_json TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS dispatch(id INTEGER PRIMARY KEY, resource_key TEXT, operation TEXT,
        received_at REAL, status TEXT, execution_key TEXT);
      CREATE TABLE IF NOT EXISTS audit_request(id INTEGER PRIMARY KEY, resource_key TEXT, project_id TEXT,
        path TEXT, method TEXT, rpc_id TEXT, received_at REAL, http_status INTEGER);
      CREATE TABLE IF NOT EXISTS command_evidence(execution_key TEXT PRIMARY KEY, resource_key TEXT NOT NULL,
        command_json TEXT NOT NULL, before_state_json TEXT NOT NULL, after_state_json TEXT NOT NULL);
    ''')


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def setup(self):
        super().setup(); self.connection.settimeout(30)

    def log_message(self, *unused): pass

    def send(self, status, value, trace=None):
        if getattr(self, 'audit_id', None):
            with database() as db: db.execute('UPDATE audit_request SET http_status=? WHERE id=?', (status, self.audit_id))
        body = canonical(value).encode()
        self.send_response(status); self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        if trace: self.send_header('X-Trace-Id', trace)
        self.end_headers()
        try: self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError): pass

    def authorized(self):
        return hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer '+TOKEN)

    def no_change(self, db, status, value):
        db.rollback()
        return self.send(status, value)

    def audit(self, path, value):
        with database() as db:
            self.audit_id = db.execute('INSERT INTO audit_request(resource_key,project_id,path,method,rpc_id,received_at) VALUES(?,?,?,?,?,?)',
                (value.get('resourceKey', ''), value.get('projectId', ''), path, self.command,
                 self.headers.get('X-Orbis-Rpc-Id', ''), time.time())).lastrowid

    def resource(self, db, value):
        project, key = value.get('projectId'), value.get('resourceKey')
        if project not in PROJECTS or not isinstance(key, str) or not key.startswith('service://closure-') or not key.endswith('/'+args.environment):
            raise PermissionError('RESOURCE_SCOPE_MISMATCH')
        row = db.execute('SELECT * FROM resource WHERE resource_key=? AND project_id=?', (key, project)).fetchone()
        if row is None: raise LookupError('RESOURCE_NOT_FOUND')
        return row

    def state(self, row):
        return {'status': 'AVAILABLE', 'projectId': row['project_id'], 'environment': args.environment,
            'resourceKey': row['resource_key'], 'version': row['version'], 'epoch': row['epoch'],
            'configuration': json.loads(row['configuration_json']), 'observedAt': dt.datetime.now(dt.timezone.utc).isoformat()}

    def do_GET(self):
        self.audit_id = None
        uri = urlparse(self.path)
        if uri.path == '/ready': return self.send(200, {'environment': args.environment, 'scope': 'ACTUAL_SIMULATED_LOCAL_RESOURCE'})
        value = {key: child[0] for key, child in parse_qs(uri.query).items()}
        self.audit(uri.path, value)
        if not self.authorized(): return self.send(403, {'error': 'UNAUTHORIZED'})
        try:
            with database() as db:
                row = self.resource(db, value); key = row['resource_key']
                if uri.path == '/control/state': return self.send(200, self.state(row))
                if uri.path == '/control/receipt':
                    receipt = db.execute('SELECT body_json FROM receipt WHERE resource_key=? AND execution_key=?',
                        (key, value.get('executionKey'))).fetchone()
                    return self.send(200, {'status': 'FOUND' if receipt else 'NOT_FOUND','projectId':row['project_id'],
                        'receipt': json.loads(receipt[0]) if receipt else None, 'resourceKey': key, 'environment': args.environment})
                if uri.path == '/evidence':
                    return self.send(200, {'state': self.state(row), **{name: [dict(item) for item in db.execute(
                        'SELECT * FROM '+name+' WHERE resource_key=?', (key,))] for name in ['receipt', 'request', 'dispatch', 'audit_request', 'command_evidence']}})
                if uri.path == '/orders':
                    started = time.monotonic(); configuration = json.loads(row['configuration_json'])
                    fault = db.execute('SELECT profile_json FROM fault WHERE resource_key=?', (key,)).fetchone()
                    fault = json.loads(fault[0]) if fault else {}
                    count = db.execute('SELECT COUNT(*) FROM request WHERE resource_key=?', (key,)).fetchone()[0]+1
                    rate = fault.get('failEvery', 0) if fault.get('postVersion') == row['version'] else configuration.get('failEvery', 0)
                    status = 503 if rate and count % int(rate) == 0 else 200
                    time.sleep(min(0.5, max(0, float(configuration.get('delayMs', 0))/1000)))
                    trace = 'closure-http-'+uuid.uuid4().hex
                    duration_ms = round((time.monotonic()-started)*1000, 3)
                    db.execute('INSERT INTO request VALUES(?,?,?,?,?,?)', (trace, key, row['version'], status,
                        duration_ms, time.time()))
                    # This is the exact monotonic measurement persisted with the
                    # trace receipt; callers can independently compare SQLite.
                    body = {**self.state(row), 'traceId': trace, 'httpStatus': status,
                        'durationMs': duration_ms}
                    db.commit(); return self.send(status, body, trace)
        except PermissionError as failure: return self.send(403, {'error': str(failure)})
        except LookupError as failure: return self.send(404, {'error': str(failure)})
        self.send(404, {'error': 'NOT_FOUND'})

    def do_POST(self):
        self.audit_id = None
        length = int(self.headers.get('Content-Length', 0))
        if length > 100_000: return self.send(413, {'error': 'REQUEST_BUDGET'})
        try:
            value = json.loads(self.rfile.read(length)); path = urlparse(self.path).path
            if not isinstance(value, dict): return self.send(400, {'error': 'INVALID_REQUEST'})
            self.audit(path, value)
            if not self.authorized(): return self.send(403, {'error': 'UNAUTHORIZED'})
            with database() as db:
                if path == '/control/seed':
                    key, project = value['resourceKey'], value['projectId']
                    if not isinstance(key, str) or not isinstance(value.get('version'), str) or not value['version'] or not valid_configuration(value.get('configuration')):
                        return self.send(400, {'error': 'INVALID_REQUEST'})
                    if project not in PROJECTS or not key.startswith('service://closure-') or not key.endswith('/'+args.environment):
                        raise PermissionError('RESOURCE_SCOPE_MISMATCH')
                    seed_hash = digest(value)
                    existing = db.execute('SELECT seed_hash FROM resource WHERE resource_key=?', (key,)).fetchone()
                    if existing and existing[0] != seed_hash: return self.send(409, {'error': 'EXISTING_RESOURCE_PRESERVED'})
                    db.execute('INSERT OR IGNORE INTO resource VALUES(?,?,?,?,?,0)',
                        (key, project, seed_hash, value['version'], canonical(value['configuration'])))
                    db.commit(); return self.send(200, self.state(self.resource(db, value)))
                db.execute('BEGIN IMMEDIATE'); row = self.resource(db, value); key = row['resource_key']
                if path == '/control/fault':
                    db.execute('INSERT INTO fault VALUES(?,?) ON CONFLICT(resource_key) DO UPDATE SET profile_json=excluded.profile_json',
                        (key, canonical(value.get('profile', {}))))
                    db.commit(); return self.send(200, {'status': 'FAULT_PROFILE_SET', 'resourceKey': key})
                if path != '/control/apply': return self.no_change(db, 404, {'error': 'NOT_FOUND'})
                command = {k: value[k] for k in ['projectId', 'resourceKey', 'expectedVersion', 'version', 'configuration', 'executionKey', 'deadline']}
                if 'expectedEpoch' in value:command['expectedEpoch']=value['expectedEpoch']
                if not all(isinstance(command[k], str) and command[k] for k in ['expectedVersion', 'version', 'executionKey', 'deadline']) or not valid_configuration(command['configuration']) or ('expectedEpoch' in command and (type(command['expectedEpoch']) is not int or command['expectedEpoch']<0)):
                    return self.no_change(db, 400, {'error': 'INVALID_REQUEST'})
                command_hash, execution = digest(command), command['executionKey']
                existing = db.execute('SELECT command_hash,body_json FROM receipt WHERE execution_key=?', (execution,)).fetchone()
                if existing:
                    if existing[0] != command_hash: return self.no_change(db, 409, {'error': 'IDEMPOTENCY_CONFLICT'})
                    return self.no_change(db, 200, json.loads(existing[1]))
                deadline = dt.datetime.fromisoformat(command['deadline'].replace('Z', '+00:00'))
                if deadline.tzinfo is None or deadline <= dt.datetime.now(dt.timezone.utc):
                    return self.no_change(db, 409, {'error': 'AUTHORITY_EXPIRED'})
                if row['version'] != command['expectedVersion']: return self.no_change(db, 409, {'error': 'VERSION_CONFLICT'})
                if 'expectedEpoch' in command and row['epoch'] != command['expectedEpoch']:return self.no_change(db,409,{'error':'EPOCH_CONFLICT'})
                profile = db.execute('SELECT profile_json FROM fault WHERE resource_key=?', (key,)).fetchone()
                profile = json.loads(profile[0]) if profile else {}
                if profile.get('beforeCommit'): return self.no_change(db, 503, {'error': 'INJECTED_BEFORE_COMMIT'})
                epoch = row['epoch']+1
                changed=db.execute('UPDATE resource SET version=?,configuration_json=?,epoch=? WHERE resource_key=? AND version=? AND epoch=?',
                    (command['version'], canonical(command['configuration']), epoch, key, command['expectedVersion'],row['epoch'])).rowcount
                if changed!=1:return self.no_change(db,409,{'error':'CONCURRENT_STATE_CONFLICT'})
                body = {'status': 'APPLIED', 'resourceKey': key, 'projectId': row['project_id'], 'environment': args.environment,
                    'executionKey': execution, 'commandHash': command_hash, 'beforeVersion': row['version'],
                    'version': command['version'], 'epoch': epoch, 'appliedAt': dt.datetime.now(dt.timezone.utc).isoformat()}
                db.execute('INSERT INTO receipt VALUES(?,?,?,?,?)', (execution, key, command_hash, canonical(body), time.time()))
                db.execute('INSERT INTO command_evidence VALUES(?,?,?,?,?)',
                    (execution, key, canonical(command), canonical(self.state(row)),
                     canonical(self.state(self.resource(db, value)))))
                db.execute('INSERT INTO dispatch(resource_key,operation,received_at,status,execution_key) VALUES(?,?,?,?,?)',
                    (key, 'APPLY', time.time(), 'COMMITTED', execution))
                db.commit()
                if profile.get('dropAfterCommit'):
                    self.close_connection = True
                    try: self.connection.shutdown(socket.SHUT_RDWR)
                    except OSError: pass
                    return
                return self.send(200, body)
        except PermissionError as failure: return self.send(403, {'error': str(failure)})
        except LookupError as failure: return self.send(404, {'error': str(failure)})
        except (KeyError, TypeError, ValueError): return self.send(400, {'error': 'INVALID_REQUEST'})


server = ThreadingHTTPServer(('127.0.0.1', args.port), Handler)
server.daemon_threads = True
server.serve_forever()
