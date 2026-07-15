#!/usr/bin/env python3
"""Start independent read-only synthetic service tasks through the normal chat API.

This creates no task classification, acceptance, proposal or successful outcome.
Existing sessions in the evidence file are retained, including failed/incomplete Runs.
"""
import argparse
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def start(output, numbers):
    if len(set(numbers)) != len(numbers) or not numbers or any(n < 1 or n > 24 for n in numbers):
        raise ValueError('Choose distinct seeded discovery services numbered 1 through 24')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    api, token = h['api'], h['token']
    state = json.loads(output.read_text()) if output.exists() else {'fixture': 'OPS-03 independent read-only discovery tasks', 'tasks': {}}
    output.parent.mkdir(parents=True, exist_ok=True)
    def save():
        output.write_text(json.dumps(state, ensure_ascii=False, indent=2) + '\n')
    for n in numbers:
        service = f'discovery-service-{n:02d}'
        if service in state['tasks']:
            continue
        query = f'请只读核对 {service} 当前实际运行的版本和健康状态，并附上本次查询依据。只确认现状，不进行修复或变更。'
        session = api('/api/v1/user/chat/session', 'POST', {'projectId': 'ops-acceptance-a',
            'title': '目录服务独立核查 · ' + service}, token)
        task = {'serviceId': service, 'sessionId': session, 'query': query, 'state': 'SESSION_CREATED'}
        state['tasks'][service] = task
        save()
        task['state'] = 'REQUEST_SENT_RESPONSE_UNCONFIRMED'
        save()
        # This ordinary chat endpoint waits for completion. A client timeout is not proof
        # that the server rejected the request; retain the session instead of resubmitting.
        response = api(f'/api/v1/user/chat/sessions/{session}/messages', 'POST', {
            'projectId': 'ops-acceptance-a', 'query': query, 'mode': 'AGENT', 'engine': 'GRAPH',
            'agentDefinitionId': 'ops-acceptance-a-ops-agent', 'agentVersion': 2, 'modelId': 'ops-acceptance-terra'}, token, timeout=720)
        task.update(runId=response['metadata']['runId'], state='SUBMITTED')
        save()
        print(json.dumps({'serviceId': service, 'runId': task['runId'], 'state': task['state']}), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--services', required=True, nargs='+', type=int)
    args = parser.parse_args()
    start(args.output, args.services)
