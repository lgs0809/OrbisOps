#!/usr/bin/env python3
"""Read-only evidence for a browser natural-language version query; never sets run outcomes."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import runpy
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-acceptance-a'


def main(run, output):
    if not re.fullmatch(r'chat-chat-session-[a-z0-9-]+', run):
        raise ValueError('Supply the actual browser run ID')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    q = h['quoted']
    statements = []
    def rows(statement):
        statements.append(statement + ';')
        return h['rows'](statement)
    facts = rows("SELECT JSON_OBJECT('runId',run_id,'status',status,'projectId',project_id,'sessionId',session_id,"
                 "'error',error_message,'request',request_json,'durationMs',duration_ms) FROM ai_ops_agent_run WHERE run_id=" + q(run))[0]
    if facts['projectId'] != PROJECT:
        raise ValueError('Expected the isolated acceptance project')
    events = h['api']('/api/v1/admin/ops-agent-runs/' + run + '/events/list', token=h['token'])
    detail = h['api']('/api/v1/admin/ops/analysis-tasks/' + run + '?projectId=' + PROJECT, token=h['token'])
    receipts = rows("SELECT JSON_OBJECT('resultId',result_id,'hash',output_hash,'output',full_output,'status',status) "
                    "FROM ai_ops_tool_result WHERE run_id=" + q(run) + " AND project_id=" + q(PROJECT) + " AND source='MCP_REMOTE_TOOL' ORDER BY id")
    dispatches = rows("SELECT JSON_OBJECT('rpcId',request_id,'nodeId',node_id,'tool',tool_name,'attempt',physical_attempt,'limit',budget_limit) "
                      "FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + q(run) + " ORDER BY id")
    req = urllib.request.Request('http://127.0.0.1:18862/evidence',
            headers={'Authorization': 'Bearer ' + h['values']['OPS_ACCEPTANCE_OBSERVABILITY_TOKEN']})
    with urllib.request.urlopen(req, timeout=10) as response: peer = json.load(response)
    with urllib.request.urlopen('http://127.0.0.1:18262/version', timeout=10) as response: target = json.load(response)
    ids = {d['rpcId'] for d in dispatches}
    # Default chat has durable tool receipts but no Workflow dispatch row.
    # Join its observed query identity to the independent provider ledger as well.
    receipt_query_ids = {json.loads(r['output']).get('normalizedContent', {}).get('queryId') for r in receipts}
    receipt_query_ids.discard(None)
    queries = [c for c in peer['queries'] if c['rpc_id'] in ids or c['query_id'] in receipt_query_ids]
    ids.update(c['rpc_id'] for c in queries)
    remote_calls = [c for c in peer['rpcCalls'] if c['rpc_id'] in ids]
    versions = {s['serviceId']: s['version'] for s in target['services']}
    checks = []
    for receipt in receipts:
        envelope = json.loads(receipt['output'])
        observed = envelope.get('normalizedContent', {})
        if 'version' not in observed: continue
        query = next((x for x in queries if x['query_id'] == observed.get('queryId')), None)
        checks.append({'resultId': receipt['resultId'], 'queryId': observed.get('queryId'),
            'serviceId': observed.get('scope', {}).get('serviceId'), 'version': observed['version'],
            'successfulObservation': receipt['status'] == 'SUCCEEDED' and observed.get('status') == 'AVAILABLE'
                and bool(query) and query['status'] == 'AVAILABLE',
            'receiptHashMatches': hashlib.sha256(receipt['output'].encode()).hexdigest() == receipt['hash'],
            'providerHashMatches': bool(query) and hashlib.sha256(json.dumps(observed, sort_keys=True).encode()).hexdigest() == query['response_sha256'],
            'actualTargetMatches': observed['version'] == versions.get(observed.get('scope', {}).get('serviceId'))})
    identities = [e.get('payload', {}) for e in events if e.get('eventType') == 'MODEL_RESPONSE_VERIFIED']
    models_ok = bool(identities) and all(e.get('requestedModel') == e.get('responseModel')
            and e.get('responseModel') in ['gpt-5.6-luna', 'gpt-5.6-terra'] for e in identities)
    source = json.loads(facts.pop('request') or '{}')
    query_text = source.get('query', '')
    natural = bool(query_text.strip()) and not query_text.lstrip().startswith(('{', '['))
    matched = bool(checks) and all(all(c[k] for k in ['successfulObservation', 'receiptHashMatches', 'providerHashMatches', 'actualTargetMatches']) for c in checks)
    summary = detail.get('summary', '') or ''
    answer_matches = any(c['version'] in summary for c in checks)
    result = {'status': 'PASS' if facts['status'] == 'SUCCEEDED' and natural and models_ok and matched and answer_matches else 'NOT_PASSED',
              'run': facts, 'userInput': query_text, 'naturalLanguageInput': natural, 'modelsVerified': models_ok,
              'summary': summary, 'answerContainsObservedVersion': answer_matches, 'crossChecks': checks,
              'receipts': receipts, 'dispatches': dispatches, 'remoteCalls': remote_calls, 'providerQueries': queries,
              'actualTarget': target, 'modelIdentities': identities,
              'modelRetries': [e for e in events if e.get('eventType') == 'MODEL_CALL_RETRYING'],
              'toolFailures': [e for e in events if e.get('eventType') == 'TOOL_CALL_FAILED']}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    output.with_name(output.stem + '-events.json').write_text(json.dumps(events, ensure_ascii=False, indent=2) + '\n')
    output.with_name(output.stem + '-inspect.sql').write_text('\n'.join(statements) + '\n')
    output.with_name(output.stem + '-inspect.tsv').write_text(h['sql']('\n'.join(statements)) + '\n')
    print(json.dumps({'status': result['status'], 'run': run, 'runStatus': facts['status'],
                      'modelResponsesVerified': len(identities), 'modelRetries': len(result['modelRetries']),
                      'realVersionReceipts': len(checks), 'crossChecks': checks}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    main(args.run_id, args.output)
