#!/usr/bin/env python3
"""Read-only cross-check of a completed natural-language MySQL diagnostic Run.

Compares persisted MCP output with the connector's independent SQLite ledger and a
fresh MySQL EXPLAIN on the actual isolated business database. Does not accept tasks.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]


def inspect(run, output):
    if not re.fullmatch(r'chat-chat-session-[a-z0-9-]+', run):
        raise ValueError('Use an actual browser Run identity')
    paths = (output, output.with_suffix('.sql'), output.with_suffix('.sqlite.sql'))
    if any(p.exists() for p in paths):
        raise ValueError('Retain earlier evidence; choose a new output')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    q = h['quoted'](run)
    sql = {
        'run': "SELECT JSON_OBJECT('runId',run_id,'projectId',project_id,'status',status,'request',request_json) FROM ai_ops_agent_run WHERE run_id=" + q,
        'receipts': "SELECT JSON_OBJECT('resultId',result_id,'status',status,'output',full_output,'hash',output_hash) FROM ai_ops_tool_result WHERE source='MCP_REMOTE_TOOL' AND run_id=" + q,
        'calls': "SELECT JSON_OBJECT('callId',call_id,'tool',tool_name,'readOnly',read_only,'status',status,'error',error_message) FROM ai_ops_mcp_tool_call WHERE run_id=" + q,
    }
    result = {'status': 'FAIL', 'boundary': 'Actual synthetic business fixture and real model/tool; task acceptance and Skill evolution are separate.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        result.update({name: h['rows'](query) for name, query in sql.items()})
        fact = result['run'][0]
        assert fact['status'] == 'SUCCEEDED' and fact['projectId'] == 'ops-acceptance-a'
        request = json.loads(fact.pop('request'))
        result['userInput'] = request['query']
        assert not request['query'].lstrip().startswith(('{', '['))
        successful_calls = [c for c in result['calls'] if c['status'] == 'SUCCEEDED']
        assert len(result['receipts']) == len(successful_calls) == 1
        assert 1 <= len(result['calls']) <= 12
        rejected = [c for c in result['calls'] if c['status'] != 'SUCCEEDED']
        for attempt in rejected:
            error = json.loads(attempt['error'])
            assert attempt['status'] == 'FAILED' and attempt['tool'] == 'sql_explain' and attempt['readOnly'] == 1
            assert error['dispatched'] is False and error['errorClass'] == 'CONTRACT_INVALID'
            assert error['message'] == 'MCP_CONTRACT_INVALID:ARGUMENTS_SCHEMA_MISMATCH'
        result['schemaRejectedBeforeDispatch'] = len(rejected)
        receipt, call = result['receipts'][0], successful_calls[0]
        assert call['tool'] == 'sql_explain' and call['readOnly'] == 1
        assert call['status'] == receipt['status'] == 'SUCCEEDED'
        assert hashlib.sha256(receipt['output'].encode()).hexdigest() == receipt['hash']
        envelope = json.loads(receipt['output'])
        assert envelope['isError'] is False
        observed = envelope['normalizedContent']
        assert observed['kind'] == 'sql_explain' and observed['status'] == 'AVAILABLE'
        scope, order = observed['scope'], observed['orderId']
        assert scope['projectId'] == 'ops-acceptance-a' and scope['environment'] == 'acceptance'
        assert re.fullmatch(r'ops-acc-a-service-[1-4]', scope['serviceId'])
        assert re.fullmatch(r'ops-acc-a-order-[0-9]{1,3}', order)
        # Bind the observed object to this explicit user request. The answer may use
        # ordinary pronouns; echoing an identifier is not proof of correct scope.
        assert set(re.findall(r'ops-acc-a-service-[0-9]+', request['query'])) == {scope['serviceId']}
        assert set(re.findall(r'ops-acc-a-order-[0-9]+', request['query'])) == {order}
        query_id = str(uuid.UUID(observed['queryId']))
        peer_sql = "SELECT * FROM queries WHERE query_id='" + query_id + "'"
        paths[2].write_text(peer_sql + ';\n')
        program = "import sqlite3,json; c=sqlite3.connect('file:/state/observability.sqlite?mode=ro',uri=True); c.row_factory=sqlite3.Row; print(json.dumps([dict(r) for r in c.execute(input())]))"
        peer = json.loads(subprocess.check_output(['docker', 'exec', '-i', 'orbisops-acceptance-observability-mcp-1',
            'python3', '-c', program], input=peer_sql, text=True))
        result['peer'] = peer
        assert len(peer) == 1 and peer[0]['status'] == 'AVAILABLE' and peer[0]['tool'] == call['tool']
        assert json.loads(peer[0]['scope_json']) == scope
        assert peer[0]['fingerprint'] == observed['queryFingerprint']
        assert peer[0]['response_sha256'] == hashlib.sha256(json.dumps(observed, sort_keys=True).encode()).hexdigest()
        sql['independentIdentity'] = "SELECT JSON_OBJECT('orderId',o.order_id,'serviceId',o.service_id,'projectId',s.project_id) FROM ops_acceptance_business_a.acceptance_order o JOIN ops_acceptance_business_a.acceptance_service s USING(service_id) WHERE o.order_id=" + h['quoted'](order)
        identities = h['rows'](sql['independentIdentity'])
        assert identities == [{'orderId': order, 'serviceId': scope['serviceId'], 'projectId': scope['projectId']}]
        sql['independentExplain'] = ("USE ops_acceptance_business_a;\nEXPLAIN FORMAT=JSON SELECT o.order_id,c.tier,s.version "
            "FROM acceptance_order o JOIN acceptance_customer c ON c.customer_id=o.customer_id "
            "JOIN acceptance_service s ON s.service_id=o.service_id WHERE s.project_id='ops-acceptance-a' AND s.service_id="
            + h['quoted'](scope['serviceId']) + ' AND o.order_id=' + h['quoted'](order))
        actual = json.loads(h['sql'](sql['independentExplain']))
        result['independentPlan'] = actual
        assert actual == observed['plan']
        events = h['api']('/api/v1/admin/ops-agent-runs/' + run + '/events/list', token=h['token'])
        models = [e['payload'] for e in events if e.get('eventType') == 'MODEL_RESPONSE_VERIFIED']
        result['modelIdentities'] = models
        assert models and all(m['requestedModel'] == m['responseModel'] and m['responseModel'] in ('gpt-5.6-luna', 'gpt-5.6-terra') for m in models)
        result['summary'] = h['api']('/api/v1/admin/ops/analysis-tasks/' + run + '?projectId=ops-acceptance-a', token=h['token'])['summary']
        assert result['summary'].strip()
        result.update(status='PASS', actualBusinessCalls=len(peer), actualOrder=identities[0])
    except Exception as error:
        result['failureType'] = type(error).__name__
        raise
    finally:
        paths[1].write_text(';\n'.join(sql.values()) + ';\n')
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'runId': run, 'actualOrder': result['actualOrder']}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    inspect(args.run_id, args.output)
