#!/usr/bin/env python3
"""Real OrbisOps/MCP/MySQL validation checks without configuration writes or reset."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import urllib.error
import urllib.request
import uuid
import time

ROOT = Path(__file__).resolve().parents[1]


def run(output):
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    actor = next(u for u in h['api']('/api/v1/admin/admin-user/query-all', token=h['token'])
                 if u['username'] == 'ops_acceptance_admin')
    queries = []
    def rows(query):
        queries.append(query)
        return h['rows'](query)
    def state():
        return {db: rows("SELECT JSON_OBJECT('version',version,'scenario',scenario,'receiptCount',"
                        '(SELECT COUNT(*) FROM ' + db + '.ops08_deployment_receipt)) FROM '
                        + db + ".acceptance_service WHERE service_id='ops-acc-a-service-2'")[0]
                for db in ('ops_acceptance_business_prepare', 'ops_acceptance_business_a')}
    proof = {'status': 'NOT_PASSED', 'scope': 'REAL_READONLY_VALIDATION_COMPONENT', 'checks': []}
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        before = state()
        proof['before'] = before
        expected = before['ops_acceptance_business_prepare']['version']
        assert before['ops_acceptance_business_prepare']['scenario'] == 'HEALTHY', 'Existing target preserved'
        for good in (True, False):
            run_id = 'ops08-read-validation-' + uuid.uuid4().hex
            body = {'projectId': 'ops-acceptance-a', 'runId': run_id,
                    'userId': actor['userId'], 'authenticatedUsername': actor['username'],
                    'toolsetId': 'mcp.ops-08-isolated-dual-target-243b1497',
                    'toolName': 'test_validate_configuration', 'requireReadOnly': True, 'executionScope': 'PRE_APPROVAL_WORKFLOW',
                    'idempotencyKey': run_id,
                    'arguments': {'projectId': 'ops-acceptance-a', 'service': 'ops-acc-a-service-2',
                                  'expectedVersion': expected if good else 'stale-' + uuid.uuid4().hex,
                                  'scenario': 'HEALTHY'}}
            req = urllib.request.Request('http://127.0.0.1:18089/api/v1/admin/ops/tool-executions',
                json.dumps(body).encode(), {'Authorization': 'Bearer ' + h['token'], 'Content-Type': 'application/json'})
            try:
                response = urllib.request.urlopen(req, timeout=40)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                payload = json.load(response)
                http_status = response.status
            result = {'runId': run_id, 'case': 'matching_version' if good else 'stale_version',
                      'httpStatus': http_status, 'responseCode': payload.get('code'), 'message': payload.get('info', payload.get('message'))}
            proof['checks'].append(result)
            receipts = rows("SELECT JSON_OBJECT('id',result_id,'output',full_output,'hash',output_hash) "
                            "FROM ai_ops_tool_result WHERE source='MCP_REMOTE_TOOL' AND run_id=" + h['quoted'](run_id))
            if good:
                assert http_status == 200 and payload.get('code') == '0000'
                assert len(receipts) == 1, 'Expected one actual provider receipt'
                receipt = receipts[0]
                assert hashlib.sha256(receipt['output'].encode()).hexdigest() == receipt['hash']
                envelope = json.loads(receipt['output'])
                actual = envelope['normalizedContent']
                result.update(providerReceipt=receipt, observation=actual)
            else:
                assert http_status >= 400 and not receipts, 'Failed validation must not become success evidence'
                failures = rows("SELECT JSON_OBJECT('id',call_id,'status',status,'output',output_json) "
                                "FROM ai_ops_mcp_tool_call WHERE status='FAILED' AND run_id=" + h['quoted'](run_id))
                assert len(failures) == 1, 'Expected one persisted remote failure'
                failure = json.loads(failures[0]['output'])
                assert failure['errorClass'] == 'TOOL_ERROR' and failure['dispatched'] is True
                envelope = json.loads(failure['rawEnvelope'])
                actual = envelope['structuredContent']
                result.update(failureReceipt=failures[0], observation=actual)
            assert envelope['isError'] is (not good)
            assert actual['status'] == ('PASSED' if good else 'FAILED')
            requests = actual['requests']
            query = ("SELECT JSON_OBJECT('traceId',event_id,'version',version,'status',http_status) FROM "
                         "ops_acceptance_business_prepare.ops04_request WHERE service_id='ops-acc-a-service-2' AND event_id IN ("
                         + ','.join(h['quoted'](r['traceId']) for r in requests) + ')')
            deadline = time.monotonic() + 25
            while True:
                facts = rows(query)
                if len(facts) == len(requests) or time.monotonic() >= deadline:
                    break
                time.sleep(1)
            assert len(facts) == len(requests) == 20
            indexed = {r['traceId']: r for r in facts}
            for request in requests:
                fact = indexed[request['traceId']]
                assert fact['version'] == request['version'] and fact['status'] == request['httpStatus']
            result['requestFacts'] = facts
        proof['after'] = state()
        assert proof['after'] == before, 'Configuration or deployment receipts changed'
        proof['status'] = 'PASS'
    finally:
        output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
        output.with_suffix('.sql').write_text(';\n'.join(queries) + ';\n')
    print(json.dumps({'status': proof['status'], 'actualRequests': 40, 'configurationWrites': 0}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    run(parser.parse_args().output)
