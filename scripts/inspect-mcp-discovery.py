#!/usr/bin/env python3
"""Read-only cross-check of a real browser discovery Run against its MCP peer and SQLite.

Requires seed-mcp-discovery.py's isolated peer. It never starts a Run or changes outcomes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def inspect(run, output):
    outputs = (output, output.with_suffix('.sql'), output.with_suffix('.sqlite.sql'))
    if any(path.exists() for path in outputs):
        raise ValueError('Use a new output path; preserve earlier evidence')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    q = h['quoted'](run)
    queries = {
        'run': "SELECT JSON_OBJECT('runId',run_id,'projectId',project_id,'status',status,'request',request_json) FROM ai_ops_agent_run WHERE run_id=" + q,
        'receipts': "SELECT JSON_OBJECT('resultId',result_id,'status',status,'output',full_output,'hash',output_hash) FROM ai_ops_tool_result WHERE source='MCP_REMOTE_TOOL' AND run_id=" + q,
        'calls': "SELECT JSON_OBJECT('callId',call_id,'tool',tool_name,'readOnly',read_only,'status',status,'input',CAST(input_json AS JSON)) FROM ai_ops_mcp_tool_call WHERE run_id=" + q,
    }
    sqlite_queries = {name: 'SELECT * FROM ' + name for name in ('discovery_services', 'requests', 'receipts')}
    output.parent.mkdir(parents=True, exist_ok=True)
    outputs[1].write_text(';\n'.join(queries.values()) + ';\n')
    outputs[2].write_text(';\n'.join(sqlite_queries.values()) + ';\n')
    result = {'status': 'FAIL', 'boundary': 'Real local synthetic fixture; no task acceptance or Skill publication is implied.'}
    try:
        result.update({name: h['rows'](sql) for name, sql in queries.items()})
        peer_script = "import sqlite3,json; c=sqlite3.connect('file:/state/discovery.sqlite?mode=ro',uri=True); c.row_factory=sqlite3.Row; print(json.dumps({t:[dict(r) for r in c.execute(s)] for t,s in json.loads(input()).items()}))"
        peer = json.loads(subprocess.check_output(['docker', 'exec', '-i', 'orbisops-acceptance-mcp-discovery-1', 'python', '-c', peer_script], input=json.dumps(sqlite_queries), text=True))
        result['peer'] = peer
        events = h['api']('/api/v1/admin/ops-agent-runs/' + run + '/events/list', token=h['token'])
        discovery = [e['payload'] for e in events if e.get('eventType') == 'MCP_DISCOVERY_MODE']
        searches = [e['payload'] for e in events if e.get('eventType') == 'MCP_TOOL_SEARCH']
        schemas = [e['payload'] for e in events if e.get('eventType') == 'MCP_SCHEMA_DISCLOSURE']
        identities = [e['payload'] for e in events if e.get('eventType') == 'MODEL_RESPONSE_VERIFIED']
        result.update(discovery=discovery, searches=searches, schemas=schemas, modelIdentities=identities)
        facts = result['run'][0]
        assert facts['projectId'] == 'ops-acceptance-a' and facts['status'] == 'SUCCEEDED'
        query = json.loads(facts.pop('request'))['query']
        result['userInput'] = query
        assert query.strip() and not query.lstrip().startswith(('{', '['))
        assert len(discovery) == 1 and discovery[0]['mode'] == 'SEARCH'
        assert discovery[0]['toolCount'] > 20 or discovery[0]['summaryTokens'] > 2000
        assert searches and all(len(s['hits']) <= 5 for s in searches)
        assert schemas and not schemas[0]['tools'] and any(s['tools'] for s in schemas[1:])
        assert identities and all(i['requestedModel'] == i['responseModel'] and i['responseModel'] in ('gpt-5.6-luna', 'gpt-5.6-terra') for i in identities)
        assert len(result['calls']) == len(result['receipts']) == 1
        call, receipt = result['calls'][0], result['receipts'][0]
        assert call['readOnly'] == 1 and call['status'] == receipt['status'] == 'SUCCEEDED'
        executed = [e['payload'] for e in events if e.get('eventType') == 'TOOL_CALL_FINISHED'
                    and e.get('payload', {}).get('remoteCallExecuted')]
        assert len(executed) == 1
        # The administrative call list deliberately redacts inputs. Use the execution trace
        # for this synthetic request identity; never infer it from the generated answer.
        invocation = json.loads(executed[0]['input'])
        assert invocation['toolName'] == call['tool']
        request_id = invocation['arguments']['requestId']
        result['executedRequestId'] = request_id
        actual = [r for r in peer['requests'] if r['method'] == 'tools/call' and r['test_id'] == request_id]
        assert len(actual) == 1 and actual[0]['tool'] == call['tool']
        assert hashlib.sha256(receipt['output'].encode()).hexdigest() == receipt['hash']
        envelope = json.loads(receipt['output'])
        observed = envelope['normalizedContent']
        assert envelope['isError'] is False and observed['count'] == 1
        service = next(s for s in peer['discovery_services'] if s['service_id'] == observed['serviceId'])
        assert observed['version'] == service['version'] and observed['state'] == service['state']
        summary = h['api']('/api/v1/admin/ops/analysis-tasks/' + run + '?projectId=ops-acceptance-a', token=h['token'])['summary']
        result['summary'] = summary
        assert all(str(observed[key]) in summary for key in ('serviceId', 'version', 'state'))
        assert not peer['receipts'], 'The read-only discovery peer must contain no write receipt'
        result.update(status='PASS', actualBusinessCalls=len(actual), actualService=service)
    except Exception as error:
        result['failureType'] = type(error).__name__
        raise
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'runId': run, 'searches': len(searches), 'actualBusinessCalls': len(actual), 'actualService': service}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    inspect(args.run_id, args.output)
