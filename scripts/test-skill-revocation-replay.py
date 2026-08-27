#!/usr/bin/env python3
"""Replay an already recorded fixture approval concurrently; never edit stored outcomes.

Run after restoring the fixture Skill through the UI. A failed terminal run must
not resume merely because its Skill became available again. Local acceptance only.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import re
import runpy
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def main(run, output):
    if not re.fullmatch(r'chat-chat-session-[a-z0-9-]+', run):
        raise ValueError('Use the actual browser run ID')
    s = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    q = s['quoted'](run)
    statement = "SELECT JSON_OBJECT('status',status,'error',error_message,'epoch',fencing_token," \
        "'project',project_id,'agent',agent_id,'dispatches',(SELECT COUNT(*) FROM ai_ops_workflow_tool_dispatch WHERE run_id=" + q + ")," \
        "'receipts',(SELECT COUNT(*) FROM ai_ops_tool_result WHERE run_id=" + q + " AND source='MCP_REMOTE_TOOL')) FROM ai_ops_agent_run WHERE run_id=" + q
    before = s['rows'](statement)[0]
    assert before['project'] == 'ops-acceptance-a' and before['agent'] == 'ops-acceptance-skill-revocation'
    assert before['status'] == 'FAILED' and before['error'] == 'SKILL_RUNTIME_ACCESS_REVOKED'
    assert before['receipts'] == before['dispatches'] == 1
    path = '/api/v1/agent/chat/runs/' + run + '/workflow-approval'
    approval = s['api'](path + '?projectId=ops-acceptance-a', token=s['token'])
    assert approval['status'] == 'APPROVED'

    def replay(_):
        req = urllib.request.Request('http://127.0.0.1:18089' + path + '/decision?projectId=ops-acceptance-a',
            json.dumps({'approvalId': approval['approvalId'], 'decision': 'APPROVE'}).encode(),
            {'Authorization': 'Bearer ' + s['token'], 'Content-Type': 'application/json'}, method='POST')
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                return {'httpStatus': response.status, 'body': json.load(response)}
        except urllib.error.HTTPError as error:
            return {'httpStatus': error.code, 'body': json.loads(error.read())}

    with ThreadPoolExecutor(max_workers=8) as pool:
        responses = list(pool.map(replay, range(8)))
    time.sleep(2)
    after = s['rows'](statement)[0]
    evidence = {'status': 'UNVERIFIED', 'runId': run, 'before': before, 'after': after, 'responses': responses}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(statement + ';\n')
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
    assert before == after, evidence
    assert all(r['httpStatus'] == 409 and r['body'].get('info', '').startswith('WORK_SESSION_NOT_WAITING_APPROVAL')
               for r in responses), responses
    evidence['status'] = 'PASS'
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': 'PASS', 'parallelReplays': 8, 'runUnchanged': True, 'newMcpCalls': 0}))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run', required=True)
    p.add_argument('--output', required=True, type=Path)
    a = p.parse_args()
    main(a.run, a.output)
