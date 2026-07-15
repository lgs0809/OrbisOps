#!/usr/bin/env python3
"""Read-only SQL cross-check for a browser/model run; never equates chat success with Landing success."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(run_id, output):
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    q = h['quoted']
    queries = []

    def rows(query):
        queries.append(query)
        return h['rows'](query)

    proof = {'runId': run_id, 'status': 'NOT_PASSED', 'scope': 'OBSERVATION_CROSSCHECK_ONLY',
             'approvalAndLanding': 'NOT_ASSERTED', 'businessRequests': [], 'observations': []}
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        proof['run'] = rows("SELECT JSON_OBJECT('runId',run_id,'status',status,'error',error_message) "
                            'FROM ai_ops_agent_run WHERE run_id=' + q(run_id))
        assert len(proof['run']) == 1
        receipts = rows("SELECT JSON_OBJECT('resultId',result_id,'tool',tool_name,'output',full_output,'hash',output_hash) "
                        "FROM ai_ops_tool_result WHERE project_id='ops-acceptance-a' AND source='MCP_REMOTE_TOOL' "
                        'AND run_id=' + q(run_id))
        for receipt in receipts:
            assert hashlib.sha256(receipt['output'].encode()).hexdigest() == receipt['hash']
            envelope = json.loads(receipt['output'])
            if receipt['tool'] not in ('test_read_state', 'prod_read_state', 'test_check_orders', 'prod_check_orders', 'test_validate_configuration'):
                continue
            assert envelope.get('isError') is False
            body = envelope['normalizedContent']
            expected_environment = receipt['tool'].split('_')[0]
            assert body['environment'] == expected_environment and body['projectId'] == 'ops-acceptance-a'
            assert body['resourceKey'] == 'service://ops-acc-a-service-2/' + expected_environment
            proof['observations'].append({'resultId': receipt['resultId'], 'hash': receipt['hash'],
                                          'tool': receipt['tool'], 'body': body})
            if receipt['tool'] == 'test_validate_configuration':
                assert body['status'] == 'PASSED' and body['verified'] is True
                for state in (body['beforeState'], body['afterState']):
                    assert state['version'] == body['expectedVersion'] and state['scenario'] == 'HEALTHY'
                assert body['requestCount'] == 20 and body['errorCount'] == 0
            requests = body.get('requests', [])
            if not requests:
                continue
            database = ('ops_acceptance_business_prepare' if expected_environment == 'test'
                        else 'ops_acceptance_business_a')
            actual = rows("SELECT JSON_OBJECT('traceId',event_id,'status',http_status,'version',version) FROM "
                          + database + ".ops04_request WHERE service_id='ops-acc-a-service-2' AND event_id IN ("
                          + ','.join(q(r['traceId']) for r in requests) + ') ORDER BY event_id')
            by_id = {r['traceId']: r for r in actual}
            assert len(by_id) == len(requests)
            for request in requests:
                fact = by_id[request['traceId']]
                assert fact['status'] == request['httpStatus'] and fact['version'] == request['version']
            proof['businessRequests'].extend(actual)
        proof['productionReceiptCount'] = rows("SELECT JSON_OBJECT('count',COUNT(*)) "
                                               'FROM ops_acceptance_business_a.ops08_deployment_receipt')[0]['count']
        assert proof['observations'], 'No actual dual-target observation in this run'
        proof['status'] = 'PASS_OBSERVATION_CROSSCHECK'
    finally:
        output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
        output.with_suffix('.sql').write_text(';\n'.join(queries) + ';\n')
    print(json.dumps({'status': proof['status'], 'requestCount': len(proof['businessRequests']),
                      'productionReceiptCount': proof['productionReceiptCount']}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    inspect(args.run_id, args.output)
