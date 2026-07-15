#!/usr/bin/env python3
"""Read-only cross-check of the local protocol workflow launched through a real browser.

The workflow is a synthetic read-only MCP fixture, not a model or business acceptance test.
Does not start a Run, approve a tool, or change database state. Writes rerunnable SELECTs.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import re
import runpy

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-acceptance-a'


def inspect(run_id, output, expected_query):
    if output.exists():
        raise ValueError('Choose a fresh evidence path; preserve previous results')
    if not re.fullmatch(r'chat-chat-session-[a-z0-9-]+', run_id):
        raise ValueError('Supply an actual browser Run ID')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    q = h['quoted']
    statements = []

    def rows(statement):
        statements.append(statement.rstrip(';') + ';')
        return h['rows'](statement)

    run = rows("""SELECT JSON_OBJECT('runId',run_id,'projectId',project_id,'sessionId',session_id,
        'agentId',agent_id,'version',agent_version,'hash',agent_definition_hash,'status',status,
        'createdAt',created_at,'updatedAt',updated_at,'error',error_message,
        'query',JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.query')),
        'route',JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.metadata.assistantRoute')),
        'answer',JSON_UNQUOTE(JSON_EXTRACT(response_json,'$.content')))
        FROM ai_ops_agent_run WHERE run_id=""" + q(run_id))[0]
    published = rows("""SELECT JSON_OBJECT('version',version,'hash',definition_hash,
        'lifecycle',lifecycle,'definition',definition_json) FROM ai_ops_agent_definition_version
        WHERE project_id=""" + q(PROJECT) + ' AND agent_id=' + q(run['agentId']) + ' AND version=' + str(run['version']))[0]
    calls = rows("""SELECT JSON_OBJECT('callId',call_id,'toolName',tool_name,'mcpId',mcp_id,
        'readOnly',read_only,'status',status,'input',input_json,'output',output_json)
        FROM ai_ops_mcp_tool_call WHERE run_id=""" + q(run_id) + ' ORDER BY id')
    receipts = rows("""SELECT JSON_OBJECT('resultId',result_id,'source',source,'status',status,
        'hash',output_hash,'output',full_output) FROM ai_ops_tool_result WHERE run_id=""" + q(run_id) + ' ORDER BY id')
    messages = rows("""SELECT JSON_OBJECT('role',role,'content',content) FROM ai_ops_chat_message
        WHERE session_id=""" + q(run['sessionId']) + " AND JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.runId'))=" + q(run_id) + ' ORDER BY id')
    dispatches = rows("SELECT JSON_OBJECT('rpcId',request_id,'attempt',physical_attempt,'limit',budget_limit) FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + q(run_id))
    parse_time = lambda value: datetime.datetime.fromisoformat(value).replace(tzinfo=datetime.timezone.utc).timestamp()
    definition = json.loads(published.pop('definition'))
    actions = [a for node in definition['nodes'] for a in node.get('config', {}).get('actions', [])]
    peer = h['peer_evidence']()
    remote = [r for r in peer['requests'] if r['method'] == 'tools/call'
              and parse_time(run['createdAt']) <= r['received_at'] <= parse_time(run['updatedAt'])
              and any(r['tool'] == a['remoteToolName'] and r['test_id'] == a['arguments']['testId']
                      and r['mode'] == a['arguments']['mode'] for a in actions)]
    result = {'status': 'UNVERIFIED', 'run': run, 'published': published, 'actions': actions,
              'calls': calls, 'receipts': receipts, 'messages': messages, 'remoteRequests': remote,
              'dispatchReservations': dispatches,
              'remoteCorrelation': 'EXACT_ARGUMENTS_AND_RUN_WINDOW_NOT_RPC_ID',
              'targetStateObservation': {'services': peer['services'], 'receipts': peer['receipts']},
              'limits': ['Synthetic MCP protocol fixture; not model quality or business acceptance.',
                         'Target state is a current observation, not a separate browser before/after snapshot.',
                         'This published v1 has no START call-budget setting; bounded dispatch/restart coverage is the separate full protocol matrix.']}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text('\n'.join(statements) + '\n')
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    checks = {
        'runSucceededInExpectedProject': run['status'] == 'SUCCEEDED' and run['projectId'] == PROJECT and not run['error'],
        'publishedVersionAndHashFrozen': published['lifecycle'] == 'PUBLISHED' and published['version'] == run['version'] and published['hash'] == run['hash'],
        'naturalLanguagePersistedUnchanged': run['query'] == expected_query and any(m['role'] == 'user' and m['content'] == expected_query for m in messages),
        'sessionBoundWorkflow': run['route'] == 'SESSION_BOUND_WORKFLOW',
        'oneRemoteActionAndOneSuccessfulReadOnlyCall': len(actions) == len(remote) == len(calls) == 1 and calls[0]['readOnly'] == 1 and calls[0]['status'] == 'SUCCEEDED',
        'immutableReceiptHashes': bool(receipts) and all(hashlib.sha256(r['output'].encode()).hexdigest() == r['hash'] for r in receipts),
    }
    remote_receipts = [r for r in receipts if r['source'] == 'MCP_REMOTE_TOOL']
    answer = json.loads(run['answer'])
    checks['renderedAnswerMatchesRemoteDataAndReceipt'] = len(remote_receipts) == 1 and answer['providerResultId'] == remote_receipts[0]['resultId'] and answer['mcpEnvelope']['normalizedContent'] == json.loads(remote_receipts[0]['output'])['normalizedContent']
    checks['answerPersistedInChat'] = any(m['role'] == 'assistant' and m['content'] == run['answer'] for m in messages)
    result['checks'] = checks
    result['status'] = 'PASS' if all(checks.values()) else 'FAIL'
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    assert all(checks.values()), checks
    print(json.dumps({'status': result['status'], 'checks': len(checks), 'actualRemoteCalls': len(remote), 'runId': run_id}))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run', required=True)
    p.add_argument('--expected-query', default='请执行当前工作流的只读核对，保留实际工具回执。')
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    inspect(a.run, a.output, a.expected_query)
