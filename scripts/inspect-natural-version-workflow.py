#!/usr/bin/env python3
"""Read-only proof for a UI-created version workflow: frozen graph, real model, MCP and target.

Keep unsuccessful previous turns separate from the successful query. Does not accept
an Episode or create learning evidence. All targets are the named local acceptance stack.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import runpy
import subprocess
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


def inspect(run_id, output, service):
    if not re.fullmatch(r'chat-chat-session-[a-z0-9-]+', run_id) or not re.fullmatch(r'ops-acc-a-service-[1-4]', service):
        raise ValueError('Use the actual local acceptance Run and service')
    if output.exists():
        raise ValueError('Preserve earlier evidence; choose a fresh path')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    q = h['quoted']; statements = []
    def rows(sql):
        statements.append(sql.rstrip(';') + ';')
        return h['rows'](sql)
    run = rows("""SELECT JSON_OBJECT('runId',run_id,'sessionId',session_id,'projectId',project_id,'agentId',agent_id,
        'version',agent_version,'definitionHash',agent_definition_hash,'status',status,'error',error_message,
        'query',JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.query')),
        'answer',JSON_UNQUOTE(JSON_EXTRACT(response_json,'$.content')))
        FROM ai_ops_agent_run WHERE run_id=""" + q(run_id))[0]
    result = {'status': 'NOT_PASSED', 'run': run, 'boundary': 'Actual isolated version query; not repair success, service health acceptance or Skill publication.'}
    result['calls'] = rows("SELECT JSON_OBJECT('callId',call_id,'tool',tool_name,'status',status,'readOnly',read_only,'error',error_message) FROM ai_ops_mcp_tool_call WHERE run_id=" + q(run_id))
    result['receipts'] = rows("SELECT JSON_OBJECT('resultId',result_id,'status',status,'hash',output_hash,'output',full_output) FROM ai_ops_tool_result WHERE run_id=" + q(run_id) + " AND source='MCP_REMOTE_TOOL'")
    events = h['api']('/api/v1/admin/ops-agent-runs/' + run_id + '/events/list', token=h['token'])
    result['modelIdentities'] = [{k: event.get('payload', {}).get(k) for k in ('requestedModel', 'responseModel')}
                               for event in events if event.get('eventType') == 'MODEL_RESPONSE_VERIFIED']
    result['modelRetryEvents'] = [{k: event.get('payload', {}).get(k) for k in ('attempt', 'maxAttempts', 'reason', 'errorClass')}
                                for event in events if event.get('eventType') == 'MODEL_CALL_RETRYING']
    output.parent.mkdir(parents=True, exist_ok=True)
    def save():
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        output.with_suffix('.sql').write_text('\n'.join(statements) + '\n')
    save()
    if run['status'] != 'SUCCEEDED' or not result['receipts']:
        result['reason'] = 'No successful real target-version receipt; a completed graph alone is insufficient.'
        save(); print(json.dumps({'status': result['status'], 'runId': run_id, 'graphStatus': run['status']})); return
    published = rows("SELECT JSON_OBJECT('version',version,'hash',definition_hash,'source',source,'lifecycle',lifecycle) FROM ai_ops_agent_definition_version WHERE agent_id=" + q(run['agentId']) + ' AND version=' + str(run['version']))[0]
    receipt = result['receipts'][0]; envelope = json.loads(receipt['output']); observed = envelope['normalizedContent']
    query_id = str(uuid.UUID(observed['queryId']))
    peer_sql = "SELECT * FROM queries WHERE query_id='" + query_id + "'"
    output.with_suffix('.sqlite.sql').write_text(peer_sql + ';\n')
    program = "import sqlite3,json; c=sqlite3.connect('file:/state/observability.sqlite?mode=ro',uri=True); c.row_factory=sqlite3.Row; print(json.dumps([dict(r) for r in c.execute(input())]))"
    peer = json.loads(subprocess.check_output(['docker', 'exec', '-i', 'orbisops-acceptance-observability-mcp-1', 'python3', '-c', program], input=peer_sql, text=True))
    oracle = rows("SELECT JSON_OBJECT('serviceId',service_id,'version',version,'scenario',scenario) FROM ops_acceptance_business_a.acceptance_service WHERE service_id=" + q(service))[0]
    with urllib.request.urlopen('http://127.0.0.1:18262/version', timeout=10) as response:
        actual_target = json.load(response)
    target = next(item for item in actual_target['services'] if item['serviceId'] == service)
    checks = {
        'succeededInExpectedProject': run['status'] == 'SUCCEEDED' and run['projectId'] == 'ops-acceptance-a',
        'naturalLanguageRequest': bool(run['query']) and not run['query'].lstrip().startswith(('{', '[')),
        'frozenUIPublishedVersion': published['version'] == run['version'] and published['hash'] == run['definitionHash'] and published['source'] == 'UI' and published['lifecycle'] == 'PUBLISHED',
        'actualModelsVerified': bool(result['modelIdentities']) and all(item['requestedModel'] == item['responseModel'] and item['responseModel'] in ('gpt-5.6-luna', 'gpt-5.6-terra') for item in result['modelIdentities']),
        'oneSuccessfulReadOnlyTargetQuery': len(result['calls']) == len(result['receipts']) == 1 and result['calls'][0]['tool'] == 'target_version' and result['calls'][0]['readOnly'] == 1 and result['calls'][0]['status'] == receipt['status'] == 'SUCCEEDED',
        'receiptHash': hashlib.sha256(receipt['output'].encode()).hexdigest() == receipt['hash'],
        'availableVersionReceipt': envelope['orbisopsResultVersion'] == 1 and envelope['isError'] is False and observed['kind'] == 'target_version' and observed['status'] == 'AVAILABLE',
        'exactScope': observed['scope']['projectId'] == 'ops-acceptance-a' and observed['scope']['environment'] == 'acceptance' and observed['scope']['serviceId'] == service,
        'independentPeerQueryLedger': len(peer) == 1 and peer[0]['tool'] == 'target_version' and peer[0]['status'] == 'AVAILABLE' and json.loads(peer[0]['scope_json']) == observed['scope'],
        'actualTargetAndDatabaseVersionMatch': target['version'] == oracle['version'] == observed['version'],
        'modelAnswerIncludesActualVersion': observed['version'] in run['answer'],
    }
    result.update(published=published, peer=peer, actualTarget=target, businessOracle=oracle, checks=checks,
                  status='PASS' if all(checks.values()) else 'FAIL')
    save(); assert all(checks.values()), checks
    print(json.dumps({'status': result['status'], 'checks': len(checks), 'actualVersion': observed['version'], 'runId': run_id}))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run', required=True); p.add_argument('--service', default='ops-acc-a-service-1')
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args(); inspect(args.run, args.output, args.service)
