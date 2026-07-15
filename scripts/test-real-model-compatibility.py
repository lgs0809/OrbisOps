#!/usr/bin/env python3
"""Opt-in real Luna transport smoke through a temporary loopback relay.

The relay forwards only the fixed smoke request to the configured acceptance provider.
It never retries or logs credentials/prompt bodies. Maven output and wire counts are retained.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import runpy
import re
import subprocess
import threading
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def main(output):
    if output.exists() or output.with_suffix('.log').exists():
        raise ValueError('Use a new evidence filename')
    support = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    binding = support['rows']("SELECT JSON_OBJECT('base',a.base_url,'path',a.completions_path,'key',a.api_key) "
        "FROM ai_client_api a JOIN ai_client_model m ON a.api_id=m.api_id "
        "WHERE m.model_name='gpt-5.6-luna' AND a.status=1 AND m.status=1")
    if len(binding) != 1:
        raise ValueError('Exactly one active Luna binding is required')
    def resolve(value):
        return re.sub(r'\$\{env:([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?}',
            lambda m: support['values'].get(m[1]) or os.environ.get(m[1]) or m[2] or '', value)
    bound = {name: resolve(value) for name, value in binding[0].items()}
    key = bound['key']
    if not key or key.startswith('${'):
        raise ValueError('Configured provider credential unavailable')
    destination = bound['base'].rstrip('/') + '/' + bound['path'].lstrip('/')
    calls = []
    rejections = []

    class Relay(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_POST(self):
            length = int(self.headers.get('Content-Length', '0'))
            if self.path != '/v1/chat/completions':
                rejections.append({'reason': 'PATH', 'path': self.path})
                self.send_error(400)
                return
            if self.headers.get('Authorization') != 'Bearer local-evaluation-relay':
                self.send_error(401)
                return
            if self.headers.get('Transfer-Encoding', '').lower() == 'chunked':
                chunks = []
                while True:
                    size = int(self.rfile.readline(64).split(b';')[0], 16)
                    if size == 0:
                        self.rfile.readline(64)
                        break
                    if sum(map(len, chunks)) + size > 8192:
                        self.send_error(413)
                        return
                    chunks.append(self.rfile.read(size))
                    if self.rfile.read(2) != b'\r\n':
                        self.send_error(400)
                        return
                body = b''.join(chunks)
            elif 1 <= length <= 8192:
                body = self.rfile.read(length)
            else:
                rejections.append({'reason': 'BODY_LENGTH', 'length': length})
                self.send_error(400)
                return
            request = json.loads(body)
            messages = request.get('messages', [])
            if (request.get('model') != 'gpt-5.6-luna' or request.get('stream')
                    or len(messages) != 1 or messages[0].get('role') != 'user'
                    or messages[0].get('content') != 'Reply exactly SPRING_AI_REAL_OK'):
                self.send_error(400)
                rejections.append({'reason': 'FIXED_SMOKE_REQUEST', 'model': request.get('model'),
                    'messageCount': len(messages), 'contentType': type(messages[0].get('content')).__name__ if messages else None})
                return
            started = time.monotonic()
            fact = {'requestedModel': request['model'], 'receivedPath': self.path}
            calls.append(fact)
            try:
                req = urllib.request.Request(destination, body,
                    {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + key})
                with urllib.request.urlopen(req, timeout=60) as response:
                    payload = response.read()
                    fact.update(http=response.status, responseModel=json.loads(payload).get('model'))
                    self.send_response(response.status)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            except Exception as error:
                fact.update(failureType=type(error).__name__)
                try:
                    self.send_error(502, 'Provider request failed')
                except OSError:
                    pass
            finally:
                fact['seconds'] = round(time.monotonic() - started, 3)

    server = ThreadingHTTPServer(('127.0.0.1', 0), Relay)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    output.parent.mkdir(parents=True, exist_ok=True)
    result = {'status': 'RUNNING', 'scope': 'REAL_PROVIDER_REAL_SPRING_AI_HTTP', 'calls': calls, 'rejections': rejections}
    command = ['mvn', '-f', 'server/pom.xml', '-Dtest=OpsOpenAiCompatibilityRealSmokeTest',
        '-Dsurefire.failIfNoSpecifiedTests=false',
        '-Dreal.model.relay.url=http://127.0.0.1:' + str(server.server_port) + '/', 'test']
    result['command'] = command
    try:
        with output.with_suffix('.log').open('x') as log:
            process = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=600)
        result['exitCode'] = process.returncode
        result['status'] = 'PASS' if process.returncode == 0 and len(calls) == 1 and calls[0].get('responseModel') == 'gpt-5.6-luna' else 'FAIL'
    finally:
        server.shutdown()
        server.server_close()
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))
    if result['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    main(parser.parse_args().output)
