#!/usr/bin/env python3
"""Independently verify both readonly MCP chains of one composite task. Never accepts it."""
from acceptance_model_contract import APPROVED_TRACE_MODELS, APPROVED_AUTHORING_MODELS
import argparse
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import uuid
from skill_composite_cases import resource_pair

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-acceptance-a'


def inspect(run, pair, output):
    if not 1 <= pair <= 6 or not re.fullmatch(r'chat-chat-session-[a-z0-9-]+', run):
        raise ValueError('Use a canonical actual Run and resource pair 1..6')
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Retain previous evidence')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    citation_matches = runpy.run_path(str(ROOT / 'scripts/inspect-mcp-discovery.py'))['receipt_citation_matches']
    q = h['quoted'](run)
    directory, service, order = resource_pair(pair)
    queries = {
        'run': "SELECT JSON_OBJECT('runId',run_id,'projectId',project_id,'status',status,'request',request_json) FROM ai_ops_agent_run WHERE run_id=" + q,
        'receipts': "SELECT JSON_OBJECT('resultId',result_id,'status',status,'output',full_output,'hash',output_hash) FROM ai_ops_tool_result WHERE source='MCP_REMOTE_TOOL' AND run_id=" + q,
        'calls': "SELECT JSON_OBJECT('callId',call_id,'tool',tool_name,'readOnly',read_only,'status',status,'error',error_message) FROM ai_ops_mcp_tool_call WHERE run_id=" + q,
    }
    report = {'status': 'FAIL', 'runId': run, 'pair': pair, 'expectedDirectory': directory,
              'expectedService': service, 'expectedOrder': order,
              'boundary': 'Two independent local synthetic resource facts, actual models, SQLite and MySQL. No acceptance or automatic maintenance outcome is implied.'}
    peer_queries = {}

    def peer(container, database, query):
        peer_queries[database + ':' + str(len(peer_queries))] = query
        program = "import sqlite3,json; c=sqlite3.connect('file:/state/" + database + ".sqlite?mode=ro',uri=True); c.row_factory=sqlite3.Row; print(json.dumps([dict(r) for r in c.execute(input())]))"
        return json.loads(subprocess.check_output(['docker', 'exec', '-i', container, 'python3', '-c', program], input=query, text=True))

    try:
        report.update({name: h['rows'](sql) for name, sql in queries.items()})
        fact = report['run'][0]
        assert fact['status'] == 'SUCCEEDED' and fact['projectId'] == PROJECT
        request = json.loads(fact.pop('request'))
        query = report['userInput'] = request['query']
        assert not query.lstrip().startswith(('{', '['))
        assert set(re.findall(r'discovery-service-[0-9]+', query)) == {directory}
        assert set(re.findall(r'ops-acc-a-service-[0-9]+', query)) == {service}
        assert set(re.findall(r'ops-acc-a-order-[0-9]+', query)) == {order}
        assert len(report['receipts']) == 2
        successful = [c for c in report['calls'] if c['status'] == 'SUCCEEDED']
        assert len(successful) == 2 and all(c['readOnly'] == 1 for c in report['calls'])
        for call in report['calls']:
            if call['status'] != 'SUCCEEDED':
                error = json.loads(call['error'])
                assert call['status'] == 'FAILED' and error['dispatched'] is False
                assert error['errorClass'] == 'CONTRACT_INVALID'
        events = h['api']('/api/v1/admin/ops-agent-runs/' + run + '/events/list', token=h['token'])
        executed = [e['payload'] for e in events if e.get('eventType') == 'TOOL_CALL_FINISHED'
                    and e.get('payload', {}).get('remoteCallExecuted')]
        assert len(executed) == 2
        models = report['modelIdentities'] = [e['payload'] for e in events if e.get('eventType') == 'MODEL_RESPONSE_VERIFIED']
        assert models and all(m['requestedModel'] == m['responseModel'] and m['responseModel'] in APPROVED_TRACE_MODELS for m in models)
        result_hashes = {}
        observations = {}
        for receipt in report['receipts']:
            assert receipt['status'] == 'SUCCEEDED'
            assert hashlib.sha256(receipt['output'].encode()).hexdigest() == receipt['hash']
            envelope = json.loads(receipt['output'])
            assert envelope['isError'] is False
            observed = envelope['normalizedContent']
            kind = 'sql' if observed.get('kind') == 'sql_explain' else 'directory'
            assert kind not in observations
            observations[kind] = dict(receipt=receipt, observed=observed)
            result_hashes[receipt['resultId']] = receipt['hash']
        assert set(observations) == {'directory', 'sql'}
        report['observations'] = observations
        projections = []
        for event in executed:
            queries['projection-' + event['resultId']] = "SELECT JSON_OBJECT('resultId',result_id,'status',status,'output',full_output,'hash',output_hash) FROM ai_ops_tool_result WHERE result_id=" + h['quoted'](event['resultId'])
            projection = h['rows'](queries['projection-' + event['resultId']])[0]
            projections.append(projection)
            assert projection['status'] == 'SUCCEEDED'
            assert hashlib.sha256(projection['output'].encode()).hexdigest() == projection['hash']
            projected = json.loads(projection['output'])
            source = next(o for o in observations.values() if o['receipt']['resultId'] == projected['providerResultId'])
            assert projected['providerOutputHash'] == source['receipt']['hash']
            assert projected['normalizedContent'] == source['observed']
            source['invocation'] = json.loads(event['input'])
            source['projection'] = projection
            result_hashes[projection['resultId']] = projection['hash']
        assert len({o['projection']['resultId'] for o in observations.values()}) == 2
        observed = observations['directory']['observed']
        assert observed['serviceId'] == directory and observed['count'] == 1
        invocation = observations['directory']['invocation']
        request_id = invocation['arguments']['requestId']
        assert isinstance(request_id, str) and 1 <= len(request_id) <= 128 and '\x00' not in request_id
        calls = peer('orbisops-acceptance-mcp-discovery-1', 'discovery',
                     "SELECT * FROM requests WHERE method='tools/call' AND test_id='" + request_id.replace("'", "''") + "'")
        resources = peer('orbisops-acceptance-mcp-discovery-1', 'discovery',
                         "SELECT * FROM discovery_services WHERE service_id='" + directory + "'")
        assert len(calls) == len(resources) == 1 and calls[0]['tool'] == invocation['toolName']
        assert observed['version'] == resources[0]['version'] and observed['state'] == resources[0]['state']
        assert not peer('orbisops-acceptance-mcp-discovery-1', 'discovery', 'SELECT * FROM receipts')
        report['directoryPeer'] = dict(calls=calls, resource=resources[0])
        observed = observations['sql']['observed']
        expected_scope = {'projectId': PROJECT, 'environment': 'acceptance', 'serviceId': service}
        scope = observed['scope']
        assert {key: scope[key] for key in expected_scope} == expected_scope
        assert set(scope) == set(expected_scope) | {'startEpoch', 'endEpoch'}
        assert all(isinstance(scope[key], int) and not isinstance(scope[key], bool) for key in ('startEpoch', 'endEpoch'))
        assert 0 < scope['endEpoch'] - scope['startEpoch'] <= 3600
        assert observed['orderId'] == order and observed['status'] == 'AVAILABLE'
        assert observations['sql']['invocation']['arguments'] == {'orderId': order, 'window': scope}
        mapping = observed['scopeMapping']
        assert all(mapping.get(key) == value for key, value in {
            'logicalEnvironment': 'acceptance', 'sourceEnvironment': 'acceptance',
            'sourceDatabase': 'ops_acceptance_business_a', 'sourceTarget': 'workflow-target:8280', 'serviceId': service}.items())
        query_id = str(uuid.UUID(observed['queryId']))
        calls = peer('orbisops-acceptance-observability-mcp-1', 'observability',
                     "SELECT * FROM queries WHERE query_id='" + query_id + "'")
        assert len(calls) == 1 and calls[0]['status'] == 'AVAILABLE' and calls[0]['tool'] == 'sql_explain'
        assert json.loads(calls[0]['scope_json']) == scope and calls[0]['fingerprint'] == observed['queryFingerprint']
        assert calls[0]['response_sha256'] == hashlib.sha256(json.dumps(observed, sort_keys=True).encode()).hexdigest()
        queries['orderIdentity'] = "SELECT JSON_OBJECT('orderId',o.order_id,'serviceId',o.service_id,'projectId',s.project_id) FROM ops_acceptance_business_a.acceptance_order o JOIN ops_acceptance_business_a.acceptance_service s USING(service_id) WHERE o.order_id=" + h['quoted'](order)
        identities = h['rows'](queries['orderIdentity'])
        assert identities == [dict(orderId=order, serviceId=service, projectId=PROJECT)]
        queries['explain'] = ("USE ops_acceptance_business_a; EXPLAIN FORMAT=JSON SELECT o.order_id,c.tier,s.version FROM acceptance_order o JOIN acceptance_customer c ON c.customer_id=o.customer_id JOIN acceptance_service s ON s.service_id=o.service_id WHERE s.project_id='ops-acceptance-a' AND s.service_id=" + h['quoted'](service) + ' AND o.order_id=' + h['quoted'](order))
        actual = json.loads(h['sql'](queries['explain']))
        assert actual == observed['plan']
        report['sqlPeer'] = dict(calls=calls, order=identities[0], actualPlan=actual)
        task = h['api']('/api/v1/admin/ops/analysis-tasks/' + run + '?projectId=' + PROJECT, token=h['token'])
        report['summary'] = task['summary']
        report['displayedEvidence'] = task.get('evidence', [])
        queries['evidence'] = "SELECT JSON_OBJECT('evidenceId',evidence_id,'resultId',tool_result_id,'hash',output_hash,'verified',verified,'runId',run_id,'projectId',project_id) FROM ai_ops_evidence WHERE project_id='ops-acceptance-a' AND run_id=" + q + ' AND tool_result_id IN (' + ','.join(h['quoted'](r) for r in result_hashes) + ')'
        evidence = report['citedEvidence'] = h['rows'](queries['evidence'])
        assert citation_matches(task['summary'], evidence, result_hashes, run, PROJECT, report['displayedEvidence'])
        for observation in observations.values():
            own = {row['resultId']: row['hash'] for row in (observation['receipt'], observation['projection'])}
            relevant = [row for row in evidence if row.get('resultId') in own and row.get('hash') == own[row['resultId']]
                        and row.get('verified') == 1 and row.get('runId') == run and row.get('projectId') == PROJECT]
            assert any(row['evidenceId'] in task['summary'] or row['hash'] in task['summary']
                       or any(item.get('evidenceId') == row['evidenceId']
                              and item.get('toolResultId') == row['resultId']
                              and item.get('outputHash') == row['hash'] and item.get('runId') == run
                              and item.get('projectId') == PROJECT and item.get('fullOutputRef') == 'db:' + row['resultId']
                              for item in report['displayedEvidence']) for row in relevant)
        assert all(value in task['summary'] for value in (directory, resources[0]['version'], resources[0]['state'], service, order))
        report.update(status='PASS_TWO_INDEPENDENT_ACTUAL_RESOURCE_CHAINS', actualReadonlyBusinessCalls=2,
                      sourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    except Exception as error:
        report['failureType'] = type(error).__name__
        raise
    finally:
        output.with_suffix('.sql').write_text(';\n'.join(queries.values()) + ';\n')
        output.with_suffix('.sqlite.sql').write_text(';\n'.join(peer_queries.values()) + ';\n')
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({key: report.get(key) for key in ('status', 'runId', 'pair', 'failureType')}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--pair', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    inspect(args.run_id, args.pair, args.output)
