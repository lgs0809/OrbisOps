#!/usr/bin/env python3
"""Exercise concurrent idempotent replay and authorization using an actually accepted task.

Never creates another positive assertion or modifies SQL outcomes. SQL is read-only;
requests reuse the original model-drafted, verified request and test explicit rejection.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import copy
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]


def test(source, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Preserve earlier evidence; choose a new output')
    prior = json.loads(source.read_text())
    if prior.get('status') != 'PASS_ACTUAL_ACCEPTANCE':
        raise ValueError('A real successful acceptance is required')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    auth = runpy.run_path(str(ROOT / 'scripts/seed-local-acceptance.py'))
    request = prior['draft']['request']
    episode = prior['episode']['episodeId']
    endpoint = '/api/v1/user/ops/task-acceptance/' + episode
    suffix = '?projectId=ops-acceptance-a'
    query = "SELECT JSON_OBJECT('id',acceptance_id,'request',request_id,'hash',record_hash,'hashMatches',SHA2(record_json,256)=record_hash) FROM ai_ops_task_acceptance WHERE episode_id=" + h['quoted'](episode) + ' ORDER BY created_at'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(query + ';\n')
    result = {'status': 'NOT_PASSED', 'episodeId': episode, 'acceptedSource': str(source), 'checks': {}}
    try:
        before = h['rows'](query)
        result['before'] = before
        with ThreadPoolExecutor(max_workers=8) as pool:
            replies = list(pool.map(lambda _: h['api'](endpoint + suffix, 'POST', request, h['token']), range(8)))
        accepted = prior['verification']['acceptanceId']
        assert all(r['acceptanceId'] == accepted and r['outcome'] == 'SUCCEEDED' for r in replies)
        result['checks']['eightReplaysSameAcceptance'] = True
        users = json.loads((ROOT / 'deploy/.acceptance-private/users.json').read_text())
        for name in ('ops_acceptance_viewer', 'ops_acceptance_b_member'):
            credential = next(u for u in users if u['username'] == name) if isinstance(users, list) else users[name]
            rejected = h['api'](endpoint + suffix, 'POST', request, auth['login'](credential), denied=True)
            assert rejected['httpStatus'] == 403, rejected
            result['checks'][name + 'Denied'] = rejected['httpStatus']
        changed = copy.deepcopy(request)
        changed['goalReview'] += ' This is a different replay payload.'
        rejected = h['api'](endpoint + suffix, 'POST', changed, h['token'], denied=True)
        assert rejected['httpStatus'] in (400, 409), rejected
        result['checks']['sameKeyDifferentContentDenied'] = rejected['httpStatus']
        rejected = h['api'](endpoint + '?projectId=ops-acceptance-b', 'POST', request, h['token'], denied=True)
        assert rejected['httpStatus'] == 403, rejected
        result['checks']['foreignProjectDenied'] = rejected['httpStatus']
        after = h['rows'](query)
        result['after'] = after
        assert before == after and all(r['hashMatches'] for r in after)
        result['checks']['ledgerUnchanged'] = True
        result['status'] = 'PASS_ACTUAL_HTTP_AND_MYSQL'
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': result['status'], 'checks': result['checks']}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--accepted-evidence', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    test(args.accepted_evidence, args.output)
