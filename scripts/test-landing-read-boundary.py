#!/usr/bin/env python3
"""Exercise deployed independent-read authority against real reviewed write tools; never write target state."""
import argparse
import datetime as dt
import json
from pathlib import Path
import runpy
import urllib.request
import urllib.error
import uuid

ROOT = Path(__file__).resolve().parents[1]


def run(output):
    h = runpy.run_path(str(ROOT / "scripts/test-mcp-runtime.py"))
    api, token, q = h["api"], h["token"], h["quoted"]
    actor = next(u for u in api('/api/v1/admin/admin-user/query-all', token=token)
                 if u['username'] == 'ops_acceptance_admin')
    tool = next(t for t in api('/api/v1/admin/ops/projects/ops-acceptance-a/tools', token=token)
                if t['mcpName'] == 'OPS-08 isolated dual target')
    results, queries = [], []
    for env in ('test', 'prod'):
        run_id = 'ops08-read-denied-' + uuid.uuid4().hex
        body = {'projectId': 'ops-acceptance-a', 'userId': actor['userId'],
                'authenticatedUsername': actor['username'], 'toolsetId': 'mcp.' + tool['mcpId'],
                'toolName': env + '_apply_configuration', 'executionScope': 'PRE_APPROVAL_WORKFLOW',
                'requireReadOnly': True, 'runId': run_id, 'idempotencyKey': run_id,
                'arguments': {'projectId': 'ops-acceptance-a', 'service': 'ops-acc-a-service-2',
                              'expectedVersion': 'fixture-1', 'version': 'read-must-not-write',
                              'scenario': 'HEALTHY', 'executionKey': run_id, 'actor': actor['username'],
                              'deadline': (dt.datetime.now(dt.timezone.utc) + dt.timedelta(minutes=2)).isoformat()}}
        request = urllib.request.Request('http://127.0.0.1:18089/api/v1/admin/ops/tool-executions',
                json.dumps(body).encode(), {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})
        try:
            response = urllib.request.urlopen(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            result = {'environment': env, 'request': body, 'httpStatus': response.code, 'response': json.load(response)}
        query = "SELECT JSON_OBJECT('remoteResults',COUNT(*)) FROM ai_ops_tool_result WHERE run_id=" + q(run_id) + " AND source='MCP_REMOTE_TOOL'"
        queries.append(query)
        result['remoteResults'] = h['rows'](query)
        results.append(result)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(';\n'.join(queries) + ';\n')
    proof = {'status': 'NOT_PASSED', 'cases': results}
    try:
        for result in results:
            assert 'READ_ONLY_REQUIRED' in json.dumps(result['response']), result['response']
            assert result['remoteResults'] == [{'remoteResults': 0}]
        proof['status'] = 'PASS'
    finally:
        output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': proof['status'], 'deniedWriteTools': len(results), 'remoteBusinessCalls': 0}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    run(parser.parse_args().output)
